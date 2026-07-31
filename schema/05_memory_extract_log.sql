-- 记忆提取审计
CREATE TABLE IF NOT EXISTS memory_extract_log
(
    id              BIGINT       PRIMARY KEY,
    user_id         VARCHAR(64)  NOT NULL,
    conversation_id VARCHAR(64)  NOT NULL,
    message_id      VARCHAR(64)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    action          VARCHAR(20),
    result_type     VARCHAR(32),
    error_message   VARCHAR(512),
    retry_count     INT          NOT NULL DEFAULT 0,
    create_time     TIMESTAMPTZ  NOT NULL,
    update_time     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_message_extract UNIQUE (message_id)
);

CREATE INDEX IF NOT EXISTS idx_memory_extract_retry
    ON memory_extract_log (status, retry_count, update_time);

COMMENT ON TABLE memory_extract_log IS '长期记忆提取幂等与审计日志';
COMMENT ON COLUMN memory_extract_log.id IS '主键';
COMMENT ON COLUMN memory_extract_log.user_id IS '用户';
COMMENT ON COLUMN memory_extract_log.conversation_id IS '会话';
COMMENT ON COLUMN memory_extract_log.message_id IS '触发提取的消息业务键';
COMMENT ON COLUMN memory_extract_log.status IS 'PENDING|SUCCESS|FAILED|SKIPPED';
COMMENT ON COLUMN memory_extract_log.action IS 'INSERT|UPDATE|MERGE|DELETE|IGNORE';
COMMENT ON COLUMN memory_extract_log.result_type IS 'PROFILE|PREFERENCE|SEMANTIC|NONE';
COMMENT ON COLUMN memory_extract_log.error_message IS '失败原因摘要';
COMMENT ON COLUMN memory_extract_log.retry_count IS '重试次数';
COMMENT ON COLUMN memory_extract_log.create_time IS '创建时间';
COMMENT ON COLUMN memory_extract_log.update_time IS '更新时间';
