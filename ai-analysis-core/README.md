# ai-analysis-core

录音分析助手编排库（jar）：将通话转写文本拆成多路 LLM 分析节点，再汇总为前端可展示的结构化结果。

## 主要功能

- **StateGraph**：`validateInput` → 客户标签 / 销售标签 / 小记 / 意向度 **四路并行** → `mergePartial` → `aggregate`
- **提示词**：`vta.*.system` / `vta.transcript.user` 等入库 `prompt_template`（`prompt_group=VTA`），禁止业务硬编码大段 Prompt
- **边界**：无记忆库、不写聊天记忆表；一次性 job，本期不用 Graph JDBC Checkpoint
- **被谁使用**：仅 `voice-text-assistant-agent-server` 启动装配

## 技术选型

| 项 | 选型 |
|---|---|
| 形态 | 独立 Maven jar |
| 编排 | Spring AI Alibaba StateGraph |
| 依赖 | → `ai-agent-core` / `ai-common` |

设计见 [docs/voice-text-assistant-design.md](../docs/voice-text-assistant-design.md)。
