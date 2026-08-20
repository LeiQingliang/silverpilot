# cecsmsui-vue

[返回项目主页](../../README.md) · [部署说明](../../docs/DEPLOYMENT.md) · [前端设计系统](../../docs/FRONTEND_DESIGN_SYSTEM.md)

这是 SilverPilot 的 Vue 3 前端目录，`package.json` 中的 npm 包名是 `cecsmsui`。前端负责交互、可访问性和状态呈现，不保存模型密钥，也不代替后端做身份、权限或写操作确认。

文档快照：2026-08-20 已按当前路由、组件、锁定依赖、Vite/Nginx 代理和实际 Edge 显示回归核对；当前单元/UI 契约测试为 43/43，属性测试为 5/5，显示矩阵为 68/68。页面证据见[项目根 README](../../README.md#界面快照)。

本地调试必须按“Docker Redis → IDEA 后端 → VSCode 前端”的顺序启动：先在项目根目录执行 `scripts/docker-dev.ps1 redis`，再在 IDEA 运行同级后端的真实启动类 `CecsmsServeApplication`（默认端口 `8083`），最后用 VSCode 单独打开当前目录。任何 npm 命令或 VSCode Task 都不会启动 Java 或 Docker。完整拓扑见[项目根 README](../../README.md)。

## 快速定位

| 目标 | 入口 | 成功标准 |
| --- | --- | --- |
| 全 Docker 查看生产构建 | 根目录 `.\start-docker.cmd` | Nginx `8082` 健康，API 与媒体同源代理可用 |
| VSCode 热更新开发 | 本页[启动](#启动) | IDEA 后端与 Redis 先健康，Vite `8081` 启动 |
| 提交前静态门禁 | `npm run check` | Lint、Knip、43 项测试、5 组属性测试、构建和 production audit 通过 |
| 验证接口与身份边界 | `npm run audit:communication` | 当前全栈同源通信矩阵通过 |
| 验证页面与图片 | `npm run audit:display` | 目标桌面/移动路由无空白、坏图、溢出或控制台异常 |
| 排查设计与可访问性 | [前端设计系统](../../docs/FRONTEND_DESIGN_SYSTEM.md) | Token、焦点、动效和断点契约不回归 |

## 环境

- Node.js 24.19.0 LTS（`package.json` 只允许 Node 24 LTS 线）
- npm 12.0.2（允许同一 12.x stable 线）
- VSCode 插件：Vue - Official（项目已提供推荐）

`packageManager`、`engines`、`devEngines` 与 `.npmrc` 会在安装前共同拒绝错误的 Node/npm 主版本；`package-lock.json` 固定本次验收的精确依赖树。

## 启动

```powershell
npm ci
npm run dev
```

首次拉取、`package-lock.json` 变化或依赖损坏时执行 `npm ci`；依赖完整时直接执行 `npm run dev`。`predev` 会先验证后端聚合健康和 Redis 健康，未就绪时不会留下一个只能返回 502 的孤立 Vite。访问 `http://127.0.0.1:8081/login`。也可以在 VSCode 中执行 **Terminal > Run Task > Frontend: dev server**。

VSCode Task **Frontend: dev server** 只直接执行 `npm run dev`。`predev` 仅读验证 IDEA 后端与 Redis 健康，未就绪时前端明确失败，不会反向拉起后端或基础设施。需要重装依赖时，依次执行 **Frontend: stop dev server** 与 **Frontend: install clean dependencies**，以免 Windows 锁定 Vite/Rolldown 原生模块。

Vite 在 `npm run dev` 和 `npm run preview` 中都会将 `/api`、`/image`、`/file` 和 `/video` 代理到 `http://127.0.0.1:8083`。如需修改后端地址，将 `.env.example` 复制为 `.env.local`，并只修改 `VITE_BACKEND_TARGET`。因此生产构建本地预览不会出现“页面能打开、接口却返回 SPA HTML”的断链。

DeepSeek API Key 只配置在后端环境变量中，不能写入前端或任何 `VITE_*` 变量。

## 页面、代理与图片契约

| 浏览器请求 | 本地 Vite / Docker Nginx 行为 | 后端责任 |
| --- | --- | --- |
| `/api/**` | 代理到 Spring Boot，并去掉 `/api` 前缀 | REST、JWT、业务与 Agent API |
| `/image/**` | 原样代理 | 上传图片、种子图片和项目根 `image` 兜底 |
| `/file/**` | 原样代理 | 下载与项目根 `file` 兜底 |
| `/video/**` | 原样代理 | 视频与项目根 `video` 兜底 |

页面不得拼接 `D:\...` 等本机路径，也不得把后端地址写死为 `localhost:8083`。数据库和 API 返回的媒体应使用 `/image/...`、`/file/...` 或 `/video/...` Web 路径，由当前前端 origin 代理，这样 Local 与 Docker 模式使用同一组件代码。

`SmartImage.vue` 会优先尝试仓库已有图片的 WebP 伴生文件，失败后回退原始 URL，最终显示可读占位而不是破损图标。`audit:display` 会把可见坏图及 `/assets|image|file|video/` 的 HTTP 失败视为门禁失败。新增或移动媒体时应同时核对数据库 URL、后端三层资源根和 Compose 挂载，不能仅在前端加占位掩盖 404。

业务图片上传使用 `/api/upload/image`，共享校验器当前接受 JPEG/PNG/GIF 且最大 2 MiB；Agent 附件通道单独支持 JPEG/PNG/WebP，并在浏览器端压缩。改变格式或大小上限时必须同步前端校验、后端校验、错误文案和测试。

登录普通用户账号后访问 `/front/ai/AiChat`。小伴生活服务工作台提供任务编排、模型选择、结构化照护方案、图片预览、浏览器语音输入/朗读、摄像头取景、可验证办理进度、写操作确认卡和审计记录。报名、预约和取消必须在卡片中再次确认，前端不会保存任何模型 Key、上传图片或业务工具原始参数。DeepSeek 只承担文字和工具调用；图片语义理解必须另行配置豆包 Vision，界面不会把“拍照成功”伪装成“识别成功”。

当前产品有意固定为浅色主题：`useTheme.js` 会清除旧的 `silverpilot-theme` 偏好，并把任何深色请求归一为 `light`；界面不再显示主题切换按钮。语义 Token 仍统一覆盖正文、图表、代码块、输入区、弹窗、空/错状态和登录页。系统尊重 reduced-motion、键盘 focus-visible 和响应式断点；断网时显示可读状态，路由分块加载失败时保留重载入口，主动停止 Agent 请求不会误报成网络断开。

管理员的 `/AgentOperationsView` 从 `/api/chat/admin/overview` 读取脱敏的 Agent 运行、模型/多模态、WorkBuddy、IMA 知识和待确认状态；普通用户不会看到该路由或其数据。

设计 Token、页面壳层、断点和可访问性约束见 [前端设计系统](../../docs/FRONTEND_DESIGN_SYSTEM.md)。

## Element Plus 组件体系

应用根节点由 `ElConfigProvider` 统一提供中文 locale，Vite 的 `ElementPlusResolver` 负责组件和样式按需解析。登录、表单校验、上传、表格、分页、标签、弹窗、反馈、空状态和移动端业务卡片均使用当前 Element Plus API；上传组件还复用同一图片类型/大小校验器。契约测试禁止仍兼容但已进入淘汰周期的 `radio label`、`true-label/false-label`、`type="text"`、`custom-class` 和 Vue 2 `.sync` 写法。

## 验证

常用 npm 入口：

| 命令 | 内容 |
| --- | --- |
| `npm run lint` | ESLint 与 Vue 3 规则 |
| `npm run audit:dead-code` | Knip 无引用文件、依赖与导出检查 |
| `npm test` | Node 单元与 UI 契约测试 |
| `npm run build` | Vite 生产构建 |
| `npm run audit` | 生产依赖 high/critical 漏洞门禁 |
| `npm run check` | 依次执行以上全部静态门禁 |
| `npm run audit:communication` | 已启动栈的前后端同源通信回归 |
| `npm run audit:display` | 已启动栈的 Edge 浅色桌面/手机路由回归；可用环境变量扩展探索矩阵 |

```powershell
npm run check
```

该命令依次执行 ESLint（含 Vue 3 弃用规则）、Knip 无引用文件/依赖/导出检查、43 项 Node 单元/UI 契约测试、5 组 fast-check 属性测试、Vite 生产构建和生产依赖安全审计。当前 `src` 文件必须可从真实入口到达，空占位文件和注释掉的组件实现会被门禁拦截；文档、配置、脚本和静态数据则按各自入口审计，不冒充浏览器运行时代码。

前后端启动且根目录已生成被忽略的 `.env.docker` 后，执行同源通信回归：

```powershell
npm run audit:communication
```

该命令覆盖登录请求格式、JWT/角色隔离、用户端与后台主要 API、Excel、业务资源、上传权限、Agent 状态及 WorkBuddy MCP，且请求全部经过当前前端服务的 `/api` 或资源代理。

后端与前端均启动后，可额外执行显示回归（Microsoft Edge、34 个场景路由，其中包含活动详情缺参容错；默认覆盖桌面/手机与当前支持的浅色主题）：

```powershell
npm run audit:display
```

审计脚本默认与固定浅色产品契约一致；探索额外组合时可显式设置 `CECSMS_QA_THEMES` 和 `CECSMS_QA_VIEWPORTS`，例如 `desktop,tablet,mobile`。脚本会把空白页、错误跳转、页面级横向溢出、可见坏图、残留加载层、运行时异常、控制台错误/警告和 HTTP 5xx 视为失败；本地 JWT 只由 npm 脚本从被 Git 忽略的根目录 `.env.docker` 注入当前 Node 进程，不会写入报告或仓库。

2026-08-20 让 Edge 直接访问当前 Nginx 生产地址 `http://127.0.0.1:8082`，重跑浅色桌面/手机矩阵结果为 `68/68`。`/front/myActivity/MyActivityView` 的条件引用节点已修复；全部场景均未发现空白页、页面横向溢出、可见坏图、交互控件相交、残留加载层、运行时异常、控制台警告或 HTTP 5xx。

## 常见问题

| 现象 | 优先检查 |
| --- | --- |
| `npm run dev` 在 Vite 前失败 | 这是 `predev` 门禁；先确认 IDEA 后端和 Docker Redis 健康，不要绕过后留下 502 页面 |
| 页面能开但 API 返回 HTML/502 | `VITE_BACKEND_TARGET`、后端 8083、Vite/Nginx `/api` rewrite 与代理健康 |
| 图片 404 或只显示占位 | URL 是否为 Web 路径、后端资源根/挂载是否命中、原图/WebP 是否至少有一个存在 |
| 登录或注册页出现重复入口 | `FrontHeaderMenu.vue` 的认证页路由门禁和 `auth-navigation.test.mjs` |
| 页面刷新后空白 | 路由分块异常处理、Nginx SPA fallback、浏览器控制台和网络请求 |
| Windows 无法清理 `node_modules` | 先停止当前项目 Vite，再运行 VSCode 的 clean install Task，不要强杀无关 Node 进程 |
| 浏览器语音/摄像头不可用 | 安全上下文、站点权限、设备占用和浏览器支持；自动测试不能替代人工设备验收 |

测试数量、路由数、版本或显示结果变化时，只在实际命令重新通过后更新本页。依赖事实源是 [`package.json`](package.json)、[`package-lock.json`](package-lock.json) 和 [`vite.config.js`](vite.config.js)；运行拓扑以根目录 `compose.yaml` 与部署文档为准。
