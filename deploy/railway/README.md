# SilverPilot Railway 部署

这些文件用于将原 Vue、Spring Boot、MySQL 和 Redis 四服务部署到 Railway。它们不代表已经创建云端服务；公网地址、资源费用和外网验收结果必须来自实际部署。

## 服务配置

在同一个 Railway project/environment 中建立下列服务。所有构建上下文均为**仓库根目录**，每个 Dockerfile 旁的专属 `.dockerignore` 限制其构建输入。源码分支必须包含本目录及注册字段安全修复。

| 服务名 | Dockerfile | 监听端口 | 持久卷 | 公网入口 |
| --- | --- | --- | --- | --- |
| mysql | `deploy/railway/mysql.Dockerfile` | 3306 | `/var/lib/mysql` | 不创建 |
| redis | `deploy/railway/redis.Dockerfile` | 6379 | `/data` | 不创建 |
| backend | `deploy/railway/backend.Dockerfile` | 8083 | `/app/uploads` | 不创建 |
| frontend | `deploy/railway/frontend.Dockerfile` | 8080 | 无 | Railway HTTPS 域名，目标端口 8080 |

四服务关闭 Serverless，设置 Always 重启策略，保留镜像默认启动命令。有卷服务保持单副本。后端健康检查使用 `/actuator/health`，`PORT=8083`；前端使用 `/readyz`，`PORT=8080`。`/healthz` 仅证明前端存活，`/readyz` 会检查后端。不要开放任何服务的额外 TCP Proxy。

Railway 从 2026-11 起不接受新服务使用旧 `railway.json` / `railway.toml`。本目录通过平台服务设置部署，不引入已弃用配置文件。设置持久化、网络、域名、Serverless、备份及费用上限仍需要已连接的 Railway 账号。

## 运行变量

以下是变量名称与用途，真实值仅写 Railway 运行变量。禁止使用构建参数、`VITE_*` 或 Git 保存凭据。密码应由密码生成器生成；四个 MySQL/bootstrap 密码必须彼此不同且为 32–72 位随机 base64url/hex 字符。建议每个生成 48 位随机十六进制字符串。

### mysql

固定 `MYSQL_DATABASE=a_old`、`MYSQL_USER=cecsms`。配置 `MYSQL_ROOT_PASSWORD`、`MYSQL_PASSWORD`、`SILVERPILOT_ADMIN_PASSWORD`、`SILVERPILOT_VISITOR_PASSWORD`。

第一次部署只使用**新空卷**。初始化前将 18 个原演示账号全部锁定，再为 `admin` 和 `linge` 设置新 bcrypt 密码。管理员密码仅交给所有者，`linge` 是具有原普通用户操作权限的账号，不是只读账号。已有数据卷不会因重新部署而重置密码。详细失败恢复边界见 [database-notes.md](database-notes.md)。

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
| `TZ` | `Asia/Shanghai` |

MCP 不提供公网访问；不需要对外 MCP 时保持 `CECSMS_MCP_API_KEY` 未配置。真实 AI 使用云端专用的 `DEEPSEEK_API_KEY` / `DEEPSEEK_MODEL`，或项目支持的其他 Provider 变量。密钥与模型额度未配置时，传统业务可用，真实 AI 不可用；不得将 Mock 当作真实模型服务。全站模型支出上限在供应商账号中设置，每用户限流不能替代费用上限。

### frontend

设置 `PORT=8080`。默认上游为 `backend.railway.internal:8083`；只有确实修改后端服务名时才设置 `SILVERPILOT_BACKEND_HOST`。

前端仅允许 Railway HTTP edge 作为公网入口。按 Railway 文档读取 `X-Real-IP`，重新生成到后端的客户端地址头和 HTTPS 协议头；不要将该配置直接搬到无可信代理的公网主机。公开前必须用伪造 `X-Real-IP` / `X-Forwarded-For` 的请求确认 Railway edge 覆盖行为。私网中的其他服务也属于这个信任边界。

## 发布顺序与验收

1. 确认 Railway 账号套餐、资源限额和账单告警；创建隔离 project/environment，保持 GitHub 源码可回溯。
2. 创建三个持久卷、设置运行变量，依次部署 mysql、redis，确认初始化成功且无公网数据库入口。
3. 部署 backend，确认健康为 `UP`；部署 frontend，生成 HTTPS 域名，再设置后端 CORS 并重新部署后端。
4. 在未登录 Railway 的浏览器中访问 `/login` 与 `/readyz`。检查验证码、两个新账号登录、原默认密码失效、业务列表与媒体、写操作确认及权限隔离。应用自身仍要求业务账号登录。
5. 验证 HTTPS origin 的请求、伪造转发头不会绕过限流，公开 MCP/管理监控入口被阻断。真实 AI 需使用已授权的模型凭据单独验证。
6. 重启后端和数据库后重复登录、媒体和持久化检查。开启卷的每日及每周备份，做一次隔离恢复验证，再记录真实地址、提交号与验收时间。

不要向项目存入真实个人或健康资料来做公开演示。当前种子数据为合成数据。受控知识有审核期限，不能通过修改日期假装完成复核；部署不会延长文档有效期。

关闭 Serverless 可避免闲置休眠，但不构成永久可用保证。带持久卷的单副本重新部署会短暂中断。托管持续性还取决于账号、资源、费用、供应商服务及访客所在地网络。

## 本地/CI 验证

`bash deploy/railway/smoke.sh` 使用随机临时凭据和隔离 Compose 项目构建、运行四服务，清理仅针对它创建的临时项目。它需要 Linux Docker Engine 与 Compose；不创建 Railway 资源。GitHub Actions 的 Railway deployment workflow 使用同一入口。通过此检查仍不能替代 Railway edge、真实卷、备份恢复和模型在线验收。

## 官方依据

- [Compose 到 Railway 服务的映射](https://railway.com/guides/docker-compose)
- [Dockerfile 构建](https://docs.railway.com/builds/dockerfiles)
- [公网代理头](https://docs.railway.com/networking/public-networking/specs-and-limits)
- [私网与 DNS](https://docs.railway.com/networking/private-networking/how-it-works)
- [卷与权限](https://docs.railway.com/volumes)及[备份](https://docs.railway.com/volumes/backups)
- [Serverless](https://docs.railway.com/deployments/serverless)与[配置迁移](https://docs.railway.com/config-as-code)
