# 版本与长期支持策略

基线日期：2026-08-20（Asia/Shanghai）。本文件把“最新长期稳定版本”转成可执行规则；版本状态会随上游发布变化，因此通过独立维护审计定期重新核验，正常启动不依赖外网或上游当天是否发布了新版本。

## 1. 选版定义

1. 上游明确提供 LTS/Extended 通道时，选择最新生产级长期支持线中的最新补丁版。
2. 上游没有 LTS 制度时，选择与长期支持运行平台兼容的最新 GA/stable 版本，排除 RC、preview、milestone、beta、nightly 与仅 mainline/innovation 的通道。
3. Spring Boot 传递依赖以当前稳定版 BOM 的完整验证组合为准，不把每个传递包强行覆盖到独立最高版本。
4. 容器和构建工具使用精确版本或锁文件；Docker 基础镜像同时保留可读标签并固定多架构 OCI 摘要。只有经过测试、运行验收和漏洞复扫后才更新基线。

因此，“所有组件都是 LTS”不是一个真实可实现的说法：Vue、Vite、npm、Go、Nginx、Alpine 和大多数 Java 库没有独立 LTS 标签。本项目对这类组件采用最新非预览稳定版。

## 2. 当前基线

| 范围 | 当前版本 | 通道与依据 |
| --- | --- | --- |
| Java | 25 LTS；本机 Temurin 25.0.4，官方容器 Temurin 25.0.3+9 | Java 25 为 LTS；官方容器尚未发布 25.0.4 精确标签 |
| Spring Boot | 4.1.0 | 当前 stable；统一管理 Spring 与常用传递依赖 |
| Maven / Wrapper | 3.9.16 / 3.3.4 | Maven 3 最新推荐稳定版；不采用 Maven 4 preview |
| OWASP Dependency-Check | 13.0.0 | GitHub Releases 最新正式稳定版；拒绝 draft、pre-release 与非稳定 SemVer |
| Java 安全覆盖 | Tomcat 11.0.24 / Netty 4.2.17.Final / Jackson 2.21.5 / POI 5.5.1 / Commons Compress 1.28.0 / Log4j 2.25.5 | 对 Spring Boot/EasyExcel 传递依赖做有证据的安全补丁覆盖，并通过 84 项测试与离线 SCA |
| MyBatis-Plus / java-jwt / EasyExcel | 3.5.17 / 4.6.0 / 4.0.3 | Maven 检查确认的最新稳定直接依赖 |
| MySQL Server | 9.7.2 LTS | 最新 MySQL LTS 线；排除 26.7 Innovation |
| MySQL Connector/J | 26.7.0 GA | Connector 无 LTS 通道；采用官方 GA 且兼容受支持的 MySQL LTS Server |
| Redis | 8.2.9 Extended | 8.2 Extended 提供五年安全/关键修复；不把 Extended 误写成 LTS |
| Node.js / npm | 24.19.0 LTS / 12.0.2 stable | 固定 Node 24 LTS 主版本与 npm 12 stable 主版本 |
| Vue / Router / Pinia | 3.5.41 / 5.2.0 / 4.0.3 | npm 当前最新稳定直接依赖 |
| Vite / plugin-vue | 8.2.1 / 6.0.8 | npm 当前最新稳定构建工具 |
| Element Plus / icons | 2.14.4 / 2.3.2 | npm 当前最新稳定 UI 依赖 |
| Axios / ECharts | 1.19.0 / 6.1.0 | npm 当前最新稳定直接依赖 |
| Sass / unplugin-vue-components | 1.102.0 / 32.1.0 | npm 当前最新稳定开发依赖 |
| ESLint / eslint-plugin-vue / Knip / fast-check | 10.8.1 / 10.10.0 / 6.32.2 / 4.9.0 | npm 当前最新稳定质量门禁；均支持 Node 24 |
| Nginx | 1.30.4 stable | 采用 stable，排除 1.31 mainline |
| Go / gosu | 上游 stable 与 Docker builder 1.27.0 / 1.19 | 固定已发布的 Docker Official Image 与多架构摘要，不使用 RC、浮动标签或自行伪造标签 |
| Ubuntu / Alpine | 26.04 LTS / 3.24.1 stable | 后端最新 Ubuntu LTS；其余最小镜像使用最新 Alpine stable |
| Docker Compose | 5.5.0 | 本次构建与验收实际选中的稳定 CLI |

