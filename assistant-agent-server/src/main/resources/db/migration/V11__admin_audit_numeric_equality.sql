-- 补充：数值比较按数学相等（忽略 BigDecimal scale），避免 temperature 0.7/0.70 误记变更
COMMENT ON COLUMN admin_audit_log.detail IS
    'JSONB 契约: {"changes":[{"field":"model","from":"旧值","to":"新值"}],"meta":{可选}}. '
    'changes 仅含实际变化字段；CREATE 时 from=null；数值按数学相等跳过（忽略 BigDecimal scale）；'
    'apiKey/password 仅记 to=[CHANGED]；禁止明文密钥/密码。';
