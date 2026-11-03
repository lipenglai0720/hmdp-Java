# 部署指南

[返回项目首页](../README.md)

## 环境与本地配置

需要 JDK 17、Maven、MySQL 8 和 Redis。后端配置模板为 `AI-dianping-backend/src/main/resources/application.example.yml`，复制到同目录的 `application.yml` 后再启动。

| 配置 | 说明 |
| --- | --- |
| `spring.datasource.url` | 数据库连接，默认 `localhost:3306/hmdp` |
| `spring.datasource.username` | 数据库账号 |
| `DB_PASSWORD` | 数据库密码环境变量 |
| `spring.data.redis.host` / `port` | Redis 地址 |
| `REDIS_PASSWORD` | Redis 密码环境变量 |
| `DEEPSEEK_API_KEY` | 两种聊天模型共用的 API 密钥 |
| `langchain4j.open-ai.*.base-url` / `model-name` | 实际使用的模型服务与模型名 |
| `HM_UPLOAD_PATH` | 上传文件绝对目录，需与 nginx 的 `/imgs/` 映射一致 |

不要将实际密钥写入模板。`application.yml`、本地上传文件、备份和日志已加入 Git 忽略规则。

## 空数据库初始化

仅对新建空库导入根目录 `db.sql`，该文件包含 `DROP TABLE`。已有业务数据时，请先备份并使用下方增量脚本。

MySQL 客户端从项目根目录进入目标数据库后，按顺序执行：

```sql
SOURCE db.sql;
SOURCE AI-dianping-backend/db/ai_skill_init.sql;
SOURCE AI-dianping-backend/db/reservation_init.sql;
SOURCE AI-dianping-backend/db/performance_upgrade.sql;
```

需要虚构演示内容时再执行：

```sql
SOURCE AI-dianping-backend/db/demo_data.sql;
```

已有数据库只执行需要的增量脚本，不重复导入根目录 `db.sql`。`performance_upgrade.sql` 会添加订单、关注的唯一约束和查询索引；如果历史记录重复，应先核对重复数据再升级。

## 启动后端与前端

后端可在项目根目录执行：

```bash
mvn -f AI-dianping-backend/pom.xml spring-boot:run
```

也可在 IDEA 中运行 `com.hmdp.HmDianPingApplication`。启动时会准备订单消费组与店铺 GEO 数据，MySQL 与 Redis 需要先可用。AI 对话和内容生成还需要配置可用的模型服务。

Windows 前端启动与管理见 [前端说明](../AI-dianping-frontend/README.md)。`conf/nginx.conf` 的默认端口为 8080，接口转发到 8081。将 `/imgs/` 的 `alias` 改为自己的上传绝对目录，并确保与后端配置一致。

Linux / macOS 使用自行安装的 nginx。将静态站点根目录指向 `AI-dianping-frontend/html/hmdp`，复制 `/api` 反向代理规则，并调整上传路径和错误页路径。

## 常用验证

```bash
mvn -f AI-dianping-backend/pom.xml test
node scripts/verify_frontend.cjs
```

正常单元测试不需要批量初始化业务数据。手工数据与并发测试通过 `HMDP_RUN_MANUAL_TESTS=true` 显式开启；压测应使用 [独立 JMeter 环境](../performance/jmeter/README.md)。

## 预约与商家绑定

用户在店铺详情提交到店预约，在个人中心查看和取消。已授权商家在预约管理页面确认或拒绝所管理店铺的申请。

商家账号由管理员绑定。将下方手机号和店铺 ID 替换为实际值，在 `hmdp` 数据库中执行：

```sql
SET @shop_id = 1;
SET @merchant_phone = '商家注册手机号';

INSERT IGNORE INTO tb_shop_manager (user_id, shop_id)
SELECT id, @shop_id
FROM tb_user
WHERE phone = @merchant_phone
  AND EXISTS (SELECT 1 FROM tb_shop WHERE id = @shop_id);
```

| 接口 | 用途 |
| --- | --- |
| `POST /reservation` | 提交预约 |
| `GET /reservation/my` | 当前用户的预约 |
| `GET /reservation/{id}` | 预约详情 |
| `PUT /reservation/{id}/cancel` | 用户取消预约 |
| `GET /reservation/managed` | 商家管理的预约 |
| `PUT /reservation/{id}/confirm` | 商家确认 |
| `PUT /reservation/{id}/reject` | 商家拒绝 |
