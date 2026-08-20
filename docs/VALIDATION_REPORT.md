# 最终验证与安全扫描报告

验证日期：2026-08-21（Asia/Shanghai）
验证范围：`v1.0.1` 基线加当前 `Unreleased` 工作区源码、本地 Docker 镜像、隔离 Compose 运行栈、只读 MCP 与合成演示 MySQL 数据。
结论边界：以下是一次时间点验收，不是零漏洞、生产 SLA、临床准确率或合规认证声明。

## 0. 根 image 真实图片与重复启动回归（2026-08-21）

- 静态种子门禁确认 48 个唯一 `/image/...` URL 全部位于项目根 `image/`，原文件签名正确且 48 个同名 WebP 伴随文件存在；完整媒体检查实际解码 211 个栅格资源、解析 1 个前端 SVG，并确认根 `image/` 的 101 组原图/WebP 均可用；
- 新数据库路径在随机临时 schema 完整导入 21 张表，18 个门禁外键、6 个关键索引、业务不变式、48 个真实图片 URL 与 `CHECK TABLE` 全部通过，临时 schema 已在 `finally` 中删除；
- 已有演示卷保留命名卷完成幂等迁移：48 条活动/资讯/菜谱/服务占位 URL 恢复为根 `image/` 的真实图片，另 1 条无对应文件的旧菜谱占位被清空；迁移后数据库不再含 `/image/demo/*.svg`，后端每次启动继续验证全部非空图片路径与文件签名；
- 后端 `87/87` tests、前端 `43/43` tests、5 组属性测试、生产构建与 production audit 均通过；
- 从变更镜像重建启动后，四个服务全部 `healthy`，48 个持久化图片 URL 逐一经前端代理返回非空 `image/*`；随后再次执行同一启动入口，命中已验证镜像且不重建/不重置卷，仍通过全部启动门禁；
- 完整运行态审计 `97/97` 通过，包含持久化业务图片及 WebP 伴随资源；真实 Nginx → Spring Boot → MyBatis → MySQL 活动 CRUD 完成并精确清理测试行。

## 0A. v1.0.1 发布候选历史门禁（2026-08-20）

- 30 个 PowerShell 脚本全部通过语法解析；发布契约同步核对 Maven/npm/Citation/Changelog/发布说明版本，阻断未固定到 40 位 SHA 的远程 Action、未禁用凭据持久化的 checkout、未固定的 Docker 镜像和 npm tarball；
- 后端 `84/84` tests，0 failures / errors / skipped；JAR 外置配置泄漏检查与 JDK 25 `jdeprscan` 通过；前端 ESLint、Knip、`34/34` 单元测试、Vite 生产构建和 npm production audit 全部通过；
- 新增 5 组 `fast-check` 属性测试，以固定 seed `20260820` 运行 10,000 个生成用例。初次运行在第 984 个统计输入捕获无原型对象触发的数值转换异常；修复为安全有限数转换并增加单元回归后，同 seed 全部通过；
- npm 12.0.2 官方 tarball 的 SHA-256 `5dbb86c71d07a1957f2e90734092dd6a58bdcd9ebc2d8d41ca1c6e6a21d364e1` 经在线下载独立核对；BuildKit 实际执行 `ADD --checksum` 并从该 tarball 安装 npm，前端镜像构建成功；
- Actionlint 1.7.12 的 Windows amd64 官方归档经官方 SHA-256 `6e7241b51e6817ea6a047693d8e6fed13b31819c9a0dd6c5a726e1592d22f6e9` 核对后验证全部工作流通过；
- Gitleaks 8.30.1 官方 Windows 归档经 SHA-256 `d29144deff3a68aa93ced33dddf84b7fdc26070add4aa0f4513094c8332afc4e` 核对后扫描全部可达历史，实际扫描 15 个提交、约 3.94 MB，0 泄漏；
- 强制在线版本门禁通过，Java/Maven/Spring Boot/Node/npm/Docker/Compose/MySQL/Redis/Nginx/Go/Alpine 均来自对应稳定、LTS 或 Extended 事实源，且 npm 文件哈希与仓库策略一致；
- 使用 `--pull` 构建 MySQL、Redis、后端和前端 4 个正式镜像；另在隔离 Compose 项目启动四服务栈，健康、前端代理、Redis、数据库身份、Agent/知识状态、97/97 同源通信检查和真实 MySQL CRUD 全部通过；测试业务行、容器、网络、数据卷和 QA 镜像均已清理；
- Docker Scout 最新数据库首次复扫在 MySQL 上游快照的 `curl`/`libcurl` 中识别出 4 个 High（CVE-2026-3783、CVE-2026-1965）；派生镜像从 Oracle Linux 受支持仓库定向升级到 `7.76.1-40.el9_8.5` 后重新构建，四个最终镜像复扫均为 0 Critical/High；修复后的 MySQL 再次通过完整 seed 合约；
- 独立 MySQL seed 合约复核 21 张表、18 个外键、6 个关键索引、业务约束与 `CHECK TABLE` 全部通过，一次性 schema 与隔离数据卷均已删除；
- 当时的增量 seed 曾把最终媒体 URL 映射到 `database/seed-assets` 的 8 个演示 SVG；该方案已由上方 2026-08-21 根 `image/` 真实图片契约取代，此条只保留为历史证据；
- 全 Docker 运行态再次通过 97/97 同源通信、真实活动 CRUD 零残留；Edge 按固定浅色产品契约覆盖 34 路由的桌面/手机 68 个场景，68/68 通过。全部路由已统一为电影感内容岛、玻璃面板、渐变能量线和五类响应式页面家族；对应测试同时阻止热链参考站素材。此前“我的活动” `ElOnlyChild` 警告与取消报名误传表格索引均已修复并加入 2 个前端回归测试；
- 使用本机忽略文件中的 DeepSeek 凭据重新执行真实 Provider 完整契约，16/16 通过；凭据未输出、未进入源码、前端产物、后端 JAR、镜像配置或容器日志。豆包 Vision 未配置，多模态用例按预期明确拒绝图片，没有伪造识别结果；
- 当前四个最终运行镜像与容器身份逐一一致后使用 Docker Scout 1.24.0 复扫：后端 `0e5dc8e5c0d7`（350 包）、前端 `38f52b92fb7d`（26 包）、MySQL `319402a1714b`（145 包）、Redis `2e47f8403202`（26 包），Critical/High 均为 0。前端视觉重建后的镜像已单独重新索引确认 0 Critical/High；Windows 临时归档清理出现文件占用警告，但扫描退出码为 0；
- GitHub 仓库 Actions 已切换为 selected 模式并强制 SHA 固定，仅允许 GitHub 官方 Action 与精确审核的 Scorecard/Gitleaks SHA；活动规则集 `Protect version tags` 对 `refs/tags/v*` 禁止更新和删除且无绕过者；
- `v1.0.1` 最终 Release、资产校验和、构建证明、不可变状态及 Release/main/tag 提交一致性，仍必须以合并后的 GitHub 工作流和独立下载核验为最终证据。

