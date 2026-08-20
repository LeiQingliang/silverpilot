# Redis 8.2.9 Extended 镜像

[返回项目主页](../README.md) · [部署说明](../docs/DEPLOYMENT.md) · [版本策略](../docs/VERSION_POLICY.md)

本目录只定义 SilverPilot 使用的 Redis Core 构建。日常使用请通过项目脚本编排，不要把本 Dockerfile 当作独立、无认证 Redis 的启动教程。

除非另有说明，本文 PowerShell 命令均从项目根目录执行。

文档快照：2026-08-20 已按当前 `runtime-versions.json`、Dockerfile、官方源码哈希和实际运行门禁核对；运行实例已复验为 Redis 8.2.9、认证 `PONG`、AOF 开启且仅绑定 `127.0.0.1:6380`。

本目录只负责构建项目实际使用的 Redis Core。可复现路径固定从 Redis 官方发布源码和官方 SHA-256 构建，不依赖某个 Docker Official Image 标签在某天是否已同步。后续如果调整版本，必须同时更新源码 URL、哈希、镜像标签和版本门禁。

## 构建来源

`Dockerfile` 从 `https://download.redis.io/releases/redis-8.2.9.tar.gz` 下载官方源码，并固定校验 Redis 官方 `redis-hashes` 仓库公布的 SHA-256：

```text
531b314e5557ad76d941f605b3e3162ac61dc141f37c407e1f91fcfe17ea8c30
```

构建启用 TLS，使用 Alpine 3.24.1 修复包，并保留 `redis-server`、`redis-cli`、`redis-benchmark`、`redis-sentinel`、`redis-check-rdb` 与 `redis-check-aof`。入口脚本启动时只修复持久化卷 `/data` 的属主，随后通过 `su-exec` 降权到 UID 999；Redis 不以 root 身份运行。Redis 官方将 8.2 归入 Extended 支持线，而不是名为 LTS 的发布通道。版本选择与重新核验入口见[版本策略](../docs/VERSION_POLICY.md)。

该镜像不包含 Redis Stack 的 Search、JSON 或向量模块。当前后端只使用 Redis 的限流、过期、删除和健康检查命令，因此不应为未使用模块扩大镜像与攻击面。

## 选择运行方式

| 场景 | 命令 | 运行结果 |
| --- | --- | --- |
| IDEA + VSCode 本地开发 | `.\scripts\docker-dev.ps1 redis` | 只保留本项目 Redis 容器，准备后端 `host` 配置 |
| 全 Docker 演示/联调 | `.\start-docker.cmd` | Compose 管理 MySQL、Redis、后端和前端 |
| 查看状态与日志 | `.\scripts\docker-dev.ps1 status` / `logs` | 只读诊断，不删除 AOF 或命名卷 |
| 验证本地 Redis-only 模式 | `.\scripts\verify-docker-redis.ps1 -RequireOnlyRedis` | 同时拒绝意外运行的其他 Compose 服务 |

两种运行模式不能各自创建不同 Redis 数据源；它们共用 `silverpilot-redis-data`，切换模式时由脚本精确管理容器并保留数据。

## 项目运行方式

不要直接启动一个无密码的临时 Redis。项目根脚本会生成被 Git 忽略的强随机密码，启用 AOF，并只把容器 `6379` 发布到宿主机回环地址 `127.0.0.1:6380`：

```powershell
.\scripts\docker-dev.ps1 redis
```

这是本地混合模式唯一允许的 Compose 服务；全 Docker 模式则由 `start-project.ps1 -Mode Docker` 同时编排 MySQL、后端和前端。两种模式共用 `silverpilot-redis-data` 命名卷，普通停止/切换不会删除 AOF 数据。

## 运行门禁

本地混合模式可单独复验：

```powershell
.\scripts\verify-docker-redis.ps1 -RequireOnlyRedis
```

门禁同时核对容器健康、镜像标签、服务端精确版本、认证 `PONG`、AOF 开启、唯一回环端口绑定，以及本地模式下没有其他 Compose 服务。它不会输出 Redis 密码。需要清空命名卷时只能按根 README 的显式 `reset -Force` 流程操作；普通排障不要删除 `silverpilot-redis-data`。

全 Docker 栈已经运行时，不要添加 `-RequireOnlyRedis`；改为执行：

```powershell
.\scripts\verify-docker-redis.ps1 -EnvironmentFile .\.env.docker
```

## 故障排查

| 现象 | 处理顺序 |
| --- | --- |
| `NOAUTH` / `WRONGPASS` | 不要打印密码；确认 `.env.docker` 与 DPAPI 备份、现有命名卷属于同一套环境 |
| `6380` 被占用 | 运行 `docker-dev.ps1 doctor` 定位进程；必要时修改被忽略的 `SILVERPILOT_REDIS_PORT` |
| 本地模式出现 MySQL/后端/前端容器 | 重新执行 `docker-dev.ps1 redis`，再加 `-RequireOnlyRedis` 复验 |
| AOF 未开启或健康检查失败 | 查看容器日志和 Compose 展开配置；不要通过关闭 Redis 健康门禁绕过 |
| 源码下载或哈希失败 | 检查官方发布地址与 `runtime-versions.json`，不得删除 SHA-256 校验继续构建 |
| 需要清空数据 | 先确认 MySQL、Redis 和上传卷都可永久删除，再按部署文档执行 `reset -Force` |

## 升级清单

升级 Redis 时必须同时更新 `runtime-versions.json`、源码 URL、官方 SHA-256、Compose 镜像标签和 README，并验证构建、精确版本、认证、AOF、重启后数据与后端健康。版本支持状态可能变化，维护升级时应重新运行联网版本策略审计；普通启动只验证仓库内已评审基线，不依赖上游网站实时可用。
