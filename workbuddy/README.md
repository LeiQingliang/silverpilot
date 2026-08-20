# WorkBuddy 集成

[返回项目主页](../README.md) · [完整接入指南](../docs/WORKBUDDY_IMA_QUICKSTART.md) · [知识库治理](../knowledge-base/README.md)

本页面说明仓库提供的本地 MCP 契约与模板边界。首次实际配置 WorkBuddy/ima 时，请使用完整接入指南；本 README 作为开发和排障速查。

除非另有说明，本文 PowerShell 命令均从项目根目录执行。

本目录提供可导入/参考的 Agent 与连接器模板，不包含账号、密钥或“已发布到企业空间”的虚假状态。

文档快照：2026-08-20 已按当前 `McpController`、模板 JSON、认证头和本地无状态端点契约核对；本地 smoke 已复验 `initialize` 与 `tools/list` 成功，精确返回 6 个只读工具。这只说明仓库和本地服务的实现状态，不等同于 WorkBuddy 云端已绑定或已发布。

## 先看能力边界

| 能力 | 当前状态 | 边界 |
| --- | --- | --- |
| 本地 Streamable HTTP MCP | 已实现并有 smoke | 无状态、同机回环、独立 Key |
| 公开业务目录与知识查询 | 6 个只读工具 | 无个人数据、确认令牌和写操作 |
| WorkBuddy 模板 | 仓库提供参考 JSON | 不保证匹配未来客户端字段，不代表已导入 |
| ima 知识包 | 8 篇 `approved` 可导入 | 需要在客户端人工创建/同步并记录版本 |
| 企业/云端接入 | 仅给出安全前置方案 | 仓库没有公网 HTTPS 网关或已发布企业连接器 |

## 文件

- `agent-manifest.json`：SilverPilot Agent 的角色与安全提示模板；
- `connector-template.json`：本地 MCP Streamable HTTP 连接器模板；
- `../.codebuddy/skills/silverpilot-operator/SKILL.md`：用于审计、知识治理、扩展和交付门禁的项目 Skill。

两个 JSON 是最小、可审计的参考模板，不含账号、工作区 ID 或凭据；WorkBuddy 字段若升级，应按当前客户端 schema 映射，不能因文件能解析就宣称已成功发布。

## 本地连接

1. 按[项目根 README](../README.md)启动本地混合或全 Docker 后端，并配置独立 `CECSMS_MCP_API_KEY`；
2. 在 WorkBuddy/兼容 MCP 客户端创建 Streamable HTTP 连接；
3. URL 使用 `http://127.0.0.1:8083/mcp`；
4. Header 使用 `X-CECSMS-MCP-Key: <local secret>`，服务也兼容 `Authorization: Bearer <secret>`；
5. 初始化后应只看到 6 个显式 `readOnlyHint=true`、`destructiveHint=false` 的工具。

当前端点是无状态 JSON-RPC 2.0 Streamable HTTP 实现，协商协议版本 `2025-03-26`，业务请求使用 `POST /mcp`；它不建立服务器推送会话。6 个公开工具为：

| 工具 | 返回范围 |
| --- | --- |
| `cecsms_get_platform_overview` | 产品、Provider 与知识库状态 |
| `cecsms_list_activities` | 公开可报名活动及 ID |
| `cecsms_list_services` | 公开可预约服务及 ID |
| `cecsms_list_recipes` | 公开菜谱目录/关键词搜索 |
| `cecsms_search_knowledge` | 已审核知识与来源 |
| `cecsms_get_agent_metrics` | 1–30 天匿名聚合 Agent 指标 |

```powershell
.\scripts\mcp-smoke.ps1 -ApiKey '<local-mcp-key>'
```

该 smoke 会验证 `initialize`、协议版本、服务标识、`tools/list`、精确工具数和只读/非破坏 annotations；它不会调用写操作。连接器只提供公开服务/活动/菜谱目录、审核知识和匿名 Agent 指标。个人健康数据、个人订单、确认令牌与写操作不在 MCP 工具列表中。

不要把 `.env.docker` 整体复制到 WorkBuddy、文档或聊天中。安全读取/复制单个 MCP Key、客户端字段映射和首次联合验收步骤见[完整接入指南](../docs/WORKBUDDY_IMA_QUICKSTART.md#步骤-2验证-mcp并安全复制密钥)。

本地最小验收顺序：

1. `.\start-docker.cmd` 启动并通过四服务健康门禁；
2. 使用本机 MCP Key 运行 `mcp-smoke.ps1`；
3. 在客户端执行 `initialize` 与 `tools/list`，确认服务标识和 6 个工具；
4. 调用一个公开目录工具与一个知识搜索工具，确认无个人字段且知识返回来源；
5. 尝试请求写操作，确认工具列表本身不提供该能力。

## 云端边界

`127.0.0.1` 只适合 WorkBuddy 与后端在同一台电脑时使用。托管/企业 WorkBuddy 无法访问用户本机回环地址。云端绑定前必须：

- 将后端部署到受保护的 HTTPS 域名；
- 经 API 网关限制来源、请求频率、超时和日志；
- 使用托管密钥并建立轮换流程；
- 不公开 MySQL、Redis、Actuator Prometheus 或上传目录管理端口；
- 在 WorkBuddy Test Run 中重新验证 6 个工具的返回和只读属性。

## 用 WorkBuddy 扩展项目

推荐流程：检索 ima 知识 → 读取 MCP 真实状态 → 使用 `silverpilot-operator` Skill 形成变更 → 本地实现/测试 → 人工审查 → 更新知识与评测。任何新业务写能力都必须先在 Web Agent 中实现 JWT 所有权、`PENDING`、人工确认、幂等与审计；不要直接通过 MCP 暴露。

WorkBuddy/连接器字段可能随产品版本变化，导入时以实际客户端 schema 为准。本仓库没有调用或伪造未公开的 WorkBuddy/ima API；当前完成的是模板、后端 MCP 契约和本地 smoke，不等同于企业空间绑定或云端发布完成。

## 常见问题

| 现象 | 优先检查 |
| --- | --- |
| 401 / 403 | 是否使用独立 MCP Key、Header 名是否正确、是否误用了 JWT 或模型 Key |
| 连接超时 | WorkBuddy 是否与后端同机；云端客户端无法访问 `127.0.0.1` |
| 工具不是精确 6 个 | 后端版本、初始化协议、`tools/list` 响应和本地 smoke；不要忽略未知写工具 |
| 知识搜索为空 | 本地知识门禁、后端 `/chat/status`、approved 状态和挂载路径 |
| 客户端拒绝模板字段 | 以当前 WorkBuddy schema 手工映射，模板能解析不等于已兼容 |
| 想增加写工具 | 先在 Web Agent 完成 JWT 所有权、PENDING、确认、幂等与审计，再做单独风险评审；默认不向 MCP 暴露 |

修改 MCP 工具名、数量、annotations、认证头或协议版本时，必须同步 `McpController` 测试、`mcp-smoke.ps1`、两个模板、本 README 和完整接入指南，并重新执行本地联合验收。
