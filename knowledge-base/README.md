# SilverPilot 知识库治理

[返回项目主页](../README.md) · [AI Harness](../docs/AI_HARNESS.md) · [Agent 评测集](../evaluation/README.md)

本页面面向知识维护者、Agent 开发者和审核人员。目标是让每条运行时知识都有责任人、版本、适用边界和复核日期，并能在变更后通过结构与行为回归。

除非另有说明，本文 PowerShell 命令均从项目根目录执行。

`ima-ready` 是本地 Agent RAG 与 ima 导入共用的单一知识源。当前目录与 `manifest.json` 一致，共 9 篇 Markdown：1 篇 `governance` 治理规范和 8 篇运行时 `approved` 文档。后端只索引 `status: approved`，不会把治理规范当作业务答案来源。

文档快照：2026-08-20 已重新核对目录、manifest、frontmatter 契约和后端索引边界，结构门禁结果为 `9 documents, 8 approved`。这次 README 更新不等于对知识内容做了新一轮专业复核，因此 manifest 与各文档的 `reviewed_at: 2026-08-18` 保持不变。

## 目录与状态模型

```text
knowledge-base/
├─ README.md          # 本治理入口
├─ manifest.json      # 文档清单、版本和运行时纳入范围
└─ ima-ready/
   ├─ 00-知识库治理规范.md
   └─ 01...08-*.md    # 当前 approved 的运行时知识
```

| 状态 | 是否进入后端检索 | 用途 |
| --- | ---: | --- |
| `draft` | 否 | 编辑、讨论和等待专业复核 |
| `approved` | 是 | 已按当前流程批准，可被 Agent 引用 |
| `governance` | 否 | 规则、模板与维护说明，不作为业务答案 |

状态只表达治理流程，不等于医学、法律或监管认证。任何过期、来源不明或超出 scope 的内容都应先降为 `draft`，而不是继续依赖模型自行判断。

## 文档门禁

每篇 Markdown 使用 frontmatter 声明：

```yaml
---
title: 文档标题
document_id: KB-DOMAIN-001
version: 1.0.0
owner: qinglianglei
status: approved
reviewed_at: 2026-08-18
next_review_at: 2026-11-18
scope: 适用范围
source_type: internal-policy
---
```

必填字段以 `scripts/validate-knowledge-base.ps1` 为唯一机器契约；日期使用 `yyyy-MM-dd`，状态只允许 `approved`、`draft` 或 `governance`，`document_id` 必须唯一。只有 `approved` 会进入本地检索。修改知识前必须：

1. 明确来源、owner、适用场景和不适用边界；
2. 不写入真实个人健康数据、凭据、客户信息或未经核验的商业数字；
3. 养老服务流程与健康安全文档由相应专业人员复核后再用于真实业务；
4. 提升版本、更新时间与下次复核时间；
5. 运行验证脚本。

```powershell
.\scripts\validate-knowledge-base.ps1
```

验证覆盖 9 个必填 metadata 字段、manifest 数量与路径、唯一文档 ID、过期日期、重复内容、失效本地链接和常见秘密模式。当前通过结果应为 `9 documents, 8 approved`。通过脚本仅说明知识包结构与治理契约合格，不代表医疗内容经过临床验证。

后端默认从 `../../knowledge-base/ima-ready` 读取这些文档，可用 `CECSMS_KNOWLEDGE_BASE_PATH` 覆盖；状态接口会报告知识库是否 ready。知识改动后除本脚本外，还应运行[评测集](../evaluation/README.md)中的来源与安全契约用例。

## 标准变更流程

1. **提出变更**：记录问题、来源、目标读者、适用/不适用范围和风险级别。
2. **先以 `draft` 编辑**：保留稳定 `document_id`；语义变化更新 `version`，不要靠改文件名规避历史。
3. **专业复核**：养老流程、健康安全、隐私或合规内容交由相应专业人员确认，并更新 `owner`、`reviewed_at`、`next_review_at`。
4. **更新 manifest**：路径、版本、状态与文件 frontmatter 一致。
5. **结构门禁**：运行 `validate-knowledge-base.ps1`，修复过期、重复、链接和秘密模式问题。
6. **行为回归**：重启或刷新后端索引，检查 `/chat/status`，再运行知识来源、拒答和安全分流评测。
7. **同步 ima**：仅导入当前 `approved` 文件，记录知识库版本和导入时间。

删除或重命名文档时还要验证旧引用是否失效、运行时索引是否删除旧内容，以及 ima 是否同步删除；只上传新文件可能留下相互冲突的旧版本。

## 导入 ima

1. 先运行验证；
2. 在 ima 中创建专用知识库，名称建议包含版本；
3. 仅导入 `ima-ready` 下 8 篇当前 `approved` 文件，不导入 `00-知识库治理规范.md`；
4. 抽查产品边界、写操作确认、健康紧急分流和服务 SOP；
5. 在 WorkBuddy 中绑定该知识库，并明确回答必须保留来源/不确定性；
6. 知识变更后重新导入或按 ima 客户端支持的同步方式更新，并记录版本。

本项目不假设 ima 存在公开自动同步 API。若未来客户端支持自动化，应在不上传敏感内容的前提下新增单独适配器和验收测试。

## 何时升级为向量检索

当前 8 篇运行文档用可解释词项检索已足够。只有在文档数量、同义表达召回或跨段语义需求经评测证明不足时，才引入 embedding/vector store；届时需要增加切分版本、embedding 模型版本、权限过滤、召回评测、删除同步和成本监控。更换检索实现前要保留当前词项检索作为可比较基线，不能只凭“用了向量库”宣称效果提升。

## 常见失败

| 现象 | 检查项 |
| --- | --- |
| 验证报告 manifest 数量不一致 | 新增、删除或重命名文件后是否同步更新 `manifest.json` |
| 后端显示知识库未就绪 | `CECSMS_KNOWLEDGE_BASE_PATH`、容器只读挂载、frontmatter 状态和启动日志 |
| 搜索不到新内容 | 文档是否仍为 `draft`、后端是否重新加载、查询词是否落在 scope 内 |
| 回答没有 `[KB:...]` 来源 | 工具是否调用 `search_knowledge_base`、来源字段是否在响应链路中保留 |
| ima 与本地回答不一致 | 两端导入版本、旧文档删除状态、approved 清单和更新时间 |
| 复核日期已过 | 先暂停运行时使用并安排 owner 复核，不要只把日期顺延 |

事实源分别是 [`manifest.json`](manifest.json)、`ima-ready` frontmatter、[`validate-knowledge-base.ps1`](../scripts/validate-knowledge-base.ps1) 和后端知识状态接口。README 只解释流程，不替代这些机器契约。
