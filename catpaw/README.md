# CatPaw / LongCat AI 运营实践

SilverPilot 为社区养老服务提供可复现的 AI 运营实践：LongCat 作为独立模型通道，CatPaw（当前官网文档名“妙手”）作为读取匿名指标、辅助分析和沉淀工作方法的工作台。

## 已交付与待验证

| 项目 | 本仓库交付 | 尚需实际账号或环境 |
| --- | --- | --- |
| LongCat | Responses 适配器、配置、HTTP 桩与人工确认回归 | 真实账号可用性、语义效果、费用、稳定性 |
| CatPaw | 可导入技能目录、只读 MCP、匿名导出与接入说明 | 客户端导入和真实 MCP 联调 |
| 运营 | 运行/动作指标、反馈状态流转、证据引用、SQL 和 Python 分析 | 真实用户试用、人工评分、业务效果 |
| 知识治理 | 到期隔离、发布校验和回归测试 | 健康草稿的实际专业复核；外部知识库副本同步 |

不存在与美团的合作、认证或生产部署承诺。合成测试只能证明测试覆盖的行为。

## 本机接入

先按根 README 启动演示栈。LongCat 配置见 [Provider 接入](../docs/LLM_PROVIDER_INTEGRATION.md)；保留其他模型通道，可显式选择 longcat 做评测。

将 [silverpilot-operations](skills/silverpilot-operations/) 文件夹或其 ZIP 导入 CatPaw 技能管理。连接步骤见 [技能连接参考](skills/silverpilot-operations/references/connection.md)。MCP 无权创建订单或读取个人运行记录。

管理员在“小伴运行”页面登记问题、指定分类与优先级，按 OPEN → TRIAGED → FIXED → VERIFIED 流转。FIXED 记录变更引用；VERIFIED 必须填入复测证据并确认已复测。此确认来自人工，不代表系统验证了外部证据真实性。

页面支持导出匿名运营 JSON；也可由已登录管理员读取 GET /chat/admin/operations/export?days=7。不要将登录 Token 放入技能包。反馈中仅保存受格式约束的引用，不保存原始对话或健康详情。

## 一次完整的运营迭代

1. 选择“查询服务→预约待确认→用户确认→办理结果”作为任务。当前动作与运行没有可靠一对一关联，不能将动作数/运行数称作转化率。
2. 读取统计窗口、状态、失败类别及样本截断提示，抽查获授权的脱敏案例。
3. 将问题登记为知识缺失、提示规划、工具错误或体验问题，提出可检验的假设。
4. 修改知识、Prompt 或交互后，运行固定评测集，保留版本和失败案例。
5. 复测通过才关闭问题。必要时重开；记录未覆盖条件。

仓库中的首个真实工程问题是“降级运行被计入成功”。修正后的成功口径为 (COMPLETED + WAITING_CONFIRMATION) / 总运行数；这属于指标校准，不是业务增长。另一个案例为过期知识的运行时隔离。实际改动和回归测试可作为作品证据。

## 评测与分析

评测主脚本支持 -Trials 3 和 -CaseIds 选择固定任务；-MaxCases 0 执行全部选中项。运行报告写入 .qa-artifacts，记录实际模型、run ID、延迟、Token 和本地/服务端版本上下文。没有账号时，只运行本地测试，不能生成假“真实模型报告”。

    python catpaw/skills/silverpilot-operations/scripts/analyze_evaluations.py report-a.json report-b.json --output .qa-artifacts/comparison.md

对比前需提交干净源码，确认两个被测服务来自同一构建。工具会拒绝数据集、知识、Prompt、用例试验数或证据类型不一致的报告；输出不对模型作无依据排名。[只读 SQL](sql/operations-readonly.sql) 可核查完整周期的聚合，需由操作者填写同一时区窗口。

人工试用记录建议：任务编号、用户类型、预期结果、实际完成、耗时、误解点、返工、辅助工具、观察者。AI 辅助与人工基线采用相同交付要求；先记录结果，再讨论效果，不预填“提效百分比”。

## 本地可复现验证

完整工程门禁需要 Java 25、项目锁定的 Node/npm、PowerShell 7 和 Python 3.12：

    pwsh -File scripts/verify-project.ps1

新增独立检查：

    pwsh -File scripts/test-knowledge-governance.ps1
    node --test scripts/test/evaluation-runner.test.mjs
    python scripts/test/test_evaluation_analysis.py

后端测试包括 LongCat HTTP 协议桩、写操作人工确认、反馈状态与权限、知识有效期及 MCP 聚合数据边界。测试没有真实模型消费，不等于完整 Docker 栈或 CatPaw 客户端验收。
