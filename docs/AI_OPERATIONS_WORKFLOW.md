# AI 运营问题闭环与指标口径

本版本复用 Agent 运行台，增加匿名运营报告、待确认动作结果和人工问题闭环。所有管理接口由 JWT 中经过数据库核验的管理员身份控制；客户端不能自报负责人或越权读取。

## 使用

管理员进入“小伴运行”页面，选择 7/14/30 天：

1. 从最近运行中选取问题对应的记录；选择知识缺失、表达不清、工具错误、确认体验或规划问题，以及 P1/P2/P3 优先级。
2. 问题先进入待分诊，分诊后由当前管理员负责。
3. 修复完成时填写 `commit:abcdef0` 或 `issue:123` 引用并标记待复测。
4. 按复测标准实际执行测试，保存报告，填写 `eval:case-001` 等记录编号，并确认人工复测通过后关闭。
5. 复测不通过或问题复发时重新进入分诊。历史流转记录保留，当前修复和复测结论清空。

引用是操作者提交的追溯编号，不是系统自动读取并核验过的外部报告。请将对应证据与版本一同保存。界面不接受原始问题正文，避免健康内容、联系方式和提示词进入运营表。

## 指标

- **运行成功率**：`(COMPLETED + WAITING_CONFIRMATION) / sampledRuns`。降级、失败及未知状态均不计成功；此指标不等于回答准确率、用户任务完成率或预约成功率。
- **运行样本范围**：所选最近 1–30 天中，按记录 ID 倒序最多 5,000 条。查询额外一条判断截断，截断时页面和导出均提示。人数、任务数和运行数不等价。
- **耗时分位数**：按毫秒排序，使用 nearest-rank P50/P95；空样本的 API 数值保留 0 以兼容调用方，页面显示“—”；空样本成功率显示“暂无数据”。
- **按引擎统计**：仍为描述性运行统计。不同引擎可能处理不同任务，不构成配对评测或 A/B 实验。
- **办理结果**：只统计 `requires_confirmation=1` 且创建时间属于所选周期的动作。只有 `SUCCEEDED` 是后端执行成功；待确认、执行中、失败、主动取消、到期分别计数。已过 TTL 的 PENDING 在报表中归入 EXPIRED，查询不会修改历史记录。当前动作没有来源 run_id，不能按模型归因。
- **问题验证关闭率**：同一创建时间批次中，当前 VERIFIED 数量 / 该批全部已登记问题。空批次返回 null；新近问题尚未经历完整观察期。重新打开的问题不计已关闭。

运行、动作、反馈采用不同记录粒度，不能把这三个总量拼成同一个用户漏斗。完整用户任务完成率仍需任务标识和用户测试，当前版本不宣称增长或真实用户改善。

## API

- `GET /chat/admin/overview?days=7`：现有受保护运行台，包含运行标识，仅供管理员界面。
- `GET /chat/admin/operations?days=7`：反馈列表（最近 100 条及截断标记）、全周期反馈汇总、动作结果。
- `POST /chat/admin/operations/feedback`：`runId/category/priority`；运行必须存在，类别和优先级采用固定枚举。
- `POST /chat/admin/operations/feedback/{id}/transition`：`version/status/changeRef/evidenceRef/verificationPassed`；过期版本返回 409，禁止跳过分诊和修复阶段。
- `GET /chat/admin/operations/export?days=7`：供 CatPaw/离线分析使用的匿名聚合 JSON，schemaVersion 为 `silverpilot-operations-v1`。不包含用户、运行、动作、反馈编号或修复引用。管理员点击“导出匿名运营报告”即可下载。

导出文件含 `totalRuns/runSuccessRate/completedRuns/waitingConfirmationRuns/degradedRuns/failedRuns/p50LatencyMs/p95LatencyMs/totalTokens/truncated/sampleLimit/providerMetrics/feedbackSummary/actionOutcomes` 等字段及口径说明。只有匿名聚合导出适合作为运营技能的输入；不要导出管理员原始 overview 代替。

## 存储与验证

`schema.sql` 在旧数据库启动时幂等创建 `agent_feedback` 和 `agent_feedback_event` 两张空表；不会重建业务表。状态更新使用版本条件防止并发覆盖，状态更新与追加事件在同一事务中完成。应用没有修改或删除历史事件的接口。

新增自动检查覆盖：无效运行/类别、不能跳级关闭、人工复测声明与证据编号、冲突版本、重新打开后的结论清除、写入与只读动作区分、到期归类、列表截断与全量分母、管理员授权、匿名导出字段、成功率口径与分位数。具体执行结果以本次验证记录为准。

### 可选浏览器回归（合成数据）

`scripts/test/operations-ui.smoke.cjs` 使用 Playwright 检查空样本展示、登记问题、分诊、修复、人工复测关闭、匿名报告下载和手机页面宽度。它拦截全部 `/api/` 请求并返回合成样本，不连接真实后端或模型，也不能证明后端鉴权或真实用户效果。截图文件名包含 `synthetic`，应保留这一标识。

前置条件：前端已安装依赖，另有可选 Playwright 环境。它不是生产依赖，不需要修改项目 package.json。先在单独终端从前端目录启动用于测试的 Vite：

```powershell
$env:SILVERPILOT_SKIP_BROWSER = '1'
node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5177 --strictPort
```

随后从仓库根目录运行：

```powershell
npm exec --yes --package=playwright -- node scripts/test/operations-ui.smoke.cjs
```

也可以将 `NODE_PATH` 指向已有工具环境的 `node_modules`，然后直接 `node scripts/test/operations-ui.smoke.cjs`。脚本优先使用已安装的 Windows Edge；其他环境可通过 `SILVERPILOT_UI_BROWSER_PATH` 指定浏览器可执行文件，或先执行 `npm exec --yes --package=playwright -- playwright install chromium` 安装测试浏览器。

可选变量：`SILVERPILOT_UI_BASE_URL`（默认 `http://127.0.0.1:5177`）、`SILVERPILOT_UI_OUTPUT_DIR`（默认系统临时目录下 `silverpilot-operations-ui-smoke`）。输出桌面/手机合成数据截图与控制台验证结果。测试结束后关闭专用 Vite 进程。