### v1.0.0 历史发布基线（2026-08-20）

- 后端从 `1.0.0-SNAPSHOT` 转为正式 `1.0.0`；Maven、npm、`runtime-versions.json`、`CITATION.cff`、Changelog 与版本发布说明由自动契约保持一致；
- 27 个 PowerShell 脚本全部通过语法解析，新增发布契约进入根级质量门禁；Actionlint 1.7.12 的 Windows amd64 官方归档经官方 SHA-256 `6e7241b51e6817ea6a047693d8e6fed13b31819c9a0dd6c5a726e1592d22f6e9` 核对后验证全部工作流通过，临时工具随后清理；
- 后端 `83/83` tests，0 failures / errors / skipped；JAR 外置配置泄漏检查与 JDK 25 `jdeprscan` 通过；
- 前端 ESLint、Knip、`31/31` tests、Vite 生产构建和 `npm audit --omit=dev --audit-level=high` 全部通过，生产依赖已知漏洞为 0；
- 所有非 `scratch` Docker 基础镜像同时固定标签和多架构 OCI SHA-256；7 个摘要在写入前通过 Docker Buildx 独立解析；Compose 模型接受固定后的 Dockerfile；
- 使用 `--pull` 完整构建 MySQL、Redis、后端和前端 4 个镜像；在隔离 Compose 项目中启动 MySQL 9.7.2，一次性 schema 的 21 张表、18 个外键、6 个关键索引、业务约束与 `CHECK TABLE` 全部通过，测试 schema、容器、网络和数据卷已精确清理；
- Gitleaks 8.30.1 官方 Windows 归档经官方 SHA-256 核对；16 个 Git 历史提交与实际将发布的 427 个文件均为零泄漏，被 `.gitignore` 隔离的本机运行配置不进入发布集；
- 强制在线版本门禁通过。Go 官方下载 API 已把 1.27.0 标为 stable，但 Docker Official Image 当前最新可用 Alpine 3.24 builder 为 1.26.7，因此事实源分别记录上游 stable 与实际 Docker builder，不使用不存在的 `golang:1.27.0-alpine3.24` 标签；
- Release 自动化只接受指向当前 `main` 的稳定 SemVer 标签，先构建/证明资产，再创建草稿、上传全部附件并发布；独立验证仍必须以最终 GitHub Release、标签提交和云端工作流结果为准。

## 1. 验证环境

| 工具 | 版本 |
| --- | --- |
| OS | Windows 11 amd64 |
| Java | 本机 Eclipse Temurin 25.0.4 LTS；容器 Temurin 25.0.3+9 LTS |
| Maven Wrapper | 3.9.16 |
| Node.js / npm | 24.19.0 LTS / 12.0.2 |
| 容器 OS 基线 | Ubuntu 26.04 LTS / Alpine 3.24.1 |
| Docker Engine / Desktop | 29.7.2 / 4.87.0 |
| Docker Compose | 5.5.0 |
| Docker Scout | 1.24.0 |

## 2. 源码门禁

执行：

```powershell
.\scripts\verify-project.ps1
```

| 门禁 | 结果 |
| --- | --- |
| 知识库治理 | 9 个文件通过；其中 8 个 `approved` 被运行时索引 |
| 评测集 schema | 16/16 用例有效 |
| 后端测试 | 84 tests，0 failures，0 errors，0 skipped |
| Java 弃用门禁 | `javac` 警告即失败；JDK 25 `jdeprscan` 使用完整依赖 classpath，0 命中 |
| 前端 Lint / 可达性 | ESLint 0 error/0 warning；Knip 0 个无引用文件、依赖或导出 |
| 前端测试 | 33 个单元测试 + 5 组属性测试；固定 seed 下 10,000 个生成用例全部通过 |
| 前端生产构建 | Vite build 通过，2,413 modules transformed |
| npm production audit gate | 0 个 high/critical |
| 版本门禁 | 每次一键启动联网核对 Java/Maven/Spring Boot/Node/npm/Docker/Compose/MySQL/Redis/Nginx/Go/Alpine 官方稳定渠道及 npm tarball SHA-256，并拒绝预发布、文件替换或版本漂移；本地模式另验宿主工具、依赖树与 MySQL |

另行执行完整 `npm audit --json`，metadata 共计 310 个 production/dev/optional 依赖关系，所有 severity 均为 0。

