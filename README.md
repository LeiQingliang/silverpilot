# SilverPilot · 智慧养老业务 Agent 平台

SilverPilot 帮助社区居民用日常语言找活动、约上门服务、订餐和查看本人的体检报告。以预约为例，居民告诉「小伴」需要什么，小伴查询服务目录并列出待确认信息，居民确认后由后端创建订单。社区管理员可以查看办理记录，追踪哪些请求需要改进。

**[观看完整演示（13 分 32 秒）](https://leiqingliang.github.io/silverpilot/)** · **[打开在线体验](https://silverpilot.up.railway.app/login)** · [演示账号](#在线体验) · [本地启动](#快速开始)

<p align="center">
  <a href="docs/screenshots/agent-workspace-20260821-0202.png"><img src="docs/screenshots/agent-workspace-20260821-0202.png" width="800" alt="居民端小伴工作台，提供找活动、找服务、安排膳食和看报告四个入口，右侧显示办理进度" /></a>
</p>

## 演示视频

https://github.com/user-attachments/assets/65eecf9c-077e-4c51-855c-c71717d7780a

**跳到操作画面**　[04:13 查询活动](https://leiqingliang.github.io/silverpilot/media/silverpilot-demo.mp4#t=253) · [06:35 查看照护建议](https://leiqingliang.github.io/silverpilot/media/silverpilot-demo.mp4#t=395) · [07:25 核对服务订单](https://leiqingliang.github.io/silverpilot/media/silverpilot-demo.mp4#t=445) · [11:25 查看模型与知识来源](https://leiqingliang.github.io/silverpilot/media/silverpilot-demo.mp4#t=685)

上方播放器为流畅版。高清播放页提供 2560 × 1528 原画，默认使用浏览器支持的最高倍速，可切回 1× 观看操作细节。[高清与倍速播放](https://leiqingliang.github.io/silverpilot/)

## 在线体验

打开登录页，输入共享账号、密码和页面验证码。演示使用合成数据，请用虚构的示例信息体验。[公网登录](https://silverpilot.up.railway.app/login)

| 想体验什么 | 用户名 | 密码 | 登录后从哪里开始 |
| --- | --- | --- | --- |
| 居民如何找活动 | `linge` | `123456` | 打开「小伴生活助理」，点击「找活动」，在「办理过程」查看查询轨迹，再到「活动」页核对结果。 |
| 管理员如何跟踪办理情况 | `admin` | `123456` | 打开「智能服务运营 → 小伴运营中心」，在「最近运行记录」查看状态、耗时和工具调用次数。 |

两个公网账号均为只读。要体验**确认预约并生成订单**，请走下方本地流程。公网 AI 额度由同一演示账号的访客共享，遇到 `429` 可稍后再试。站点暂时不可用时，仍可观看录屏。

## 快速开始

### Windows 一键启动

准备 Git、Windows PowerShell 5.1 或 PowerShell 7，并启动带 Compose 的 Docker Desktop。该模式在容器中运行前端、后端、MySQL 和 Redis。首次构建需要联网获取镜像和依赖。

```powershell
git clone https://github.com/LeiQingliang/silverpilot.git
cd silverpilot
.\start-docker.cmd
```

启动器会生成本地配置 `.env.docker`。浏览器打开登录页后，用上表账号登录。没有模型密钥也可以浏览活动、服务和订单。要让小伴调用真实模型，请先完成[模型配置](#模型配置)。

| 检查项 | 默认地址或命令 | 预期结果 |
| --- | --- | --- |
| 打开应用 | <http://127.0.0.1:8082/login> | 显示登录页和验证码 |
| 检查后端 | <http://127.0.0.1:8083/actuator/health> | JSON 中的 `status` 为 `UP` |
| 检查容器 | `.\scripts\docker-dev.ps1 status` | MySQL、Redis、后端、前端均为 `healthy` |

关闭启动窗口后服务继续在后台运行。在仓库根目录执行 `.\stop-project.cmd` 可正常停止，数据库、Redis 和上传文件的命名卷会保留。

### 验证一笔上门服务预约

在**本地 Docker 环境**配置真实文本模型后，用普通账号 `linge` 打开「小伴生活助理」。

1. 输入「有哪些可以预约的上门服务？」。从返回的目录选一项，记下服务名称。
2. 输入「帮我预约刚才的〈服务名称〉」，提供今天或之后的日期（`YYYY-MM-DD`）和虚构的服务地址。核对确认卡上的服务名称、日期和脱敏地址，此时动作处于 `PENDING`。
3. 点击「确认并执行」。办理成功后点击右上角用户名，选择「服务订单」核对新订单。也可以对另一笔待确认预约点击「暂不执行」，此时订单列表应保持原样。

### 模型配置

编辑首次启动生成的 `.env.docker`，保存后重新运行 `.\start-docker.cmd`。密钥仅放在本机被 Git 忽略的配置中。全部变量见[环境配置模板](.env.docker.example)。

| 需要的能力 | 要配置的内容 |
| --- | --- |
| 文本问答与真实业务工具 | 填写 `SILVERPILOT_DEEPSEEK_API_KEY`，并将 `SILVERPILOT_DEEPSEEK_MODEL` 设为账号可用的模型 ID。 |
| 图片理解 | 为豆包配置 `SILVERPILOT_DOUBAO_API_KEY` 和 `SILVERPILOT_DOUBAO_MODEL`（模型或推理接入点 ID）。 |
| 无密钥测试只读工具 | 显式设置 `SILVERPILOT_AI_MOCK_ENABLED=true`，按下方命令运行 6 条只读契约。 |

```powershell
.\scripts\run-agent-evaluation.ps1 -Provider mock -MaxCases 6 -EnvFile .\.env.docker
```

LongCat 文本适配器也有独立配置。真实账号联调进度、各模型支持的输入和错误处理见 [Provider 接入说明](docs/LLM_PROVIDER_INTEGRATION.md)。

### 常见问题

| 现象 | 处理 |
| --- | --- |
| 启动时报端口冲突 | 默认宿主端口为前端 `8082`、后端 `8083`、MySQL `3307`、Redis `6380`。在 `.env.docker` 中调整冲突组件的端口，重新启动后使用新地址。 |
| 页面或容器未就绪 | 在仓库根目录运行 `.\scripts\docker-dev.ps1 status`，再用 `.\scripts\docker-dev.ps1 logs` 查看对应服务的启动错误。 |
| 能浏览业务，但小伴无法回答 | 查看工作台的模型状态，核对 `.env.docker` 中的密钥和模型 ID，重启 Docker 栈。只想验证只读工具时可启用 Mock。 |

卷管理、数据库初始化和分模式排障见[部署说明](docs/DEPLOYMENT.md)。公网部署的只读账号、访问限制和模型预算见 [Railway 部署指南](deploy/railway/README.md)。

## Agent 如何办理业务

Vue 工作台收集需求并展示工具结果。Spring Boot 根据登录身份读取 MySQL、检查数据归属和业务条件，模型通过后端提供的工具参与办理。

| 能力 | 具体行为 |
| --- | --- |
| 查询业务 | 8 个只读工具覆盖活动、服务目录、本人订单、体检报告、菜谱及养老知识。个人数据按当前登录用户读取。 |
| 执行写操作 | 活动报名、预约或取消服务、预订或取消助餐共 5 类。工具先创建待确认动作，后端校验确认人、有效期和动作状态，业务执行成功后才返回办理结果。 |
| 引用知识 | 从审核通过且在复核有效期内的 Markdown 检索词项，回答中可查看 `[KB:…]` 来源。照护建议还会核对可选服务 ID，用于服务导航和就医沟通准备。 |
| 跟踪问题 | 管理员可查看运行状态、工具调用次数、动作结果与耗时，登记失败案例，关联修复记录并复测。 |
| 对接外部助手 | WorkBuddy MCP 提供 6 个非个人、只读工具，用于查询目录、审核知识和匿名聚合指标。 |

读写时序、工具权限和知识检索设计见[系统架构](docs/ARCHITECTURE.md)与 [AI Harness](docs/AI_HARNESS.md)。健康相关功能仅供信息整理与服务导航，临床判断由专业人员负责。

## 界面快照

| 管理端 · 小伴运行 | 管理端 · 运营数据总览 |
| --- | --- |
| [![小伴运行页面中的模型、知识和运行统计](docs/screenshots/agent-operations-20260821-0202.png)](docs/screenshots/agent-operations-20260821-0202.png) | [![运营总览中的服务评分和业务趋势](docs/screenshots/operations-dashboard-20260821-0202.png)](docs/screenshots/operations-dashboard-20260821-0202.png) |

<details>
<summary>查看手机端服务目录与订单</summary>

| 居民端 · 服务目录 | 居民端 · 服务订单 |
| --- | --- |
| [![手机端的养老服务目录](docs/screenshots/mobile-services-20260821-0202.png)](docs/screenshots/mobile-services-20260821-0202.png) | [![手机端的服务订单和状态筛选](docs/screenshots/mobile-service-orders-20260821-0202.png)](docs/screenshots/mobile-service-orders-20260821-0202.png) |

</details>

截图采自 2026-08-21 的本地 Docker 演示环境。社区首页、登录页及原尺寸截图见[截图目录](docs/screenshots/)。

## 开发与验证

后端使用 Java 25、Spring Boot 4 和 MyBatis-Plus，前端使用 Vue 3 和 Vite，数据层使用 MySQL 和 Redis。精确版本见[运行时版本清单](runtime-versions.json)。

### 严格 IDEA + VSCode 本地开发

1. 准备本机 MySQL（`127.0.0.1:3306`）并初始化 `a_old`，设置 `CECSMS_LOCAL_DB_PASSWORD` 或 `CECSMS_DB_PASSWORD`。在仓库根目录执行 `.\start-local.cmd`，由脚本准备 Docker Redis 和配置。[数据库初始化](database/README.md)
2. 用 IntelliJ IDEA 打开 `SourceCode/cecsmsServe-springboot`，选择 JDK 25，运行 `com.cecsmsserve.CecsmsServeApplication`。先检查后端健康接口返回 `UP`。[后端开发指南](SourceCode/cecsmsServe-springboot/README.md)
3. 用 VSCode 打开 `SourceCode/cecsmsui-vue`，按版本清单准备 Node.js 24 和 npm 12。在该目录依次运行 `npm ci`、`npm run dev`，访问 <http://127.0.0.1:8081/login>。[前端开发指南](SourceCode/cecsmsui-vue/README.md)

### 复现检查

| 想验证什么 | 从哪里开始 |
| --- | --- |
| 本地 Docker 是否完整可用 | 启动后从仓库根目录运行 `.\scripts\verify-running-stack.ps1`，检查服务健康、前端代理和媒体资源。 |
| 后端、前端与知识库是否通过工程检查 | 准备 JDK、Node.js、npm、Python，并在前端目录执行 `npm ci`。回到仓库根目录运行 `.\scripts\verify-project.ps1`。 |
| 真实 Agent 的工具选择、权限与确认行为 | 配置 DeepSeek 并启动本地栈后，运行 `.\scripts\run-agent-evaluation.ps1 -Provider deepseek -MaxCases 0 -EnvFile .\.env.docker`。完整集含 16 例，会使用模型额度。[评测集与判定规则](evaluation/README.md) |

2026-08-20 的 DeepSeek 记录中，16 项工程契约全部通过。其中写操作用例检查待确认状态后取消。要验证确认后的订单，请按[上门服务预约步骤](#验证一笔上门服务预约)操作。[原始评测报告](docs/agent-evaluation-live.json) · [整栈验证记录](docs/VALIDATION_REPORT.md) · [后续 AI 运营扩展验证](docs/AI_OPERATIONS_VALIDATION.md)

## 文档与项目资料

| 阅读目的 | 入口 |
| --- | --- |
| 维护知识、接入外部助手 | [知识审核与有效期](knowledge-base/README.md) · [WorkBuddy / ima 接入](docs/WORKBUDDY_IMA_QUICKSTART.md) · [CatPaw / LongCat 实践](catpaw/README.md) |
| 复盘运营问题 | [指标口径、问题登记与复测](docs/AI_OPERATIONS_WORKFLOW.md) |
| 部署、加固与发布 | [Railway 入口规则](deploy/railway/EDGE_PROTECTION.md) · [安全设计](docs/SECURITY.md) · [发布与回滚](docs/RELEASE_PROCESS.md) · [变更记录](CHANGELOG.md) |
| 阅读毕业设计与答辩材料 | [论文 PDF（56 页）](docs/thesis/silverpilot-thesis.pdf) · [可编辑 Word](docs/thesis/silverpilot-thesis.docx?raw=true) · [答辩幻灯片（5 页）](docs/thesis/silverpilot-defense.pptx?raw=true) · [论文对应源码](https://github.com/LeiQingliang/silverpilot/tree/768674c3f86eaae16983a35b60bae71fee8e2faa) |
| 查看求职作品与贡献记录 | [项目展示概览](portfolio/career-2026-11-11/SilverPilot-README求职展示补充.md) · [岗位证据包](portfolio/career-2026-11-11/SilverPilot-三岗位求职证据包.md) · [验证与贡献记录模板](portfolio/career-2026-11-11/SilverPilot-验证与贡献记录模板.md) |
| 下载演示原片 | [高清 MP4（约 296 MB）](https://github.com/LeiQingliang/silverpilot/releases/download/demo-video-2026-11-04/silverpilot-demo-original.mp4) · [视频归档与校验文件](https://github.com/LeiQingliang/silverpilot/releases/tag/demo-video-2026-11-04) |

代码与文档贡献请参阅[贡献指南](CONTRIBUTING.md)和[行为准则](CODE_OF_CONDUCT.md)。使用问题见[支持渠道](SUPPORT.md)，安全问题请通过[私密报告渠道](SECURITY.md)提交。

本项目采用 [Apache License 2.0](LICENSE)。引用项目时可使用仓库的[引用元数据](CITATION.cff)。
