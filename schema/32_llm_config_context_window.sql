-- llm_config.context_window_size：输入上下文窗口，与 max_tokens（输出上限）分开
ALTER TABLE llm_config
    ADD COLUMN IF NOT EXISTS context_window_size INTEGER;

UPDATE llm_config
SET context_window_size = 8192
WHERE model_kind = 'EMBEDDING'
  AND context_window_size IS NULL;

UPDATE llm_config
SET context_window_size = 32768
WHERE context_window_size IS NULL;

ALTER TABLE llm_config
    ALTER COLUMN context_window_size SET DEFAULT 32768;

ALTER TABLE llm_config
    ALTER COLUMN context_window_size SET NOT NULL;

COMMENT ON COLUMN llm_config.context_window_size IS '输入上下文窗口（token），与 max_tokens 分开。CHAT 默认 32768，EMBEDDING 默认 8192';
