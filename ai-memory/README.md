# ai-memory

记忆模块库（jar）：管理用户短期会话上下文与长期画像 / 语义记忆，含抽取、冲突解决与按需入模。

## 主要功能

- **短期记忆**：会话窗口、水位与上下文装配
- **长期记忆写入**：Memory Action（INSERT / UPDATE / MERGE / DELETE / IGNORE）+ 冲突解决；默认异步，显式「记住」可实时
- **抽取**：L2 默认 hybrid（LLM + 规则降级）；分流 `user_profile` 与 `user_semantic_memory`
- **读路径入模**：`MemoryRoutePort` + `LongTermMemoryRetriever`，按需加载画像与语义向量召回，注入 Chat System
- **生命周期**：衰减 / 过期整理；按 `user_id` 隔离

## 技术选型

| 项 | 选型 |
|---|---|
| 形态 | 独立 Maven jar |
| 存储 | PostgreSQL（画像表）+ PGVector（语义记忆向量） |
| 模型 | 经主服务注入的 Chat / Embedding（配置在 `llm_config`） |
| 提示词 | 库表 `prompt_template`（如 `memory.extract.*`、`memory.retrieve.router.*`） |
| 依赖方向 | → `ai-common` |

设计见 [docs/agent-memory.md](../docs/agent-memory.md)。
