---
title: Agent 执行、确认与审计策略
document_id: KB-AGENT-001
version: 1.0.0
owner: qinglianglei
status: approved
reviewed_at: 2026-08-18
next_review_at: 2026-11-18
scope: Agent 工具、权限、确认、审计和降级
source_type: system-policy
---

# 执行链路

`User goal -> model routing -> tool planning -> argument validation -> tool execution -> grounded response -> run metrics`

该链路只展示可核验的执行事件，不暴露模型内部推理。

# 读写分级

- 读操作：校验当前用户后立即执行，写入审计记录。
- 写操作：先生成有限期的 `PENDING` 动作，界面明示摘要、编号和过期时间。
- 确认执行：使用当前 JWT 用户与一次性确认令牌，并在执行前再次验证资源所有权和业务状态。
- 终态：`SUCCEEDED`、`FAILED`、`CANCELLED` 或 `EXPIRED`；终态会清除待执行参数。

# 真实性护栏

1. 模型没有调用工具时，不得宣称预约、报名、取消或订单已成功。
2. 工具参数由后端校验；日期、数量、资源状态和所有权不依赖模型自觉。
3. 连续工具调用上限为 8 轮；用户请求有速率限制，消息数量和总长度有上限。
4. 图片只传给明确声明支持 vision 且已配置的模型通道。
5. 观测表不保存原始 Prompt、图片或工具参数，只保存运营指标。

# WorkBuddy 边界

MCP 只暴露公共目录、受控知识和匿名聚合指标。个人健康数据和业务写工具不通过 MCP 暴露；真实写入仍要回到 Web 端登录和人工确认链路。
