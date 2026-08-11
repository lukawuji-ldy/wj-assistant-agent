# ai-rag

知识库（RAG）模块库（jar）：文档入库、切分、向量化与检索，并向 Agent 提供 Retrieval Tool。

## 主要功能

- **文档入库**：版本管理、中文切分、Chunk Revision；当前激活向量落在 `kb_chunk`
- **Embedding**：按 `wuji.rag.embedding-config-id` 指向 `llm_config` 的 `EMBEDDING` 行；模型指纹记在 `kb_document_version`
- **检索**：余弦相似度 / ILIKE 等策略，供问答与引用
- **Agent 工具**：`knowledge_retrieval`，由 Agent 按需调用
- **管理支撑**：配合 Admin API 做知识库 / Chunk CRUD、重建向量等（控制器在 server 侧）

## 技术选型

| 项 | 选型 |
|---|---|
| 形态 | 独立 Maven jar |
| 向量库 | PostgreSQL + PGVector（与业务库同实例） |
| Spring AI | `spring-ai-pgvector-store` 及相关自动配置能力 |
| 依赖方向 | → `ai-common`；与用户语义记忆分表，禁止混用 |

设计见 [docs/rag-design.md](../docs/rag-design.md)、[docs/database-design.md](../docs/database-design.md)。
