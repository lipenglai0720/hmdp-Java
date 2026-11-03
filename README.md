# AI 本地生活系统

本项目使用 Spring Boot 3.5.6、JDK 17、MySQL 8、Redis 和 Vue 2。前端由 nginx 托管，默认访问 http://localhost:8080/；后端默认监听 8081。

页面与接口的对应关系见 `AI-dianping-frontend/FEATURE-COVERAGE.md`。

JMeter 压测计划、独立测试环境和完整操作步骤见 [压测指南](performance/jmeter/README.md)。

## 启动

1. 首次克隆后，将 `AI-dianping-backend/src/main/resources/application.example.yml` 复制为同目录的 `application.yml`。启动 MySQL 和 Redis，并在 `application.yml` 中配置数据库、Redis 和模型服务，也可通过 `DB_PASSWORD`、`REDIS_PASSWORD` 和 `DEEPSEEK_API_KEY` 环境变量提供凭据。`application.yml` 仅保存在本机，已由 Git 忽略；配置模板中不要填写实际密钥。
2. 新建空的 `hmdp` 数据库时，可导入根目录 `db.sql`。它包含删表语句，**不要导入已有业务数据的数据库**。
3. 已有数据库请依次执行以下补充脚本，它们可重复执行并保留已有记录：
   - `AI-dianping-backend/db/ai_skill_init.sql`：AI 技能及记录表。
   - `AI-dianping-backend/db/reservation_init.sql`：预约和商家授权表。
   - `AI-dianping-backend/db/demo_data.sql`：可选的本地演示数据。请在预约表创建后执行。
4. 在 IDEA 运行 `HmDianPingApplication`。订单消费组和店铺 GEO 坐标由后端启动时自动初始化。
5. 启动 `AI-dianping-frontend/nginx.exe`。修改 nginx 配置后执行 `nginx.exe -s reload`，修改页面后刷新浏览器。

如果提示端口 8081 已占用，请先确认占用它的是哪个进程，再停止不需要的进程或修改后端及 nginx 的端口配置。

## 预约

在店铺详情选择“预约到店”，填写到店时间、人数和备注后提交。预约最初为“待确认”，用户可以在“个人中心 → 我的预约”查看和取消。商家可以在同一页面的“商家预约”中确认或拒绝所管理店铺的申请。

商家账号必须由管理员手动绑定店铺。用户先用手机号登录注册，再由管理员将下面的手机号和店铺 ID 替换为实际值，在 `hmdp` 数据库执行：

```sql
SET @shop_id = 1;                       -- 替换为实际店铺 ID
SET @merchant_phone = '商家注册手机号'; -- 替换为实际手机号

INSERT IGNORE INTO tb_shop_manager (user_id, shop_id)
SELECT id, @shop_id
FROM tb_user
WHERE phone = @merchant_phone
  AND EXISTS (SELECT 1 FROM tb_shop WHERE id = @shop_id);
```

预约接口：

```text
POST /reservation                 提交预约
GET  /reservation/my              当前用户的预约
GET  /reservation/{id}            当前用户的预约详情
PUT  /reservation/{id}/cancel     用户取消预约
GET  /reservation/managed         已授权店铺的预约
PUT  /reservation/{id}/confirm    商家确认
PUT  /reservation/{id}/reject     商家拒绝
```

## 演示数据与功能边界

演示脚本添加的店铺、笔记、评论、优惠、用户和预约均有“演示”标识，是虚构数据；演示用户手机号是不可用于登录的标识，演示优惠不能用于实际消费。脚本不会创建真实订单或模拟 AI 运行记录。

目前验证码只输出到后端开发日志，尚未接入短信服务；预约尚未提供商家通知、时段容量控制或真实支付。正式对外使用前，需要完成这些业务能力、安全配置和实际部署环境下的测试。

AI Skills 的流程为：选择技能、填写表单、渲染模板、调用模型、保存记录并返回结果。AI 输出作为草稿或建议；预约由用户提交，并由已授权商家确认或拒绝。MCP 当前只提供公开目录查询，不提供未经身份验证的个人预约、订单和 AI 调用。