`mvn dependency:analyze -DskipTests` 构建成功。它把 Spring Boot、MyBatis-Plus 和测试 starter 所提供的传递依赖同时标记为 “used undeclared / unused declared”；这是聚合 starter 的静态分析局限，不能作为删除 starter 的依据，也不是漏洞结果。

长期支持版本刷新另行核验：Maven Versions Plugin 显示当前父项目、构建插件和其余直接依赖均无更高稳定版，仅将 MySQL Connector/J 从 9.7.0 升至 26.7.0 GA；`npm outdated --json` 返回空对象。Spring Boot 管理的传递依赖继续采用 4.1.0 BOM 的一致版本集，没有强行覆盖成未经该框架组合验证的单项最高版本。

### MyBatis-Plus、Element Plus 与文件可达性增量复核

- MyBatis-Plus 原有分页配置合并为唯一配置 Bean，先执行全表写保护，再按官方建议将 MySQL 分页拦截器放在最后，并限制单页 100 条；测试实际证明拦截器顺序与参数，测试数据清理也改为带条件删除；
- 业务动态查询从字符串列名迁移为 Lambda 方法引用，菜谱 `Page` 分页、逻辑删除、`BaseMapper`/`ServiceImpl` 和复杂 XML 联表各自保留适合的职责；
- 根级 `ElConfigProvider` 统一中文 locale，路由恢复按钮、活动状态筛选、类型搜索、医生字段、健康报告和图片上传继续使用 Element Plus；上传回调不再是空实现，并与后端 `imageFile` 契约一致；
- 删除 17 个经引用图审计确认不可达、空占位或失效的旧前端文件；恢复仍有真实业务价值的搜索和报告字段；所有 Git 跟踪文件中 0 个零字节文件；
- ESLint 10 + eslint-plugin-vue 会阻断 Vue 3 弃用语法和未使用符号，UI 契约测试阻断 Element Plus 已知淘汰绑定，Knip 会阻断新出现的无引用文件/依赖/导出。这里的“文件有用”指源码可从真实入口到达、脚本/文档/配置有明确入口，不虚构每个文件都属于浏览器运行时。

## 3. Docker 与数据库运行验收

执行 `scripts/docker-dev.ps1 full` 后：

| 服务 | 状态 | 主机端口 |
| --- | --- | --- |
| MySQL 9.7.2 LTS 最小派生镜像 | healthy | `127.0.0.1:3307` |
| Redis 8.2.9 Extended 官方源码构建镜像 | healthy，authenticated `PONG` | `127.0.0.1:6380` |
| Spring Boot backend | healthy，Actuator `UP` | `127.0.0.1:8083` |
| Nginx/Vue frontend | healthy，`/healthz` HTTP 200 | `127.0.0.1:8082` |

早期三拓扑方案也曾从全 Docker 运行态做往返验收：当时 `docker-dev.ps1 up` 会保留 Docker MySQL/Redis、移除 `backend/frontend`，外置 Java 使用 `local` profile。该结果仅是已退役方案的历史记录，不再代表当前启动方式；本地 Java 占用 `8083` 时，`full` 预检拒绝并报告 PID 的保护仍然保留。

2026-08-19 又验收了一键本地混合模式：`docker-dev.ps1 redis` 从完整停止状态只启动 `silverpilot-redis-1`，镜像与服务端均为 Redis 8.2.9，authenticated `PONG`、AOF、`127.0.0.1:6380` 和唯一 Compose 服务门禁全部通过；没有启动 Compose MySQL/backend/frontend。随后 `start-project.ps1 -Mode Local` 以 `host` profile 启动本机 Java，端口归属实测为本机 `mysqld.exe:3306`、`java.exe:8083`、`node.exe:8081`，`3307/8082` 空闲；`verify-local-stack.ps1` 通过后端直连/代理健康、Vite 页面、本机数据库身份查询、Agent 状态和独立 Docker Redis 复核。向门禁注入错误期望版本 `0.0.0` 时脚本按预期非零退出并报告镜像漂移，恢复真实期望版本后 Redis 门禁与后端健康仍为 `UP`。该模式不再接受关闭 Redis 或进程内 fallback 作为正常启动结果。

旧开发拓扑还曾从完整 `down` 状态冷启动 Docker MySQL/Redis，并确认命名卷前后均为 22 张业务表、18 个演示用户；当前 `redis/up/dev` 已统一为 Redis-only 本地基础设施动作，Docker MySQL 只在 `full` 模式启动。

旧方案首次把外置配置改为无 profile 的高优先级 `config/application.properties` 时，门禁发现其 MySQL URL 会覆盖 H2 测试配置，随后曾改为 `application-local.properties`。当前进一步合并为外置 `application-host.properties`，仅保留 `host/test/docker` 三类配置作用域；JAR 门禁同时拒绝 `application-local.properties` 和 `application-host.properties` 进入制品。

2026-08-19 最终统一回归确认 IDEA + VSCode 已成为本地混合模式的手动入口，不再维护 Docker MySQL 开发变体。命令行后端与 IDEA 共用外置 `application-host.properties`，直接启动日志确认 `host` profile，聚合健康和 Redis 独立健康均为 `UP`。`verify-run-modes.ps1 -CrossSwitch` 依次完成本地同模式重复启动、本地停止后再启、Docker 同模式重复启动、Docker 停止后再启、Local→Docker、Docker→Local，以及各停止点的端口/项目进程/容器/网络零残留断言。随后直接执行 `start-local.cmd` 与 `start-docker.cmd`，两者均在就绪后正常停止并释放资源。文件清单指纹改为 .NET 序数排序后，PowerShell 7 首次建立新指纹，Windows PowerShell 5.1 紧接着识别相同输入并使用 `--no-build`，证明两个宿主版本之间不会误触发重建。

