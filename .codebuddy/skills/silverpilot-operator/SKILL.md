---
name: silverpilot-operator
description: Operate, audit, test, and safely extend the SilverPilot/CECSMS elderly-care Agent project. Use for knowledge-base curation and ima imports, Agent capability checks, WorkBuddy MCP operations, AI operations reports, frontend/backend changes, evaluation, release verification, or evidence-backed product and sales-demo improvements in this workspace.
---

# SilverPilot Operator

Operate this repository as a production-style Agent product. Preserve real tool execution, user ownership, human confirmation, auditability, privacy, and evidence boundaries.

## Route the task

- For knowledge or ima work, read `references/knowledge-governance.md` and run `scripts/audit-silverpilot.ps1` before and after edits.
- For backend, frontend, MCP, or provider changes, read `references/architecture.md` before editing.
- For portfolio, sales demo, KPI, or interview outputs, read `references/evidence-policy.md` and distinguish implemented evidence from roadmap or simulation.
- For first-time WorkBuddy binding, read `references/workbuddy-setup.md`; never place credentials in repository files.

## Execute safely

1. Restate the outcome as verifiable acceptance criteria.
2. Inspect current files and live status; do not infer that optional providers or external bindings are online.
3. Classify changes as documentation, read-only capability, or business mutation.
4. Keep business mutations behind JWT ownership checks, a persisted `PENDING` action, explicit user confirmation, idempotency, and audit history.
5. Keep MCP read-only. Do not expose personal reports, order ownership, confirmation tokens, or direct mutation tools through WorkBuddy.
6. Add or update tests and observability whenever behavior changes.
7. Run the relevant gates and report exact results. Never call an untested integration complete.

## Standard gates

- Fast knowledge and structure gate: `powershell -ExecutionPolicy Bypass -File .codebuddy/skills/silverpilot-operator/scripts/audit-silverpilot.ps1`
- Full project gate: add `-Full` to the command above.
- Live MCP gate, after the backend is running and the secret is in the process environment: `powershell -ExecutionPolicy Bypass -File scripts/mcp-smoke.ps1`

## Completion report

State the outcome, changed surfaces, safety effect, tests run, live integrations verified, optional credentials still required, and exact claims defensible in a portfolio. Separate roadmap ideas from implemented behavior.
