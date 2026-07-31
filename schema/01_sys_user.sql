-- 系统用户（预置账号，无自助注册）
CREATE TABLE IF NOT EXISTS sys_user
(
    id            BIGINT       PRIMARY KEY,
    user_id       VARCHAR(64)  NOT NULL,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(200) NOT NULL,
    nickname      VARCHAR(100) NOT NULL,
    tenant_id     VARCHAR(64)  NOT NULL DEFAULT 'default',
    role          VARCHAR(64)  NOT NULL DEFAULT 'user',
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    create_time   TIMESTAMPTZ  NOT NULL,
    update_time   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_sys_user_id UNIQUE (user_id),
    CONSTRAINT uk_sys_username UNIQUE (username)
);

COMMENT ON TABLE sys_user IS '系统预置用户表，登录鉴权用';
COMMENT ON COLUMN sys_user.id IS '主键';
COMMENT ON COLUMN sys_user.user_id IS '业务用户键，写入 JWT';
COMMENT ON COLUMN sys_user.username IS '登录用户名';
COMMENT ON COLUMN sys_user.password_hash IS '密码哈希（BCrypt 等）';
COMMENT ON COLUMN sys_user.nickname IS '展示昵称';
COMMENT ON COLUMN sys_user.tenant_id IS '租户标识，单租户可用 default';
COMMENT ON COLUMN sys_user.role IS '角色，与 RAG ACL 对齐';
COMMENT ON COLUMN sys_user.status IS '状态：ACTIVE/DISABLED 等';
COMMENT ON COLUMN sys_user.create_time IS '创建时间';
COMMENT ON COLUMN sys_user.update_time IS '更新时间';
