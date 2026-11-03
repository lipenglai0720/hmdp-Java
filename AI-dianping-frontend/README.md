# 本地生活平台前端

[项目首页](../README.md) · [功能对照](FEATURE-COVERAGE.md) · [部署指南](../docs/deployment.md)

前端使用 Vue 2、原生 Fetch API 和 nginx，无需安装 npm 依赖或执行构建。默认访问 <http://localhost:8080/>，后端默认监听 8081。

## 本地运行

在 `AI-dianping-frontend` 目录的 PowerShell 中执行：

```powershell
New-Item -ItemType Directory -Path logs,temp -Force | Out-Null
.\nginx.exe -t
Start-Process -FilePath ".\nginx.exe" -WorkingDirectory (Get-Location).Path -WindowStyle Hidden
```

修改 HTML、CSS 或 JavaScript 后刷新页面。修改 nginx 配置后执行 `.\nginx.exe -s reload`；停止时执行 `.\nginx.exe -s quit`。

`conf/nginx.conf` 中的 `/imgs/` 目录需要与后端上传目录一致。Linux / macOS 可使用自行安装的 nginx，按部署指南调整静态根目录、上传目录与反向代理。

## 页面入口

| 页面 | 路径 |
| --- | --- |
| 发现首页 | `/` |
| 店铺搜索与列表 | `/shop-list.html` |
| 店铺详情与优惠 | `/shop-detail.html?id=1` |
| 到店预约 | `/reservation.html?shopId=1` |
| 用户与商家预约处理 | `/reservations.html` |
| 验证码登录 | `/login.html` |
| AI 创作工作台 | `/skills.html` |
| AI 对话 | `/aiassistant.html` |
| 我的订单 | `/orders.html` |
| 商家管理 | `/merchant.html` |
| 个人中心 | `/info.html` |
| 笔记详情与发布 | `/blog-detail.html?id=4`、`/blog-edit.html` |

商家权限需要管理员绑定店铺，验证码当前只输出到后端开发日志。`login2.html` 作为兼容入口跳转到验证码登录页。

## 源码与验证

| 目录 / 文件 | 职责 |
| --- | --- |
| `html/hmdp/views/` | Vue 页面模板 |
| `html/hmdp/js/app.js` | 页面加载入口 |
| `html/hmdp/js/pages-*.js` | 页面业务逻辑 |
| `html/hmdp/css/` | 主题、组件和响应式样式 |
| `html/hmdp/assets/` | 标记、头像与本地配图 |
| `conf/nginx.conf` | 静态服务、上传目录和 API 代理 |

从项目根目录执行 `node scripts/verify_frontend.cjs`，检查 Vue 模板编译、资源引用和 JavaScript 语法。第三方 JavaScript、字体与 nginx 许可证保留在原文件或 `docs/` 目录中。
