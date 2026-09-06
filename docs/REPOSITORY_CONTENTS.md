# 仓库内容与本机文件

本仓库包含项目源码、数据库种子、前后端与容器配置、脚本、测试、知识库、评测数据、项目文档和静态媒体。

2026-09-07 根据维护者要求补齐原先被忽略的项目内容：

| 目录或文件 | 内容 |
| --- | --- |
| `image/` | 全部 202 个图片文件，即 101 张原图及 101 张 WebP；种子使用其中 48 组 |
| `file/文档/` | 全部 27 份原始文档，包括 10 份 DOCX 和 17 份 PDF |
| `video/.gitkeep` | 保留当前为空的视频资源目录 |
| `SourceCode/cecsmsServe-springboot/config/dependency-check-suppressions.xml` | Maven 引用的共享扫描配置；后续安全升级已移除旧 Tomcat 临时例外，当前为空，详见 [Java SCA 记录](JAVA_DEPENDENCY_CHECK.md) |

图片和文档按照原始路径及字节内容提交，以便 Git 检出后恢复完整项目资源。中文文件名也纳入发布检查。现有源码、文档、界面验证资料和 `.codebuddy` 项目技能继续随仓库跟踪。

下列内容属于本机运行状态或可重新生成的数据，不纳入源码仓库：

- `.git/`、IDE 工作区状态和编辑器缓存；
- `node_modules/`、`target/`、`dist/`、覆盖率和编译产物；
- `.dependency-check-data/` 等扫描数据库、运行日志及 `backups/`；
- `.env.docker`、其 DPAPI 备份、`application-host.properties` 等包含运行凭据或机器配置的文件。

环境文件使用仓库中的示例和启动脚本在本机生成。数据库初始化使用 `database/a_old.sql`。运行中产生的文件上传存放于配置的上传目录或 Docker 数据卷，提交前仍需检查其中是否含真实个人数据或凭据。

资源完整与漏洞检查是独立的验证：补齐图片可以修复全新检出时的资源缺失；依赖漏洞检查仍以对应提交的 GitHub CI 结果为准。
