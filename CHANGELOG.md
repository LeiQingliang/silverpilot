# Changelog

本项目的重要变更记录在此文件中，格式参考 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)，版本遵循 [Semantic Versioning](https://semver.org/spec/v2.0.0.html)。

## [Unreleased]

### Added

- OWASP Dependency-Check 13.0.0 的官方镜像缓存、SHA-256 信任标记、更新/扫描硬超时、离线回退，以及 CI/Release Java SCA 门禁。
- 根 `image/` 真实图片引用门禁、已有数据库占位 URL 幂等迁移，以及每次启动逐一读取持久化图片的运行态检查。
- “我的活动”取消报名参数与 Element Plus 单子节点契约回归测试；Mock 知识路由增加人工确认策略问法覆盖。

### Changed

- 升级 Tomcat、Netty、Jackson、POI、Commons Compress 与 Log4j 的安全补丁版本，并移除项目未使用的 Tomcat WebSocket 运行时。
- 全部 34 个前端路由统一为原创的电影感页面系统：浅色画布、深色内容岛、玻璃面板、渐变能量线、画廊卡片，以及认证/公共服务/个人中心/小伴/运营后台五类响应式布局。
- MySQL 的 `gosu` 构建器升级到已正式发布的 Go 1.27.0 Alpine 3.24 官方镜像，并继续固定多架构摘要。
- Edge 显示门禁默认按产品实际支持的浅色主题及桌面/手机断点运行 68 个场景，额外主题和视口仍可显式扩展。
- 无真实模型密钥的本机验收可显式启用可辨识的 Mock，只允许验证本地只读工具，不冒充真实模型或写操作；本次真实 DeepSeek 完整契约与 Agent smoke 已重新通过。

### Fixed

- 修复 GitHub Java SCA 的 18 个 CVSS 7.0+ 失败条目：Spring Boot 升至 4.1.1，统一带入 Spring Framework 7.0.9 / Spring Security 7.1.1；Tomcat 覆盖至 11.0.25，并删除已失效的临时漏洞例外。
- 修复从仓库根导入 Maven 模块后由 IDEA 启动本地混合模式时，外置 `host` 配置未加载、媒体根误解析到仓库外并触发持久化图片完整性失败的问题。
- 修复一键启动依赖上游实时版本查询的问题：正常启动改为离线运行兼容性门禁，断网、官方站点故障或上游发布新补丁不再阻断已验证版本；严格最新版本核验保留为显式维护审计。
- 修复首次 Java SCA 直接等待 NVD REST API 导致的无界超时；当前漏洞处置和复扫边界见 `docs/JAVA_DEPENDENCY_CHECK.md`。
- 修复 Maven Central 短暂 5xx 会直接中断后端镜像构建的问题，加入不进入运行镜像的 BuildKit Maven 缓存与最多三次有界预取重试。
- 修复页面入场模糊滤镜可能在低性能设备上残留，以及 Element Plus 描边主按钮在深浅内容区对比度不足的问题。
- 修复全新 Windows/Docker 环境中缺少卷或镜像时的 PowerShell 原生 stderr 误终止、缺少运行目录、TCP 排除端口未预检等启动问题。
- 修复数据库最终 URL 仍指向缺失演示 SVG、导致页面图片 404 和一键启动预检失败的问题；新旧数据库现统一引用根 `image/` 中真实图片。
- 修复“我的活动”条件引用节点触发的 `ElOnlyChild` 警告，以及未来活动取消时把表格索引误传给业务函数的问题。
- 修复 Windows PowerShell 5.1 直接运行高级脚本时 `$PSScriptRoot` 默认参数失效，以及中文 SQL/JSON 经原生管道转码导致的校验误判。

## [1.0.1] - 2026-08-20

### Added

- 基于 `fast-check` 的前端属性模糊测试，以固定种子覆盖聊天历史、AI 照护方案、运营统计、图片上传和媒体 URL 五类不可信输入边界。
- 自动化 Release 契约，阻断未固定到 40 位提交 SHA 的 Action，以及未显式关闭凭据持久化的 checkout 步骤。

### Changed

- AI 运行统计和照护方案归一化现在拒绝 `NaN`/无穷值，并限制模型返回标识、元数据和证据文本的长度。
- Dependabot Docker 策略区分 Node LTS、Nginx stable 与 MySQL LTS，减少主线版、创新版和跨主版本噪声。

### Security

- 前端 Docker 构建和 GitHub Release/CI 从经 SHA-256 核验的 npm 官方 tarball 安装固定版本，不再只依赖可变的版本解析。
- MySQL 派生镜像从 Oracle Linux 受支持仓库定向升级 `curl`/`libcurl`，修复当前基础镜像中的 CVE-2026-3783 与 CVE-2026-1965。
- 所有 GitHub Actions 使用完整提交 SHA，所有 checkout 步骤均设置 `persist-credentials: false`；仓库侧只允许 GitHub 官方 Action 与两个精确允许的第三方 SHA。
- `v*` 标签由仓库规则集禁止更新和删除；正式 Release 继续使用不可变资产与 GitHub Artifact Attestations。

## [1.0.0] - 2026-08-20

### Added

- Apache-2.0 许可证、贡献指南、行为准则、安全策略、支持与治理说明。
- GitHub Issue/PR 模板、Dependabot、CI、CodeQL、Gitleaks 和 OpenSSF Scorecard 工作流。
- 发布安全门禁与完全合成的数据库联系信息/身份标识。
- 可重复发布脚本、版本一致性契约、GitHub Release 自动化、SPDX SBOM、SHA-256 校验和与构建来源证明。

### Changed

- 将项目整理为可公开复现的 SilverPilot 单仓库工程。
- 加固上传路径、临时文件、日志换行和浏览器会话中的个人信息处理，并增加回归测试。
- 将后端从 `1.0.0-SNAPSHOT` 转为正式 `1.0.0`，并统一 Maven、npm、Citation 与发布说明的版本元数据。
- 版本门禁区分 Go 上游 stable 与 Docker Official Image 实际发布节奏，拒绝把尚不存在的镜像标签写入发布基线。

### Security

- 所有非 `scratch` Docker 基础镜像同时固定可读标签和多架构 OCI SHA-256 摘要。
- 正式 Release 使用草稿附加全部资产后发布，并启用不可变标签/资产与 GitHub 签名证明。

[Unreleased]: https://github.com/LeiQingliang/silverpilot/compare/v1.0.1...HEAD
[1.0.1]: https://github.com/LeiQingliang/silverpilot/releases/tag/v1.0.1
[1.0.0]: https://github.com/LeiQingliang/silverpilot/releases/tag/v1.0.0
