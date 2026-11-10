# SilverPilot Railway 部署

本目录将 Vue、Spring Boot、MySQL 和 Redis 四服务部署到 Railway。公开演示已于 **2026-11-10** 首次上线：[SilverPilot](https://silverpilot.up.railway.app/login)，首发已验证真实 DeepSeek 文字回复与业务工具查询。

**本次安全加固状态：**本文描述新增只读账号策略、入口限制和 Redis 预算的生产配置；新版云端部署与复验尚待完成。首次上线记录不能证明新增控制已在云端生效；完成复验后再更新状态、提交号与验收时间。

## 服务配置

在同一个 Railway project/environment 中建立下列服务。所有构建上下文均为**仓库根目录**，每个 Dockerfile 旁的专属 `.dockerignore` 限制其构建输入。源码分支必须包含本目录及注册字段安全修复。

| 服务名 | Dockerfile | 监听端口 | 持久卷 | 公网入口 |
| --- | --- | --- | --- | --- |
| mysql | `deploy/railway/mysql.Dockerfile` | 3306 | `/var/lib/mysql` | 不创建 |
| redis | `deploy/railway/redis.Dockerfile` | 6379 | `/data` | 不创建 |
| backend | `deploy/railway/backend.Dockerfile` | 8083 | `/app/uploads` | 不创建 |
| frontend | `deploy/railway/frontend.Dockerfile` | 8080 | 无 | Railway HTTPS 域名，目标端口 8080 |

四服务关闭 Serverless，设置 Always 重启策略，保留镜像默认启动命令。当前四服务均按单副本配置；Nginx 计数仅在同一实例内共享，不能无评估增加前端副本。有卷服务保持单副本。后端健康检查使用 `/actuator/health`，`PORT=8083`；前端使用 `/readyz`，`PORT=8080`。`/healthz` 仅证明前端存活，`/readyz` 会检查后端。不要开放任何服务的额外 TCP Proxy。

Railway 从 2026-11 起不接受新服务使用旧 `railway.json` / `railway.toml`。本目录通过平台服务设置部署，不引入已弃用配置文件。设置持久化、网络、域名、Serverless、备份及费用上限仍需要已连接的 Railway 账号。

## 运行变量

以下是变量名称与用途，真实值仅写 Railway 运行变量。禁止使用构建参数、`VITE_*` 或 Git 保存凭据。密码应由密码生成器生成；四个 MySQL/bootstrap 密码必须彼此不同且为 32–72 位随机 base64url/hex 字符。建议每个生成 48 位随机十六进制字符串。

### mysql

固定 `MYSQL_DATABASE=a_old`、`MYSQL_USER=cecsms`。配置 `MYSQL_ROOT_PASSWORD`、`MYSQL_PASSWORD`、`SILVERPILOT_ADMIN_PASSWORD`、`SILVERPILOT_VISITOR_PASSWORD`。

第一次部署只使用**新空卷**。初始化前将 18 个原演示账号全部锁定，再为 `admin` 和 `linge` 设置强随机 bcrypt 密码。已有数据卷不会因重新部署而重置密码。详细失败恢复边界见 [database-notes.md](database-notes.md)。

当前公开站保留 `admin / 123456` 和 `linge / 123456` 作为共享演示凭据，**两个账号都必须启用后端只读策略**：`CECSMS_DEMO_READ_ONLY_ENABLED=true`、`CECSMS_DEMO_READ_ONLY_USER_IDS=1,17`。管理角色不豁免只读检查。只允许浏览、业务查询、受预算限制的 AI 对话与照护建议；禁止业务写入、上传、报名、预约及 AI 写动作确认。

运维使用另行创建的私有管理员，用户名、密码及创建输出不得写入源码或公开文档。新环境若也要公开上述弱口令，应先准备私有管理员并启用、验证共享账号只读，再单独调整演示口令；不要把 `123456` 写入要求强随机值的 bootstrap 变量。其他原种子账号保持锁定。

### redis

配置独立随机 `REDIS_PASSWORD`（至少 32 字符）。私网地址为 `redis.railway.internal:6379`；AOF 每秒持久化。凭据不能与数据库或应用登录密码复用。

### backend

| 变量 | 值或用途 |
| --- | --- |
| `PORT` / `CECSMS_SERVER_PORT` | `8083` |
| `RAILWAY_RUN_UID` | `0`，入口修正卷根目录权限后立即降权至 10001 |
| `SPRING_PROFILES_ACTIVE` | `docker` |
| `CECSMS_DB_URL` | `jdbc:mysql://mysql.railway.internal:3306/a_old?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia%2FShanghai&sslMode=DISABLED&allowPublicKeyRetrieval=true` |
| `CECSMS_DB_USERNAME` | `cecsms` |
| `CECSMS_DB_PASSWORD` | 引用 mysql 服务的 `MYSQL_PASSWORD` |
| `CECSMS_REDIS_ENABLED` / `CECSMS_REDIS_HEALTH_ENABLED` | `true` |
| `CECSMS_REDIS_HOST` / `CECSMS_REDIS_PORT` | `redis.railway.internal` / `6379` |
| `CECSMS_REDIS_PASSWORD` | 引用 redis 服务的 `REDIS_PASSWORD` |
| `CECSMS_JWT_SECRET` | 独立高熵随机值，至少 32 字符 |
| `CECSMS_CORS_ALLOWED_ORIGINS` | 最终前端 HTTPS origin，不含末尾 `/` |
| `CECSMS_FORWARD_HEADERS_STRATEGY` | `framework`；仅在后端无公共域名/TCP Proxy、只能经可信前端或项目私网访问时使用 |
| `CECSMS_KNOWLEDGE_BASE_PATH` | `/app/knowledge-base` |
| `CECSMS_LEGACY_ASSET_DIR` | `/app/repository-media` |
| `CECSMS_UPLOAD_DIR` | `/app/uploads` |
| `CECSMS_AI_MOCK_ENABLED` | `false` |
| `DEEPSEEK_MODEL` / `DEEPSEEK_READ_TIMEOUT` | 已授权的真实模型；当前首发使用 `deepseek-flash`，本次生产单次读取超时配置 `20s` |
| `CECSMS_DEMO_READ_ONLY_ENABLED` / `CECSMS_DEMO_READ_ONLY_USER_IDS` | `true` / `1,17`，两个共享演示账号均只读 |
| `CECSMS_REGISTRATION_PER_HOUR` / `CECSMS_REGISTRATION_GLOBAL_PER_HOUR` | `3` / `30` |
| `CECSMS_REGISTRATION_REDIS_FAIL_CLOSED` | `true`，共享计数不可用时拒绝注册 |
| `TZ` | `Asia/Shanghai` |

MCP 不提供公网访问；不需要对外 MCP 时保持 `CECSMS_MCP_API_KEY` 未配置。真实 AI 使用云端专用的 `DEEPSEEK_API_KEY`，或项目支持的其他 Provider 变量。密钥与模型额度未配置时，传统业务可用，真实 AI 不可用；不得将 Mock 当作真实模型服务。当前公开站接入真实 DeepSeek，并关闭 Mock；其他模型和视觉能力未因此获得已验证状态。

### AI 共享额度与保守预算

下面是本次生产配置，后端默认值见 `application.properties`；建议在 Railway 显式配置，便于审阅实际运行值。Redis 必须启用，且额度检查使用共享的原子操作；不能用本地开发模式代替生产 Redis。

| 控制 | 运行变量 | 值 |
| --- | --- | --- |
| 全站 / 用户 / IP 每分钟请求 | `CECSMS_AI_GLOBAL_PER_MINUTE` / `CECSMS_AI_USER_PER_MINUTE` / `CECSMS_AI_IP_PER_MINUTE` | `20` / `3` / `6` |
| 全站 / 用户 / IP 每日请求 | `CECSMS_AI_GLOBAL_PER_DAY` / `CECSMS_AI_USER_PER_DAY` / `CECSMS_AI_IP_PER_DAY` | `200` / `20` / `40` |
| 全站 / 用户 / IP 并发运行 | `CECSMS_AI_GLOBAL_CONCURRENCY` / `CECSMS_AI_USER_CONCURRENCY` / `CECSMS_AI_IP_CONCURRENCY` | `2` / `1` / `2` |
| 每日模型 HTTP 尝试 | `CECSMS_AI_MODEL_CALLS_PER_DAY` | `200` |
| 每日保守 Token 预留 | `CECSMS_AI_TOKENS_PER_DAY` | `500000` |
| 模型请求体上限 | `CECSMS_AI_MAX_BODY_BYTES` | `65536` |
| 运行授权窗口 / 并发租约 TTL | `CECSMS_AI_RUN_TIMEOUT` / `CECSMS_AI_LEASE_TTL` | `90s` / `5m` |

共享额度覆盖 `/chat`、`/chat/care-plan` 和 `/report/analyze/{id}`。每日按 `Asia/Shanghai` 自然日分桶，IPv6 来源按 `/64` 汇总。每次真实模型 HTTP 尝试（包括重试）都先占用模型尝试与 Token 预算，失败不退回预留。预留按序列化请求的 UTF-8 字节数、输出上限及附加开销保守计算；实际 usage 超过预留时补记，核算失败时拒绝后续调用。更换模型或供应商不会获得新的全站总额度。

一次提问可能包含多次模型尝试，多个请求、Token 和并发上限同时适用，先达到的限制生效。90 秒是后续模型尝试的授权窗口，不是强制中断已在途 HTTP 请求；生产 DeepSeek 另设 `20s` 读取超时。工具批次由后端限制为每轮最多 4 次、每次运行总计最多 8 次，不能由提示词放大。

这是低额度共享体验：所有使用 `linge` 的访客共享该账号每日 20 次请求和 1 个并发位，`admin` 同理；普通真人也可能收到 `429`。独立注册用户仍受 IP 和全站总量约束。Redis 故障、租约或预算核算不可用时，生产模式返回拒绝，不能降级为无限制的付费模型调用。

该预算不是美元或人民币的硬性账单封顶。还应在模型供应商账户设置余额、费用限制与告警；Railway 托管费用独立计费。预算计数、限流或浏览器挑战不能保证百分之百抵御攻击。风险依据见 [OWASP LLM10 Unbounded Consumption](https://genai.owasp.org/llmrisk/llm102025-unbounded-consumption/) 与 [OWASP LLM01 Prompt Injection](https://genai.owasp.org/llmrisk/llm01-prompt-injection/)；模型输出、用户提示和检索内容都不能授予业务权限。

### 注册

注册请求必须提供一次性验证码 `captchaId` / `verificationCode`。验证码正确后，Redis 同时预留每 IP 每小时 3 次、全站每小时 30 次注册额度；失败的后续创建不会提供无限重试机会。服务端只接受普通用户资料，忽略或拒绝客户端指定的管理员角色、用户 ID、积分和令牌。注册配额不是创建管理员的途径。生产开启 `CECSMS_REGISTRATION_REDIS_FAIL_CLOSED=true`，Redis 不可用时返回服务不可用，不切换到本机计数绕过总量限制。

### frontend

设置 `PORT=8080`。默认上游为 `backend.railway.internal:8083`；只有确实修改后端服务名时才设置 `SILVERPILOT_BACKEND_HOST`。

公开 Host 按 `SILVERPILOT_PUBLIC_HOST`、`RAILWAY_PUBLIC_DOMAIN` 的顺序读取，最后回退到 `silverpilot.up.railway.app`。更换域名时必须同步 Host 允许值与后端 CORS。仅保留本机及 Railway 健康检查 Host 的必要访问。

前端仅允许 Railway HTTP edge 作为公网入口。按 Railway 文档读取 `X-Real-IP`，重新生成到后端的客户端地址头和 HTTPS 协议头；不要将该配置直接搬到无可信代理的公网主机。公开前必须用伪造 `X-Real-IP` / `X-Forwarded-For` 的请求确认 Railway edge 覆盖行为。私网中的其他服务也属于这个信任边界。

Nginx 叠加全站 40 请求/秒、每 IP 10 请求/秒，以及 API、登录、验证码、注册、管理、上传和 AI 独立限额，并限制全站与每 IP 并发。API 最大 256 KiB、AI 64 KiB、上传 10 MiB；慢请求、异常 Host/方法、matrix 与编码路径歧义被限制。超额返回 `429` 并提供 `Retry-After`，过大请求 `413`，非法方法 `405`。完整数值、突发额度和仅本机的验证脚本见 [EDGE_PROTECTION.md](EDGE_PROTECTION.md)。后端 Redis 限额继续独立生效，以较先触发的限制为准。

## 发布顺序与验收

1. 确认 Railway 账号套餐、资源限额和账单告警；创建隔离 project/environment，保持 GitHub 源码可回溯。
2. 创建三个持久卷、设置运行变量，依次部署 mysql、redis，确认初始化成功且无公网数据库入口。
3. 部署 backend，确认健康为 `UP`；部署 frontend，生成 HTTPS 域名，再设置后端 CORS 并重新部署后端。
4. 在未登录 Railway 的浏览器中访问 `/login` 与 `/readyz`。验证两个公开演示账号可登录、均不能写业务；私有管理员具备必要维护权限，其他原种子账号锁定。检查验证码、业务列表、媒体和角色隔离；应用自身仍要求业务账号登录。
5. 以少量请求复验转发头覆盖、共享只读权限与公开 MCP/非健康 Actuator 阻断。确认生产 Redis、配额变量和真实模型状态；调用、并发、故障及费用预算测试在隔离环境完成，不向公开站压测或大量调用模型。
6. 重启后端和数据库后重复登录、媒体和持久化检查。开启卷的每日及每周备份，做一次隔离恢复验证，再记录真实地址、提交号与验收时间。

不要向项目存入真实个人或健康资料来做公开演示。当前种子数据为合成数据。受控知识有审核期限，不能通过修改日期假装完成复核；部署不会延长文档有效期。

关闭 Serverless 可避免闲置休眠，但不构成永久可用保证。带持久卷的单副本重新部署会短暂中断。当前首发使用 Railway 试用额度；长期在线需要在试用到期或额度耗尽前切换合适的付费套餐，持续支付资源和模型费用。托管持续性还取决于账号、资源、供应商服务及访客所在地网络，不能承诺无限免费、永久在线或全球任意网络都可达。

Railway 平台提供网络层缓解，但官方说明应用层攻击仍可能使服务过载。[Under Attack Mode](https://docs.railway.com/networking/waf) 适合攻击期间临时启用：浏览器取得 clearance 后同源 API 可用，未验证的脚本和非导航请求会被拒绝。它不默认常开，也不能替代应用配额；对抓取、外部监控和健康检查的影响必须另行验证。

## 本地/CI 验证

`bash deploy/railway/smoke.sh` 使用随机临时凭据和隔离 Compose 项目构建、运行四服务，清理仅针对它创建的临时项目。它需要 Linux Docker Engine 与 Compose；不创建 Railway 资源。入口限流与连接测试使用同一前端镜像和本机 HTTP stub，不访问真实模型；集成检查只接受 loopback 目标。GitHub Actions 的 Railway deployment workflow 使用同一入口。通过此检查仍不能替代 Railway edge、真实卷、备份恢复和模型在线验收。

## 官方依据

- [Compose 到 Railway 服务的映射](https://railway.com/guides/docker-compose)
- [Dockerfile 构建](https://docs.railway.com/builds/dockerfiles)
- [公网代理头](https://docs.railway.com/networking/public-networking/specs-and-limits)
- [私网与 DNS](https://docs.railway.com/networking/private-networking/how-it-works)
- [卷与权限](https://docs.railway.com/volumes)及[备份](https://docs.railway.com/volumes/backups)
- [Serverless](https://docs.railway.com/deployments/serverless)与[配置迁移](https://docs.railway.com/config-as-code)
- [Railway WAF / Under Attack Mode](https://docs.railway.com/networking/waf)
- [OWASP Unbounded Consumption](https://genai.owasp.org/llmrisk/llm102025-unbounded-consumption/)与[Prompt Injection](https://genai.owasp.org/llmrisk/llm01-prompt-injection/)
