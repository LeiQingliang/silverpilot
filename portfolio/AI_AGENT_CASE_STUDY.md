# SilverPilot：从养老管理系统到可执行 AI Agent

## 问题

传统养老管理页要求用户理解菜单、表单和订单状态；一个普通聊天框虽然更易用，却可能编造结果、越权操作或在用户不知情时写入数据。SilverPilot 的目标是把自然语言变成真实、受控、可追踪的业务执行。

## 已实现架构

```text
用户目标 / 图片 / 语音输入
          ↓
多模型路由（DeepSeek 文本工具；豆包 Vision 配置位）
          ↓
最多 8 轮规划与 13 个真实工具
          ↓
读操作立即执行 ── 写操作进入 PENDING
          ↓                 ↓
知识来源与真实结果      JWT + 所有权 + 人工确认
          ↓                 ↓
       自然语言答复 + 执行轨迹 + agent_run / agent_action
```

关键证据位于：

- 编排与多模态输入：`ChatController.java`
- 模型路由：`AiProviderService.java`
- 真实业务工具：`ToolExecutor.java` 与 `Tools.java`
- 写操作状态机：`AgentActionService.java`
- 运行指标：`AgentRunService.java`
- 受控检索：`KnowledgeBaseService.java` 与 `knowledge-base/ima-ready`
- WorkBuddy：`McpController.java` 与 `.codebuddy/skills/silverpilot-operator`
- 交互：`AiChat.vue`

## 关键产品决策

1. “能做事”必须由后端工具结果证明，模型不能自行宣布成功。
2. 报名、预约和取消先生成有效期内的待确认动作；确认前不改业务数据，重复确认不重复下单。
3. 图片只路由到明确支持视觉且已配置的通道，不把多模态写成装饰性上传框。
4. 知识文档只有通过元数据、过期、重复、链接和秘密扫描后才能进入运行时检索及 ima。
5. WorkBuddy MCP 只开放公共目录、知识和匿名指标；个人健康数据与写操作不出现在工具列表。
6. 观测层不保存原始 Prompt 和图片，只记录模型、模态、延迟、工具次数、状态与失败类型。

## 可复现实验

在项目根目录执行：

```powershell
.\scripts\verify-project.ps1
```

启动后端并配置独立 MCP 密钥后执行：

```powershell
.\scripts\mcp-smoke.ps1
```

## 真实边界

这是可本地运行和验证的个人项目，不是已在养老机构部署的商业系统。豆包在线视觉调用需要用户自己的火山方舟凭据和模型 Endpoint ID；WorkBuddy 企业 Agent 与 ima 的最终绑定需要对应账号权限。机器人与半导体内容是可迁移方案设计，不是行业客户交付。
