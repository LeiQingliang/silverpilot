# AI Harness 与评测设计

## 1. 能力范围

SilverPilot 的 AI 层服务于三个真实场景：

1. 自然语言查询并调用活动、服务、健康和助餐业务；
2. 检索审核知识，为回答附加 `[KB:来源]`；
3. 根据数据库中的有效服务目录和知识片段生成结构化照护导航方案。

健康内容只做信息整理、风险提示和沟通准备，不诊断、不处方、不自动改变照护方案。

## 2. 可替换 Provider

`AiProviderService` 对编排层提供统一的路由、请求装饰、结构化输出和 Token 统计接口。

| Provider | 文本 | 工具调用 | 图片 | 无 Key 行为 |
| --- | --- | --- | --- | --- |
| DeepSeek | 是 | 是 | 当前关闭 | 返回 503 并说明配置项 |
| 豆包/火山方舟 | 是 | 是 | 是 | 返回 503，不伪造图片理解 |
| `silverpilot-deterministic-mock-v1` | 明确标注 Mock | 仅确定性只读路由 | 否 | 需显式开启 |

`auto` 对纯文本优先选择已配置 DeepSeek，再选择豆包，最后选择显式 Mock；包含图片时只选择已配置的豆包 Vision。DeepSeek 接口和 JSON 输出遵循其官方 [Chat Completion API](https://api-docs.deepseek.com/api/create-chat-completion) 与 [JSON Output 指南](https://api-docs.deepseek.com/guides/json_mode/)，豆包使用火山方舟的 [OpenAI 兼容 Chat API](https://www.volcengine.com/docs/82379/66619f8df281250274ef4f88?lang=zh)。

## 3. Prompt 生命周期

Prompt 位于后端 `src/main/resources/prompts`，不是散落在 Controller 中的长字符串：

| ID | 版本 | 用途 |
| --- | --- | --- |
| `silverpilot-agent` | `3.0.0` | 多轮业务工具编排、模糊指令补全与自然语言协议 |
| `care-recommendation` | `1.0.0` | 结构化照护服务导航 |

每个文件包含 frontmatter 元数据。`PromptTemplateService` 在启动/调用时解析 ID、version、purpose，替换受控变量，并计算内容指纹；`agent_run.prompt_version` 保存版本，方便按版本比较成功率、延迟和 Token。

变更规则：

- 行为、工具契约或安全规则变化必须提升版本；
- 不在 Prompt 中保存密钥、个人信息或环境路径；
- 修改后先跑单元测试、16 项离线契约集，再进行受控在线冒烟；
- 不以展示模型私有思维链作为“可解释性”，只展示工具、状态、来源和必要依据。

## 4. 上下文和成本控制

- 客户端消息条数由 `CECSMS_AGENT_HISTORY_LIMIT` 限制，单条文字和附件数量/大小均有校验；
- 每次生成默认最多 1,800 completion tokens，单次 Agent 运行最多累计 12,000 tokens；
- 工具循环默认最多 8 次；
- `agent_run` 分别记录 prompt、completion 和 total tokens；
- UI 显示本次 Token 与最近 7 日汇总，便于识别高成本 Prompt/任务；
- 费用应按实际供应商账单核验，项目不写死成本或“节省比例”。DeepSeek 最新单价以其 [官方价格页](https://api-docs.deepseek.com/zh-cn/quick_start/pricing) 为准。

## 5. 结构化输出

照护方案请求使用 `response_format={"type":"json_object"}`，并在 Prompt 中提供完整 JSON 契约。后端不直接信任模型输出，还会验证：

- `urgency` 只能是 `low|medium|high|emergency`；
- 推荐最多 3 项；
- 每个 `serviceId/serviceName` 必须与本次数据库候选集合一致；
- evidence 只能来自本次 `[KB:...]` 或 `BUSINESS:服务ID` allowlist；
- 缺失、不合法或幻觉 ID 会触发可观测错误/降级，不被包装成成功。

结构化方案是只读决策辅助；真正预约仍要走 `book_service → PENDING → 用户确认`。

## 6. RAG 与知识治理

当前知识规模小且要求严格审批，因此采用可解释、确定性的 Markdown 词项检索，而没有为“看起来像 AI”引入向量数据库。

检索门禁：

- 只扫描配置根目录下最多两层的 `.md`；
- 只索引 frontmatter `status: approved` 的文件；
- 单文档最多 1 MB、总数最多 200；
- 返回最多 5 条，保留文件级 `[KB:sourceId]`；
- 结果缓存默认 60 秒；目录缺失时明确降级；
- `scripts/validate-knowledge-base.ps1` 检查元数据、过期日期、重复、链接、秘密模式和 manifest 一致性。

本地 RAG 与可导入 ima 的 `knowledge-base/ima-ready` 使用同一份内容，避免两个知识源长期漂移。

## 7. 安全、隐私与内容边界

`AiSafetyService` 在发送外部模型前：

- 拒绝疑似 API Key、私钥或已知密钥赋值；
- 脱敏身份证号、手机号与邮箱；
- 阻断索取系统提示词、密钥或绕过安全协议的请求；
- 图片仅接受允许的 MIME、尺寸和 Data URL，且不写入 `agent_run`。

工具层始终从已验证 JWT 获取用户 ID，并校验订单/报告归属。模型不能通过参数切换为其他用户。

## 8. 可靠性与降级

- Provider 使用连接/读取超时和有限次数指数退避，仅对适合重试的暂时性失败重试；
- Redis 提供多实例共享限流，连接失败时记录告警并降级到进程内窗口，不无限放行；
- 知识库检索可在外部模型失败时返回明确的本地只读结果，不假装完成复杂推理；
- 未配置 Provider、图片能力或 MCP Key 时接口返回明确状态；
- 所有写操作与模型调用解耦，Provider 超时不会产生“半完成订单”。

## 9. 可观测性

| 信号 | 位置 | 隐私策略 |
| --- | --- | --- |
| 请求关联 | `X-Request-ID`、日志 MDC | 不含 Prompt |
| Agent 运行 | `agent_run` | 模型、模态、延迟、工具数、Token、状态、错误类型 |
| 写操作 | `agent_action` | 所有权、状态、过期时间、脱敏摘要；终态清参数 |
| 指标 | `/actuator/prometheus` | 应通过网关限制访问 |
| UI | 最近 7 日运行指标 | 当前用户范围，展示成功率/延迟/Token/降级 |

## 10. 评测策略

`evaluation/agent-evaluation-dataset.jsonl` 含 16 个契约用例，覆盖：

- 8 类只读工具选择与知识来源；
- 缺参追问、写操作人工确认、订单所有权；
- 紧急健康提示与非诊断边界；
- 密钥请求、Prompt 注入、多模态路由和循环上限。

```powershell
.\scripts\validate-evaluation-dataset.ps1
.\scripts\run-agent-evaluation.ps1 -EnvFile .\.env.docker -MaxCases 0
```

在线脚本检查实际 HTTP 响应、工具轨迹、来源和待确认状态，并取消评测产生的待确认动作。它衡量工程契约遵循，不衡量医学有效性、用户满意度或商业转化；这些需要独立专家标注、真实用户研究与合规流程。