官方核验入口：[Java 路线图](https://www.oracle.com/java/technologies/java-se-support-roadmap.html)、[Spring Boot](https://spring.io/projects/spring-boot/)、[Maven 下载](https://maven.apache.org/download.cgi)、[Dependency-Check Releases](https://github.com/dependency-check/DependencyCheck/releases)、[Node.js 发布线](https://nodejs.org/en/about/previous-releases)、[MySQL 发布模型](https://dev.mysql.com/doc/refman/9.7/en/mysql-releases.html)、[Redis 版本管理](https://redis.io/docs/latest/operate/oss_and_stack/install/version-mgmt/)、[Nginx 下载](https://nginx.org/en/download.html)、[Go 下载](https://go.dev/dl/)、[Alpine 发布分支](https://alpinelinux.org/releases/) 和 [Ubuntu 26.04 说明](https://documentation.ubuntu.com/release-notes/26.04/)。

## 3. 仓库门禁

- `runtime-versions.json` 是机器可读的受验收版本基线，同时记录项目 SemVer 与 7 个基础镜像的 OCI 摘要。正常一键启动以 `-RuntimeOnly` 做离线的仓库固定版本、镜像摘要和运行兼容性校验，不因断网、官方站点故障或上游刚发布新补丁而阻断已验证版本。直接执行 `scripts/verify-version-policy.ps1 -Mode Docker` 或 `-Mode Local` 才会联网查询官方发布渠道，并严格报告最新稳定版漂移；该维护审计不会自动升级全局 Java、Node.js、npm、Maven、Docker 或数据库。
- 本地混合模式核对宿主机 Java/Javac/JAVA_HOME、Node/npm、Maven Wrapper、完整 npm 依赖树与本机 MySQL；全 Docker 模式核对 Docker Engine/Compose、Dockerfile/Compose 的构建与运行版本，以及相应官方镜像标签，宿主机不需要另装 Java、Node.js、npm、Maven 或 MySQL。
- Maven Wrapper 固定 Maven 3.9.16；Enforcer 要求 Java `[25,26)` 与 Maven `[3.9.16,4.0.0)`。
- Dependency-Check 固定 13.0.0；版本门禁查询 GitHub `releases/latest`，CI/Release 缓存同版本漏洞数据库，并由独立脚本执行有界更新与离线扫描。
- Spring Boot parent 固定 4.1.0；`mysql.version` 明确覆盖到 Connector/J 26.7.0 GA。
- 前端通过 `packageManager`、`engines`、`devEngines` 和 `engine-strict=true` 拒绝错误的 Node/npm 主版本；`package-lock.json` 固定精确依赖树。Docker 与 GitHub CI/Release 从 npm 官方 tarball 安装 npm，并先核对 `runtime-versions.json` 中的 SHA-256。
- ESLint 启用 Vue 3 essential 弃用规则并把警告视为失败；Knip 要求前端无不可达文件、未使用依赖和未使用导出；Element Plus 契约测试阻断已知弃用绑定；`fast-check` 以固定种子和可复现失败路径覆盖五类不可信输入边界。
- Maven Compiler 将 `unchecked`、`deprecation` 与 `removal` 警告视为失败；根验证脚本使用完整依赖 classpath 执行 JDK 25 `jdeprscan`。
- Dockerfile 固定 Temurin、Node、Nginx、Go、MySQL 与 Alpine 的标签和多架构 OCI SHA-256；Redis 源码与 npm 工具 tarball 另固定官方文件 SHA-256。
- MySQL 派生镜像只从与固定基础镜像匹配的 Oracle Linux 受支持仓库安装已确认的精确 `curl`/`libcurl` 修复版本；每次变更后必须重新验证数据库契约与最终镜像漏洞结果，不能用无界系统升级替代版本评审。
- 后端 Ubuntu 26.04 基础层自带但未使用的 Pebble helper 已删除，最终运行根文件系统压平，防止已删除的脆弱二进制残留在镜像历史层。

## 4. 项目版本与正式发布

- `runtime-versions.json` 的 `projectVersion`、后端 Maven 版本、前端 npm 版本、`CITATION.cff`、`CHANGELOG.md` 和 `docs/releases/vX.Y.Z.md` 必须一致；`scripts/verify-release-contract.ps1` 在每次 CI 中阻断漂移。
- 正式标签使用 `vMAJOR.MINOR.PATCH`，只允许指向受保护 `main` 的当前提交。预发布必须使用独立说明和显式 prerelease 标识，不能冒充稳定版本。
- `scripts/build-release.ps1` 从干净 Git 提交生成可执行 JAR、确定时间戳的前端 ZIP、精确源码 ZIP、发布清单、SPDX 2.3 SBOM 和 `SHA256SUMS`。
- Release 工作流固定所有 GitHub Actions 到完整提交 SHA，checkout 显式禁用凭据持久化，并为资产生成 GitHub Artifact Attestations。仓库 Actions 策略要求 SHA 固定，只允许 GitHub 官方 Action 与精确允许的 Scorecard/Gitleaks SHA。
- 仓库启用 Release immutability，`v*` 标签规则集禁止更新或删除；工作流先创建草稿、上传全部资产，再一次性发布。
- 已发布的不可变标签和资产不得移动或替换；修复使用新的补丁版本。完整门禁和回滚规则见[发布流程](RELEASE_PROCESS.md)。

## 5. 升级复核命令

```powershell
# 后端版本、测试与解析
Set-Location .\SourceCode\cecsmsServe-springboot
.\mvnw.cmd -B -ntp clean verify
.\mvnw.cmd -B -ntp versions:display-parent-updates versions:display-property-updates versions:display-plugin-updates versions:display-dependency-updates
Set-Location ..\..
.\scripts\invoke-dependency-check.ps1 -Mode All

# 前端直接依赖、测试、构建与审计
Set-Location ..\cecsmsui-vue
npm outdated --json
npm run check

# 容器构建、运行配置与最终镜像扫描
Set-Location ..\..
docker compose --env-file .env.docker --profile full config --quiet
docker compose --env-file .env.docker --profile full build --pull mysql redis backend frontend
docker scout cves --only-severity critical,high silverpilot-mysql:9.7.2
docker scout cves --only-severity critical,high silverpilot-redis:8.2.9
docker scout cves --only-severity critical,high silverpilot-backend:latest
docker scout cves --only-severity critical,high silverpilot-frontend:latest
```

版本数字更新后，必须同时通过后端全量测试、前端 `check`、Compose 健康检查、同源通信矩阵与四个最终镜像复扫。仅看到“有新版本”不构成升级完成。
