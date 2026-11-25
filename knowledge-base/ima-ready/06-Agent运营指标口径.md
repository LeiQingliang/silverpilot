---
title: Agent 运营指标和评测口径
document_id: KB-OPS-001
version: 1.0.1
owner: qinglianglei
status: approved
reviewed_at: 2026-11-26
next_review_at: 2027-02-26
scope: Agent 运行、写动作和反馈的工程统计，及历史契约评测的解释
source_type: metric-definition
---

# 统计窗口与样本

管理员运营导出的运行指标来自 `agent_run`。窗口可选最近 1 至 30 天，按创建时间筛选、按记录 ID 降序，最多统计最近 5,000 条。若 `truncated=true`，报告须注明样本上限。聊天入口在创建运行前拒绝的限流请求不计入。记录运行数与独立使用人数、任务数有不同的统计单位。

# 如何读在线指标

| 指标 | 定义与解读 |
| --- | --- |
| 运行成功率 | （`COMPLETED` 运行数 + `WAITING_CONFIRMATION` 运行数）÷ 样本运行数，以百分数返回；`DEGRADED` 和 `FAILED` 单列。等待确认表示系统已交付待办。 |
| 工具成功率 | 样本内成功工具次数之和 ÷ 工具调用次数之和。写工具成功生成待办也计入成功，业务办理结果需另查动作表。 |
| 响应延迟 | 以运行计时起点至生成运行摘要的毫秒数为样本。平均值取整；P50、P95 将延迟升序排列，取第 ⌈百分位 × 样本数⌉ 项。居民阅读和确认所用时间不在计时范围内。 |
| 待确认运行数 | 运行记录中状态为 `WAITING_CONFIRMATION` 的条数。后续确认或撤销按动作状态统计；计算当前未处理量须查询仍有效的待办。 |
| 反馈验证关闭率 | 观察窗口内创建且当前为 `VERIFIED` 的问题数 ÷ 同一批问题总数，标明截止时间；新问题可能尚未完成验证。 |

运行或工具指标遇零分母时，接口可能返回 0；反馈关闭率零分母时返回 null。对外报告将相应字段标为“暂无样本”，同时给出分子、分母及观察窗口。

# 如何核对实际办理

运营导出按创建时间另行统计需要确认的 `agent_action`，仅 `SUCCEEDED` 表示成功执行。其他状态包括待确认、执行中、已撤销、已过期和失败；到期仍为 `PENDING` 的动作在这份汇总中计为过期。一次运行、一个写动作和一条反馈分别计数；目前导出未将写动作关联到运行。衡量预约完成率需定义用户任务并核对最终订单；评估办理效率还需实测同类任务的耗时和完成情况。

# 离线评测

历史工程契约按每条样例的预期工具、响应或动作状态判定。预期为等待确认或受控失败的样例也能通过。另做真实服务验收时，应记录提交、数据集、模型或 Mock 模式、样本数及逐项失败；用户满意度和服务机构验收需独立采集。此次复核对象为计算逻辑与导出字段。

技术依据为 [运行聚合](https://github.com/LeiQingliang/silverpilot/blob/99732e2bebfdda25580c0c95f5e24e46d43cf7dc/SourceCode/cecsmsServe-springboot/src/main/java/com/cecsmsserve/service/AgentRunService.java)、[工具调用计数](https://github.com/LeiQingliang/silverpilot/blob/99732e2bebfdda25580c0c95f5e24e46d43cf7dc/SourceCode/cecsmsServe-springboot/src/main/java/com/cecsmsserve/controller/ChatController.java)、[动作与反馈分组](https://github.com/LeiQingliang/silverpilot/blob/99732e2bebfdda25580c0c95f5e24e46d43cf7dc/SourceCode/cecsmsServe-springboot/src/main/java/com/cecsmsserve/service/AgentFeedbackService.java) 和 [运营导出](https://github.com/LeiQingliang/silverpilot/blob/99732e2bebfdda25580c0c95f5e24e46d43cf7dc/SourceCode/cecsmsServe-springboot/src/main/java/com/cecsmsserve/controller/AgentOperationsWorkflowController.java)。
