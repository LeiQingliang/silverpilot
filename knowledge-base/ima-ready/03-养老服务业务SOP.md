---
title: 养老服务预约与异常处理 SOP
document_id: KB-SOP-001
version: 1.0.1
owner: qinglianglei
status: approved
reviewed_at: 2026-11-26
next_review_at: 2027-02-26
scope: SilverPilot 演示系统活动报名、上门服务和助餐的工具参数、人工确认与异常结果
source_type: business-sop
---

# 演示系统的办理范围

本次技术复核覆盖 Web Agent 从目录查询到业务写入的代码规则。服务站的接单条件、人员调度、上门时效、照护或膳食适宜性，需要机构及相应专业人员另行核准。演示仅使用虚构地址及备注。

# 活动、服务与助餐怎么办理

| 任务 | 先查什么 | 生成待确认动作的条件 |
| --- | --- | --- |
| 活动报名 | 查询可报名活动，选取其活动 ID | 活动状态开放且尚未开始；设置人数上限时仍有名额；本人尚未报名。确认执行时再次核对这些条件。 |
| 上门服务预约 | 查询可用服务目录，选取服务小类 ID | 所属大类与小类均启用。用户提供本次服务地址和预约日期（`yyyy-MM-dd`，可选今天）。地址长度为 2 至 200 个字符；按本次输入办理。 |
| 助餐预订 | 列出或按关键词查询启用的菜谱，选取菜谱 ID | 提供未来的用餐时间（上海时间，`yyyy-MM-dd HH:mm:ss`）和 1 至 20 人的用餐人数。备注可选，最多 500 个字符。 |

服务预约卡使用地址摘要。当前实现对超过 4 个字符的地址保留前 3 个字符和末字符，较短地址原样显示。截图、工单或演示报告使用虚构信息。

# 确认、撤销与取消订单

1. 目录没有结果时如实告知；缺少参数时向本人追问。信息齐备后，具有写权限的用户可收到确认卡。此时运行状态为 `WAITING_CONFIRMATION`，待办动作为 `PENDING`。
2. 本人可在有效期内确认或撤销待办。确认时后端校验身份、令牌与动作状态，再执行业务检查。仅在动作结果为 `SUCCEEDED` 时报告办理成功；核对预约可再查本人的订单列表。公网共享演示账号只读。
3. 撤销待办只结束这次计划。要取消已有订单，应查询本人订单，使用真实订单 ID 单独发起取消并确认。服务订单仅可取消“待受理”，助餐订单仅可取消“待处理”。
4. 若名额、服务、菜谱或订单状态已变化，按后端返回的失败原因重新查询。系统异常时记录可用的运行 ID 供排查，待确认或超时均不能作为下单凭证。

技术复核依据为 [工具校验与执行](https://github.com/LeiQingliang/silverpilot/blob/99732e2bebfdda25580c0c95f5e24e46d43cf7dc/SourceCode/cecsmsServe-springboot/src/main/java/com/cecsmsserve/service/ToolExecutor.java)、[动作状态与确认](https://github.com/LeiQingliang/silverpilot/blob/99732e2bebfdda25580c0c95f5e24e46d43cf7dc/SourceCode/cecsmsServe-springboot/src/main/java/com/cecsmsserve/service/AgentActionService.java) 和 [聊天与确认入口](https://github.com/LeiQingliang/silverpilot/blob/99732e2bebfdda25580c0c95f5e24e46d43cf7dc/SourceCode/cecsmsServe-springboot/src/main/java/com/cecsmsserve/controller/ChatController.java)。
