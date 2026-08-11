# assistant-mcp-server

独立 MCP Tool 服务（默认端口 **8081**）：按 MCP 规范对外暴露工具，与 Agent 主服务进程解耦，可多实例部署。本仓库两个可启动 Boot 应用之一。

## 主要功能

- **MCP Server**：基于 WebFlux 暴露 SSE 传输的 Tool 端点
- **样例工具**：如 `echo_ping`，供 Client 联调与绑定验证
- **进程边界**：不依赖 `ai-agent-core` / `ai-memory` / `ai-rag`；不承载 Chat / Admin API
- **与主服务协作**：由 `assistant-agent-server` 的 MCP Client 按 `mcp_server_ref` 动态连接，工具子集由 `mcp_tool_binding` 控制

## 技术选型

| 项 | 选型 |
|---|---|
| 运行时 | JDK 17、Spring Boot 3.4.8 |
| Web | Spring WebFlux |
| MCP | `spring-ai-starter-mcp-server-webflux`（SSE） |
| 依赖模块 | 仅 `ai-common` |

设计见 [docs/mcp-design.md](../docs/mcp-design.md)。
