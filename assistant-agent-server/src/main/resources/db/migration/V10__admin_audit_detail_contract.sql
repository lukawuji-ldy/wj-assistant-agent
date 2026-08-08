-- admin_audit_log.detail 字段级变更契约（列集合不变，仅固化语义）
-- detail JSON: { "changes": [ { "field", "from", "to" } ], "meta"?: { ... } }
-- CREATE 时 from 为 null；敏感字段 apiKey/password 的 to 为 [CHANGED]，禁止明文

COMMENT ON TABLE admin_audit_log IS '管理台写操作审计；detail 记录字段级 from→to 变更';

COMMENT ON COLUMN admin_audit_log.detail IS
    'JSONB 契约: {"changes":[{"field":"model","from":"旧值","to":"新值"}],"meta":{可选}}. '
    'changes 仅含实际变化字段；CREATE 时 from=null；apiKey/password 仅记 to=[CHANGED]；禁止明文密钥/密码。';
