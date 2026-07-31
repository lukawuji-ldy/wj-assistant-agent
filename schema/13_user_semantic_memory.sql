-- 用户语义长期记忆（与知识库分表）
CREATE TABLE IF NOT EXISTS user_semantic_memory
(
    id                UUID PRIMARY KEY,
    user_id           VARCHAR(64)  NOT NULL,
    content           TEXT         NOT NULL,
    memory_type       VARCHAR(32)  NOT NULL DEFAULT 'experience',
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    importance        REAL         NOT NULL DEFAULT 0.5,
    confidence        REAL         NOT NULL DEFAULT 0.8,
    tags              JSONB,
    metadata          JSONB,
    source            VARCHAR(32)  NOT NULL DEFAULT 'EXTRACTED',
    source_message_id VARCHAR(64),
    expire_time       TIMESTAMPTZ,
    last_used_time    TIMESTAMPTZ,
    embedding         VECTOR(1536) NOT NULL,
    create_time       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_time       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_semantic_user_status
    ON user_semantic_memory (user_id, status);

CREATE INDEX IF NOT EXISTS idx_user_semantic_expire
    ON user_semantic_memory (status, expire_time);

DO $$
BEGIN
    BEGIN
        CREATE INDEX IF NOT EXISTS idx_user_semantic_embedding
            ON user_semantic_memory USING ivfflat (embedding vector_cosine_ops)
            WITH (lists = 100);
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'skip idx_user_semantic_embedding: %', SQLERRM;
    END;
END $$;

COMMENT ON TABLE user_semantic_memory IS '用户语义长期记忆向量表（非企业知识库）';
COMMENT ON COLUMN user_semantic_memory.id IS '主键 UUID';
COMMENT ON COLUMN user_semantic_memory.user_id IS '所属用户，检索必须过滤';
COMMENT ON COLUMN user_semantic_memory.content IS '叙述性记忆正文';
COMMENT ON COLUMN user_semantic_memory.memory_type IS 'experience|project|note 等';
COMMENT ON COLUMN user_semantic_memory.status IS 'ACTIVE 等生命周期状态';
COMMENT ON COLUMN user_semantic_memory.importance IS '重要度';
COMMENT ON COLUMN user_semantic_memory.confidence IS '置信度';
COMMENT ON COLUMN user_semantic_memory.tags IS '标签 JSON 数组';
COMMENT ON COLUMN user_semantic_memory.metadata IS '扩展元数据';
COMMENT ON COLUMN user_semantic_memory.source IS 'EXTRACTED 等';
COMMENT ON COLUMN user_semantic_memory.source_message_id IS '溯源消息 ID';
COMMENT ON COLUMN user_semantic_memory.expire_time IS '过期时间';
COMMENT ON COLUMN user_semantic_memory.last_used_time IS '最近使用时间';
COMMENT ON COLUMN user_semantic_memory.embedding IS '向量';
COMMENT ON COLUMN user_semantic_memory.create_time IS '创建时间';
COMMENT ON COLUMN user_semantic_memory.update_time IS '更新时间';
