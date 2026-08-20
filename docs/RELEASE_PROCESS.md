# Release process

SilverPilot 使用 Semantic Versioning、受保护的 `main`、不可变 GitHub Release、SHA-256 校验和与 GitHub Artifact Attestations。正式发布只标记已经通过 Pull Request 和全部必需检查的当前 `main` 提交。

## 发布前门禁

1. 同步 `runtime-versions.json` 的 `projectVersion`、后端 `pom.xml`、前端 `package.json`、`CITATION.cff`、`CHANGELOG.md` 与 `docs/releases/vX.Y.Z.md`。
2. 确保所有非 `scratch` Docker 基础镜像同时固定可读标签与多架构 OCI 摘要，npm 官方 tarball 固定 SHA-256；所有远程 Action 使用完整提交 SHA，checkout 禁用凭据持久化。
3. 执行：

   ```powershell
   .\scripts\verify-publication-safety.ps1
   .\scripts\verify-project.ps1
   .\scripts\build-release.ps1 -Tag vX.Y.Z -IncludeRepositorySbom
   ```

4. 从独立分支提交 Pull Request；只有必需检查全部成功后才能合并。
5. 复核 `main` 的提交 SHA、工作树、CodeQL、Gitleaks、依赖审查和数据库契约，不从未审查分支创建标签。

## 发布

仓库管理员先确认 GitHub Release immutability 已启用，Actions 策略只允许已审核来源并要求 SHA 固定，且活动规则集 `Protect version tags` 对 `refs/tags/v*` 禁止更新和删除。随后为当前 `main` 创建并推送带说明的 SemVer 标签：

```powershell
git switch main
git pull --ff-only origin main
git tag -a vX.Y.Z -m "SilverPilot vX.Y.Z"
git push origin vX.Y.Z
```

标签触发 [Release workflow](../.github/workflows/release.yml)。工作流会再次运行源码与发布门禁，构建后端 JAR、前端静态包和精确源码包，导出 SPDX 2.3 SBOM，生成 `SHA256SUMS`，签发构建来源证明，然后遵循 GitHub 推荐流程“草稿 → 附件 → 发布”。发布后标签与资产不可移动、替换或删除。

GitHub 官方参考：[管理 Release](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)、[不可变 Release](https://docs.github.com/en/code-security/concepts/supply-chain-security/immutable-releases)、[验证 Release 完整性](https://docs.github.com/en/code-security/how-tos/secure-your-supply-chain/secure-your-dependencies/verify-release-integrity)。

## 独立验证

```powershell
gh release verify vX.Y.Z --repo LeiQingliang/silverpilot
gh release download vX.Y.Z --repo LeiQingliang/silverpilot --dir .\release-download
Set-Location .\release-download
Get-Content .\SHA256SUMS
Get-ChildItem -File | Where-Object Name -ne SHA256SUMS | Get-FileHash -Algorithm SHA256
gh attestation verify .\silverpilot-X.Y.Z-backend.jar `
  --repo LeiQingliang/silverpilot `
  --signer-workflow LeiQingliang/silverpilot/.github/workflows/release.yml
```

校验时应同时确认 Release 指向的提交与本地标签、`main` 和发布清单中的 40 位提交 SHA 一致。

## 修复与回滚

不可变 Release 不做标签移动或资产替换。发现发布错误时，先停止使用受影响版本，记录影响和回滚条件，再从最后一个已验证标签恢复；修复通过正常 Pull Request 后发布新的补丁版本。数据库回滚必须单独验证 schema 与数据备份，不能因为应用版本回退而自动删除命名卷或业务数据。
