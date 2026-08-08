-- 后台管理员（与 sys_user 隔离）
CREATE TABLE IF NOT EXISTS admin_user
(
    id            BIGINT       PRIMARY KEY,
    admin_id      VARCHAR(64)  NOT NULL,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(200) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    role          VARCHAR(32)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    is_builtin    BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time   TIMESTAMPTZ  NOT NULL,
    update_time   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_admin_user_id UNIQUE (admin_id),
    CONSTRAINT uk_admin_username UNIQUE (username)
);

COMMENT ON TABLE admin_user IS '后台运营账号，与 sys_user 隔离';
COMMENT ON COLUMN admin_user.admin_id IS '业务键，写入 Admin JWT';
COMMENT ON COLUMN admin_user.role IS 'SUPER_ADMIN | OPERATOR';
COMMENT ON COLUMN admin_user.status IS 'ACTIVE | DISABLED';
COMMENT ON COLUMN admin_user.is_builtin IS '内置管理员：不可删/改角色/禁用，仅可改密';
