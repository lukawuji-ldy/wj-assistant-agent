-- LLM 连接配置（CHAT / EMBEDDING 同表分行）
CREATE TABLE IF NOT EXISTS llm_config
(
    id              BIGINT        PRIMARY KEY,
    config_id       VARCHAR(64)   NOT NULL,
    name            VARCHAR(128)  NOT NULL,
    provider        VARCHAR(64)   NOT NULL DEFAULT 'openai_compatible',
    model_kind      VARCHAR(20)   NOT NULL DEFAULT 'CHAT',
    base_url        VARCHAR(512)  NOT NULL,
    api_key_cipher  TEXT          NOT NULL,
    model           VARCHAR(128)  NOT NULL,
    temperature     NUMERIC(4, 2),
    max_tokens      INT,
    extra_json      JSONB,
    status          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    create_time     TIMESTAMPTZ   NOT NULL,
    update_time     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_llm_config_id UNIQUE (config_id)
);

CREATE INDEX IF NOT EXISTS idx_llm_config_status ON llm_config (status);
CREATE INDEX IF NOT EXISTS idx_llm_config_kind_status ON llm_config (model_kind, status);

COMMENT ON TABLE llm_config IS '大模型 OpenAI Compatible 连接配置（CHAT/EMBEDDING）';
COMMENT ON COLUMN llm_config.id IS '主键';
COMMENT ON COLUMN llm_config.config_id IS '配置业务键，如 llm_primary / llm_embedding';
COMMENT ON COLUMN llm_config.name IS '展示名称';
COMMENT ON COLUMN llm_config.provider IS '提供商标识，默认 openai_compatible';
COMMENT ON COLUMN llm_config.model_kind IS 'CHAT | EMBEDDING';
COMMENT ON COLUMN llm_config.base_url IS 'API Base URL';
COMMENT ON COLUMN llm_config.api_key_cipher IS 'API Key 密文';
COMMENT ON COLUMN llm_config.model IS '模型名（CHAT=对话模型，EMBEDDING=向量模型）';
COMMENT ON COLUMN llm_config.temperature IS '温度（仅 CHAT）';
COMMENT ON COLUMN llm_config.max_tokens IS '最大生成 token（仅 CHAT）';
COMMENT ON COLUMN llm_config.extra_json IS '扩展参数 JSON（路径、dimensions 等）';
COMMENT ON COLUMN llm_config.status IS 'ACTIVE/DISABLED';
COMMENT ON COLUMN llm_config.create_time IS '创建时间';
COMMENT ON COLUMN llm_config.update_time IS '更新时间';
