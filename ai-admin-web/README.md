# ai-admin-web

运营后台（npm）：配置与观测控制台，对接 `assistant-agent-server` 的 `/api/admin/**`（Admin JWT，与聊天 User JWT 双轨隔离）。

## 主要功能

- **账号与权限**：管理员登录、后台用户管理（`admin_user`）
- **模型与提示词**：`llm_config` 管理；`prompt_template` 草稿 / 发布 / 回滚 / Diff
- **知识库**：文档、切分、Chunk / Revision、Embedding 相关运维
- **记忆**：聊天用户画像与语义记忆管理
- **MCP**：多 Server 连接（`mcp_server_ref`）、工具绑定 / 启用两态（`mcp_tool_binding`）
- **观测与审计**：LLM 调用日志、Checkpoint 回放、`admin_audit_log` 只读查询

P0–P6 能力已落地；细节与分期见设计文档。

## 技术选型

| 项 | 选型 |
|---|---|
| 框架 | Vue 3 + TypeScript |
| UI | Element Plus + `@element-plus/icons-vue` |
| 工程 | Vite 8、Vue Router、Pinia |
| HTTP | Axios；开发态 proxy `/api/admin` → `http://127.0.0.1:8080` |
| 鉴权 | Admin JWT（`Authorization: Bearer`） |

设计见 [docs/admin-design.md](../docs/admin-design.md)。
