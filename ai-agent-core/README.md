# ai-agent-core

Agent 编排核心库（jar）：ReactAgent 构建、工具注册与执行门面，供 `assistant-agent-server`（及 VTA 侧复用部分能力）装配。

## 主要功能

- **Agent 工厂**：基于 Spring AI Alibaba 构建有界 `ReactAgent`（最大模型调用 / 工具轮次）
- **模型路由**：`ModelRouter` 支持 primary + fallbacks（仅 `llm_config` 的 `CHAT` 行）；吞掉的 429 等可重抛后再切备用
- **工具装配**：RAG `knowledge_retrieval`、MCP 工具（Registry / allowlist）等
- **执行门面**：统一封装调用、失败与达上限策略（如 `AGENT_MAX_ITERATIONS`）
- **Checkpoint**：与 PostgresSaver 配合，支撑管理台回放（管理 API 在旁路仓）

## 技术选型

| 项 | 选型 |
|---|---|
| 形态 | 独立 Maven jar（无 Boot 启动类） |
| 框架 | Spring AI Alibaba Agent Framework、Spring AI 1.1.x |
| 依赖方向 | → `ai-memory`、`ai-rag`、`ai-common`（**禁止**被 MCP Server 依赖） |

设计见 [docs/agent-flow.md](../docs/agent-flow.md)。仓库总览见 [根 README](../README.md)。