2026-08-19 根据最终运行边界要求，上述“脚本托管本机 Java/Vite”的一键 Local 方案已退役，仅作历史验收记录。当前 `start-project.ps1 -Mode Local`/`start-local.cmd` 只准备 Redis 与外置配置；`start-local-backend.ps1` 不再启动 Java，VSCode `Frontend: dev server` 只执行 `npm run dev`，`stop-project.ps1` 不再终止 IDEA/VSCode 进程。新增 `verify-ide-separation.ps1` 作为根门禁，阻断 Maven 嵌入前端、npm/VSCode 启动 Java、Local 根脚本拉起两端或 detached Vite 的回归。

同日全 Docker `-FullAudit` 再次通过 97/97 同源通信检查，并完成一条随机临时活动的创建、读取、更新、删除及 MySQL 零残留确认。最终状态为 0 个项目运行容器、0 个项目网络、0 个 `8081/8082/8083/3307/6380` 监听和 0 个项目 Java/Vite 运行进程；`silverpilot_silverpilot-mysql-data`、`silverpilot_silverpilot-redis-data`、`silverpilot_silverpilot-uploads`、无关 `redis-lts-data` 以及 IntelliJ JPS 构建服务均保留。

端口保护另做负向验证：用一个可精确回收的独立 PowerShell 进程占用 `127.0.0.1:8082` 后启动 Docker 模式，启动器以非零退出并准确报告进程名/PID，没有结束该进程，也没有留下项目容器；测试结束后仅按记录 PID 回收临时监听器，`8082` 再次空闲。

现有命名卷从 MySQL 8.4.11 升级到 9.7.2 前，先生成被 Git 忽略的逻辑备份 `backups/a_old-before-mysql-9.7.2-20260818-113346.sql`（109,049 bytes，SHA-256 `eeb14640f65b8fb4ed6b31298a748bb83a306ee93091a3149975808e9d2ebe0a`），并在临时 schema 完成恢复验证。升级日志确认数据字典从 `80300` 升至 `90200`。升级后核验：

- `a_old` 共 22 张表；
- `user` 共 18 条演示账户；
- 18/18 密码字段为 60 字符 BCrypt 格式；
- `agent_run` 的 prompt version 与三项 Token 列共 4 列全部存在；
- 后端重连并通过健康检查，数据卷未重置。

Redis 保留原命名卷，8.2.9 首次挂载时由最小入口脚本修复旧 AOF 目录属主后降权运行；进程 UID 实测为 999。写入临时标记、重启、读取并删除的 AOF 持久化回归通过，后端在 Redis 重启后仍为 `UP`。本次将实际运行镜像从 Alpine 3.23.5 重建到 3.24.1 时继续复用同一命名卷，重建前后 `DBSIZE=1`、`appendonly=yes` 保持不变，最终镜像摘要为 `3a61373bfd1e` 且健康。

本次版本刷新后的最终镜像再次启动验收：后端 Actuator 为 `UP`，前端 `/healthz` 与 `/login` 均为 HTTP 200；所有请求经 `http://127.0.0.1:8082` 同源代理的非写入通信矩阵为 97/97，通过 MySQL、Redis、JWT/角色隔离、业务查询、Agent 与 MCP 路径。

### 活动域数据库完整性与真实 CRUD 增量复核（2026-08-18）

- 修复前只读审计发现 11 条 `user_activity` 活动孤儿记录和 1 条 `role_function` 功能孤儿记录；执行修复前生成完整逻辑备份 `backups/a_old-before-integrity-20260818-204611.sql`（159,422 bytes，SHA-256 `15b4055017c6c92c6823f456bab2cb3f583458750bb5056404caec8efc880ca5`），问题行同时进入 `data_integrity_quarantine`，随后活动域孤儿计数为 0；
- 当前 schema 增加 18 个真实外键和 6 个高频查询复合索引；报名状态统一为中文业务枚举，`activity.signNum` 与确认报名数重新对齐；数据库契约已进入 Actuator 健康检查，而非仅写在文档中；
- 修复 Jackson 3 对 `dId/uId/mId/aId` 等 JavaBeans 缩写字段的请求/响应命名差异，并在 Activity、ServiceOrder、UserActivity、Report 的 MyBatis result map 中显式保留外键列；回归测试同时覆盖序列化和反序列化；
- 服务订单状态迁移、取消报名释放名额、活动定时状态与积分发放改为带期望状态的条件更新，避免重复请求或多实例调度导致重复扣减、重复积分及覆盖更新；
- `verify-database-crud.ps1` 已经前端 Nginx 代理完成活动 Create/Read/Update/Delete，逐步用直连 SQL核对写入结果，并验证读取响应仍含负责人/类型外键；随机 QA 行在结束后为 0；
- `verify-database-seed.ps1` 已将 `database/a_old.sql` 导入随机临时 schema，21/21 必需表、18/18 外键、6/6 关键索引、报名状态、报名计数、服务层级及 21 张表的原生 `CHECK TABLE` 全部通过，临时 schema 已删除；
- 最终源码总门禁为后端 81/81、前端 29/29、PowerShell 23 个脚本语法检查、Vite 生产构建和 npm 生产依赖审计全部通过；重建后的同源通信为 97/97，真实 CRUD 全路径通过。

## 4. 在线 Agent 与 MCP

执行：

```powershell
.\scripts\run-agent-evaluation.ps1 -EnvFile .\.env.docker -Provider deepseek -MaxCases 0
.\scripts\smoke-agent.ps1 -EnvFile .\.env.docker
.\scripts\mcp-smoke.ps1 -ApiKey '<local-only-key>'
```

结果：

