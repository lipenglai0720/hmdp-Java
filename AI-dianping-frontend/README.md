# 黑马点评前端

前端沿用 Vue 2、Element UI 和 nginx，直接访问 http://localhost:8080/。本次界面升级不需要安装 npm 依赖或执行构建。

## 启动

后端监听 8081。nginx 已运行时，修改 HTML、CSS、JavaScript 后刷新浏览器即可；如果浏览器仍显示旧样式，使用 Ctrl + F5。

首次启动前端：

~~~powershell
cd D:\hmdp-Java\AI-dianping-frontend
.\nginx.exe -t
Start-Process -FilePath ".\nginx.exe" -WorkingDirectory (Get-Location).Path -WindowStyle Hidden
~~~

nginx 的管理命令：

~~~powershell
.\nginx.exe -s reload
.\nginx.exe -s quit
~~~

## 页面

| 页面 | 路径 |
| --- | --- |
| 发现首页 | / |
| 商户分类、搜索与列表 | /shop-list.html |
| 店铺信息与优惠 | /shop-detail.html?id=1 |
| 提交到店预约 | /reservation.html?shopId=1 |
| 用户预约与商家处理 | /reservations.html |
| 手机验证码登录 | /login.html |
| AI 创作工作台 | /skills.html |
| AI 对话助手 | /aiassistant.html |
| 我的订单 | /orders.html |
| 商家管理 | /merchant.html |
| 个人中心 | /info.html |
| 账户资料 | /info-edit.html |
| 作者主页 | /other-info.html?id=2 |
| 笔记阅读 | /blog-detail.html?id=4 |
| 笔记发布 | /blog-edit.html |
| 关于与帮助 | /about.html |

旧的 login2.html 跳转到验证码登录。AI 对话页面通过 POST 发送问题，服务端按账号隔离会话。商家预约管理需要先绑定店铺账号，绑定方法见项目根目录 README。当前验证码仅在后端开发日志中显示，尚未接入短信服务。

## 维护入口

完整的页面与接口对应关系见 `FEATURE-COVERAGE.md`。

- html/hmdp/css/ui.css：色彩、排版、导航、卡片、表单及响应式布局。
- html/hmdp/js/ui.js：共用导航、图标、图片兜底和展示工具。
- html/hmdp/js/footer.js：手机底部导航。
- html/hmdp/js/common.js：请求地址、登录凭证、网络异常与超时反馈。
- html/hmdp/assets/：本地品牌标记、默认头像和城市生活配图。
- html/hmdp/assets/image-prompts.json：内置 image_gen 生成配图时使用的提示词。

图片优先使用接口返回的原始图片。缺图时使用带“氛围配图”标记的本地封面，避免把示意图当作商户实拍。笔记文字草稿会暂存在当前浏览器会话中；退出登录或发布成功会清除相应草稿。

## 2026-10-23 界面升级验证记录

以下记录针对当时的访客界面，后续新增功能另行进行了接口验证。

- 1440 像素桌面与 390 像素手机视口下检查主要页面。
- 验证商户分类、真实店名搜索、无结果反馈、加载更多及当前结果排序。
- 商户列表由 5 家追加至 9 家，没有重复。
- 验证普通优惠券详情弹窗、登录必填校验、AI 场景切换。
- 验证笔记文字刷新后恢复，以及清空文字操作。
- 检查页面无横向溢出、无破损图片、无 JavaScript 运行错误。
- JavaScript 语法与静态资源引用检查通过。
- 验收使用访客状态，未发送验证码、发布笔记、创建订单或实际调用付费模型。

截图位于 D:\hmdp-Java\artifacts\frontend-redesign。
改版前的页面备份位于 D:\hmdp-Java\.codex-backups\frontend-before-redesign-20261023-171020.zip。
