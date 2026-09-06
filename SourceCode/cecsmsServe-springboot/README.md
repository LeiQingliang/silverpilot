# cecsmsServe-springboot

[返回项目主页](../../README.md) · [部署说明](../../docs/DEPLOYMENT.md) · [系统架构](../../docs/ARCHITECTURE.md) · [AI Harness](../../docs/AI_HARNESS.md)

这是 SilverPilot 的 Spring Boot 后端与 Agent 执行边界。Maven `artifactId` 是 `cecsms-serve`，启动类是 `com.cecsmsserve.CecsmsServeApplication`；模型不能绕过这里的身份、权限、参数校验、确认状态机或业务服务直接写数据库。

文档快照：2026-08-21 已按当前 `pom.xml`、应用配置、Controller/Service、数据库契约健康项和实际本地混合启动结果核对；本次 `mvnw.cmd clean verify` 为 87/87 通过。

版本基线为 Java 25 LTS、Spring Boot 4.1.1、Maven Wrapper 3.9.16 与 MySQL Connector/J 26.7.0 GA。Maven Enforcer 会拒绝非 Java 25 或低于 3.9.16/进入 Maven 4 preview 线的构建环境；Spring Boot BOM 统一管理其传递依赖，避免为了追逐单个更高版本而破坏框架验证过的组合。

## 快速定位

