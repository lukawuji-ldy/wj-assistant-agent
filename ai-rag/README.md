# ai-rag

知识库（RAG）模块库（jar）：文档入库、中文切分、Chunk Revision、向量化与检索，并向 Agent 提供 `knowledge_retrieval` Tool。

企业侧真正关心的是：**引用可追溯到哪份文档的哪个版本/片段**、停用旧政策后是否还会被命中、无可靠命中时是否拒答——而不只是「向量库能搜到一段话」。

---

## 双后端可选：PGVector vs Elasticsearch

运行时由 `wuji.rag.vector-backend` **二选一**（默认 `pgvector`）。经统一端口 `VectorIndexPort` 适配；**禁止**同进程双后端检索，也**禁止** ES 失败静默回落 PG 向量。切换须**重启进程**并对 ACTIVE 版本 **rebuild**。

| | **PGVector（默认）** | **Elasticsearch Hybrid（可选）** |
|---|---|---|
| 向量落点 | `kb_chunk.embedding` | ES index 投影；PG 仍权威存正文/revision，`kb_chunk.embedding` 保持 NULL |
| 检索 | 余弦 + ILIKE（整句/中文切词，覆盖未嵌入文档） | BM25 + kNN + **客户端 RRF**（elasticsearch-java **8.15.4**） |
| 优点 | 与业务库同实例、运维简单、默认路径成熟、中小规模成本低 | 关键词 + 向量双路、适合更大语料与 Hybrid 召回 |
| 代价 / 风险 | 超大规模时检索与运维扩展不如专用搜索集群 | 维度须与 EMBEDDING 模型对齐；索引运维更重；依赖独立 ES 集群 |
| 适用 | 多数企业内知识库、快速落地 | 需要强关键词命中或已有 ES 基础设施时 |

元数据（文档版本、Chunk Revision、ACL 字段等）**始终在 PostgreSQL**；用户语义长期记忆向量在 `ai-memory` 独立表，**禁止与知识库混写**。

---

## 主要功能

- **入库**：版本管理、中文预处理/切分、Chunk Revision；Embedding 经 `llm_config` 的 `EMBEDDING` 行；批量限速与 429 退避（`wuji.rag.embedding.*`）
- **检索门面**：`KnowledgeRetrievalService`；无可靠命中可 `rejected=true`（grounded 拒答）
- **Agent 工具**：`knowledge_retrieval`；工具说明 / 回答策略来自库表 Prompt（`rag.knowledge_retrieval.system` / `rag.answer.system`）
- **预检索**：`prefetch-enabled` 时 ChatFacade 入模前召回，缓解部分模型跳过 Tool 直接拒答
- **管理支撑**：CRUD / 重建等由旁路 Admin API 调用本库能力（本聊天仓无 `/api/admin/**`）

---

## 技术选型

| 项 | 选型 |
|---|---|
| 形态 | 独立 Maven jar |
| 默认向量 | `spring-ai-pgvector-store` |
| 可选 ES | `elasticsearch-java` **8.15.4**（ai-rag 侧 optional；Boot 在 ES 模式引入） |
| 依赖 | → `ai-common` |

设计细节见 [docs/rag-design.md](../docs/rag-design.md)、[docs/database-design.md](../docs/database-design.md)。
