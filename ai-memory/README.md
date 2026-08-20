# ai-memory

记忆模块库（jar）：管理用户**短期会话上下文**与**长期画像 / 语义记忆**，含抽取、冲突解决与按需入模。

设计目标不是「把聊天全文塞进向量库」，而是让 Agent **多轮连续**的同时 **认识用户**，且写入可控、可冲突消解、可衰减。

---

## 短长期如何配合（核心巧妙之处）

```
用户请求
   │
   ▼
MemoryManager
   ├─ ShortMemory：会话消息 + summary（滑动窗口 + watermark）
   └─ LongMemory
         ├─ user_profile（画像 / 偏好，结构化）
         ├─ user_semantic_memory（叙述性记忆 + Embedding）
         └─ Lifecycle：Extract → Action → Conflict → Store → Retrieve → Decay
   │
   ▼
Context Builder → 注入 ChatFacade / ReactAgent System
```

| 层次 | 解决什么问题 | 不做的事 |
|---|---|---|
| **短期** | 「上一句说的订单号」这类任务内连续 | 不永久保存全量历史 |
| **长期** | 「我叫什么 / 偏好 / 重要事实」跨会话认识用户 | **禁止**把全部聊天原样写入 |

典型链路：

1. **读**：`MemoryRoutePort`（默认 rule，可配 hybrid）判断本轮是否需要画像 / 语义召回 → `LongTermMemoryRetriever` 按需加载 ACTIVE 画像与向量命中 → 拼进 System。
2. **写**：回复后（默认异步）走抽取；显式「记住」可实时。LLM/规则产出多条 **Memory Action**，经冲突解决再落库。
3. **隔离**：一律按 `user_id`；用户语义向量表与企业知识库 **分表**，禁止混写。

---

## 主要功能

- **短期记忆**：`max-message-count` / `max-token` 窗口；越窗后 summary + watermark，保留最近消息
- **长期写入**：Action = INSERT / UPDATE / MERGE / DELETE / IGNORE + 冲突解决
- **抽取**：默认 `wuji.memory.extract.mode=hybrid`（LLM 多 Action，失败可降级规则启发式）；分流 PROFILE/PREFERENCE → `user_profile`，SEMANTIC → `user_semantic_memory`
- **规则分 key 约定**：如 `display_name`（仅「我叫」）、`hometown`、`residence`、`preference.*`；**禁止**用「我是」写 `display_name`
- **读路径入模**：Router + Retriever；非每轮全量灌入长期记忆
- **生命周期**：置信度 / 重要度 / 过期与后台衰减整理

---

## 技术选型

| 项 | 选型 |
|---|---|
| 形态 | 独立 Maven jar |
| 存储 | PostgreSQL（画像）+ PGVector（语义记忆向量） |
| 模型 | 由主服务注入 Chat / Embedding（`llm_config`） |
| 提示词 | `memory.extract.*`、`memory.retrieve.router.*`（库表 ACTIVE） |
| 依赖 | → `ai-common` |

完整契约见 [docs/agent-memory.md](../docs/agent-memory.md)。
