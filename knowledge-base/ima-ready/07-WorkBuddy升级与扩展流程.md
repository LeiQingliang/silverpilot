---
title: WorkBuddy 驱动的产品升级与扩展流程
document_id: KB-WORKBUDDY-001
version: 1.0.1
owner: qinglianglei
status: approved
reviewed_at: 2026-11-26
next_review_at: 2027-02-26
scope: 仓库工作区、同机只读 MCP、ima 资料导入及改动验收
source_type: engineering-workflow
---

# 同机连接与验收

在 WorkBuddy 打开项目根目录可读取项目 Skill、源码及测试。仓库提供 Agent 和连接器参考模板。此次技术复核覆盖固定提交中的模板、后端协议和冒烟脚本；WorkBuddy 账号授权、ima 同步及企业空间发布需由实际操作者分别验收。

1. 启动本机后端。模板的 Streamable HTTP 地址为 `http://127.0.0.1:8083/mcp`。将独立 MCP 密钥填入客户端的安全凭据字段，由客户端通过 `X-CECSMS-MCP-Key` 传送。Docker 模式从本机环境文件取 `SILVERPILOT_MCP_API_KEY`，后端读取 `CECSMS_MCP_API_KEY`。
2. 在仓库根目录运行 `.\scripts\mcp-smoke.ps1 -EnvFile .\.env.docker`。脚本检查 `initialize`、协议版本 `2025-03-26`、服务名和 `tools/list` 的 6 项只读且非破坏性标记。实际工具范围为平台概览、活动、服务、菜谱、受控知识和匿名聚合指标。
3. 客户端另做一次公开目录查询，并记录日期、客户端和结果。个人订单、健康报告、确认令牌及业务写操作均在该 MCP 范围之外。云端工作区若需访问本机以外的服务，须另外配置可达的受保护 HTTPS 服务并独立验收。

# ima 导入与撤回

从 `knowledge-base/ima-ready` 选择状态为 `approved`、且当前上海日期落在两次审核日期（含首尾）之间的文档；跳过治理规范、草稿与到期文档。由授权操作者在 ima 导入、在 WorkBuddy 绑定，并抽查一次能返回正确文档 ID 的检索。换版时按文档 ID 替换旧版本，撤回或隔离时从外部资料库同步移除。后端对本地文件生效的日期过滤不会替操作者更新外部资料库。密钥、环境文件、日志和用户数据不进入知识包。

# 变更与交付

- 明确用户任务、受影响的接入面和可观察的成功条件。改动知识时运行 `.\scripts\validate-knowledge-base.ps1`，记录文档版本与导入状态。
- 改动 MCP 时核对工具清单、只读声明以及返回数据范围，再用本机冒烟和真实客户端分别验证；改动 Web 写工具时单独验证登录身份、本人资源、待确认、重复确认及失败路径。
- 交付记录注明改动提交、运行过的检查、外部授权或发布状态和回退办法。客户端连接、ima 检索和正式发布各自保留验收记录。

技术依据为 [连接器模板](https://github.com/LeiQingliang/silverpilot/blob/99732e2bebfdda25580c0c95f5e24e46d43cf7dc/workbuddy/connector-template.json)、[MCP 工具白名单与认证](https://github.com/LeiQingliang/silverpilot/blob/99732e2bebfdda25580c0c95f5e24e46d43cf7dc/SourceCode/cecsmsServe-springboot/src/main/java/com/cecsmsserve/controller/McpController.java)、[本机冒烟脚本](https://github.com/LeiQingliang/silverpilot/blob/99732e2bebfdda25580c0c95f5e24e46d43cf7dc/scripts/mcp-smoke.ps1) 和 [知识治理](https://github.com/LeiQingliang/silverpilot/blob/99732e2bebfdda25580c0c95f5e24e46d43cf7dc/knowledge-base/README.md)。