- 2026-08-20 使用忽略文件中的本机凭据重新执行完整 DeepSeek 在线契约评测，16/16 通过；脱敏报告见 [agent-evaluation-live.json](agent-evaluation-live.json)，源码和报告均不保存凭据；
- 同一容器版本再次通过真实 Agent smoke：Provider usage、版本化 Prompt、RAG、结构化照护方案、待确认写动作取消、MCP 与 analytics；
- 覆盖真实工具选择、知识来源、动态真实服务/菜谱 ID、写操作 `PENDING` 后自动取消、所有权拒绝、120 紧急分流、秘密/注入防护、图片路由与循环上限；
- 多模态用例的 `run.status=FAILED` 是预期安全结果：本次强制 DeepSeek 文本通道且豆包未配置，后端明确拒绝图片，没有假装识别；
- 本机配置中的 DeepSeek Mock 已关闭；没有 Provider 凭据时仍可显式启用 Mock，只用于 6 条本地只读工具/知识契约，不能替代真实模型验收；
- MCP initialize 与 `tools/list` 通过，只暴露 6 个 read-only/non-destructive 工具。

自动评测判断的是机器可验证契约，不衡量医疗有效性、长期稳定性、用户满意度或商业转化。豆包在线 Vision 未配置，因此没有宣称其真实效果、延迟或费用已验证。

## 5. HTTP 安全响应头

全 Docker 前端 `/login` 实测包含：

- `Content-Security-Policy`：同源脚本/连接，禁止 object、frame ancestor；
- `X-Content-Type-Options: nosniff`；
- `X-Frame-Options: DENY`；
- `Referrer-Policy: strict-origin-when-cross-origin`；
- `Permissions-Policy: camera=(), geolocation=(), microphone=(self)`。

后端测试覆盖安全 Header 和 HTTPS 条件 HSTS；登录、Agent 与 MCP 响应配置 `Cache-Control: no-store`。

## 6. 漏洞扫描

### Docker Scout 最终镜像

扫描命令：

```powershell
docker scout cves --only-severity critical,high <image>
```

| 镜像 | 摘要前缀 | 包数 | Critical | High |
| --- | --- | ---: | ---: | ---: |
| `silverpilot-mysql:9.7.2` | `1ac09f2bc338` | 145 | 0 | 0 |
| `silverpilot-redis:8.2.9` | `e29dce385da8` | 26 | 0 | 0 |
| `silverpilot-backend:latest` | `ed464d642afd` | 350 | 0 | 0 |
| `silverpilot-frontend:latest` | `b04f32ebf5c5` | 26 | 0 | 0 |

扫描过程中 Docker Scout 在 Windows 临时目录报告过“临时镜像归档正被占用、无法删除”的清理警告，但索引和漏洞结论均正常返回，仓库中未产生这些临时文件。

前端曾使用带 4 Critical/15 High 的旧 Nginx 运行镜像；最终改为固定 `nginx:1.30.4-alpine3.24-slim` stable 并清零。MySQL 9.7.2 派生镜像移除运行时不需要的 `mysql-shell`、以 Go 1.27.0 重编译 `gosu`、定向安装 Oracle 已修复的 `curl`/`libcurl` 并压平层。Redis 8.2.9 初次源码构建扫描出基础 Alpine 中已存在修复版的 OpenSSL/musl 问题；最终固定 Alpine 3.24.1 并升级安全修复。后端切换到 Ubuntu 26.04 LTS 后，首次复扫在基础层未使用的 `/usr/bin/pebble` 中识别出 Go 1.26.5 的 1 Critical/5 High；最终构建删除该 helper 并压平清理后的根文件系统，运行文件与历史层均不再携带它。四个最终运行镜像复扫均为 0 Critical/High。

确认全部容器的实际镜像 ID 后，已逐个删除不再被引用的 `silverpilot-mysql:8.4.11`、`mysql:8.4.11`、`redis:8.2.8-alpine`、`nginx:1.31.3-alpine-slim`、`node:24.18.0-alpine3.23` 与 `alpine:3.23.3`。另一个独立容器 `redis-lts` 仍在使用 `redis:8.2.8`，不属于本项目且不是“未使用镜像”，因此保留，避免越过项目边界破坏其他服务。

### OWASP Java Dependency-Check

2026-08-20 改为 OWASP 维护的 NVD JSON 2.0 镜像、项目级持久 H2 缓存和强制离线扫描。首次冷缓存更新约 1 分 53 秒，后续离线扫描约 10 秒；更新与扫描分别有 15/10 分钟硬超时，缓存必须同时满足固定扫描器版本、168 小时窗口和数据库 SHA-256。CI 与 Release 使用按扫描器版本和 UTC 日期分代的持久缓存，因此不再让 `check` 目标直接等待 NVD REST API。官方版本门禁随后识别出 13.0.0 已成为最新稳定版，扫描器、缓存代际与门禁同步升级；旧 12.2.2 缓存先因版本不匹配被拒绝，再由 13.0.0 在约 28 秒内完成镜像刷新，最终离线扫描约 10 秒且报告结果不变。

第一次有效扫描真实发现 Tomcat 11.0.22、Netty 4.2.15.Final，以及 POI 5.2.5、Commons Compress 1.25.0、Jackson 2.21.4、Log4j 2.25.4 的漏洞。升级到 Tomcat 11.0.24、Netty 4.2.17.Final、POI 5.5.1、Commons Compress 1.28.0、Jackson 2.21.5、Log4j 2.25.5，并排除源码零引用的 Tomcat WebSocket 运行时后，后端 `84/84` 测试通过。最终 Dependency-Check 报告为 **82 个依赖、0 个受影响依赖、0 个未抑制漏洞**。

