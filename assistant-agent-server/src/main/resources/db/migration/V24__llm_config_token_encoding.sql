-- llm_config.token_encoding：主备 CHAT 行各自的文本 token 词表（可空）
ALTER TABLE llm_config
    ADD COLUMN IF NOT EXISTS token_encoding VARCHAR(32);

COMMENT ON COLUMN llm_config.token_encoding IS '文本 token 词表：cl100k_base | o200k_base；空则按 model 推断，对不上时用 cl100k_base';
