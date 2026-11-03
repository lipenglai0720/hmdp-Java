# 本项目的 JMeter 完整压测流程

已按当前接口生成 6 套 Apache JMeter 5.6.3 计划，无需第三方插件。默认使用独立数据库、独立 Redis 和独立后端。GUI 用来检查结构和小流量调试，正式压力测试使用非 GUI 模式。[官方最佳实践](https://jmeter.apache.org/usermanual/best-practices.html)

**1. 文件与测试范围**

| 文件 | 用途 | 重点检查 |
| --- | --- | --- |
| login-load.jmx | 验证码登录 | HTTP 与业务成功、提取有效 token |
| read-load.jmx | 店铺详情、分类、附近、热门笔记、店铺笔记、优惠、我的订单、我的预约、技能目录 | 每个读接口的吞吐量、P95、P99、错误率 |
| seckill-load.jmx | 不同用户同时抢同一张券 | 提交、异步落库、库存和唯一约束 |
| duplicate-load.jmx | 同一用户并发重复抢购 | 最多一张新订单 |
| claim-load.jmx | 普通券领取 | 实际订单创建及重复领取校验 |
| reservation-load.jmx | 创建预约并取消 | 提取预约 ID，取消后没有活动预约残留 |
| manage.py | 准备、业务校验和停止测试环境 | 测试库前缀、端口隔离、进程身份 |
| analyze.py | 分析 JTL | 区分业务拒绝和系统错误，统计异步完成耗时 |
| scripts/*.groovy | 提取、断言、轮询 | 编译缓存，不记录 token 到 JTL 列 |
| build_plans.py | 重新生成 JMX | 统一公共配置 |
| VerifyJMeterGui.java | 不执行请求的 GUI 加载校验 | 覆盖界面打开文件时的组件初始化 |

这些 HTTP 计划不执行前端 JavaScript，不代表浏览器渲染性能。实际大模型生成和流式客服不在默认负载中，需要单独测试模型限流、首字延迟、总耗时和费用。

**2. 在 CMD 中准备环境**

本机找到 JMeter 5.6.3，官方发布页要求 Java 8+；本项目使用 JDK 17。[官方发布页](https://jmeter.apache.org/download_jmeter.cgi)

以下命令使用 Windows CMD 语法，不是 PowerShell：

~~~cmd
cd /d D:\hmdp-Java
set "JAVA_HOME=D:\biyeshejixuexi\e_software\Java_jdk\jdk-17.0.13"
set "HMDP_JMETER=D:\BaiduNetdiskDownload\apache-jmeter-5.6.3\bin\jmeter.bat"
set "HMDP_PY=D:\miniconda3\python.exe"
if not exist performance\jmeter\results mkdir performance\jmeter\results
~~~

先编译最新后端。下面使用本机 IDEA 内置 Maven；若安装了其他 Maven，请换成实际路径：

~~~cmd
call "D:\IntelliJ IDEA 2026.1.1\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" -f AI-dianping-backend\pom.xml -Dmaven.repo.local=C:\Users\31625\.m2\repository -DskipTests package
~~~

然后准备 200 名用户、两张各有 100 张库存的秒杀券以及一张普通券：

~~~cmd
"%HMDP_PY%" -X utf8 performance\jmeter\manage.py prepare --users 200 --stock 100
~~~

按提示输入 MySQL 密码；不会把密码写到文件或 Java 命令行。也可通过当前进程的 MYSQL_PWD 提供。若工具位置改变，可用 --java、--mysql、--redis-server 参数，详见 prepare --help。

脚本会创建：

- 新库 hmdp_perf_时间戳_随机串，保留正常 hmdp 库的记录。
- 独立 Redis 127.0.0.1:16379，新目录启动，无持久化。
- 独立后端 http://127.0.0.1:18081，两个 Redis 客户端均指向测试实例。
- .runtime/current.properties：JMeter 目标和数据路径。
- .runtime/具体批次/：CSV、fixture.json、服务日志和业务校验 JSON。

不会停止正常的 6379、8080、8081 服务。脚本发现测试端口已占用时会拒绝启动。模型 URL 指向本机禁用端口，避免默认用例触发外部模型调用。

项目补充 SQL 中的 USE hmdp 指令会仅在导入内存副本中去掉，再检查没有其他切库指令；原 SQL 文件不修改。

users.csv 每行是不同用户；same-user.csv 只有同一个用户一行，用于重复领取；claim-users.csv 使用单独的普通券。不同写用例使用不同优惠券。

验证码由准备脚本预先写入测试 Redis，有效两小时。登录仍调用真实 /user/login，但不包含短信通道耗时，不能称为“包含短信的完整登录吞吐量”。过期后续期：

~~~cmd
"%HMDP_PY%" -X utf8 performance\jmeter\manage.py renew-codes
~~~

CSV 和报告均已加入 Git 忽略规则，不要把真实账号、验证码或 token 提交到仓库。

**3. 先调通一名用户，再开始加压**

用 GUI 打开现成计划，同时加载当前批次的参数：

~~~cmd
call "%HMDP_JMETER%" -t performance\jmeter\read-load.jmx -q performance\jmeter\.runtime\current.properties -Jthreads=1 -Jloops=1
~~~

计划结构包含 HTTP 默认请求值、HTTP 信息头管理器、CSV 数据文件设置、登录请求、内部业务循环和 JSR223 断言。

每个线程先读取自己的 CSV 行并登录，提取返回的 token，再执行后续请求。authorization=${token} 是原样 token，不需要自行添加 Bearer。

若手动搭建登录请求：

- 方法 POST，路径 /user/login。
- Body Data：{"phone":"${phone}","code":"${code}"}。
- Content-Type：application/json。
- JSON 提取器变量名 token，JSONPath 为 $.data，默认值 TOKEN_NOT_FOUND。
- 后续请求的 HTTP 信息头添加 authorization=${token}。
- 断言 HTTP 状态以及 JSON 的 success=true，不能只检查 HTTP 200。

现成计划用 Groovy 同时提取并校验 token，登录失败会停止该线程，避免未登录请求形成假压力。业务参数写入 Parameters 或 JSON Body Data。

JMeter 进程正常退出不代表所有断言都通过；即使有失败样本，进程也可能返回 0。请检查 JTL/HTML 的错误率，并运行 analyze.py；该脚本在发现失败时返回非零退出码。

只在这种单用户调试时添加“查看结果树”。正式压测禁用该监听器，使用 -l 保存 CSV/JTL。[资源使用建议](https://jmeter.apache.org/usermanual/best-practices.html#reducing-resource-requirements)

**4. 登录、预热和逐级增加读压力**

先做 3 用户、每人 1 次登录：

~~~cmd
call "%HMDP_JMETER%" -n -t performance\jmeter\login-load.jmx -q performance\jmeter\.runtime\current.properties -Jthreads=3 -Jramp_s=1 -Jloops=1 -l performance\jmeter\results\login-smoke.jtl -j performance\jmeter\results\login-smoke.log
~~~

再用 5 用户预热 30 秒：

~~~cmd
call "%HMDP_JMETER%" -n -t performance\jmeter\read-load.jmx -q performance\jmeter\.runtime\current.properties -Jthreads=5 -Jramp_s=5 -Jduration_s=30 -Jthink_ms=100 -l performance\jmeter\results\warmup.jtl -j performance\jmeter\results\warmup.log
~~~

20 用户、20 秒逐步启动、总共 120 秒的读压测：

~~~cmd
call "%HMDP_JMETER%" -n -t performance\jmeter\read-load.jmx -q performance\jmeter\.runtime\current.properties -Jthreads=20 -Jramp_s=20 -Jduration_s=120 -Jthink_ms=100 -l performance\jmeter\results\read-20.jtl -j performance\jmeter\results\read-20.log -e -o performance\jmeter\results\read-20-html
~~~

然后分别改为 50、100、200 用户，每次使用新结果文件和报告目录。每档建议先持续 2～5 分钟，确定稳定负载后再做 30～60 分钟持续测试。出现持续异常或积压失控时停止并定位。

CSV 用户数必须不小于线程数；不足时线程会在文件结尾停止。外层线程组循环一次以固定每个线程的用户，内部循环重复业务请求。read 场景默认循环至 duration_s 到期；-Jloops=1 可用于一次完整读循环的冒烟。其他写计划默认有限次数，不使用读计划的持续时间参数。

线程数不等于 QPS。9 类读请求串行执行，think_ms 为每个读请求前的等待，吞吐量还取决于服务响应和压测机能力。

analyze.py 的接口吞吐量以整个选定统计窗口为分母，窗口内可能包含登录和逐步启动；字段名明确为 requests_per_second_over_measured_window。一次秒杀突发的短时结果不能外推为持续 QPS，容量结论需要稳态与持续测试支持。

排除前 20 秒逐步启动阶段，查看稳定窗口：

~~~cmd
"%HMDP_PY%" -X utf8 performance\jmeter\analyze.py performance\jmeter\results\read-20.jtl --skip-first-seconds 20 --expected-threads 20 --output performance\jmeter\results\read-20-summary.json
~~~

analyze.py 使用 nearest-rank 分位数，与 HTML 报告的估计方法可能略有差异。对比时使用相同统计窗口和口径。

**5. 多人抢购：200 名不同用户抢 100 张券**

确认这张测试券未被此前的写冒烟用例消耗。查询计划不会消费库存：

~~~cmd
call "%HMDP_JMETER%" -n -t performance\jmeter\seckill-load.jmx -q performance\jmeter\.runtime\current.properties -Jthreads=200 -Jramp_s=10 -Jattempts=1 -Jpoll_limit=100 -Jpoll_ms=200 -l performance\jmeter\results\seckill-200.jtl -j performance\jmeter\results\seckill-200.log -e -o performance\jmeter\results\seckill-200-html
"%HMDP_PY%" -X utf8 performance\jmeter\analyze.py performance\jmeter\results\seckill-200.jtl --expected-threads 200 --output performance\jmeter\results\seckill-200-summary.json
"%HMDP_PY%" -X utf8 performance\jmeter\manage.py verify --case seckill --expected-count 100
~~~

同步定时器在 POST 前集合线程，最多等 30 秒，再发起一次突发请求。

| 标签/字段 | 含义 |
| --- | --- |
| seckill.submit | 提交接口延迟 |
| outcome=ACCEPTED | Lua 通过并入队，尚未确认 MySQL 落库 |
| SOLD_OUT / DUPLICATE | 预期业务拒绝，单独统计，不算技术错误 |
| SYSTEM_ERROR | HTTP 错误、超时、未知业务失败、异常响应 |
| seckill.poll | 查询 /voucher-order/result/{order_id} |
| order.final | CREATED 才算订单创建成功；FAILED/ERROR/超时均失败 |
| business_ms | 从实际提交请求到观测到 CREATED 的时间，不含同步集合等待，包含轮询观测延迟 |

默认最多轮询 100 次，间隔 200ms；每次 HTTP 另受 response_ms 超时限制，网络故障时总等待可能超过 20 秒。按真实消费速度选择合理上限，避免无限等待掩盖积压。

verify 等待队列排空后检查订单数、重复用户、MySQL/Redis 库存、已购用户数、pending 和 lag。理想终态：订单 100、两边库存 0、重复用户 0、pending=0、lag=0。业务售罄不是系统错误，但也不是购买成功。

手动核对时，在本批次 hmdp_perf_* 测试库执行：

~~~sql
SELECT COUNT(*) FROM tb_voucher_order WHERE voucher_id=800000001;
SELECT stock FROM tb_seckill_voucher WHERE voucher_id=800000001;
SELECT user_id,voucher_id,COUNT(*) AS cnt
FROM tb_voucher_order
WHERE voucher_id=800000001
GROUP BY user_id,voucher_id
HAVING COUNT(*)>1;
~~~

**6. 同一账号并发重复领取：50 线程**

~~~cmd
call "%HMDP_JMETER%" -n -t performance\jmeter\duplicate-load.jmx -q performance\jmeter\.runtime\current.properties -Jthreads=50 -Jramp_s=5 -Jattempts=1 -l performance\jmeter\results\duplicate-50.jtl -j performance\jmeter\results\duplicate-50.log
"%HMDP_PY%" -X utf8 performance\jmeter\analyze.py performance\jmeter\results\duplicate-50.jtl --expected-threads 50
"%HMDP_PY%" -X utf8 performance\jmeter\manage.py verify --case duplicate --expected-count 1
~~~

使用另一张券以及循环读取的同一个账号。初始库存 100 时，预期新订单 1、剩余库存 99，其余请求提示重复。不能用同一个 token 的 200 线程代替“200 名不同用户”测试。

重新比较不同抢购并发时，先停止并重建测试环境。写用例会消费库存，不能直接拿已售罄的旧券继续跑然后比较性能。

**7. 普通领券、预约创建与取消**

~~~cmd
call "%HMDP_JMETER%" -n -t performance\jmeter\claim-load.jmx -q performance\jmeter\.runtime\current.properties -Jthreads=200 -Jramp_s=10 -l performance\jmeter\results\claim-200.jtl -j performance\jmeter\results\claim-200.log
"%HMDP_PY%" -X utf8 performance\jmeter\manage.py verify --case claim --expected-count 200

call "%HMDP_JMETER%" -n -t performance\jmeter\reservation-load.jmx -q performance\jmeter\.runtime\current.properties -Jthreads=50 -Jramp_s=10 -Jloops=1 -l performance\jmeter\results\reservation-50.jtl -j performance\jmeter\results\reservation-50.log
"%HMDP_PY%" -X utf8 performance\jmeter\manage.py verify --case reservation --expected-count 50
~~~

预约用例先创建再取消，预期 50 条新预约均为已取消，活动预约 0。每个写计划也要用 analyze.py 检查 JTL；数据库终态正确，不代表中间没有请求失败。

**8. 监控、瓶颈判断与报告**

| 指标 | 检查方式 |
| --- | --- |
| 错误率 | HTTP 错误、SYSTEM_ERROR、FAILED/超时，与售罄/重复区分 |
| 吞吐量 | 按相同接口标签和稳态时间窗口比较，不把轮询 QPS 当提交 QPS |
| P50/P95/P99 | 查看尾延迟，不能只看平均值 |
| business_ms | 成功订单从提交至可观测落库的耗时 |
| Stream lag/pending | lag 为尚未交付；pending 为已交付未 ACK；XLEN 是保留消息总数，不等于积压 |
| JVM | 任务管理器查看 CPU/内存；jps -l 定位 PID；jstat -gcutil PID 1000 60 观察 GC |
| Redis | 测试端口 INFO stats、INFO memory、INFO clients |
| MySQL | SHOW GLOBAL STATUS：Threads_running、Queries、Slow_queries、Innodb_row_lock_waits；累计计数取两次采样差值 |
| 压测机 | 检查 JMeter 自身 CPU、内存、GC；与服务端同机时存在资源争用 |

开始前先定验收目标，例如某并发下查询 P95 的阈值、意外失败率阈值和允许的最大积压。不能把尚未测出的目标写成性能结论。

缓存专项可复制读计划，只保留 /shop/1；区分新环境冷缓存与预热后热缓存、单一热点与分散店铺，同时观察数据库查询量和锁等待。

AI 生成接口需另外断言 fallback=false，本地模板耗时不代表模型推理耗时。流式客服区分首字延迟与整个回答完成时间；默认测试实例禁用外部模型，不能用于模型容量结论。

HTML 报告打开输出目录的 index.html。也可从已有 JTL 重新生成：

~~~cmd
call "%HMDP_JMETER%" -g performance\jmeter\results\read-20.jtl -q performance\jmeter\.runtime\current.properties -o performance\jmeter\results\read-20-report-new
~~~

JTL 使用新文件名，HTML 输出目录须不存在或为空。[官方报告说明](https://jmeter.apache.org/usermanual/generating-dashboard.html)

报告记录硬件、JDK/JMeter/Redis/MySQL 版本、JVM 参数、并发、ramp-up、持续时间、数据量、是否热缓存、是否直连后端。默认测试后端是 18081；测试 Nginx 时另配一个指向 18081 的代理，再设 -Jport=测试代理端口 -Jpath_prefix=/api。原 8080 仍指向正常业务后端，不应直接使用测试夹具切过去。

**9. 停止和重建**

~~~cmd
"%HMDP_PY%" -X utf8 performance\jmeter\manage.py stop --drop-test-database
~~~

脚本核对 PID 和本批次标识，只停止自己启动的服务，删除库限定本批次 hmdp_perf_*；CSV、日志和报告保留便于复查。重新 prepare 创建全新批次。若只想停服务保留测试库，用 stop 不带删除参数。

**10. GUI 加载异常与校验**

若打开计划时报 `NoSuchElementException: Property HTTPsampler.Arguments is unset`，检查 `HTTP Defaults` 是否缺少下面的空参数容器。没有默认参数时也需要这个节点，JMeter 5.6.3 的界面会读取它：

~~~xml
<elementProp name="HTTPsampler.Arguments" elementType="Arguments"
             guiclass="HTTPArgumentsPanel" testclass="Arguments" enabled="true">
  <collectionProp name="Arguments.arguments" />
</elementProp>
~~~

项目生成器和六份计划均已补齐。该问题可能在命令行执行时没有暴露，因此 XML 解析和非 GUI 冒烟不能代替界面加载校验。若旧窗口只剩空测试计划，关闭时选择不保存，再重新打开修复文件。

下面的 JDK 17 源文件运行命令会使用本机 JMeter 的 `JMeterTreeModel.addSubTree` 加载全部组件，在 Swing 事件线程上验证界面配置。它先删除内存副本中的参数节点，确认能复现原异常，再验证六份完整计划；不会启动 JMeter 引擎、调用接口或消耗库存：

~~~cmd
"%JAVA_HOME%\bin\java.exe" -Djava.awt.headless=true ^
-Djmeter.logfile=performance/jmeter/results/gui-load-check.log ^
-Dlog4j.configurationFile="D:\BaiduNetdiskDownload\apache-jmeter-5.6.3\bin\log4j2.xml" ^
--class-path "D:\BaiduNetdiskDownload\apache-jmeter-5.6.3\lib\*;D:\BaiduNetdiskDownload\apache-jmeter-5.6.3\lib\ext\*" ^
performance\jmeter\VerifyJMeterGui.java ^
"D:\BaiduNetdiskDownload\apache-jmeter-5.6.3" ^
performance\jmeter\claim-load.jmx ^
performance\jmeter\duplicate-load.jmx ^
performance\jmeter\login-load.jmx ^
performance\jmeter\read-load.jmx ^
performance\jmeter\reservation-load.jmx ^
performance\jmeter\seckill-load.jmx
~~~

正确输出应包含六个计划各自的 `PASS`，并以 `PASS: all plans loaded; no samplers executed` 结束。
