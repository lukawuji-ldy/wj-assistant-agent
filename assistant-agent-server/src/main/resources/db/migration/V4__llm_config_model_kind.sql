-- llm_config：区分 CHAT / EMBEDDING，并写入向量模型种子行
ALTER TABLE llm_config
    ADD COLUMN IF NOT EXISTS model_kind VARCHAR(20) NOT NULL DEFAULT 'CHAT';

CREATE INDEX IF NOT EXISTS idx_llm_config_kind_status ON llm_config (model_kind, status);

COMMENT ON COLUMN llm_config.model_kind IS 'CHAT | EMBEDDING';
COMMENT ON TABLE llm_config IS '大模型 OpenAI Compatible 连接配置（CHAT/EMBEDDING）';
COMMENT ON COLUMN llm_config.config_id IS '配置业务键，如 llm_primary / llm_embedding';
COMMENT ON COLUMN llm_config.model IS '模型名（CHAT=对话模型，EMBEDDING=向量模型）';
COMMENT ON COLUMN llm_config.extra_json IS '扩展参数 JSON（路径、dimensions 等）';

INSERT INTO llm_config
(id, config_id, name, provider, model_kind, base_url, api_key_cipher, model, temperature, max_tokens,
 extra_json, status, create_time, update_time)
VALUES (3, 'llm_embedding', '默认向量模型', 'openai_compatible', 'EMBEDDING',
        'https://api.openai.com/v1', 'CHANGE_ME', 'text-embedding-3-small',
        NULL, NULL,
        '{"dimensions":1536,"embeddings_path":"/v1/embeddings"}'::jsonb,
        'ACTIVE', NOW(), NOW())
ON CONFLICT (config_id) DO NOTHING;
