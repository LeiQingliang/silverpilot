# 开发、容器与部署说明

## 1. 支持的运行模式

| 模式 | MySQL | Redis | Spring Boot | Vue | 用途 |
| --- | --- | --- | --- | --- | --- |
| 本地严格分离（IDEA + VSCode） | 本机 `3306` | Docker `6380` | 仅 IDEA | 仅 VSCode | 日常运行、断点调试与热更新 |
| 全 Docker | Docker `3307` | Docker `6380` | Docker | Nginx Docker | 一键演示、集成验收 |

IDEA + VSCode 是唯一支持的本地应用进程入口：IDEA 独占 Java/`8083`，VSCode 独占 Vite/`8081`。本地启动脚本只管理 Docker Redis 和外置配置，不代替任何 IDE 启动或停止应用进程。

## 2. Compose 服务

| 服务 | 镜像/构建 | 主机端口 | 健康检查 | 持久化 |
| --- | --- | --- | --- | --- |
| `mysql` | `silverpilot-mysql:9.7.2`（基于官方 LTS 镜像的最小派生） | `127.0.0.1:3307` | root 鉴权后查询业务库 `user` 表 | `silverpilot-mysql-data` |
| `redis` | `silverpilot-redis:8.2.9`（校验官方源码后构建） | `127.0.0.1:6380` | authenticated `redis-cli ping` | `silverpilot-redis-data` |
| `backend` | Maven + Temurin 25.0.3+9 LTS / Ubuntu 26.04 LTS 多阶段构建与运行层压平 | `127.0.0.1:8083` | Actuator `/health` | `silverpilot-uploads` + 根目录只读媒体 |
| `frontend` | Node 24.19.0 LTS/npm 12.0.2 + Nginx 1.30.4 stable / Alpine 3.24.1 多阶段构建 | `127.0.0.1:8082` | `/healthz` | 无状态 |

