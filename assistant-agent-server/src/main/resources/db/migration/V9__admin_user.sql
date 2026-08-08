-- 后台管理员与管理写操作审计（对齐 docs/database-design.md §2.0b / §2.0c）

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

CREATE TABLE IF NOT EXISTS admin_audit_log
(
    id            BIGINT       PRIMARY KEY,
    admin_id      VARCHAR(64)  NOT NULL,
    action        VARCHAR(64)  NOT NULL,
    resource_type VARCHAR(64)  NOT NULL,
    resource_id   VARCHAR(128),
    detail        JSONB,
    create_time   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_admin_audit_admin_time ON admin_audit_log (admin_id, create_time);
CREATE INDEX IF NOT EXISTS idx_admin_audit_resource ON admin_audit_log (resource_type, resource_id);

COMMENT ON TABLE admin_audit_log IS '管理台写操作审计';
