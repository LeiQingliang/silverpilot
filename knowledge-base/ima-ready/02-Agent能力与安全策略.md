---
title: Agent 执行、确认与审计策略
document_id: KB-AGENT-001
version: 1.0.1
owner: qinglianglei
status: approved
reviewed_at: 2026-11-26
next_review_at: 2027-02-26
scope: Web Agent 工具授权、确认、运行记录与外部只读接口
source_type: system-policy
---

# 查询与办理

Web Agent 提供 8 个查询工具和 5 个写工具。后端从当前登录会话取得用户身份，校验工具名、参数和访问权限。通过校验的查询可以直接执行；读取个人订单或报告时，还要核对数据归属。公网共享演示账号只获得查询工具，后端也会拒绝其写入或确认请求。

活动报名、服务预约和取消、助餐预订和取消都需要用户确认。以服务预约为例，用户可按动作状态判断结果。

| 动作状态 | 用户看到的结果 |
| --- | --- |
| `PENDING` | 已生成待确认摘要。可在有效期内确认或取消，此时尚未创建订单。默认确认期限为 10 分钟。 |
| `EXECUTING` | 后端已校验用户与确认令牌，并取得执行权。操作处理中，可稍后刷新记录。 |
| `SUCCEEDED`、`FAILED` | 后端完成业务检查和办理，返回对应结果。只有成功才按结果核对订单。 |
| `CANCELLED`、`EXPIRED` | 本次动作已终止，如仍需办理应重新发起。 |

后端仅允许同一待确认动作取得一次执行权，执行前重新准备并校验工具参数。模型的文字回复或运行状态 `WAITING_CONFIRMATION` 均不能证明订单已经生成。

# 模型与审计记录

- 工具规划有迭代和调用数量上限。达到迭代上限后，后端停止继续调用模型。模型服务失败时，符合降级条件的文字问题可转入只读本地知识检索，其他请求返回错误。图片请求需要已配置的豆包视觉通道。
- `agent_run` 记录用户与运行标识、模型、输入类型、耗时、调用次数、Token 计数、状态及错误类别等字段。该表不保存对话正文或原图。
- `agent_action` 单独记录工具名称、用户、摘要和执行结果。待确认的写动作会暂存执行所需参数，进入终态后清空参数字段。工具结果可能包含业务数据，演示和测试应使用虚构资料。
- WorkBuddy 的 MCP 接口提供平台概况、活动、服务、菜谱、知识检索和匿名聚合指标六项只读能力。本人订单、体检报告及需要确认的操作通过已登录的 Web 端办理。

# 复核记录

2026-11-26 按仓库实现核对工具权限、确认状态转换、模型路由、审计存储与 MCP 能力。本文记录应用行为，第三方模型与资料库的数据保留方式须按所选服务另行核实。[Web 编排](https://github.com/LeiQingliang/silverpilot/blob/99732e2bebfdda25580c0c95f5e24e46d43cf7dc/SourceCode/cecsmsServe-springboot/src/main/java/com/cecsmsserve/controller/ChatController.java) · [确认与动作记录](https://github.com/LeiQingliang/silverpilot/blob/99732e2bebfdda25580c0c95f5e24e46d43cf7dc/SourceCode/cecsmsServe-springboot/src/main/java/com/cecsmsserve/service/AgentActionService.java) · [运行记录字段](https://github.com/LeiQingliang/silverpilot/blob/99732e2bebfdda25580c0c95f5e24e46d43cf7dc/SourceCode/cecsmsServe-springboot/src/main/java/com/cecsmsserve/entity/AgentRun.java) · [模型路由](https://github.com/LeiQingliang/silverpilot/blob/99732e2bebfdda25580c0c95f5e24e46d43cf7dc/SourceCode/cecsmsServe-springboot/src/main/java/com/cecsmsserve/service/AiProviderService.java) · [MCP 工具](https://github.com/LeiQingliang/silverpilot/blob/99732e2bebfdda25580c0c95f5e24e46d43cf7dc/SourceCode/cecsmsServe-springboot/src/main/java/com/cecsmsserve/controller/McpController.java)
