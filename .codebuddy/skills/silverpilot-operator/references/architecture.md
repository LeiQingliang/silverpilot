# Architecture boundaries

- Backend: `SourceCode/cecsmsServe-springboot`, Spring Boot, entry point `com.cecsmsserve.CecsmsServeApplication`, port 8083 by default.
- Frontend: `SourceCode/cecsmsui-vue`, Vue/Vite, port 8081 by default.
- Business tools: `ToolExecutor`; model schemas: `Tools`; orchestration: `ChatController`.
- Mutations: `AgentActionService` and `agent_action`; never bypass confirmation or ownership validation.
- Run observability: `AgentRunService` and `agent_run`; do not persist prompts or images.
- Knowledge retrieval: `KnowledgeBaseService` reads approved Markdown from `knowledge-base/ima-ready`.
- Model routing: `AiProviderService`; DeepSeek is text/tool capable, Doubao is the configured vision route.
- WorkBuddy: `/mcp` is stateless Streamable HTTP and exposes only public read tools.

Configuration lives in environment variables. Never write model keys, JWT secrets, database passwords, MCP keys, confirmation tokens, or user health data to source files, logs, examples, or knowledge documents.
