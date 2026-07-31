# wuji-assistant-agent
AI智能聊天系统
按 `agents.md`：**无 Maven parent 聚合**，各子工程独立 `pom.xml`。

## 子工程

| 目录 | 说明 |
|---|---|
| `ai-common` | 公共错误码 / 异常 |
| `ai-memory` | 记忆模块骨架 |
| `ai-rag` | RAG 模块骨架 |
| `ai-agent-core` | Agent 核心骨架 |
| `assistant-agent-server` | 主服务（8080），含数据源与 Flyway |
| `assistant-mcp-server` | MCP 服务骨架（8081） |

## 数据库

- DDL 分文件：[`schema/`](schema/)（`00_extensions.sql` … `13_*.sql`），一键：[`schema/all.sql`](schema/all.sql)；说明见 [`schema/README.md`](schema/README.md)
- Flyway：`assistant-agent-server/src/main/resources/db/migration/V1__init.sql`（与 schema 内容一致）
- 配置库：`jdbc:postgresql://127.0.0.1:5432/vector_test`

构建示例（JDK 17）：

```bash
mvn -f ai-common/pom.xml clean install -DskipTests
# 再依次 ai-memory → ai-rag → ai-agent-core → assistant-*-server
```

作者约定：代码注释 `@author liudy`。

## 第 2 期联调（Auth + 会话 + SSE）

本地账号由启动种子写入：`admin` / `admin123`。

```bash
# 配置真实 OpenAI Compatible Key（或更新 llm_config.api_key_cipher）
set WUJI_LLM_API_KEY=sk-...

mvn -f assistant-agent-server/pom.xml spring-boot:run
```

```http
POST /api/auth/login  {"username":"admin","password":"admin123"}
POST /api/conversations  Authorization: Bearer <token>
POST /api/chat/stream   Accept: text/event-stream
```