Tomcat 11.0.24 仅剩 `CVE-2026-66299`；Apache 将其限定为完整发行包中的 WebSocket chat 示例并明确未部署 examples 的用户不受影响。实际 `tomcat-embed-core` JAR 共 1,611 个条目，examples/chat 条目为 0。由于 11.0.25 尚未发布，仓库只对精确 `tomcat-embed-core@11.0.24` PURL + CVE 设置 2026-10-01 到期规则，并启用“未使用 suppression 即失败”。它不是对其他 Tomcat 或 CVE 的宽泛忽略。完整命令、失败边界和升级要求见 `docs/JAVA_DEPENDENCY_CHECK.md`。

## 7. 敏感信息与仓库门禁

- `.env.docker`、后端外置 `config/application-host.properties`、构建输出、日志、上传目录和本地原始素材均在 `.gitignore`；外置配置同时被 Docker 构建排除，JAR 条目检查确认没有 `application-local.properties`、`application-host.properties` 或 `.env`；
- 示例环境文件只含占位符；
- 模型 Key 不进入 Vue、Dockerfile、README、截图或在线评测报告；
- Gitleaks 8.30.1 经官方 SHA-256 校验后扫描全部可达本地 Git 历史，实际扫描 15 个提交、约 3.94 MB 内容为 0 泄漏；发布门禁同时检查将要公开的已跟踪/未跟踪文件、90 MiB 文件上限、高置信 Token/私钥和大陆手机号/身份证格式；
- 数据库种子中的联系号码和身份字段已替换为明确无效的合成值，并重新通过随机临时 schema 的 21 张表、18 个外键、6 个关键索引、业务不变式与 `CHECK TABLE` 门禁；
- GitHub Actions 工作流通过 actionlint 1.7.12，第三方 Action 均固定到完整提交 SHA；`git diff --check`、JAR 条目检查和公开文件清单复核通过。

任何曾在聊天、截图、日志或其他渠道暴露的模型 Key 都应在供应商控制台轮换；新 Key 只能放入本机被忽略配置。

### VSCode 前端启动回归（2026-08-18）

- 现场复现为残留 Vite 进程锁定 Windows Rolldown 原生模块，同时 `node_modules/.bin/vite.cmd` 已缺失，`npm ls vite` 判定依赖无效；
- 仅停止命令行明确属于当前仓库的 Vite 进程后，以 `npm ci` 从现有 lockfile 恢复 139 个包，完整审计返回 0 个已知漏洞；
- 新增的启动/停止脚本已实测：依赖自检、运行中拒绝重装、重复启动幂等、`/login` HTTP 200 与 Vue 挂载点、显式停止及 8081 释放全部通过；
- `npm run check` 最终通过 ESLint、Knip、29/29 前端测试、2,415 模块生产构建和生产依赖审计。该结论只覆盖本次本机代码与依赖快照，不等于未来不会出现新增漏洞。

### 前端设计系统与浏览器矩阵（2026-08-18）

- 建立统一浅色/深色语义 Token，并覆盖公共端、个人中心、运营后台与小伴服务工作台；核心色彩组合由自动化对比度测试校验；
- 修复 Element Plus 样式按需加载造成的路由样式闪动、表格百分比列宽失效、暗色主题 scoped `:global` 选择器误作用于根节点等问题；
- 修复 `sys_function.fId` 到 `parentId` 的显式 MyBatis/JSON 映射；真实接口返回 18 条权限记录，后台侧栏恢复 5 个一级业务菜单；
- 个人中心的活动、服务、体检报告和菜谱订单在 390×844 下使用业务卡片，保留查看、取消、确认和评分操作；
- 历史菜谱种子引用了从未分发的 `/image/recipe/fish.jpg`，当前运行库不做自动写入修改；前端识别该旧路径并显示可读占位，新建数据库的 SQL 已改为 `NULL`；
- 修复 ECharts `GraphicComponent` 漏注册、管理图表脱离后台壳层、活动详情缺少 ID 时请求 `NaN`、Element Plus 无效选项/分页/过渡绑定及多个移动端操作列不可见问题；
- 使用本地真实后端数据和短期 QA 身份执行 Edge 无头浏览器矩阵：32 个注册路由及 1 个缺参容错场景，在 1440×1000、768×1024、390×844 与浅色/深色组合下共 198/198 通过；空白页、错误跳转、页面级横向溢出、可见坏图、残留加载层、Vue/运行时异常、控制台错误与警告、HTTP 5xx 均为 0；
- 视觉复核覆盖首页、活动/服务目录、活动与菜谱详情、社区、小伴服务工作台双主题、后台运营总览与管理表格、个人中心移动端。详细约束见 [前端设计系统](FRONTEND_DESIGN_SYSTEM.md)。

浏览器矩阵是本地 Chromium 内核的只读时间点测试，不能替代 Firefox/Safari、读屏器、真实低端设备或生产网络条件下的人工验收。

### 前后端通信与容器资源回归（2026-08-18）

