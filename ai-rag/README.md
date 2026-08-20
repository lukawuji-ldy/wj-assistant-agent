# ai-rag

知识库（RAG）模块库（jar）：文档入库、切分、向量化与检索，并向 Agent 提供 Retrieval Tool。

## 主要功能

- **文档入库**：版本管理、中文切分、Chunk Revision；当前激活向量落在 `kb_chunk`
- **Embedding**：按 `wuji.rag.embedding-config-id` 指向 `llm_config` 的 `EMBEDDING` 行；`extra_json.dimensions` 须与模型输出一致；模型指纹记在 `kb_document_version`；批量入库/重建经 `wuji.rag.embedding.*` 限速与 429 退避（默认间隔 1000ms）
- **检索**：余弦相似度 / ILIKE（PGVector）或 Hybrid Search（Elasticsearch 可选）；供问答与引用
- **Agent 工具**：`knowledge_retrieval`，由 Agent 按需调用
- **管理支撑**：配合 Admin API 做知识库 / Chunk CRUD、重建向量等（控制器在 server 侧）

## 技术选型

| 项 | 选型 |
|---|---|
| 形态 | 独立 Maven jar |
| 向量库 | PostgreSQL + PGVector（默认）；可选 Elasticsearch Hybrid（`wuji.rag.vector-backend=elasticsearch`） |
| Spring AI | `spring-ai-pgvector-store` 及相关自动配置能力 |
| ES 维度 | 索引 `embedding.dims` 与 EMBEDDING 模型一致；不一致时默认 `wuji.rag.elasticsearch.recreate-index-on-dimension-mismatch=true` 自动删建索引（需对 ACTIVE 版本重建向量） |
| 依赖方向 | → `ai-common`；与用户语义记忆分表，禁止混用 |

设计见 [docs/rag-design.md](../docs/rag-design.md)、[docs/database-design.md](../docs/database-design.md)。
