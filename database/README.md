# 数据库说明

[返回项目主页](../README.md) · [部署与数据卷](../docs/DEPLOYMENT.md) · [验证报告](../docs/VALIDATION_REPORT.md)

本页面向需要初始化、修改或验证 SilverPilot 数据层的开发者。先确认操作对象是一次性临时 schema、当前演示卷还是重要数据库，再执行任何 SQL。

除非另有说明，本文 PowerShell 命令均从项目根目录执行。

文档快照：2026-08-21 已按当前 `a_old.sql`、Dockerfile、数据库健康指示器和校验脚本重新核对；临时 schema 验证为 21 张表、18 个门禁外键、6 个关键索引、48 个唯一种子图片 URL，结束后无临时 schema 残留。

## 先选正确操作

| 目标 | 推荐入口 | 是否触碰当前演示数据 |
| --- | --- | --- |
| 首次启动全 Docker | `.\start-docker.cmd` | 仅在 MySQL 命名卷首次创建时导入种子 |
| 验证 SQL 可完整导入 | `.\scripts\verify-database-seed.ps1` | 否；使用随机临时 schema 并自动删除 |
| 验证真实 Web CRUD | `.\scripts\verify-database-crud.ps1` | 会短暂写入当前演示库，但精确回滚测试记录 |
| 查看运行健康 | `.\scripts\verify-running-stack.ps1` | 只读，除非显式增加 `-FullAudit` |
| 清空并重建本项目数据卷 | `.\scripts\docker-dev.ps1 reset -Force` | **是，永久删除 MySQL/Redis/上传卷** |

普通排障不得从 `reset -Force` 开始。先查看 `docker-dev.ps1 doctor`、`logs` 和 Actuator 的 `databaseContract` 详情。

## 用途与边界

`a_old.sql` 是本地演示数据库初始化脚本，不是生产数据备份。数据用于页面、权限、Agent 工具和测试演示，不代表真实老人、养老机构、订单或经营结果。所有联系号码和身份标识均为明确无效的合成值，用于保持字段与引用契约，禁止替换为真人数据后提交。

当前种子包含 21 张基础表、21 个外键、6 个启动门禁关键复合索引和 18 个演示账户。其中 18 个核心业务外键进入 `databaseContract` 与种子验证门禁，另有 3 个菜谱/助餐关系外键；`agent_action`、`agent_run` 已包含在这 21 张表中。表数、18/18 核心外键、6/6 关键索引和数据不变式由临时 schema 验证；外键总数同时与当前 SQL 声明核对。

## 初始化

Docker Compose 在 MySQL 命名卷第一次创建时自动导入：

```text
database/a_old.sql → a_old
```

已有命名卷不会因修改 `a_old.sql` 或重启容器而重新导入，这是 MySQL 初始化目录的正常语义。需要验证新 SQL 时优先使用临时 schema 脚本；需要迁移已有数据时编写显式迁移，不要靠删除卷模拟升级。

手工导入前应先创建 `a_old`，字符集使用 `utf8mb4`、排序规则使用 `utf8mb4_0900_ai_ci`，再明确选择该数据库执行脚本。SQL 含 `DROP TABLE IF EXISTS`，但不含 `CREATE DATABASE` 或 `USE`；不要对包含重要数据的 schema 直接执行。

全 Docker 栈启动后，可在随机临时 schema 中做一次可自动清理的完整导入验证：

```powershell
.\scripts\verify-database-seed.ps1
```

脚本校验 21/21 表、18/18 门禁外键、6/6 关键索引、报名状态/计数、服务层级、全部种子图片路径与原生 `CHECK TABLE`，并在 `finally` 中删除临时 schema 和容器内临时 SQL。它不会重建正在使用的 `a_old`。

项目根 `image/` 保存 101 张可解码原图及其 101 张同名 WebP 优化图。当前种子使用其中 48 张真实栅格图；`validate-seed-assets.ps1` 在每次 Local/Docker 启动前检查 URL 边界、文件存在性、文件签名和 WebP 伴随文件。已有卷中的旧演示 SVG 路径由后端幂等迁移为这些真实图片，无法解析的持久化图片会阻止后端报告就绪。