- 新增 `scripts/audit-frontend-backend-communication.mjs`，所有业务请求强制经前端同源 `/api` 代理，并覆盖登录表单编码、无令牌 401、JWT/角色隔离、用户/活动/服务/报告/菜谱/留言/看板、Excel、历史资源、multipart 权限、Agent 状态和 MCP；
- IDEA 后端 + Vite 开发模式最终 98/98 通过，包含一次真实图片上传、经 `/image` 回读、字节一致校验和精确路径清理；同一生产构建的 Vite `preview` 模式也为 98/98，确认构建预览未丢失代理；
- 首次全 Docker/Nginx 回归为 85/86，唯一失败是数据库历史图片 `/image/20240919/15.png` 返回 404。根因是容器只挂载新上传卷，没有映射仓库历史资源；
- 2026-08-18 当时保留 `silverpilot-uploads` 可写卷，并把仓库历史资源作为只读兜底；后端“可写上传优先、只读资源兜底”的 2 个集成测试继续保留；
- 2026-08-20 当时的工作区曾将数据库最终 URL 统一映射到 `database/seed-assets` 演示 SVG；2026-08-21 已改为根 `image/` 真实栅格图并完成旧卷迁移，此条不再代表当前实现；
- 重建后全 Docker/Nginx 通信矩阵最终 98/98 通过，包含容器上传卷的图片写入、Nginx 回读、字节校验及容器内精确路径清理；清理后当日测试目录文件数为 0。同一容器版本的真实 Edge 路由矩阵 198/198 通过，可见坏图、运行时/控制台错误、HTTP 5xx 均为 0；
- DeepSeek 在 Vite 代理与 Docker/Nginx 代理下均完成真实 Provider 调用、版本化 Prompt、RAG、结构化照护方案、待确认写操作创建后取消、MCP 和运行指标；健康报告 AI 分析也已经 Vite 代理实测通过。

该矩阵对修改类 API 使用无效输入或权限拒绝验证路由/方法/请求体契约，不会为了测试批量改写业务数据；完整的成功写入状态机仍由后端单元/接口测试与 Agent 待确认后取消流程覆盖。

### 专业化界面、多模态入口与图片性能最终复核（2026-08-18）

- 公共端、登录注册、个人中心、小伴服务工作台和运营后台统一为专业中性的社区养老设计；最终 Edge 只读矩阵覆盖 34 个路由在 1440×1000 与 390×844 浅色视口的 68 个场景，68/68 通过，交互控件相交、横向溢出、可见坏图、运行时异常、控制台警告和 HTTP 5xx 均为 0；
- 101 张历史图片均生成 WebP 同名伴随资源，原图合计 165.95 MiB，WebP 合计 6.49 MiB，下降 96.09%；前端使用 WebP 优先、原图回退、懒加载与异步解码，Nginx/后端实测返回 `image/webp` 与 365 天 immutable 缓存；
- 小伴工作台已实装浏览器语音识别、语音朗读、摄像头取景、图片压缩、自然语言清洗、模糊指令补全和写操作二次确认；人物资源为 42.4 KiB WebP，并提供受 reduced-motion 约束的轻量动画；
- 管理员运营页可查看 WorkBuddy 只读调用、IMA 知识来源、Provider/多模态通道、办理统计、最近运行和脱敏操作记录，不显示密钥或个人健康原文；
- 当前最终项目门禁为后端 81/81、前端 29/29、同源通信 97/97，并追加真实数据库 CRUD；Java 编译弃用警告、JDK 25 `jdeprscan`、ESLint、Knip、Vite 生产构建和生产依赖审计全部通过，npm 已知漏洞为 0。

### 认证、启动与异常恢复增量复核（2026-08-18）

- 命令行后端启动会检查 `pom.xml`/Maven Wrapper/`src` 是否比 JAR 新；本次现场验证自动重建后 JAR 时间戳新于新增源码，健康检查为 `UP`，再次启动幂等返回；
- 验证码从 Controller 静态 Map 迁移到独立服务：Redis 使用 TTL 和原子 `GETDEL`，键名只保留随机挑战 ID 的 SHA-256 前缀；Redis 发放失败时才使用有容量上限的单实例降级；
- 新增 10 个验证码/登录定向测试与 2 个 HTTP 错误契约集成测试；覆盖过期、一次性、大小写、容量、Redis 故障降级、成功登录、限流、400 与 405；加入 MyBatis-Plus 配置测试后全量后端最终 58/58；
- `smoke-authentication.ps1` 分别通过后端直连、Vite `/api` 与 Docker/Nginx `/api` 完成真实验证码发放、Redis 一次性消费、BCrypt 凭据校验、JWT 签发与本人受保护读取；脚本不输出密码、验证码或 Token；
- 内置边缘 Nginx 将 `X-Forwarded-For` 覆盖为真实连接地址，防止客户伪造 Header 绕过登录限流；容器后端显式启用 native forwarded-header 解析，并有前端静态契约防回归；
- Axios 区分主动取消、超时、断网、429 和后端 `msg`；主动停止 Agent 不再同时误报断网。登录验证码增加加载/失败/重试状态，路由分块失败与断网均保留可恢复 UI；
- 新增前端异常、代理信任、对比度、Element Plus API 和上传契约后最终 23/23；Vite 开发版与重建后 Docker/Nginx 各自完成 198/198 Edge 页面矩阵；
- 重建后 Docker/Nginx 通信 98/98（含容器上传/回读/字节一致/精确清理），并再次通过真实 DeepSeek Agent smoke。

### 一键启动、重复启动与数据恢复门禁（2026-08-18）

