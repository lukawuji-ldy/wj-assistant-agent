# wuji-assistant-agent

企业级 AI 智能聊天基础平台：对话、知识库问答（RAG）、Agent 工具执行、MCP、用户记忆与运营管理，前后端同仓、子工程独立构建。

仓库**无 Maven parent 聚合**，各 Java 子工程独立 `pom.xml`；前端为 npm 工程，不纳入 Maven。

## 主要功能

- **大模型对话**：OpenAI Compatible 接入；流式 `POST /api/chat/stream`（SSE）与非流式 `POST /api/chat`
- **知识库问答（RAG）**：文档入库、PGVector 检索、Agent 侧 `knowledge_retrieval` 工具
- **Agent 编排**：Spring AI Alibaba `ReactAgent` / Workflow，有界轮次，主备模型路由
- **MCP 工具**：独立 `assistant-mcp-server`；Client 经 SSE 动态连接，工具子集可绑定
- **记忆**：短期会话上下文 + 长期画像 / 语义记忆（抽取、冲突解决、按需入模）
- **双前端**：聊天端（ChatGPT 式 UI）与运营后台（LLM / Prompt / 知识库 / MCP / 记忆 / 审计）
- **可观测**：OpenTelemetry + 入模审计表 `llm_call_log`

## 技术选型

| 技术 | 版本 / 说明 |
|---|---|
| Java | JDK 17 |
| Spring Boot | 3.4.8 |
| Spring AI / Spring AI Alibaba | 1.1.0 / 1.1.2.2（Extensions 1.1.0.0） |
| 大模型接入 | `spring-ai-starter-model-openai`（OpenAI Compatible） |
| Agent | `spring-ai-alibaba-agent-framework` |
| MCP | Client / Server WebFlux Starter + SSE |
| 存储 | PostgreSQL + PGVector（同实例） |
| 可观测 | OpenTelemetry 1.35.0（Micrometer Tracing + OTLP） |
| 聊天前端 | Next.js 15 + React 19 + TypeScript + Tailwind CSS 4 |
| 运营后台 | Vue 3 + Element Plus + Vite + Pinia |

## 子工程

| 目录 | 类型 | 一句话职责 |
|---|---|---|
| [`assistant-agent-server`](assistant-agent-server/) | Boot 应用 | Chat / Admin API，装配 Agent、MCP Client、Memory、RAG |
| [`assistant-mcp-server`](assistant-mcp-server/) | Boot 应用 | 按 MCP 规范暴露 Tool，独立部署 |
| [`ai-agent-core`](ai-agent-core/) | jar | Agent 构建、工具注册、执行门面 |
| [`ai-memory`](ai-memory/) | jar | 短/长期记忆、抽取与路由入模 |
| [`ai-rag`](ai-rag/) | jar | 知识库入库、向量检索、Retrieval Tool |
| [`ai-common`](ai-common/) | jar | 公共 DTO / 错误码 / 约定 |
| [`wuji-assistant-web`](wuji-assistant-web/) | npm | 登录 / 聊天 UI / Settings / Profile |
| [`ai-admin-web`](ai-admin-web/) | npm | 运营后台（Admin JWT） |

**依赖方向（禁止反向、禁止环）**

```
assistant-agent-server → ai-agent-core → ai-memory / ai-rag / ai-common
assistant-mcp-server → ai-common
```

DDL 与迁移说明见 [`schema/`](schema/)；设计细节见 [`AGENTS.md`](AGENTS.md) 与 [`docs/`](docs/)。
