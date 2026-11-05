# CatPaw / 妙手连接

适用于同一台电脑运行的 SilverPilot 演示栈。桌面端在设置 → MCP 添加 HTTP 服务：

- URL：http://127.0.0.1:8083/mcp
- Headers JSON 输入框填写 {"Authorization":"Bearer <本机 MCP 密钥>"}；替换占位符为 SILVERPILOT_MCP_API_KEY 的值，保留 Bearer 后的空格。
- 密钥仅录入客户端凭据配置，不写入技能、截图或提交。
- 保存后新建会话，完成 initialize、tools/list，并读取平台概况。应看到 6 个只读工具。
- 在仓库根目录运行 pwsh -File scripts/mcp-smoke.ps1 -EnvFile .env.docker 可检查服务端协议；这不替代客户端实际调用验证。

本地文件夹或 ZIP 技能导入应选择包含 SKILL.md 的 silverpilot-operations 目录。

云端 Managed Agents 不能访问用户电脑的 127.0.0.1。需另行部署受保护 HTTPS Streamable HTTP 服务，并按平台文档配置凭据与权限。本项目不自动公开本机端口。

官方文档核查于 2026-11-05：
- https://catpaw.meituan.com/docs/#/desktop/settings-mcp
- https://catpaw.meituan.com/docs/#/desktop/skills-market
- https://catpaw.meituan.com/docs/#/agent/mcp

当前官方文档使用“妙手”名称；旧 CatPaw IDE 与全场景平台能力不能混用。