- 项目根目录新增唯一图形入口 `start-project.cmd`；它兼容 Windows PowerShell 5.1 与 PowerShell 7，并统一调用 `scripts/start-project.ps1`，任何门禁失败都会返回非零退出码而不是误报“启动成功”；
- 启动前检查 Docker CLI/Compose、Docker Engine、端口归属、磁盘空间、四项强密钥及持久卷状态；Docker Desktop 未运行时会尝试启动并限时等待，环境文件损坏时可从当前 Windows 用户的 DPAPI 加密副本恢复；已有数据卷但没有匹配凭据时拒绝自动轮换，避免旧数据变成不可访问；
- 启动内容指纹覆盖四个镜像的真实构建输入。源码不变且镜像齐全时实测使用 `--no-build`，不依赖镜像仓库或网络；内容变化、镜像缺失或显式 `-Rebuild` 时才构建，并仅在运行态验证通过后写入新指纹；
- Compose 的 MySQL 健康检查使用配置凭据读取真实 `user` 表，Redis、后端和前端按依赖顺序等待；四个服务均配置自动重启策略与优雅停止时间。首次失败会保留命名卷、强制重建容器并重试一次，同时输出服务状态与最近日志；
- 从完整 `down` 状态冷启动并执行完整审计实测 43.4 秒，四个服务均为 `healthy`，同源通信 97/97；冷启动前后 MySQL 均为 22 张表、18 个用户，Redis `DBSIZE=1`，三个命名卷保持不变；
- 运行中重复启动、无变更离线分支、依赖顺序 `restart` 和根目录 CMD 入口均实测通过。最后一次无变更重复启动为 16.9 秒，并通过前端 `/healthz`、Vue 登录壳、后端 Actuator、带 JWT 的真实用户数据库查询、Agent/知识状态六层运行验证；
- `verify-project.ps1` 现在会先解析全部项目 PowerShell 脚本，启动脚本的语法错误会直接阻断总门禁。上述结果证明当前本机与当前代码快照的可重复启动和数据保持，不构成对断电、磁盘损坏、Docker/Windows 故障或外部模型服务中断的绝对保证。

### 本地混合模式有界自愈复核（2026-08-19）

- 本地启动链增加分层的单次有界重试：Docker Redis 失败时保留命名卷重建容器，后端失败时重新验证 Redis 并强制重建 JAR，前端失败时按 `package-lock.json` 执行 `npm ci`，端到端门禁失败时只重建项目运行层；每层第二次仍失败即返回非零错误并保留诊断，不做无限循环；
- PID/进程处理改为同时校验仓库绝对路径与精确的后端 JAR/启动类或 Vite 命令。故障注入早期曾暴露过宽 Java 匹配并停止一个未监听 `8083` 的 PID 47288；其命令行未在停止前留存，因此不能追溯证明它属于项目。规则随即收紧，随后无效 PID 文件测试只移除 PID 文件并保留 IntelliJ Maven Server PID 118216，最终清理也复验该进程仍在；
- 从后端子目录调用根级启动器完成一次完整本地冷启动，证明不依赖调用者当前目录。最终归属为本机 `mysqld.exe:3306`、本仓库 `node.exe:8081`、本仓库 `java.exe:8083`，`3307/8082` 空闲，Compose 中只有 `silverpilot-redis-1`；Redis 镜像与服务端均为 8.2.9，认证 `PONG`、AOF、回环绑定和后端 `/actuator/health/redis=UP` 全部通过；
- 受控删除 `silverpilot-redis-1` 容器但保留 `silverpilot_silverpilot-redis-data` 后，`docker-dev.ps1 redis` 自动重建同版本容器并恢复 `healthy`，运行中的 Java 随后再次返回 Redis 组件 `UP`；强制后端重建把监听 PID 136916 安全替换为 60756，聚合健康中的数据库、数据库契约和 Redis 组件均为 `UP`；前端停止后以 `-RepairDependencies` 完成 `npm ci` 并恢复页面；
- 最终静态门禁实测 22 个 PowerShell 脚本语法全部通过，后端 37 个测试报告合计 81/81、前端 29/29、JAR 配置泄漏检查、JDK 25 `jdeprscan`、ESLint、Knip、Vite 构建和 npm 生产依赖审计全部通过。最终本地运行门禁再次通过 Docker Redis、精确进程归属、后端聚合/Redis 健康、Vite 页面/代理、真实本机 MySQL 查询和 Agent 状态，并通过同源 97/97 只读通信回归；
- 这里验证的是“当前依赖完整时，可安全恢复的常见本地故障会自愈”。JDK/Node/npm/Docker 或源码损坏、凭据不可恢复、非项目端口占用、编译失败、磁盘/数据损坏、断电和操作系统/硬件故障仍必须失败退出；脚本不会用误杀无关进程、关闭 Redis 门禁、删除数据卷或伪造成功来满足绝对化表述。

## 8. 已知限制

- OWASP Java SCA 仍依赖外部镜像的每日尽力更新；无合格缓存且镜像不可用时会有界失败，Tomcat examples-only 例外必须在 11.0.25 发布或 2026-10-01 到期前移除；
- Docker/npm 数据库只代表扫描时间点，后续新 CVE 需要重新评估；
- Eclipse Temurin 官方 Docker 标签在本次验收时最新为 25.0.3+9 LTS，而本机发行版已是 25.0.4；容器已固定当时官方可用的精确标签并使用 Ubuntu 26.04 LTS，待上游发布 25.0.4 容器后应重新构建复扫；
- Redis 8.2.9 镜像是校验官方源码后构建的 Redis Core，不包含 Redis Stack 的 Search/JSON/向量模块；
- Docker Desktop 4.87.0 自带的 Compose 5.4.0 受安装程序管理，未直接删除；官方 5.5.0 已安装为用户级 CLI 插件并由 `docker compose version` 实际选中；
- 豆包在线视觉、WorkBuddy 企业发布和 ima 账号绑定需要外部账号/权限，当前仅完成代码、模板和本地 MCP/知识包验证；
- 本地 RAG 是适合小型审核语料的词项检索，未做向量召回对照；
- 没有真实养老机构数据、临床验证、生产负载测试、灾备演练、等保或独立渗透测试；
- 上传的 Office/PDF/视频尚无恶意内容扫描；
- 演示账号和公开密码不得用于公网部署。
- Redis 整体故障时的验证码降级仅对发放它的单应用实例可用；多实例生产环境应优先修复 Redis，不应宣称降级仍可跨实例；
- 如果在内置 Nginx 之前增加外部负载均衡，必须另行配置精确的 `real_ip` 信任网段并重做限流测试。
