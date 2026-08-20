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

2026-08-20 的冷缓存更新用时约 1 分 53 秒，离线扫描约 10 秒。官方 13.0.0 发布后，旧版本缓存先被版本门禁拒绝，再在约 28 秒内完成同一官方镜像的数据刷新；13.0.0 离线复扫仍约 10 秒。初次扫描真实拦截了 Tomcat 11.0.22、Netty 4.2.15.Final，以及旧 POI、Commons Compress、Jackson 和 Log4j 传递依赖。项目已升级到 Tomcat 11.0.24、Netty 4.2.17.Final、POI 5.5.1、Commons Compress 1.28.0、Jackson 2.21.5 和 Log4j 2.25.5，并移除未使用的 Tomcat WebSocket 运行时。后端 84/84 测试通过后，最终报告为 82 个依赖、0 个受影响依赖、0 个未抑制漏洞。

`CVE-2026-66299` 仅影响完整 Tomcat 发行包中的 WebSocket chat 示例；Apache 明确说明未部署 examples 应用的用户不受影响。Spring Boot 的 `tomcat-embed-core` 11.0.24 JAR 已核对为 1,611 个条目、0 个 examples/chat 条目。由于修复版 11.0.25 尚未发布，仓库保留一条仅匹配该精确 PURL/CVE、于 2026-10-01 到期的规则；`failBuildOnUnusedSuppressionRule=true` 会在升级或匹配漂移时阻断构建，不能掩盖其他发现。

## 边界与维护

- 镜像和 NVD 都是外部数据源；本方案保证等待有上限、扫描可离线复现、失败不伪装成通过，但不承诺第三方永久在线。
- OWASP 镜像是每日尽力更新，不是实时漏洞情报。CI 每日尝试刷新，最长离线回退窗口为 168 小时。
- 每次 Dependency-Check 正式版变化都必须同步 `runtime-versions.json`、POM、CI 缓存键，并重新执行完整扫描。
- Tomcat 11.0.25 正式发布后应立即升级并删除临时 suppression；到期或未使用规则会让门禁失败。
- Dependency-Check 是尽力而为的识别工具，0 个发现不等于未来或所有分析器中绝对没有漏洞。

官方依据：[OWASP 数据源与缓存](https://dependency-check.github.io/DependencyCheck/data/index.html)、[NVD 镜像配置](https://dependency-check.github.io/DependencyCheck/data/mirrornvd.html)、[H2 缓存模式](https://dependency-check.github.io/DependencyCheck/data/cacheh2.html)、[suppression 规则](https://dependency-check.github.io/DependencyCheck/general/suppression.html)、[Tomcat 11 安全公告](https://tomcat.apache.org/security-11.html)。
