-- 会话元数据
CREATE TABLE IF NOT EXISTS conversation
(
    id                       BIGINT       PRIMARY KEY,
    conversation_id          VARCHAR(64)  NOT NULL,
    user_id                  VARCHAR(64)  NOT NULL,
    title                    VARCHAR(200),
    summary                  TEXT,
    summary_until_time       TIMESTAMPTZ,
    summary_until_message_id VARCHAR(64),
    summary_compressed_at    TIMESTAMPTZ,
    message_count            INT          NOT NULL DEFAULT 0,
    last_active_time         TIMESTAMPTZ  NOT NULL,
    create_time              TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_conversation_id UNIQUE (conversation_id)
);

CREATE INDEX IF NOT EXISTS idx_conversation_user_active
    ON conversation (user_id, last_active_time);

COMMENT ON TABLE conversation IS '聊天会话元数据';
COMMENT ON COLUMN conversation.id IS '主键';
COMMENT ON COLUMN conversation.conversation_id IS '对外业务键';
COMMENT ON COLUMN conversation.user_id IS '所属用户';
COMMENT ON COLUMN conversation.title IS '侧栏标题，可由 LLM 异步生成';
COMMENT ON COLUMN conversation.summary IS '当前生效的结构化摘要 JSON（滚动合并覆盖写）';
COMMENT ON COLUMN conversation.summary_until_time IS '摘要覆盖到的最后一条消息时间（watermark）';
COMMENT ON COLUMN conversation.summary_until_message_id IS '摘要覆盖到的最后一条消息业务键';
COMMENT ON COLUMN conversation.summary_compressed_at IS '最近一次压缩发生时间（审计）';
COMMENT ON COLUMN conversation.message_count IS '消息条数';
COMMENT ON COLUMN conversation.last_active_time IS '最后活跃时间';
COMMENT ON COLUMN conversation.create_time IS '创建时间';
