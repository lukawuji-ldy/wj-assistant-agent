-- llm_config.max_tokens 改名为 output_max_tokens：输出上限，并从 context_window_size 中预留
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'llm_config'
          AND column_name = 'max_tokens'
    ) THEN
        ALTER TABLE llm_config RENAME COLUMN max_tokens TO output_max_tokens;
    END IF;
END $$;

COMMENT ON COLUMN llm_config.output_max_tokens IS '输出上限（token，仅 CHAT）。发给模型的 max_tokens，并从 context_window_size 中预留';
COMMENT ON COLUMN llm_config.context_window_size IS '输入上下文窗口（token），与 output_max_tokens 分开。CHAT 默认 32768，EMBEDDING 默认 8192';
