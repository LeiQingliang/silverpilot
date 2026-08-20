# 安全设计、审计范围与剩余风险

本文件记录已实现控制和仍需外部基础设施完成的事项。它不是“零漏洞”声明，也不能替代独立渗透测试、合规评估或生产安全运营。

## 1. 威胁与控制矩阵

| 风险 | 当前控制 | 验证方式 | 剩余风险 |
| --- | --- | --- | --- |
| 弱密码存储 | 种子库使用 BCrypt cost 12；启动迁移旧明文并幂等更新 | 单元测试 + 容器库检查 60 字符 BCrypt | 演示密码公开，生产必须删除/改密并增加密码策略 |
| JWT 伪造/过期 | HMAC256，启动拒绝短于 32 字符的 secret，issuer/expiry 校验 | `JWTInterceptor` 与接口冒烟 | 无刷新令牌、撤销列表和密钥轮换服务 |
| 越权/IDOR | 每次请求按 JWT userId 查库；角色与资源所有权在业务入口校验 | Controller/Service 测试与 Agent ownership 用例 | 自定义授权代码需要持续做端点级回归 |
| 暴力登录/验证码滥用 | IP + 规范化账号窗口限流；Redis TTL + 原子 GETDEL 一次性验证；发放故障时有界本地降级 | 单元测试 + 真实 Redis/凭据/JWT smoke | 多实例且 Redis 故障时已发放的本地验证码不能跨实例验证 |
| CSRF | 不使用 Cookie 会话；JWT 由 JS 以 Header 发送；CORS 不允许凭证 | 配置审计 | 一旦改为 Cookie 认证必须引入 CSRF Token/SameSite |
| CORS | 明确 origin allowlist、有限方法/Headers、`allowCredentials=false` | 配置与响应头检查 | 生产域名必须显式配置，禁止通配符 |
| SQL 注入/误操作 | MyBatis 参数绑定；动态条件使用 MyBatis-Plus Lambda 列引用；分页上限 100；全表更新/删除由 BlockAttack 拦截 | Mapper 合约、拦截器配置与全量集成测试 | 新增 XML 时仍需禁止 `${}` 接收用户输入；批量维护必须提供明确条件 |
| XSS/点击劫持 | Vue 默认转义；Nginx CSP、`nosniff`、`DENY`、Referrer/Permissions Policy | Docker HTTP Header 验收 | CSP 仍允许 inline style 以兼容 Element Plus，应逐步收紧 |
| 文件上传 | 仅 staff、类别 allowlist、大小限制、UUID 文件名、规范化路径；图片解码验证 | `FileUploadUtils` 审计 | PDF/Office/视频无恶意内容扫描；生产应使用隔离对象存储 |
| 路径穿越 | 不使用原始文件名保存；目标 normalize 后必须位于 base path | 代码审计 | 静态资源目前由应用提供，生产需独立下载域与内容策略 |
| 密钥泄露 | 仅环境变量/被忽略本地文件；前端无模型 Key；日志不保存 Prompt/附件 | Git staged secret scan | 聊天中曾公开过的 Key 应立即在供应商侧轮换 |
| Prompt 注入/数据外带 | 系统协议、敏感模式拒绝、PII 脱敏、工具 allowlist、JWT 所有权 | 安全单测 + injection/secret 评测 | LLM 防注入不是绝对保证，敏感业务仍需人工确认 |
| 未授权 Agent 写入 | 写工具只产生 `PENDING`；随机令牌、所有权、TTL、原子抢占、幂等 | `AgentActionServiceTests` + 在线取消/确认冒烟 | 生产需增加高风险动作分级审批和通知 |
| MCP 越权 | 独立高熵 API Key；只读非个人工具；不复用模型/JWT secret | MCP 测试与 smoke | 云端需 HTTPS、网关限流、密钥轮换与来源限制 |
| 依赖漏洞 | npm audit、Docker Scout、OWASP 镜像缓存 + 强制离线 Dependency-Check、固定运行镜像版本 | Java SCA 报告与 `docs/VALIDATION_REPORT.md` | 扫描结果会随时间变化，必须在 CI/发布前刷新并复扫 |

## 2. 身份与授权

- 公开端点仅包括注册、登录、验证码、基础健康检查和独立鉴权的 MCP；
- `JWTInterceptor` 接受标准 `Authorization: Bearer`，兼容旧前端 `token` Header；
- token subject 仅作为用户 ID，每次请求重新从数据库读取用户与角色，避免信任客户端 `roleId`；
- 角色：管理员 `1`、社区工作者 `2`、医生 `3`、普通用户 `4`；
- 用户资料、健康报告、服务/助餐订单和活动报名按角色或所有权检查；
- Agent 工具不接受可改变身份的 `userId` 参数。

当前项目使用轻量自定义 JWT Interceptor，而不是完整 Spring Security FilterChain。这减少了迁移对原业务的破坏，但也意味着新增端点必须显式设计角色与所有权规则；生产扩展时建议迁移到集中式声明授权并补充契约测试。

