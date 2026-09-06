# Java 依赖漏洞扫描

SilverPilot 的 Java SCA 不再让每次构建直接等待 NVD REST API。项目使用 OWASP Dependency-Check 13.0.0、OWASP 维护的 NVD JSON 2.0 镜像、仓库级持久 H2 缓存和强制离线扫描，把“远程数据刷新”和“本地依赖判定”拆成两个有界阶段。

## 运行模型

1. 更新阶段只从 `https://dependency-check.github.io/DependencyCheck_Builder/nvd_cache/nvdcve-{0}.json.gz` 刷新 `.dependency-check-data`，默认硬超时 15 分钟。
2. 成功后写入 UTC 时间、Dependency-Check 版本、镜像地址和 H2 数据库 SHA-256。没有匹配标记、哈希不符、版本不符或超过 168 小时的缓存都不能扫描。
3. 扫描阶段固定 `autoUpdate=false`，默认硬超时 10 分钟，因此不会在 Maven `check` 中再次访问 NVD 或无限等待更新。
4. 刷新失败时，脚本只会恢复并使用刷新前、哈希已验证且不超过 168 小时的缓存；没有合格缓存就明确失败。
5. CI 和 Release 以扫描器版本及 UTC 日期缓存 `.dependency-check-data`，每日生成新缓存键，并允许恢复最近的同版本缓存。

`.dependency-check-data`、Maven 日志和原始报告都不进入发布源码。报告写入 `SourceCode/cecsmsServe-springboot/target/dependency-check`，包括 HTML、JSON、SARIF 和 JUnit。

## 命令

```powershell
# 推荐：需要时刷新，然后从缓存离线扫描
.\scripts\invoke-dependency-check.ps1 -Mode All

# 只刷新镜像缓存
.\scripts\invoke-dependency-check.ps1 -Mode Update

# 完全离线扫描；缓存无效时拒绝运行
.\scripts\invoke-dependency-check.ps1 -Mode Scan

# 查看缓存是否可用，状态不可用时返回非零
.\scripts\invoke-dependency-check.ps1 -Mode Status
```

`pom.xml` 中的 `dependency-check` profile 隔离扫描插件及配置，并把离线 `check` 绑定到 `verify`，不会隐式更新数据，也不会拖慢日常应用或容器构建：

```powershell
Set-Location .\SourceCode\cecsmsServe-springboot
.\mvnw.cmd -B -ntp -Pdependency-check verify
```

直接运行 profile 前必须先用根脚本准备有效缓存。日常 `clean verify` 不重复执行耗时 SCA，CI/Release 使用独立、明确超时的 Java SCA 作业。

## 当前漏洞处置

2026-09-07 核对 [GitHub 失败报告](https://github.com/LeiQingliang/silverpilot/actions/runs/34062887630)，原依赖树有 18 个 CVSS 7.0+ 条目触发门禁，集中在以下三组依赖。评分来自当次扫描数据，不代表已经确认项目具备每个漏洞的利用条件。

| 依赖 | 原版本 | 修复版本 | 原 CVSS 7.0+ 条目数 |
| --- | --- | --- | --- |
| Spring Framework | 7.0.8 | 7.0.9 | 7 |
| Spring Security | 7.1.0 | 7.1.1 | 2 |
| Tomcat embed core / EL | 11.0.24 | 11.0.25 | 9（core） |

父项目升级为 [Spring Boot 4.1.1 正式版](https://spring.io/blog/2026/08/20/spring-boot-4-1-1-available-now/)，由其 BOM 统一管理 Spring Framework 与 Spring Security。该 BOM 仍固定 Tomcat 11.0.24，因此保留单独的 `tomcat.version` 安全覆盖，将 core 与 EL 一致升级到 11.0.25。版本已与 Maven Central 的正式发布元数据及最终可执行 JAR 中的实际依赖核对。

官方修复依据：[Spring Framework 公告](https://spring.io/security/cve-2026-59313/)、[Spring Security 公告](https://spring.io/security/cve-2026-59270/)、[Spring Security WebAuthn 公告](https://spring.io/security/cve-2026-47841/)、[Tomcat 11.0.25 安全修复](https://tomcat.apache.org/security-11.html#Fixed_in_Apache_Tomcat_11.0.25)。

Tomcat 11.0.25 同时修复 `CVE-2026-66299`，原来仅针对 11.0.24 examples 的临时例外已删除。共享 suppression 文件保留为空，发布契约拒绝重新加入规则；`failBuildOnCVSS=7.0`、`failOnError=true` 和 `failBuildOnUnusedSuppressionRule=true` 继续启用。

本地 `verify-project.ps1` 已通过后端 88 项、前端 58 项测试、5 组属性测试、静态检查、构建、JDK API 扫描和前端审计。后端包含真实随机端口 Tomcat HTTP 请求，覆盖错误响应和静态资源读取；本次未重新构建或启动 Docker 运行栈。

2026-09-07 以 `invoke-dependency-check.ps1 -Mode All -ForceUpdate` 从官方镜像重新刷新数据库，再执行离线复扫。报告时间为 `2026-09-06T22:19:45Z`（北京时间 2026-09-07），共 103 个依赖、0 个 CVSS 7.0+ 条目，Maven 返回 `BUILD SUCCESS`；Spring 与 Tomcat 原有失败项均不再命中，项目级 suppression 为 0 条。

报告仍保留 `CVE-2023-0833`（CVSS 5.5 / MEDIUM），分别命中 Ark SDK 引入的 `logging-interceptor:2.7.5`、`okhttp:2.7.5` 与 `okhttp3:3.14.9`，即 1 个漏洞编号、3 个依赖条目。它没有被隐藏或新增例外，也不触发既定的 7.0 门禁；Ark SDK / OkHttp 跨版本迁移和该条目的适用性需要另行核查。本次修复的是导致 CI 失败的依赖条目，不将扫描通过描述为“所有漏洞清零”。

2026-08-20 的 82 个依赖、0 个未抑制发现只属于历史扫描快照，不能代替升级后的复扫结果。

## 边界与维护

- 镜像和 NVD 都是外部数据源；本方案保证等待有上限、扫描可离线复现、失败不伪装成通过，但不承诺第三方永久在线。
- OWASP 镜像是每日尽力更新，不是实时漏洞情报。CI 每日尝试刷新，最长离线回退窗口为 168 小时。
- 每次 Dependency-Check 正式版变化都必须同步 `runtime-versions.json`、POM、CI 缓存键，并重新执行完整扫描。
- 当前没有项目级 suppression。新增例外必须单独评审其适用范围、精确版本、漏洞编号和到期日，并同步发布契约；优先升级到正式修复版。
- Dependency-Check 是尽力而为的识别工具，0 个发现不等于未来或所有分析器中绝对没有漏洞。

官方依据：[OWASP 数据源与缓存](https://dependency-check.github.io/DependencyCheck/data/index.html)、[NVD 镜像配置](https://dependency-check.github.io/DependencyCheck/data/mirrornvd.html)、[H2 缓存模式](https://dependency-check.github.io/DependencyCheck/data/cacheh2.html)、[suppression 规则](https://dependency-check.github.io/DependencyCheck/general/suppression.html)、[Tomcat 11 安全公告](https://tomcat.apache.org/security-11.html)。
