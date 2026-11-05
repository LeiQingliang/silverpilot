# SilverPilot 知识库治理

[返回项目主页](../README.md) · [AI Harness](../docs/AI_HARNESS.md) · [Agent 评测集](../evaluation/README.md)

本页面面向知识维护者、Agent 开发者和审核人员。目标是让每条运行时知识都有责任人、版本、适用边界和复核日期，并能在变更后通过结构与行为回归。

除非另有说明，本文 PowerShell 命令均从项目根目录执行。

`ima-ready` 是本地 Agent RAG 与 ima 导入共用的单一知识源。2026-11-06 的目录与 `manifest.json` 一致，共 9 篇 Markdown：1 篇 `governance` 治理规范、7 篇 `approved` 文档和 1 篇隔离的 `draft`。后端只索引 `status: approved` 且审核有效的文档，不把治理规范或草稿当作业务答案来源。数量随审核有效期变化，以状态接口为准。

历史快照：2026-08-20 的结构门禁结果为 `9 documents, 8 approved`，保留作历史记录。2026-11-06 将已于 2026-10-18 到期的 `KB-HEALTH-001` 改为 `draft`，并补齐运行时日期过滤；此为工程治理维护，不代表新的医疗专业复核，manifest 的 `reviewedAt` 与文档原有 `reviewed_at` 保持不变。

## 目录与状态模型

```text
knowledge-base/
├─ README.md          # 本治理入口
├─ manifest.json      # 文档清单、版本和运行时纳入范围
└─ ima-ready/
   ├─ 00-知识库治理规范.md
   └─ 01...08-*.md    # 按 frontmatter 和审核有效期选择；04 当前为 draft
```

| 状态 | 是否进入后端检索 | 用途 |
| --- | ---: | --- |
| `draft` | 否 | 编辑、隔离和等待专业复核；过期可保留并产生警告 |
| `approved` | 审核日期有效时 | 已按当前流程批准，可被 Agent 引用；过期阻断门禁 |
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

必填字段以 `scripts/validate-knowledge-base.ps1` 为机器契约；日期使用真实有效的 `yyyy-MM-dd`，状态只允许 `approved`、`draft` 或 `governance`，`document_id` 必须唯一。后端按 Asia/Shanghai 日期执行 `reviewed_at ≤ 今天 ≤ next_review_at`，且仅接纳 `approved`；缺失、重复、非法或前后矛盾的日期均拒绝进入检索。到期日当天仍有效，次日零点起失效，缓存命中也重新过滤。修改知识前必须：

1. 明确来源、owner、适用场景和不适用边界；
2. 不写入真实个人健康数据、凭据、客户信息或未经核验的商业数字；
3. 养老服务流程与健康安全文档由相应专业人员复核后再用于真实业务；
4. 记录实际变更版本；只有完成真实复核后才更新审核时间与下次复核时间；
5. 运行验证脚本。

```powershell
.\scripts\validate-knowledge-base.ps1
```

验证覆盖 9 个必填 metadata 字段、manifest 一一对应路径、唯一文档 ID、日期合法性与先后关系、重复内容、失效本地链接和常见秘密模式。2026-11-06 的状态为 `9 documents, 7 approved, 1 draft (excluded)`，并针对健康文档报告待复核警告。`approved` 或 `governance` 过期仍失败；`draft` 可保留历史过期日期，但不能缺失/伪造日期。通过脚本仅说明知识包结构与治理契约合格，不代表医疗内容经过临床验证。

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
3. 仅导入 `ima-ready` 下当前 `approved` 且审核有效的文件；2026-11-06 为 01、02、03、05、06、07、08 共 7 篇，不导入 00 治理规范和 04 健康草稿；
4. 从外部 ima 库移除过期的 04 文件并验证删除结果，再抽查产品边界、写操作确认和服务 SOP；外部删除状态需要实际连接后核验，仓库修改不代表已同步；
5. 在 WorkBuddy 中绑定该知识库，并明确回答必须保留来源/不确定性；
6. 知识变更后重新导入或按 ima 客户端支持的同步方式更新，并记录版本。

本项目不假设 ima 存在公开自动同步 API。若未来客户端支持自动化，应在不上传敏感内容的前提下新增单独适配器和验收测试。

## 何时升级为向量检索

当前小规模知识包保留可解释词项检索作为基线。只有在文档数量、同义表达召回或跨段语义需求经评测证明不足时，才引入 embedding/vector store；届时需要增加切分版本、embedding 模型版本、权限过滤、召回评测、删除同步和成本监控。更换检索实现前要保留当前词项检索作为可比较基线，不能只凭“用了向量库”宣称效果提升。

## 待完成的专业复核

`KB-HEALTH-001` 的责任人需安排专业人员核验来源、健康边界与紧急分流措辞，记录复核人、依据、结论和实际日期，确定下一次复核期限，升级文档版本后再恢复 `approved`。该工作未由本次代码维护完成。外部 ima 中旧副本是否已删除也尚待实际核验。原有 Prompt/后端安全拦截仍是独立防线，不能用它们代替文档专业复核。

其他文档的下次复核日期为 2026-11-18；到期前应安排维护，不能因本次修复而默认续期。历史评测报告保持原样，新增 Provider 或知识版本的结果必须重新运行并记录。

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
