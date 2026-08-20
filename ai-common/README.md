# ai-common

跨模块公共库（jar）：统一错误码、异常、公共 DTO 与约定类型，供 Agent / Memory / RAG / MCP Server / VTA 复用。

## 主要功能

- **错误码与异常**：业务失败码、统一异常模型，避免各模块私自散落常量
- **公共 DTO / 约定**：跨模块传输与契约类型
- **边界**：不含 Agent 编排、记忆算法、RAG 入库逻辑；不含 Boot 启动与 Web 控制器

## 技术选型

| 项 | 选型 |
|---|---|
| 形态 | 独立 Maven jar（最底层公共依赖） |
| 基线 | JDK 17；版本与仓库锁定表对齐 |
| 被依赖方 | `ai-agent-core`、`ai-memory`、`ai-rag`、`ai-analysis-core`、`assistant-mcp-server` 等 |

编码约定见 [docs/coding-standard.md](../docs/coding-standard.md)。
