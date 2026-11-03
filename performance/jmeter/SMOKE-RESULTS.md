# JMeter 测试计划冒烟验证记录

验证日期：2026-11-02。使用本机 Apache JMeter 5.6.3、JDK 17，以非 GUI 方式运行项目内生成的六套计划。

这是测试计划与业务校验的功能验证，规模为 3 个虚拟用户、每张秒杀券初始库存 2；没有进行 200 用户容量测试或长时间稳定性测试，不能据此得出系统最大 QPS、容量或正式 SLA。

测试环境由 manage.py 新建：独立 hmdp_perf_* 数据库、Redis 16379、后端 18081。模型服务 URL 指向本机禁用端口，未调用外部模型。

| 场景 | 实际结果 | 校验 |
| --- | --- | --- |
| 登录 | 3 次真实登录请求均取得 token | 通过 |
| 混合读取 | 3 用户各执行 9 类业务读取，共 27 次读取，另有 3 次登录；无失败样本 | 通过 |
| 多用户秒杀 | 3 次提交，2 次受理并最终 CREATED，1 次明确售罄 | 通过 |
| 同账号重复秒杀 | 3 次提交，1 次最终 CREATED，2 次明确重复领取 | 通过 |
| 普通券领取 | 3 次领取，3 张最终订单 | 通过 |
| 预约闭环 | 3 条新预约均成功取消，活动预约 0 | 通过 |
| 故意使用错误验证码 | HTTP 200、success=false 被标记为失败，线程停止，后续业务请求 0 | 通过 |
| HTML 报告 | 已由实际 JTL 生成标准 JMeter HTML Dashboard | 通过 |

秒杀数据库和 Redis 终态已单独检查：

- 多用户券：MySQL 订单 2、MySQL 库存 0、Redis 库存 0、Redis 已购用户 2。
- 重复领取券：MySQL 订单 1、MySQL 库存 1、Redis 库存 1、Redis 已购用户 1。
- 两种场景均无重复 user_id + voucher_id，Stream pending=0、lag=0。
- 普通券订单 3，无重复用户；预约总数 3，已取消 3，活动预约 0。

正常六套计划的分析结果均为 passed=true。负例按预期产生 1 个失败样本，证明不会仅凭 HTTP 200 判定登录成功。

结果文件保留在已加入 Git 忽略的 results/，包括 smoke-summary.json、各场景 JTL/日志，以及 smoke-read-html/index.html。数据库校验 JSON 位于本地 .runtime/ 对应批次目录。

验证后已停止本次创建的测试服务并删除本次临时数据库；正常 6379/8080/8081 服务保留。再次正式测试前，请按 README 重新 prepare；测试账号、验证码和库存会为新批次重新创建。

**2026-11-02 GUI 加载回归**

用户实际打开计划时，`jmeter.log` 中确认 `HttpDefaultsGui.configure` 因默认请求配置缺少 `HTTPsampler.Arguments` 抛出 `NoSuchElementException`。已在生成器和六份 JMX 中增加空 `Arguments.arguments` 容器，并恢复加载失败后只剩空测试计划的秒杀文件。修复前的六份文件保存在本地 `.runtime/gui-fix-backup-20261102-194152/`。

使用本机 JMeter 5.6.3、JDK 17 运行 `VerifyJMeterGui.java`，在无窗口模式下调用实际 Swing 树加载代码 `JMeterTreeModel.addSubTree`，覆盖 `addComponent -> GUI.configure -> modifyTestElement`。先对内存副本删除参数节点，成功复现原异常，再加载完整文件：

| 计划 | 已加载元素 | HTTP 采样器 | 结果 |
| --- | ---: | ---: | --- |
| claim-load.jmx | 18 | 3 | PASS |
| duplicate-load.jmx | 18 | 3 | PASS |
| login-load.jmx | 8 | 1 | PASS |
| read-load.jmx | 27 | 10 | PASS |
| reservation-load.jmx | 13 | 3 | PASS |
| seckill-load.jmx | 18 | 3 | PASS |

该回归只加载界面组件，不执行采样器，没有进行新一轮登录、秒杀或性能测试。实际可见窗口的显示操作未自动执行。