## 3. 密码与种子数据

`database/a_old.sql` 仅包含 BCrypt 哈希。`LegacyPasswordMigration` 用于升级已有本地卷中的旧明文，更新带旧值条件以避免并发覆盖，并可通过 `CECSMS_MIGRATE_LEGACY_PASSWORDS=false` 关闭。

README 公开的 `admin/123456` 与 `linge/123456` 是本地演示凭据，因此绝不能直接用于公网或真实数据环境。生产准备清单必须包括删除种子用户、强密码/锁定策略、多因素认证评估和管理员单独身份域。

## 4. API 与浏览器边界

后端所有响应设置：

```text
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: camera=(), geolocation=(), microphone=(self)
Content-Security-Policy: default-src 'none'; ...
```

HTTPS 请求额外设置 HSTS；登录、Agent 和 MCP 响应设置 `Cache-Control: no-store`。Nginx 前端有适合 SPA 的 CSP，并仅允许同源脚本、连接和资源。

开发环境 CORS 默认只允许 `localhost/127.0.0.1:8081`。Docker 全栈根据配置只允许 `8082`。不要在生产配置 `*`。

组合拓扑中的 Nginx 是边缘入口，会用真实 TCP 连接地址覆盖客户传入的 `X-Forwarded-For`；容器后端才开启 native forwarded-header 解析。如果在它前面增加云负载均衡，需要以明确网段配置 Nginx `real_ip`，不得相信任意客户提供的转发 Header。

## 5. 数据、日志与 AI 隐私

- `agent_run` 不保存原始问题、模型回答、图片或思维链；
- `agent_action` 在终态清除原始工具参数；
- 日志包含 request ID、状态和必要错误，不应打印 Authorization、Provider response body 或完整个人健康数据；
- 外部模型请求前对身份证、手机号、邮箱进行脱敏，并阻止疑似密钥/私钥文本；
- 健康报告 AI 分析只有报告本人、医生或管理员可以调用。

若用于真实养老服务，还需要完成个人信息影响评估、最小必要字段、授权/撤回机制、数据保留与删除、跨境传输评估、供应商数据处理协议和审计访问流程。

## 6. 容器安全

- MySQL、Redis、后端和前端端口只绑定 `127.0.0.1`；
- Redis 启用密码与 AOF；数据库和上传使用命名卷；
- 后端/前端运行阶段使用非 root 用户；
- 后端在 Ubuntu 26.04 LTS 根文件系统中移除未使用且存在已修复 CVE 的 Pebble helper，再压平最终运行层，避免被删除内容残留在历史层；
- Nginx 使用固定的 `1.30.4-alpine3.24-slim` stable 版本，减少运行包；
- MySQL 基于官方 `9.7.2` LTS，移除未使用的 `mysql-shell`，并用 Go 1.27.0 重编译仅用于降权的 `gosu`，最终镜像压平并保留官方 entrypoint 契约；
- Redis 8.2.9 Extended 使用官方源码 SHA-256 校验构建，最终层固定 Alpine 3.24.1 并升级安全修复；入口脚本只修正 `/data` 权限，随后以 UID 999 启动 Redis；
- Compose 不包含真实密钥，变量由被忽略的 `.env.docker` 注入；
- 健康检查和 `depends_on.condition=service_healthy` 约束启动顺序。

本地 Compose 网络不应直接当作互联网生产边界。上线前仍需只开放 HTTPS、禁止数据库/Redis 公网端口、使用托管密钥、镜像签名/SBOM、只读根文件系统评估与运行时监控。

## 7. 扫描与报告

可复核的扫描结果记录在 [VALIDATION_REPORT.md](VALIDATION_REPORT.md)。扫描时间点之后新披露的 CVE 不会自动反映在旧报告中；每次发布前至少重跑：

```powershell
cd SourceCode\cecsmsui-vue
npm audit --json

docker scout cves --only-severity critical,high silverpilot-backend:latest
docker scout cves --only-severity critical,high silverpilot-frontend:latest
docker scout cves --only-severity critical,high silverpilot-mysql:9.7.2
docker scout cves --only-severity critical,high silverpilot-redis:8.2.9
```

Java SCA 使用 `scripts/invoke-dependency-check.ps1` 分离有界镜像更新与强制离线扫描；缓存必须通过时间、版本和 SHA-256 验证。更新超时且没有 168 小时内的已验证缓存时必须失败，不能写成通过。完整设计与维护规则见 [Java 依赖漏洞扫描](JAVA_DEPENDENCY_CHECK.md)。

## 8. 漏洞报告

不要在公开 Issue 中粘贴真实凭据、用户数据或可直接利用的生产细节。请按根目录 [`SECURITY.md`](../SECURITY.md) 使用 GitHub Private Vulnerability Reporting；支持版本、报告内容和尽力而为的响应目标均以该策略为准。
