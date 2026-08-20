---
title: WorkBuddy 驱动的产品升级与扩展流程
document_id: KB-WORKBUDDY-001
version: 1.0.0
owner: qinglianglei
status: approved
reviewed_at: 2026-08-18
next_review_at: 2026-11-18
scope: 需求分析、代码变更、知识治理、验证和交付
source_type: engineering-workflow
---

# 接入面

- 项目 Skill：`.codebuddy/skills/silverpilot-operator/SKILL.md`。
- 只读 MCP：`http://127.0.0.1:8083/mcp`，使用独立的 `CECSMS_MCP_API_KEY`。
- ima 知识库：导入 `knowledge-base/ima-ready` 并在 WorkBuddy 内完成原生绑定。
- 工作区：项目根目录，不把构建产物、日志或密钥纳入知识库。

# 升级流程

1. 用 WorkBuddy 先读取产品边界、目标用户和当前 MCP 能力，将需求转成可验收条件。
2. 划分“仅修文档/知识”、“只读业务能力”、“写操作”三种风险等级。
3. 对写操作保留 JWT 身份、资源所有权、待确认状态机和幂等性；不把直接写入工具暴露给 MCP。
4. 每个新能力同时补齐正常、参数缺失、越权、重复执行和上游失败测试。
5. 先运行知识校验、后端测试和前端检查，再进行真实服务冒烟。
6. 交付时更新架构、环境变量、验收证据和未配置外部能力，不声称未验证集成已上线。

# 变更输出

每次升级至少输出：问题和用户价值、修改文件、安全影响、测试结果、可观测指标、回滚方式、真实边界。
