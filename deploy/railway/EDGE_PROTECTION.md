# 公网入口防护

这些规则用于单副本 Nginx，限制到达应用的工作量。它们不能保证任意规模攻击下的可用性，也不能代替后端按用户、按费用预算的限制。Railway 的网络防护和应用层限制应同时保留。

| 范围 | 持续请求速率 | 同时处理请求上限 |
| --- | --- | --- |
| 全站 / 每 IP | 40 次/秒 / 10 次/秒 | 128 / 24 |
| 所有 API / 每 IP | 20 次/秒 / 5 次/秒 | 32 / 8 |
| 登录 / 每 IP | 登录与注册合计全局 2 次/秒；登录每 IP 6 次/分钟 | 继承 API 上限 |
| 验证码 / 每 IP | 20 次/分钟 | 继承 API 上限 |
| 注册 / 每 IP | 2 次/分钟 | 继承 API 上限 |
| AI / 每 IP | 全局 30 次/分钟；每 IP 6 次/分钟 | 4 / 2 |
| 管理接口 / 每 IP | 5 次/秒 / 60 次/分钟 | 继承 API 上限 |
| 上传 / 每 IP | 6 次/分钟 | 继承 API 上限 |

各组允许短时突发，以模板中的 `burst` 为准。为同一出口 IP 下连续打开桌面和手机页面的静态资源冷启动留出余量，全站基础桶的 `burst` 为 **160**，每 IP 基础桶为 **80**；持续速率仍为 **40 次/秒、每 IP 10 次/秒**。API、AI 等独立限制保持不变，所有适用限制叠加。

AI 的共享限额覆盖 `/api/chat`、`/api/chat/care-plan` 和 `/api/report/analyze/{id}`；改变验证码 key、查询参数、大小写或尾斜线不能换桶。路径先按 Nginx 规则归一，再在代理改写前固定分类；matrix 参数、剩余百分号、反斜线及编码后的路径分隔歧义会被拒绝。

AI 请求体最多 64 KiB，其他 API 256 KiB，上传 10 MiB。当前公开部署仅配置文字 AI；若以后启用图片识别，需要同时评估预算后再调大入口限制。请求头和请求体读取空闲超时 10 秒，响应发送空闲超时 15 秒，普通上游响应空闲超时 30 秒、AI 95 秒。空闲超时不是整个请求的绝对时限；慢速持续发送仍需连接上限和平台防护共同约束。

限流/连接拒绝返回 `429` 和 `Retry-After`；过大请求返回 `413`；不允许的方法返回 `405` 和 `Allow`。`/healthz` 是不访问后端的快速存活探测；`/readyz` 和 `/api/actuator/health` 受 API 限制并检查后端。其他 Actuator 和 MCP 入口不公开。

公开 Host 默认取 `SILVERPILOT_PUBLIC_HOST`、`RAILWAY_PUBLIC_DOMAIN`，最后回退到 `silverpilot.up.railway.app`。如更换公开域名，先配置该值。仅额外允许本机 Host，以及只能访问健康路径的 `healthcheck.railway.app`。后端、数据库、Redis 均不得增加公网入口或 TCP 代理。

## 验证

- `python3 deploy/railway/edge-smoke.py --nginx /absolute/path/to/nginx`：真实 Nginx、本机临时 HTTP stub，无数据库或模型调用。
- `python3 deploy/railway/edge-smoke.py --docker-image sha256:...`：Linux CI 使用已构建前端镜像的同一 Nginx；临时容器只连接本机 stub。
- `smoke.sh` 自动运行上述镜像测试，再对隔离 Compose 做少量协议检查。`--base-url` 拒绝公网、域名、HTTPS、用户信息和非 loopback 目标；不用于生产压测。

入口只信任 Railway HTTP edge 已覆盖的 `X-Real-IP`，转发时重新生成 `X-Forwarded-For`，固定 `https`。本机 stub 只能验证转发规则，不能证明 Railway 当前的头覆盖行为。每次改变入口拓扑后，由部署者做以下两次低流量公开验证：

1. 对 `/api/mcp/edge-<随机标记>-baseline` 发一个普通 GET。
2. 对 `/api/mcp/edge-<同标记>-spoof?redaction_probe=synthetic` 发一个 GET，附 `X-Real-IP: 203.0.113.42` 和 `X-Forwarded-For: 198.51.100.52`。

两次应均为 404；前端安全日志的来源 IP 应相同，且不能变为上述伪造地址。日志应只含路径，不能含 `redaction_probe`。若不满足，不能把每 IP 限流当作可信保护。2026-11-10 首次公开部署已通过该验证；新配置上线后的复验由部署者负责。日志含限流和连接状态，但不记录查询参数或身份令牌。

## Railway 平台能力与限制

官方说明 Metal 缓解 L4 及以下攻击；公共域名平台阈值约为 11,000 请求/秒、10,000 并发，应用可能在达到这些阈值前就过载。[平台限制](https://docs.railway.com/networking/public-networking/specs-and-limits)

Under Attack Mode 支持 Railway 自动域名，可按服务临时启用。它要求新访客完成浏览器检查；同域页面通过后，其 API/验证码可继续访问，但没有 clearance 的脚本、API 请求会被拒绝。官方建议用于正在发生的攻击，不能把它当作对所有客户端透明的常开开关。[WAF](https://docs.railway.com/networking/waf)、[CLI](https://docs.railway.com/cli/waf)

当前官方页面没有明确承诺 Trial 适用性、额外费用、Googlebot 或外部健康探针豁免，因此本次不自动开启或购买。常开可能影响抓取和外部监测，这是基于其非浏览器拒绝行为的推断。Railway 插件当前没有 WAF 配置工具；需要时可由部署者核对控制台或 CLI 状态，并优先采用限时模式。

这些限制的计数在同一 Nginx 实例的 workers 间共享；增加前端副本会增加可用总额度。共享出口网络的用户也共享 IP 限额。调参或扩容应结合安全访问日志和正常用户量进行，不能靠关闭限制解决异常流量。
