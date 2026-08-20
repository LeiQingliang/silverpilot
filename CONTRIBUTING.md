# Contributing to SilverPilot

感谢你愿意改进 SilverPilot。项目接受缺陷修复、测试、文档、可访问性、安全加固和经过论证的功能建议。

## 开始之前

- 先搜索现有 Issue 与 Pull Request，避免重复工作。
- 功能范围较大时先开 Discussion 或功能建议 Issue，确认边界后再实现。
- 安全漏洞必须按 [SECURITY.md](SECURITY.md) 私密报告，不要建立公开 Issue。
- 不要提交真实老人、机构、订单、健康档案、手机号、身份证号、密钥或生产日志。
- AI 辅助代码必须由提交者逐行复核、运行测试并在 PR 中说明使用范围；责任仍由提交者承担。

## 开发环境

最快的完整评审路径是 Windows + Docker Desktop：

```powershell
.\scripts\start-project.ps1 -Mode Docker
```

需要后端断点调试或前端热更新时，请按 [README](README.md#严格-idea--vscode-本地开发) 和 [部署说明](docs/DEPLOYMENT.md) 配置 Local 模式。

## 分支与提交

1. 从最新 `main` 创建短生命周期分支，例如 `fix/login-rate-limit` 或 `docs/docker-setup`。
2. 保持提交单一职责，推荐 Conventional Commits：`feat:`、`fix:`、`docs:`、`test:`、`refactor:`、`build:`、`ci:`。
3. 不要把格式化、依赖升级和业务改动混在同一提交中。
4. 不要改写他人的公开历史，也不要提交构建产物、本地配置或扫描数据库。

## 必须通过的检查

安装前端依赖后，在仓库根目录运行：

```powershell
Push-Location SourceCode\cecsmsui-vue
npm ci
Pop-Location
.\scripts\verify-project.ps1
.\scripts\verify-publication-safety.ps1
```

如果改动数据库、容器或运行模式，还应运行对应脚本；完整矩阵见 [验证报告](docs/VALIDATION_REPORT.md) 和 [部署说明](docs/DEPLOYMENT.md)。运行时测试产生的数据必须在 `finally` 或等价清理流程中精确删除。

## Pull Request 要求

PR 应说明：

- 改了什么、为什么改；
- 用户、接口、数据库或部署影响；
- 已运行的命令和结果；
- 未完成或无法验证的边界；
- UI 改动的前后截图；
- 是否涉及迁移、兼容性、安全、隐私或第三方许可。

维护者可能要求拆分过大的 PR。合并方式默认使用 squash merge。

## 许可证

提交贡献即表示你有权提交该内容，并同意按本仓库的 [Apache License 2.0](LICENSE) 授权该贡献。第三方代码、图片、数据或模型产物必须附带可核验来源和兼容许可证。
