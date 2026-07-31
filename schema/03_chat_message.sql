-- 聊天消息（含流式状态列 STREAMING|COMPLETED|CANCELLED）
CREATE TABLE IF NOT EXISTS chat_message
(
    id              BIGINT       PRIMARY KEY,
    message_id      VARCHAR(64)  NOT NULL,
    conversation_id VARCHAR(64)  NOT NULL,
    user_id         VARCHAR(64)  NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    content         TEXT         NOT NULL,
    token_count     INT          NOT NULL DEFAULT 0,
    status          VARCHAR(20)  NOT NULL DEFAULT 'COMPLETED',
    create_time     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_message_id UNIQUE (message_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_message_conv_time
    ON chat_message (conversation_id, create_time);

COMMENT ON TABLE chat_message IS '会话消息（短期记忆）';
COMMENT ON COLUMN chat_message.id IS '主键';
COMMENT ON COLUMN chat_message.message_id IS '消息业务键';
COMMENT ON COLUMN chat_message.conversation_id IS '所属会话业务键';
COMMENT ON COLUMN chat_message.user_id IS '所属用户';
COMMENT ON COLUMN chat_message.role IS '角色：user|assistant|system|tool';
COMMENT ON COLUMN chat_message.content IS '消息正文';
COMMENT ON COLUMN chat_message.token_count IS '估算 token 数';
COMMENT ON COLUMN chat_message.status IS '流式状态：STREAMING|COMPLETED|CANCELLED';
COMMENT ON COLUMN chat_message.create_time IS '创建时间';
