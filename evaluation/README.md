# Agent 评测集

[返回项目主页](../README.md) · [AI Harness](../docs/AI_HARNESS.md) · [Provider 接入](../docs/LLM_PROVIDER_INTEGRATION.md)

本目录定义 SilverPilot Agent 的机器可判定契约，适合在 Prompt、模型路由、工具 schema、知识库或写操作状态机变化后做回归。它不是医疗效果评测，也不是用单一通过率比较模型能力的排行榜。

除非另有说明，本文 PowerShell 命令均从项目根目录执行。

`agent-evaluation-dataset.jsonl` 当前包含 16 个工程契约用例、10 个类别，覆盖工具选择、知识来源、缺参追问、人工确认、所有权、健康安全、密钥请求、Prompt 注入、多模态路由和循环上限。它评测 Agent 工程契约，不是医疗效果或自然语言质量排行榜。

文档快照：2026-08-20 已按当前 JSONL 和运行脚本重新核对用例数、类别、占位符与参数语义；结构门禁复验为 16 个有效用例。外部 Provider 的效果、费用和稳定性不因 README 更新而视为已重测。

## 选择评测层级

| 层级 | 命令/Provider | 能证明什么 | 不能证明什么 |
| --- | --- | --- | --- |
| 数据集结构 | `validate-evaluation-dataset.ps1` | JSONL、字段、唯一 ID、确认契约有效 | Agent 或模型实际可用 |
| 本地 Mock 冒烟 | `-Provider mock -MaxCases 6` | 本地路由、只读工具、知识与报告链路 | 真实模型语义、多模态和写操作质量 |
| 真实 Provider 快速回归 | `-Provider deepseek -MaxCases 6` | 当前账号下核心文本/工具契约 | 全部类别稳定性 |
| 真实 Provider 全量回归 | `-MaxCases 0` | 当前代码、Prompt、知识、Provider 组合的 16 条契约 | 临床有效性、满意度或长期 SLA |

先跑结构门禁，再根据改动范围选择在线层级；不要用 Mock 结果替代真实 Provider 结论。

## 数据格式

每行是独立 JSON 对象，机器校验要求以下字段：

- `id`：稳定且唯一的用例 ID；
- `category`、`risk`：能力类别与风险级别；
- `userMessage`：发给 Agent 的测试输入；
- `expectedBehavior`：脚本可判定的行为契约；
- `requiresConfirmation`：是否必须只创建 `PENDING` 动作；
- `expectedTool`：可选的预期工具名，允许为 `null`。

用例不包含真实个人健康数据或客户数据。需要真实业务 ID 的写操作用 `{{serviceId}}`、`{{recipeId}}` 等占位符，运行时通过只读 MCP 动态解析，不把易漂移的种子 ID 写死。

## 运行

### 1. 结构校验

```powershell
.\scripts\validate-evaluation-dataset.ps1
```

该命令验证 JSONL、必填字段、唯一 ID 和人工确认用例的 `pending-not-executed` 契约；当前预期为 16 个有效用例。

如果当前 PowerShell 执行策略阻止本地脚本，可对单次进程显式使用：

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass `
    -File .\scripts\validate-evaluation-dataset.ps1
```

### 2. 在线契约评测

前提是后端与依赖服务健康、`.env.docker` 存在且目标 Provider 已配置：

```powershell
.\scripts\run-agent-evaluation.ps1 -EnvFile .\.env.docker -MaxCases 0
```

省略参数时默认只跑前 6 项，可用 `-MaxCases 6` 明确做快速冒烟；`-MaxCases 0` 执行全部 16 项。全量运行需要 `.env.docker` 中的 JWT 与 MCP 密钥，并需要可用的 Provider；可用 `-Provider auto|deepseek|doubao|mock` 选择路由，用 `-ReportPath <path>` 保存脱敏 JSON 结果。

脚本使用本地签名 JWT 调用真实 `/chat`，检查工具轨迹、知识来源、待确认状态和安全文本；服务/菜谱 ID 经 MCP 动态读取。测试创建的待确认动作会立即取消，触发限流时只按 `Retry-After` 有界重试一次。若指定 Mock，结果只能证明本地契约和只读工具链，不能当作真实模型效果。

建议将报告写入被 Git 忽略的本地 QA 目录，并在引用结果前确认其中不含凭据或个人数据：

```powershell
.\scripts\run-agent-evaluation.ps1 `
    -EnvFile .\.env.docker `
    -Provider deepseek `
    -MaxCases 0 `
    -ReportPath .\.qa-artifacts\agent-evaluation.json
```

## 结果解释

- 通过表示本次模型/Prompt/代码组合满足机器可判定契约；
- 不表示回答事实全部正确、医疗有效、用户满意或商业 KPI 提升；
- 模型输出有随机性，失败需保存 run ID、provider、prompt version、错误类别和测试时间；
- Prompt、模型、工具 schema 或知识版本变化后必须重新跑全量集。

历史时间点结果与边界见[验证报告](../docs/VALIDATION_REPORT.md)；引用时必须同时标明 Provider、Prompt/知识版本与测试日期，不将历史通过率当作当前结论。后续应增加养老从业者人工评分 rubric、固定成本/延迟基线、同一用例多次运行的稳定性，以及真实但完整脱敏且获得授权的用户研究样本。

## 失败排查与提交要求

失败时至少保留：用例 ID、时间、Provider/模型、Prompt ID 与指纹、知识版本、run ID、工具轨迹摘要、错误类别和脚本退出码。不要在 Issue 或报告中保存 API Key、JWT、确认令牌、原始图片或真实健康内容。

| 失败类型 | 优先检查 |
| --- | --- |
| 结构校验失败 | JSON 是否一行一对象、ID 是否重复、确认用例是否要求 `pending-not-executed` |
| 占位符无法解析 | MCP 是否健康、种子活动/服务/菜谱是否存在、工具名是否变化 |
| 工具选择漂移 | Prompt/模型版本、tool schema、上下文裁剪与循环上限 |
| 写操作直接执行 | `PENDING` 状态机、用户归属和确认端点；这是阻断发布的问题 |
| 来源或健康安全失败 | `approved` 知识是否被索引、拒答/紧急分流规则是否仍生效 |
| 限流或 Provider 失败 | `/chat/status`、`Retry-After`、账号配额、网络和模型 Endpoint |

修改数据集时必须说明新增风险、判定规则和误报边界，并同时运行结构校验；修改 Prompt、Provider、工具或知识时还需运行相应在线层级。当前数据源是 [`agent-evaluation-dataset.jsonl`](agent-evaluation-dataset.jsonl)，执行逻辑是 [`run-agent-evaluation.ps1`](../scripts/run-agent-evaluation.ps1)。
