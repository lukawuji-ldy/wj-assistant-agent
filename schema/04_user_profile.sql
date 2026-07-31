-- 用户结构化画像 / 偏好
CREATE TABLE IF NOT EXISTS user_profile
(
    id             BIGINT         PRIMARY KEY,
    memory_id      VARCHAR(64)    NOT NULL,
    user_id        VARCHAR(64)    NOT NULL,
    memory_type    VARCHAR(32)    NOT NULL,
    memory_key     VARCHAR(128)   NOT NULL,
    memory_value   TEXT           NOT NULL,
    status         VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    confidence     NUMERIC(3, 2)  NOT NULL DEFAULT 1.00,
    importance     NUMERIC(3, 2)  NOT NULL DEFAULT 0.50,
    source         VARCHAR(32)    NOT NULL,
    version        INT            NOT NULL DEFAULT 1,
    expire_time    TIMESTAMPTZ,
    last_used_time TIMESTAMPTZ,
    create_time    TIMESTAMPTZ    NOT NULL,
    update_time    TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uk_user_profile_memory_id UNIQUE (memory_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_profile_active_key
    ON user_profile (user_id, memory_key)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_user_profile_user_status
    ON user_profile (user_id, status);

CREATE INDEX IF NOT EXISTS idx_user_profile_expire
    ON user_profile (status, expire_time);

COMMENT ON TABLE user_profile IS '用户结构化长期记忆（画像/偏好）';
COMMENT ON COLUMN user_profile.id IS '主键';
COMMENT ON COLUMN user_profile.memory_id IS '记忆业务键';
COMMENT ON COLUMN user_profile.user_id IS '所属用户';
COMMENT ON COLUMN user_profile.memory_type IS '类型：PROFILE|PREFERENCE';
COMMENT ON COLUMN user_profile.memory_key IS '稳定键，如 occupation、goal.current';
COMMENT ON COLUMN user_profile.memory_value IS '记忆值，须自包含语境';
COMMENT ON COLUMN user_profile.status IS '状态：ACTIVE|INACTIVE|DELETED|EXPIRED';
COMMENT ON COLUMN user_profile.confidence IS '置信度 0~1';
COMMENT ON COLUMN user_profile.importance IS '重要度 0~1';
COMMENT ON COLUMN user_profile.source IS '来源：USER_DIRECT|EXTRACTED|MERGED|SYSTEM';
COMMENT ON COLUMN user_profile.version IS '同 key 演进版本号';
COMMENT ON COLUMN user_profile.expire_time IS '过期时间，空表示永久';
COMMENT ON COLUMN user_profile.last_used_time IS '最近检索命中时间';
COMMENT ON COLUMN user_profile.create_time IS '创建时间';
COMMENT ON COLUMN user_profile.update_time IS '更新时间';