`backend` 和 `frontend` 位于 `full` profile。Compose 使用健康条件确保数据库/Redis 健康后再启动后端、后端健康后再启动前端，符合 Docker 官方关于 [Compose 启动顺序](https://docs.docker.com/compose/how-tos/startup-order/) 的机制。新上传内容写入 `silverpilot-uploads`；项目根 `image/file/video` 只读挂载到 `/app/repository-media`。启动前会校验种子引用，后端启动时会迁移旧占位 URL 并核对全部持久化图片，启动后还会逐一通过前端代理读取图片。

当前可复现基线为 Spring Boot 4.1.0、Maven 3.9.16、MySQL 9.7.2 LTS、Connector/J 26.7.0 GA、Redis 8.2.9 Extended、Node.js 24.19.0 LTS、npm 12.0.2、Go 1.27.0、Nginx 1.30.4 stable、Ubuntu 26.04 LTS 与 Alpine 3.24.1。Redis 官方镜像仓库在本次验收时尚未提供 `8.2.9` 标签，因此 `redis/Dockerfile` 从 Redis 官方发布地址下载源码，并用官方 `redis-hashes` 中的 SHA-256 固定校验；它只构建本项目实际需要的 Redis Core，不冒充 Redis Stack 模块镜像。完整选版规则和升级门禁见 [版本与长期支持策略](VERSION_POLICY.md)。

启动预检同时检查监听进程与 Windows TCP 排除端口范围；默认端口落入系统保留范围时会在构建前报告具体范围，使用者应在被忽略的 `.env.docker` 中选择空闲端口，而不是结束无关进程。本次本机验收因 `3307-3406` 被系统排除，隔离栈使用 `127.0.0.1:13307` 映射 MySQL。

## 3. 本地密钥文件

`.env.docker.example` 只含变量名和 `__GENERATE__` 占位符。第一次执行脚本时生成：

```powershell
.\scripts\docker-dev.ps1 redis
```

生成的 `.env.docker`、当前用户 DPAPI 加密备份 `.env.docker.dpapi` 和 `SourceCode/cecsmsServe-springboot/config/application-host.properties` 均被 Git 忽略；后者还被后端 `.dockerignore` 排除。配置位于源码资源目录之外，因此不会被 Maven 打入 JAR，也不会进入 Docker 构建上下文。DPAPI 备份只能由生成它的 Windows 用户解密，用于防止误删环境文件后旧数据卷凭据失配；它不是跨电脑备份。IDEA 使用 `host`，测试使用 `test`，Compose 后端使用 `docker`，彼此不会读取对方的 profile 配置。

如需从模板手动配置，必须替换所有 `__GENERATE__`，并为以下用途使用互不相同的随机值：

- MySQL root 密码；
- 应用数据库密码；
- Redis 密码；
- JWT HMAC secret（至少 32 字符）；
- MCP API Key；
- 每家模型供应商的 API Key。

## 4. 本机应用运行

### 4.1 本地 IDE 基础设施准备

Windows 直接双击根目录 `start-local.cmd`。等价命令为：

```powershell
.\scripts\start-project.ps1 -Mode Local
```

此命令只把 Redis 放进 Docker，并生成 IDEA 需要的外置 `host` 配置；MySQL 固定使用本机 `3306`。它不启动、停止或重启 Spring Boot/Vite。返回成功前会校验 Redis 是唯一运行的 Compose 服务、镜像与服务端版本均为 8.2.9、认证 `PONG`、AOF 已启用且只发布到 `127.0.0.1:6380`。

直接执行 `scripts\docker-dev.ps1 redis` 与 Local 准备等价；它会移除本项目的其他 Compose 容器，但不会删除 MySQL、Redis 或上传命名卷，也不会停止本机 `MySQL97`。

缺失、停止或不健康的 Redis 容器会在保留命名卷的前提下有界重建一次。本地准备不扫描、清理、重建或恢复 Java/Vite 进程；这些生命周期完全归 IDEA/VSCode 所有。

脚本不会自动修复或绕过不可安全恢复的条件，包括 JDK/Node/npm/Docker 安装损坏、源码无法编译、凭据缺失且 DPAPI 备份不可用、非项目进程占用端口、磁盘/数据损坏、断电或操作系统故障；此时它会返回非零错误并保留数据，不会杀死无关进程、删除命名卷或关闭 Redis 门禁。

### 4.2 IDEA + VSCode 步骤

```powershell
# 项目根目录
.\scripts\docker-dev.ps1 redis
```

IDEA：

1. 打开 `SourceCode/cecsmsServe-springboot`，或在仓库根项目中加载该目录的 `pom.xml`；
2. 使用 JDK 25 加载 `pom.xml`；
3. 新建或检查 IDEA Application 配置：Main class 为 `com.cecsmsserve.CecsmsServeApplication`；Working directory 可为当前后端模块或仓库根，`host` profile 会从对应位置导入同一份外置配置；
4. 确认 `http://127.0.0.1:8083/actuator/health` 为 `UP`。

`redis` 是本地模式切换命令：若全 Docker 的 `mysql/backend/frontend` 正在运行，它会精确停止并移除这些容器、释放 `3307/8083/8082`，保留全部命名卷，然后只启动 Redis。它还会重新生成外置 `application-host.properties`；仓库的 `application.properties` 默认启用 `host`。在 IDEA 中直接运行真实启动类，也可显式填写 Active profiles=`host`。本地不存在另一个命令行 Java 启动入口。

VSCode：

```powershell
cd SourceCode\cecsmsui-vue
npm ci
npm run dev
```

推荐在 VSCode 中直接执行 Task `Frontend: dev server`：它只执行 `npm run dev`，不启动 Docker 或 Java。npm `predev` 仅读验证 IDEA 后端和 Redis 健康。需要重装依赖时，依次执行 `Frontend: stop dev server` 与 `Frontend: install clean dependencies`；安装任务会拒绝在本项目 Vite 占用原生模块时清理 `node_modules`。

访问 `http://127.0.0.1:8081/login`。

模型或端口修改后：

```powershell
.\scripts\docker-dev.ps1 config
```

然后重启 IDEA 后端，使本地 profile 重新加载。

## 5. 全 Docker 步骤

Windows 直接双击根目录 `start-docker.cmd`。成功后窗口保留运行状态，按回车或关闭窗口后，服务仍在 Docker 后台运行；需要停止时使用 `stop-project.cmd`。自动化调用可设置 `SILVERPILOT_NO_PAUSE=1`。显式指定 `-WaitForStop` 时，只有输入 `STOP` 才会停止，直接回车会保留服务。不保留状态窗口的 PowerShell 命令为：

```powershell
.\scripts\start-project.ps1 -Mode Docker
.\scripts\docker-dev.ps1 status
```

启动器生命周期回归可单独运行 `pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-startup-lifecycle.ps1`。19 项检查在临时目录运行真实脚本，验证状态窗口保留、窗口结束、回车、浏览器失败、并发操作、配置丢失、重复停止和停止失败；测试不会访问实际 Docker 服务或数据卷。同一工作目录的启动、切换、停止和清理互斥，重复点击时后一个操作会明确提示等待后重试或跳过。

`clean-project-residue.cmd` 默认发现活动容器或项目进程后会直接跳过，保留服务、日志、缓存和构建记录，避免把正在运行的后端当作残留终止。需要主动停止全部项目进程并清理时，使用 `clean-project-residue.cmd --stop-running`；数据卷、源码和依赖保留。`pwsh -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-residue-cleanup.ps1` 提供 12 项隔离回归，覆盖运行、重启、暂停、本地进程、并发启动、Docker 不可用、另一工作目录同名容器，以及显式完整清理。

更新后仍保留的旧标签页需要先在登录页按一次 `Ctrl+F5`。入口 HTML 使用 `Cache-Control: no-cache` 重新校验版本；带内容哈希的静态资源继续长期缓存。页面使用中文语言标记并声明禁止自动翻译，以避免翻译器改写 Vue 正在管理的节点。

`start-project.cmd` 仍保留为两种模式的选择菜单。全 Docker 与 IDEA 后端共享主机端口 `8083`，不能同时运行。启动器会先检查 Docker Desktop、必要文件、磁盘空间、端口唯一性/占用和 Compose 展开；如果 IDEA/其他进程占用端口，会报告进程名和 PID，而不会擅自结束进程。构建输入指纹与已验证镜像匹配时使用 `--no-build` 快速启动，不访问镜像仓库；源码、依赖或 Dockerfile 变化后自动重新构建。需要强制重建可加 `-Rebuild`。

启动器的版本与运行预检不查询上游发布渠道：它核对仓库内已评审的版本/摘要一致性、Docker 能力、四个容器健康、登录页、Nginx→Spring Boot 同源代理、真实数据库身份查询和 Agent 状态，不会因官方站点故障或上游刚发布新补丁而拒绝启动。已验证镜像存在且构建输入未变化时可在断网条件下直接启动；全新机器或需要重建缺失镜像时仍需下载基础镜像和构建依赖。最新版本漂移使用独立的 `scripts\verify-version-policy.ps1 -Mode Docker` 联网维护审计。访问 `http://127.0.0.1:8082/login`。需要额外复验：

```powershell
Invoke-RestMethod http://127.0.0.1:8083/actuator/health
Invoke-WebRequest http://127.0.0.1:8082/healthz -UseBasicParsing
.\scripts\smoke-authentication.ps1 -BaseUrl 'http://127.0.0.1:8082/api'
$env:CECSMS_QA_BASE_URL = 'http://127.0.0.1:8082'
node --env-file=.env.docker .\scripts\audit-frontend-backend-communication.mjs
Remove-Item Env:CECSMS_QA_BASE_URL
```

一键执行 97 项只读通信回归，并追加一次自动清理的真实数据库 CRUD：

```powershell
.\scripts\start-project.ps1 -FullAudit
```

启动失败时会输出 Compose 状态与最后 120 行日志，并在不删除命名卷的前提下安全重建容器重试一次。仍失败即返回非零退出码。恢复现有镜像但保留数据可执行 `.\scripts\docker-dev.ps1 restart`；只有明确接受数据永久删除时才允许 `reset -Force`。

停止但保留数据：

```powershell
.\stop-project.cmd
```

停止入口根据容器保存的 Compose 工作目录和配置文件标签确认归属，依次关闭前端、后端、Redis 和 MySQL，再移除容器与空闲的项目网络；命名卷和其他项目保留。即使 `.env.docker` 丢失或项目名称被修改，仍能停止当前工作目录已启动的容器。Docker CLI/引擎不可用或停止失败时返回错误，不会把无法验证的状态报告为成功。本地应用应先在 VSCode 结束 Vite、在 IDEA 结束 Java，再运行此停止入口关闭 Redis。

重置会永久删除本项目命名卷，只能在确认没有重要数据时执行：

```powershell
.\scripts\docker-dev.ps1 reset -Force
```

## 6. 数据库初始化与迁移

`database/a_old.sql` 只在 MySQL 命名卷第一次创建时执行。它是本地演示种子库，会创建业务表和 BCrypt 演示账户，不应导入真实生产库。

应用启动时：

- `schema.sql` 幂等创建 `agent_action` 与 `agent_run`；
- `AgentSchemaMigration` 通过 JDBC metadata 增加 Agent 指标列，兼容当前 MySQL 9.7；
- `LegacyPasswordMigration` 将旧卷中的非 BCrypt 密码升级为 BCrypt。
- `databaseContract` Actuator 健康项核验必需表、活动域外键、关键索引、孤儿记录、报名状态/计数和服务分类层级；失败时后端容器不会被标记为健康。

这些是针对当前演示项目的轻量迁移。真实生产应改用 Flyway/Liquibase、审批过的向前迁移和备份/回滚演练。

当前卷只读审计、经备份后修复，以及种子库隔离导入验证分别使用：

```powershell
.\scripts\repair-database-integrity.ps1
# 只有确认要执行“备份 -> 隔离问题行 -> 修复 -> 加约束”时才加 -Apply
.\scripts\repair-database-integrity.ps1 -Apply
.\scripts\verify-database-seed.ps1
```

`verify-database-seed.ps1` 只操作脚本自己生成的 `qa_seed_<GUID>` 临时 schema，并在 `finally` 中删除；`verify-database-crud.ps1` 只操作随机 `QA-DB-*` 活动并精确清理。两者失败都返回非零退出码。

MySQL 跨 LTS 升级前必须先执行一致性逻辑备份并做恢复测试，不能用删除命名卷的方式“升级”。本仓库从 8.4.11 升级到 9.7.2 时保留原卷，先用 `mysqldump --single-transaction --routines --triggers --events` 备份，再在临时 schema 恢复验证 22 张表与 18 个用户；本机备份位于被 Git 忽略的 `backups/`，不会提交到仓库。

## 7. 关键环境变量

| 变量 | 默认/要求 | 作用 |
| --- | --- | --- |
| `CECSMS_DB_URL/USERNAME/PASSWORD` | 密码必填 | MySQL 连接 |
| `CECSMS_REDIS_ENABLED` | 源码安全默认 false；所有受支持启动器均强制设为 true | 是否使用 Redis 共享限流；本地混合模式不允许关闭 |
| `CECSMS_CAPTCHA_TTL` | `5m` | 一次性验证码有效期 |
| `CECSMS_CAPTCHA_MAX_LOCAL_ENTRIES` | `10000` | 代码级应急保护上限；不是受支持的本地运行拓扑 |
| `CECSMS_FORWARD_HEADERS_STRATEGY` | 默认 `NONE`；容器后端为 `native` | 仅在受信代理后解析客户地址/协议 |
| `CECSMS_JWT_SECRET` | 至少 32 字符 | JWT 签名 |
| `DEEPSEEK_API_KEY/MODEL` | 可选 | DeepSeek 文本 Agent |
| `DOUBAO_API_KEY/MODEL` | 可选且必须成对 | 豆包图文通道 |
| `CECSMS_AI_MOCK_ENABLED` | false | 显式开发 Mock |
| `CECSMS_MCP_API_KEY` | MCP 使用时必填 | WorkBuddy 独立鉴权 |
| `CECSMS_AGENT_RATE_LIMIT` | 12/min/user | Agent 限流 |
| `CECSMS_AI_MAX_TOTAL_TOKENS` | 12000 | 单次运行 Token 上限 |
| `CECSMS_KNOWLEDGE_BASE_PATH` | 项目知识目录 | 只读 RAG 根目录 |
| `CECSMS_CORS_ALLOWED_ORIGINS` | 本地前端 | 跨域 allowlist |
| `CECSMS_UPLOAD_DIR` | 项目根/容器卷 | 上传根目录 |
| `CECSMS_ASSET_DIR` | 可选；Docker 默认留空 | 可选的第一只读资源根目录 |
| `CECSMS_LEGACY_ASSET_DIR` | 可选；Docker 为 `/app/repository-media` | 第二只读资源根目录，用于项目根 `image/file/video` 历史媒体 |

全部变量和缺省值见后端 `.env.example` 与根目录 `.env.docker.example`。

## 8. 生产前置清单

当前 Compose 可用于本地与受控演示。部署到真实网络前至少需要：

1. 在负载均衡/API 网关终止 TLS，只公开 HTTPS；
2. 使用云密钥管理，不把 `.env.docker` 复制到镜像；
3. 禁止 MySQL/Redis 公网监听，建立最小权限数据库账户；
4. 删除演示账户和种子数据，迁移到受审批 schema 管理；
5. 将上传迁移到隔离对象存储，增加病毒扫描、内容类型探测和过期策略；
6. 配置集中日志、Prometheus 抓取、错误率/延迟/Provider 费用告警；
7. 完成数据库与 Redis 恢复演练，明确 RPO/RTO；
8. 为模型供应商完成隐私、保留、地域和费用评估；
9. 重跑单元/接口/Agent 评测、依赖与镜像扫描、授权回归和独立渗透测试；
10. 将 WorkBuddy MCP 放在 HTTPS 网关后，轮换独立 Key 并限制来源。

内置 Nginx 作为组合拓扑的边缘入口，会覆盖客户伪造的 `X-Forwarded-For`，再由 Tomcat native forwarding 恢复限流所需的连接地址。如果在 Nginx 之前另加云负载均衡，必须重新配置 Nginx `real_ip` 信任网段，不能直接恢复信任任意转发 Header。

## 9. 故障排查

```powershell
.\scripts\docker-dev.ps1 status
.\scripts\docker-dev.ps1 doctor
.\scripts\docker-dev.ps1 logs
.\scripts\verify-running-stack.ps1
docker compose --env-file .env.docker --profile full config --quiet
```

- IDEA 报 `Port 8083 was already in use`：执行 `docker-dev.ps1 redis`，它会清理本项目全 Docker 的应用容器；若仍被占用，检查监听 PID 并停止对应 IDEA/项目后端，不要结束无关 Java 进程；
- 本地混合模式报告 Redis 门禁失败：执行 `docker-dev.ps1 redis`，确认只出现 `silverpilot-redis-1`，再运行 `verify-docker-redis.ps1 -RequireOnlyRedis`；不要通过关闭 `CECSMS_REDIS_ENABLED` 绕过；
- `full` 报端口占用：先停止 IDEA 后端；MySQL/Redis 端口若被其他项目占用，再修改 `.env.docker` 的对应 `SILVERPILOT_*_PORT`；
- IDEA 连不上数据库：先确认宿主机 MySQL 正在 `127.0.0.1:3306` 监听且已设置 `CECSMS_LOCAL_DB_PASSWORD` 或 `CECSMS_DB_PASSWORD`，再执行 `docker-dev.ps1 config`，确认模块根目录存在被忽略的 `config/application-host.properties`，并运行仓库内 **CecsmsServeApplication** 配置；
- Agent 503：查看 `/chat/status`，核对 Provider 是否配置；无 Key 时显式启用 Mock；
- 图片请求 503：必须同时配置豆包 API Key 和模型/Endpoint ID；
- 全 Docker 页面图片 404：先运行 `scripts/validate-seed-assets.ps1`，再确认 `image/file/video` 与 Compose 的 `/app/repository-media/*:ro` 挂载生效；正常启动会逐一检查数据库中的图片 URL；
- 知识库为空：运行 `scripts/validate-knowledge-base.ps1` 并核对路径；
- 数据库脚本未重新导入：命名卷只在首次创建时初始化，除非明确允许，勿用 `reset -Force`。
- `.env.docker` 被误删：再次启动会优先从当前用户可解密的 `.env.docker.dpapi` 恢复；若加密备份也丢失且数据卷已存在，脚本会拒绝自动轮换密码，避免把凭据失配误判成数据库损坏。
