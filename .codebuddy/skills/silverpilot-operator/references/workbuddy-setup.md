# WorkBuddy binding

1. Start the backend and verify `/actuator/health`.
2. Store a dedicated random value in `CECSMS_MCP_API_KEY`; never reuse a model key or paste it into the repository.
3. For same-machine local testing, create a custom Streamable HTTP connector with URL `http://127.0.0.1:8083/mcp`, and inject the secret as `X-CECSMS-MCP-Key` or `Authorization: Bearer ...`.
4. Run `scripts/mcp-smoke.ps1`, then verify six read-only tools in WorkBuddy Test Run.
5. Import `knowledge-base/ima-ready/*.md` into a dedicated ima knowledge base and bind it natively. Keep document IDs and replace the complete package on update.
6. Import or copy `workbuddy/agent-manifest.json`, attach the `silverpilot-operator` skill, connector, and ima knowledge base, then Test Run before publishing.

The connector intentionally excludes personal health data and all business writes. WorkBuddy may edit the workspace through the skill, but every code change still requires project gates.

`127.0.0.1` is not a cloud deployment address. Before publishing the connector in a managed WorkBuddy environment, deploy only the backend behind an authenticated HTTPS gateway, change the URL to `https://your-domain.example/mcp`, restrict ingress, rotate the MCP key, and rerun the smoke test. Never expose MySQL or the administration UI directly.