| 目标 | 入口 | 成功标准 |
| --- | --- | --- |
| 全 Docker 运行 | 根目录 `.\start-docker.cmd` | `/actuator/health` 为 `UP`，前端代理可访问 |
| IDEA 断点开发 | 本页[IDEA 启动](#idea-启动) | Redis 与数据库健康，IDEA 独占 Java/8083 |
| 编译与单元测试 | `.\mvnw.cmd clean verify` | Enforcer、编译、87 项当前测试及项目门禁通过 |
| 排查数据库契约 | Actuator `databaseContract` + [数据库 README](../../database/README.md) | 21 表、18 核心外键、6 关键索引及不变式通过 |
| 排查图片/文件 | 本页[静态资源与上传文件](#静态资源与上传文件) | 上传、种子和仓库媒体按优先级解析，无越界路径 |
| 修改 Agent | 本页[Agent 后端](#agent-后端) | 工具、确认、知识、限流与评测契约按改动范围通过 |

## 运行拓扑与配置优先级

| Profile | 由谁启动 | 数据库 / Redis | 配置来源 |
| --- | --- | --- | --- |
| `host` | IDEA | 本机 MySQL `3306` / Docker Redis `6380` | 被忽略的 `config/application-host.properties` |
| `docker` | Compose | Compose 服务名与容器端口 | `compose.yaml` 注入环境变量 |
| `test` | Maven/Spring 测试 | 测试资源与 Mock/隔离配置 | `src/test/resources` |

公共默认值在 `src/main/resources/application.properties`，敏感值和本机差异必须来自外置配置或环境变量。`.env.example` 只是变量目录，Spring Boot 不会自动读取它；不要把真实 Key 写入 `src/main/resources`、README 或 Maven 参数。

## MyBatis-Plus 数据链路

- Mapper 统一继承 `BaseMapper`，业务 Service 使用 `IService`/`ServiceImpl`，动态查询使用 `LambdaQueryWrapper`，避免字符串列名在字段重构后静默失效。
- `MybatisPlusConfig` 先注册 `BlockAttackInnerInterceptor`，阻止无条件全表更新和删除；MySQL 分页拦截器按官方建议放在最后，单页最多 100 条。
- 菜谱分页直接使用 `Page`，逻辑删除、自动填充和实体表字段映射继续由 MyBatis-Plus 管理；配置顺序和分页上限有独立测试。
- 活动、服务订单、健康报告和活动报名的外键 ID 在 Jackson 3 与 MyBatis result map 中显式映射，避免 `dId/uId/aId` 等字段在请求或响应中静默丢失。
- Actuator 的 `databaseContract` 健康项会核验 21 张必需业务表、18 个纳入启动门禁的核心业务外键、6 个关键索引、孤儿记录、报名状态/计数和服务分类层级；种子 SQL 另有 3 个菜谱域外键，总数为 21。任一门禁契约失败都会使容器健康检查失败。
- Java 编译启用 `unchecked`、`deprecation`、`removal` 警告并将任一警告视为失败；根验证脚本再以完整运行时 classpath 执行 `jdeprscan`。

完整的数据库、后端和前端启动顺序见[项目根 README](../../README.md)。

本地开发严格分离：IDEA 是 Spring Boot/Java 进程的唯一启动者，VSCode 是 Vue/Vite 进程的唯一启动者。项目根 Local 入口只准备 Docker Redis 8.2.9 Extended 和外置 `host` 配置，不会创建 Java 或 Vite 进程。

## IDEA 启动

1. 使用 IDEA 打开当前目录 `cecsmsServe-springboot`，或在仓库根项目中加载本目录的 `pom.xml`。
2. 从本目录的 `pom.xml` 重新加载 Maven 项目。
3. Project SDK 选择 JDK 25。
4. 新建或检查 Application 运行配置：Main class 为 `com.cecsmsserve.CecsmsServeApplication`。Working directory 可为当前后端目录或仓库根；`host` profile 会从对应位置导入生成的同一份外置配置。
5. 打开 `src/main/java/com/cecsmsserve/CecsmsServeApplication.java`，点击 `main` 方法左侧绿色三角形运行。

建议把 IDEA 配置命名为 **CecsmsServeApplication**。仓库不提交 `.run` 文件，避免把本机路径和 IDE 状态当成跨机器配置；真正的启动类和 profile 以上述值为准。两种 Working directory 都只加载被忽略的 `config/application-host.properties`，不会把密钥写入源码资源。

推荐先在项目根目录执行 `scripts/docker-dev.ps1 redis`。脚本只启动并验证 Docker Redis，同时创建被 Git 和 Docker 构建忽略的外置 `config/application-host.properties`；其中统一保存宿主机 MySQL `3306`、Docker Redis `6380`、随机 JWT/MCP secret 和本机模型配置，不位于 `src/main/resources`，不会被打进 JAR。应用在 `application.properties` 中把 `host` 设为默认 profile，因此命令行和 IDEA 使用同一拓扑；测试使用 `test`、Compose 使用 `docker`，不会读取本机配置。

如果此前运行过全 Docker，`redis` 会停止并移除本项目的 `mysql/backend/frontend` 容器，自动释放 `.env.docker` 中配置的 MySQL、后端和前端宿主端口，但不会删除 MySQL、Redis 或上传命名卷，也不会停止宿主机 MySQL。可先运行 `scripts/docker-dev.ps1 doctor` 查看配置和容器状态。

需要脱离生成的外置配置手工启动时，至少要提供同等的数据库、Redis、JWT 和模型配置；完整变量说明见 `.env.example`：

```text
CECSMS_DB_URL
CECSMS_DB_USERNAME
CECSMS_DB_PASSWORD
CECSMS_REDIS_ENABLED=true
CECSMS_REDIS_HOST=127.0.0.1
CECSMS_REDIS_PORT=6380
CECSMS_REDIS_PASSWORD
CECSMS_REDIS_HEALTH_ENABLED=true
CECSMS_JWT_SECRET
DEEPSEEK_API_KEY
DOUBAO_API_KEY
DOUBAO_MODEL
CECSMS_MCP_API_KEY
```

`.env.example` 只用于说明变量名，Spring Boot 不会自动读取该文件，也不要将真实密钥写入可提交文件。推荐仍由根脚本生成被忽略的配置，完整 Docker 方案见根 README。

## 命令行验证

```powershell
.\mvnw.cmd clean verify
```

2026-08-21 的直接单元测试结果为 87 项通过、0 失败/错误/跳过。这个数字只代表当前测试集；Controller、数据库和 Provider 的真实运行链路仍需按改动范围追加全栈验证。

全 Docker 运行态可在项目根目录执行 `scripts\verify-running-stack.ps1 -FullAudit`，其中包含经前端代理的真实 MySQL CRUD；`scripts\verify-database-seed.ps1` 会在随机临时 schema 中完整导入并校验种子 SQL，结束后删除临时 schema。

本地不提供命令行 Java 等价启动。`start-local-backend.ps1` 仅为历史命令兼容保留：它可以准备 Redis/配置、验证 IDEA 进程健康，但不含任何 Java 启动逻辑。编译、运行、断点和停止都在 IDEA 内完成。

默认端口为 `8083`，健康检查地址为 `http://127.0.0.1:8083/actuator/health`。

## 静态资源与上传文件

`/image/**`、`/file/**` 和 `/video/**` 优先从 `CECSMS_UPLOAD_DIR` 的可写目录读取；未命中时依次从可选的 `CECSMS_ASSET_DIR` 与 `CECSMS_LEGACY_ASSET_DIR` 只读根目录兜底。Docker 将项目根 `image/file/video` 挂载到 `/app/repository-media`，数据库种子和已有卷都使用其中真实存在的栅格图片。

本地 IDEA 配置由 `scripts/docker-dev.ps1` 把 `file.image-base-path` 固定为当前项目根目录。这样即使 Windows 用户或机器环境中残留了另一个检出目录的 `CECSMS_UPLOAD_DIR`，也不会把本项目的 `/image/**` 请求错误地导向旧目录。

| 优先级 | 配置 | Docker 对应位置 | 用途 |
| ---: | --- | --- | --- |
| 1 | `CECSMS_UPLOAD_DIR` | `/app/uploads` | 用户新上传的可写内容 |
| 2 | `CECSMS_ASSET_DIR` | 默认留空 | 可选的额外只读素材 |
| 3 | `CECSMS_LEGACY_ASSET_DIR` | `/app/repository-media` | 项目根 `image/file/video` 的只读历史媒体 |

请求路径会先做规范化和根目录边界校验，禁止 `..` 等路径穿越。`PersistedImageIntegrityMigration` 会幂等恢复旧演示占位路径，并在每次后端启动时验证四类业务表的全部非空图片 URL、文件存在性与文件签名；任一路径失效都会使启动失败。数据库应保存 Web URL，不保存本机绝对路径；Docker 挂载改变后必须重建/重启后端并执行运行验证，不能仅验证文件在宿主机存在。

登录验证码在 Redis 开启时使用带 TTL 的原子 `GETDEL`，可跨后端实例一次性验证；代码仍保留有容量上限的故障应急保护，但受支持的本地启动模式必须通过 Docker Redis 门禁。参数缺失、类型错误和不支持的 HTTP 方法分别返回统一 400/405 JSON，不再误报为 500。

## Agent 后端

Agent 入口是 `/chat`，共有 13 个真实业务/知识工具。只读工具直接执行；活动报名、服务预约/取消、助餐预订/取消必须先创建待确认动作，再通过：

```text
POST /chat/actions/{confirmationToken}/confirm
POST /chat/actions/{confirmationToken}/cancel
GET  /chat/actions?limit=10
```

写动作有用户归属校验、10 分钟有效期、原子状态迁移和幂等确认。执行或取消后会清除保存的原始参数，并保留不含原始参数的审计摘要。每个请求还会返回 `X-Request-ID` 便于从 `logs/cecsms.log` 定位问题。

主要端点：

| 方法与路径 | 用途 |
| --- | --- |
| `POST /chat` | 文字/图片请求、模型路由与工具循环 |
| `POST /chat/care-plan` | 生成经真实服务 ID、知识来源和 JSON 契约校验的只读照护导航方案 |
| `GET /chat/status`、`GET /chat/providers` | 查看 Prompt、知识库、限流、MCP 与 Provider 可用状态 |
| `GET /chat/analytics?days=7` | 当前用户的脱敏运行指标 |
| `GET /chat/admin/overview` | 管理员运营总览与脱敏审计数据 |
| `GET/POST /chat/actions...` | 查询、确认或取消当前用户的待确认动作 |

管理员总览端点只接受 `roleId=1`，聚合脱敏运行指标、Provider/多模态可用性、Prompt 元数据、知识状态、WorkBuddy 只读调用和最近操作；不返回密钥、原始 Prompt 或个人健康内容。

豆包未配置时只禁用图片通道，不影响已配置的 DeepSeek 文本工具链；没有外部 Key 时可显式启用 Mock 只读开发模式。

`POST /mcp` 是 WorkBuddy 的无状态 Streamable HTTP 入口，必须配置独立 `CECSMS_MCP_API_KEY`，且只提供 6 个非个人只读工具。

`src/main/resources/schema.sql` 会在启动时以 `CREATE TABLE IF NOT EXISTS` 创建 `agent_action` 和 `agent_run`，不会重建其他业务表；旧 Agent 表由 JDBC metadata 驱动的幂等迁移升级。当前 Prompt 是 `prompts/silverpilot-agent-v3.md` 与 `prompts/care-recommendation-v1.md`，加载时会记录 ID、版本和 SHA-256 指纹。完整 AI Harness、限流、Token、重试和知识配置见 [AI Harness](../../docs/AI_HARNESS.md)。

## 常见问题

| 现象 | 优先检查 |
| --- | --- |
| IDEA 启动后 8083 被占用 | 是否仍运行全 Docker 后端；先从根目录切换到 Redis-only 模式，不要结束无关 Java 进程 |
| 数据库连接失败 | `host` 配置是否生成、本机 MySQL 是否监听 3306、密码环境变量是否属于当前用户 |
| Actuator 为 `DOWN` | 展开具体 component；重点检查 `databaseContract`、Redis、知识路径，不要只重启掩盖错误 |
| 页面图片 404 | URL 前缀、三层资源根、Compose 只读挂载、文件大小与通信审计 |
| `/chat` 返回 503 | `/chat/status` 中 Provider、知识、MCP 和多模态状态；无 Key 时只能显式启用 Mock |
| 写动作没有等待确认 | `agent_action` 状态迁移、用户归属、TTL 和前端确认卡；这是阻断发布的问题 |
| MCP 401/工具数不对 | 独立 `CECSMS_MCP_API_KEY`、请求头、协议初始化和精确 6 个只读工具 |

变更后端 README 中的版本、端点数量或验证数字前，应同时核对 `pom.xml`、Controller/Service、`runtime-versions.json` 和本次实际命令输出；历史结果不自动继承到新代码。
