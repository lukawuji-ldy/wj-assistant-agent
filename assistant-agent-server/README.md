# assistant-agent-server

主后端服务（默认端口 **8080**）：对外 Chat / SSE，并装配 ReactAgent、MCP Client、Memory、RAG。本仓库可启动 Boot 应用之一。

> **Admin API 已迁出**至旁路仓库 `wj-assistant-agent-manage`（`assistant-agent-manage`）。本服务仅 User JWT。

## 主要功能

- **聊天 API**：`POST /api/chat/stream`（SSE）与 `POST /api/chat`（同步 `ChatResult`），身份以 User JWT 为准
- **会话与鉴权**：登录、会话管理；禁止信任前端传入的 `userId`
- **Agent 编排**：经 `ModelRouter` + 有界 `ReactAgent`（主备 CHAT 配置、工具轮次上限）
- **能力装配**：长期记忆入模、知识库检索工具、可选 MCP 工具（库表 `mcp_server_ref` / `mcp_tool_binding`）
- **数据与迁移**：PostgreSQL + Flyway；LLM / Prompt 等连接与模板以库表为准
- **可观测**：Micrometer Tracing + OTLP、`llm_call_log` 入模审计

## 技术选型

| 项 | 选型 |
|---|---|
| 运行时 | JDK 17、Spring Boot 3.4.8 |
| Web | Spring WebFlux（阻塞 JDBC/LLM 走有界线程池） |
| AI | Spring AI OpenAI Starter、Spring AI Alibaba Agent、MCP Client WebFlux、PGVector |
| 安全 | Spring Security + JJWT（**仅 User JWT**） |
| 数据 | PostgreSQL、Flyway、JDBC |
| 可观测 | Actuator、Micrometer Tracing、OpenTelemetry OTLP |
| 依赖模块 | `ai-agent-core`（进而 Memory / RAG / Common） |

设计见 [docs/architecture.md](../docs/architecture.md)、[docs/agent-flow.md](../docs/agent-flow.md)。管理台见旁路 `wj-assistant-agent-manage`。
