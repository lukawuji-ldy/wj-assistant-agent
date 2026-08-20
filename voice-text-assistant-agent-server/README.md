# voice-text-assistant-agent-server

录音分析助手服务（默认端口 **8082**）：接收通话 ASR 文本，经 Graph 编排输出客户/销售标签、小记、意向度与汇总。本仓库可启动 Boot 应用之一。

## 主要功能

- **API**（User JWT）：`POST /api/vta/analyze`、`POST /api/vta/analyze/stream`、`GET /api/vta/jobs/**`
- **编排**：依赖 `ai-analysis-core` StateGraph（四路并行分析 + aggregate）
- **边界**：VTA 运行时**不扫** Memory/RAG Bean；**禁止写入聊天记忆表**；与智能聊天进程隔离
- **审计**：`llm_call_log.biz_source=VTA`，`biz_ref_id=jobId`
- **超时**：任务/节点超时可配置，避免 Graph 无限挂起

## 技术选型

| 项 | 选型 |
|---|---|
| 运行时 | JDK 17、Spring Boot 3.4.8 |
| Web | Spring WebFlux |
| 编排 | `ai-analysis-core`（Spring AI Alibaba StateGraph） |
| 依赖 | `ai-analysis-core` → `ai-agent-core` / `ai-common` |
| Flyway | 本进程可关闭；表与 Prompt 种子由共享库 / 管理工程维护 |

设计见 [docs/voice-text-assistant-design.md](../docs/voice-text-assistant-design.md)。前端入口 `/voice-analysis` 见 [`wuji-assistant-web`](../wuji-assistant-web/)。