媒体 URL 使用 Web 路径（例如 `/image/...`），不是 Windows 绝对路径。运行态查找顺序和三类资源根目录见[后端 README](../SourceCode/cecsmsServe-springboot/README.md#静态资源与上传文件)。

## 账户

脚本中的 18 个密码字段均为 BCrypt 哈希。根 README 中公开的两个账号仅供本地演示，生产部署必须删除或改密。应用启动时的 `LegacyPasswordMigration` 只用于将旧本地卷的遗留明文升级为 BCrypt，不会把 README 或环境变量中的明文重新写进种子脚本。

## Agent 表

- `agent_action`：写操作确认状态机、用户归属、TTL 与脱敏审计；
- `agent_run`：provider、模态、prompt version、工具数、延迟、Token、状态和错误类别。

后端的 `src/main/resources/schema.sql` 只负责幂等创建两张 Agent 表，`AgentSchemaMigration` 通过 JDBC metadata 升级旧 `agent_run` 列，避免依赖特定版本的 `ADD COLUMN IF NOT EXISTS` 方言。其余 19 张业务表仍由本目录的种子/正式迁移负责，应用启动不会静默重建它们。

`database/Dockerfile` 基于官方 MySQL 9.7.2 LTS，移除服务端运行和初始化均不需要的 `mysql-shell`，以 Go 1.27.0 重编译入口降权所需的 `gosu`，再复制最终文件系统以避免已删除组件残留在镜像层中。`mysqld`、`mysql`、`mysqladmin`、`mysqldump` 与官方 entrypoint 契约均保留。

从旧 LTS 升级时必须先备份并做隔离恢复验证。2026-08-18 执行的 8.4.11 → 9.7.2 升级保留了命名卷，升级前备份已在临时 schema 成功恢复 22 张表与 18 个演示用户；其中现有卷额外保留了完整性修复产生的隔离表，而全新 `a_old.sql` 的可移植契约仍是上述 21 张表。本地 `backups/` 被 Git 忽略。不要把数据库备份、密码或真实数据提交到仓库；时间点证据见[验证报告](../docs/VALIDATION_REPORT.md)。

需要验证真实 Web → Spring Boot → MyBatis-Plus → MySQL 写链路时，在已启动的全 Docker 栈执行：

```powershell
.\scripts\verify-database-crud.ps1
```

该脚本只创建一条带随机 `QA-DB-*` 标识的活动，逐步核对创建、读取和更新，最后按 ID 与标识双重限定删除；它不是通用数据清理工具。

## 生产迁移建议

真实部署应将初始化与种子数据拆开，采用 Flyway/Liquibase 版本迁移、最小权限账户、加密备份、恢复演练、数据保留/删除策略和个人信息分级。健康数据需要单独的访问审计与合规评估。

## 故障定位

| 现象 | 先检查 | 不要这样处理 |
| --- | --- | --- |
| 修改 SQL 后页面仍是旧数据 | MySQL 命名卷是否已经初始化 | 直接删除卷或对重要 schema 重跑含 `DROP TABLE` 的种子 |
| 容器健康但接口报字段/外键错误 | `databaseContract`、当前 schema 与 Mapper/实体映射 | 只看容器 `Running` 状态就认定兼容 |
| 页面图片 404 | 运行 `validate-seed-assets.ps1`，核对数据库 URL 是否落在根 `image/` 且 Compose 只读挂载存在 | 把本机绝对路径写进 SQL |
| 临时验证失败 | 脚本输出的表、外键、索引或媒体具体门禁 | 手工删除名称不确定的 schema |
| 需要保留现有数据升级 | 先备份、隔离恢复、再执行版本化迁移 | 用全新种子导入结果代替旧卷升级证明 |

## 事实来源

- 表结构和合成数据：[`a_old.sql`](a_old.sql)
- MySQL 构建：[`Dockerfile`](Dockerfile)
- 容器拓扑和卷：[`../compose.yaml`](../compose.yaml)
- 启动健康契约：后端 `DatabaseContractHealthIndicator`
- 可复现校验：[`verify-database-seed.ps1`](../scripts/verify-database-seed.ps1)、[`verify-database-crud.ps1`](../scripts/verify-database-crud.ps1)
