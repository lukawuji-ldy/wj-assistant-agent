# 无忌助手 · 运营后台（ai-admin-web）

Vue 3 + Element Plus + Vite。对接 `assistant-agent-server` 的 `/api/admin/**`（Admin JWT）。

## 开发

```bash
npm install
npm run dev
```

默认 http://127.0.0.1:5173 ，Vite 将 `/api/admin` 代理到 `http://127.0.0.1:8080`。

本地默认管理员（`admin_user`，与聊天 `sys_user.admin` 同名不同表）：

- 用户名：`admin`
- 密码：`admin123`

## 已实现页面

| 路由 | 说明 |
|---|---|
| `/login` | 管理员登录 |
| `/users` | 后台用户管理（P0） |
| `/llm-configs` | LLM 配置（P1） |
| `/prompts` | 提示词版本（P1） |
| `/logs/llm-calls` | LLM 调用日志（P2） |
| `/logs/checkpoints` | Checkpoint 双栏回放 + 明细弹框（P2；服务端解码 state） |

其余菜单（知识库 / 记忆 / MCP）为后续分期占位。

## 构建

```bash
npm run build
```

设计与分期见 [docs/admin-design.md](../docs/admin-design.md)。
