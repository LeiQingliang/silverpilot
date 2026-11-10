# Railway 后端服务

本目录的 `backend.Dockerfile` 从**仓库根目录**构建，保留原后端的 Temurin 25.0.3+9、Maven wrapper、依赖和有界构建重试。它把审核知识目录及仓库 `image/file/video` 打包进镜像，Java 进程不能修改这些素材。新增上传必须写入独立的 `/app/uploads` 持久卷。

这是部署适配文件；文件存在、静态检查通过或镜像构建成功都不代表公网部署已完成。后端、MySQL、Redis 只走项目私网；只为前端服务创建公开 HTTPS 域名。

## 服务设置

| 设置 | 值 |
| --- | --- |
| Source root / 构建上下文 | 仓库根目录 |
| Dockerfile | `deploy/railway/backend.Dockerfile` |
| Start command | 留空，保留镜像入口 |
| Volume mount path | `/app/uploads` |
| Public domain / TCP proxy | 不创建 |
| Healthcheck path | `/actuator/health` |
| `PORT` | `8083`，显式设置供 Railway 健康检查使用 |
| `CECSMS_SERVER_PORT` | `8083` |
| `RAILWAY_RUN_UID` | `0`，仅用于入口处理挂载目录权限 |
| `SPRING_PROFILES_ACTIVE` | `docker` |

入口只调整 `/app/uploads` 与 `/app/logs` 目录本身的归属，随后用 `setpriv` 切换到 UID/GID `10001`、清除能力并执行 Java。应用不会以 root 常驻。已有卷中的文件应已属于 UID `10001`；从其他系统恢复的文件需在恢复流程中单独核验归属，不做无范围递归改写。卷须挂到 `/app/uploads`，不能挂到 `/app`，否则会遮住 JAR 和只读素材。

Java 绑定 `[::]:8083` 并启用 JVM 双栈；部署验收须同时确认 IPv4 健康检查和前端到后端的私网解析/访问。`CECSMS_FORWARD_HEADERS_STRATEGY` 必须与前端的可信转发头策略配合，只允许前端代理提供真实访客地址及 HTTPS 协议，不能因为启用私网就信任客户端传来的任意头部。

## 运行变量

在 Railway 的服务变量中设置；不要写入 Dockerfile、Git、构建参数或公开日志。内部域名使用实际服务名的 Railway 私网引用。

| 变量 | 要求 |
| --- | --- |
| `CECSMS_DB_URL` | 指向私网 MySQL 的 JDBC URL，数据库名及字符集参数与原 Compose 一致 |
| `CECSMS_DB_USERNAME` / `CECSMS_DB_PASSWORD` | 独立应用账号，禁止使用 root |
| `CECSMS_REDIS_HOST` / `CECSMS_REDIS_PORT` | 实际 Redis 私网主机和端口 |
| `CECSMS_REDIS_PASSWORD` | 独立随机密码 |
| `CECSMS_JWT_SECRET` | 至少 32 字符的独立随机密钥 |
| `CECSMS_MCP_API_KEY` | 如启用 MCP，单独随机密钥；前端公网网关默认应阻断 MCP |
| `CECSMS_CORS_ALLOWED_ORIGINS` | 最终前端 HTTPS origin，精确匹配，不用通配符 |
| `DEEPSEEK_API_KEY` / `DEEPSEEK_MODEL` | 需要真实文本 Agent 时配置有效账号及模型 |
| `DOUBAO_API_KEY` / `DOUBAO_MODEL` | 需要图文通道时成对配置 |
| `LONGCAT_API_KEY` / `LONGCAT_MODEL` | 需要 LongCat 时配置，URL 沿用应用默认或显式指定官方端点 |

镜像已设置 Redis 与其健康项启用、Mock 禁用、知识/素材/上传路径。不要把密钥放入 `VITE_*`；不要为消除健康检查失败而禁用数据库完整性或 Redis 检查。数据库首次初始化、种子账号处理和备份是独立服务的部署步骤，后端镜像不内嵌任何数据库凭据。

## 验证

从仓库根目录执行构建（需要 Docker）：

```sh
docker build -f deploy/railway/backend.Dockerfile -t silverpilot-railway-backend .
```

构建前后检查：

1. 对照原后端 Dockerfile，确认两个 Temurin 摘要一致；运行后端原有测试，构建镜像并确认所有依赖可下载。
2. 确认 Docker 使用相邻的 `backend.Dockerfile.dockerignore`。其默认拒绝整个上下文，再允许构建必需代码、知识及媒体；本机 `.env`、外置配置、构建结果、日志、密钥文件和备份不得进入上下文或镜像。
3. 挂接一个新卷，检查进程用户为 UID `10001`、`/app/uploads` 可写、JAR/知识/仓库素材不可写。入口不能打印环境变量值。
4. 在真实私网 MySQL/Redis 就绪且变量齐备后启动，确认 `/actuator/health` 返回 `UP`；从前端访问 `http://<实际后端私网域名>:8083/actuator/health`。
5. 经前端验收登录、验证码、媒体、低权限业务和授权边界；用管理员上传一份无敏感内容的测试素材，重启后检查仍可访问，再精确清理。
6. 以真实供应商配置验证 Agent，确认未经用户确认不会执行写操作。单独检查公网端口没有暴露数据库、Redis 或后端。

当前环境若没有 Docker 或 Railway 连接，就只能验证文件和脚本；应明确保留镜像构建、卷权限、双栈和真实服务验收为待办，不能宣称通过。Railway 部署健康检查仅覆盖发布阶段，长期访问仍需要单独的持续监控和备份验证。

官方说明：[私网通信](https://docs.railway.com/networking/private-networking)、[卷与权限](https://docs.railway.com/volumes)、[健康检查端口](https://docs.railway.com/deployments/healthchecks)。
