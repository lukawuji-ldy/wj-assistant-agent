# assistant-agent-server

主后端服务（默认端口 **8080**）：对外 Chat / SSE，装配 ReactAgent、MCP Client、Memory、RAG。本仓库可启动 Boot 应用之一。

> **Admin API 已迁出**至旁路仓库 `wj-assistant-agent-manage`。本服务**仅 User JWT**，不暴露 `/api/admin/**`。

## 主要功能

- **聊天 API**：`POST /api/chat/stream`（SSE）与 `POST /api/chat`（同步 `ChatResult`）；请求体共用 `ChatStreamRequest`
- **会话与鉴权**：登录、会话管理；身份以 JWT 为准，**禁止信任前端传入的 userId**
- **Agent 编排**：`ModelRouter` + 有界 `ReactAgent`（CHAT 主备、工具轮次上限、PostgresSaver）
- **能力装配**：长期记忆入模、`knowledge_retrieval`、可选 MCP 工具（`mcp_server_ref` / `mcp_tool_binding`）
- **数据**：PostgreSQL + Flyway；LLM / Prompt 以库表为准
- **可观测**：Micrometer Tracing + OTLP、`llm_call_log` 入模审计

## 技术选型

| 项 | 选型 |
|---|---|
| 运行时 | JDK 17、Spring Boot 3.4.8 |
| Web | Spring WebFlux（阻塞 JDBC/LLM 走有界线程池） |
| AI | Spring AI OpenAI Starter、Spring AI Alibaba Agent、MCP Client WebFlux、PGVector（可选 ES） |
| 安全 | Spring Security + JJWT（**仅 User JWT**） |
| 依赖 | `ai-agent-core`（进而 Memory / RAG / Common） |

设计见 [docs/architecture.md](../docs/architecture.md)、[docs/agent-flow.md](../docs/agent-flow.md)。仓库总览见 [根 README](../README.md)。
