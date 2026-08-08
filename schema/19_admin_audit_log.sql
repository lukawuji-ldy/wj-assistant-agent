-- 管理台写操作审计
-- detail JSON 契约: {"changes":[{"field","from","to"}],"meta"?:{}}
-- CREATE 时 from=null；敏感字段 apiKey/password 的 to=[CHANGED]，禁止明文
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

COMMENT ON TABLE admin_audit_log IS '管理台写操作审计；detail 记录字段级 from→to 变更';
COMMENT ON COLUMN admin_audit_log.detail IS
    'JSONB 契约: {"changes":[{"field":"model","from":"旧值","to":"新值"}],"meta":{可选}}. '
    'changes 仅含实际变化字段；CREATE 时 from=null；数值按数学相等跳过（忽略 BigDecimal scale）；'
    'apiKey/password 仅记 to=[CHANGED]；禁止明文密钥/密码。';
