ALTER TABLE llm_call_log
    ADD COLUMN IF NOT EXISTS estimated_prompt_tokens INTEGER;

COMMENT ON COLUMN llm_call_log.estimated_prompt_tokens IS 'prepare 估算的输入 token。只在一次请求的第一次模型调用写入；ReAct 后续轮与摘要/抽取调用为空';
