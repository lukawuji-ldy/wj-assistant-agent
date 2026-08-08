package com.wuji.assistant.server.admin.audit;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 管理台审计 {@code detail} JSON 构建器：字段级 from→to。
 *
 * <pre>
 * {
 *   "changes": [ { "field": "model", "from": "A", "to": "B" } ],
 *   "meta": { ... }
 * }
 * </pre>
 *
 * @author liudy
 */
public final class AdminAuditDetail {

    /** 敏感字段变更占位，禁止写入明文。 */
    public static final String CHANGED = "[CHANGED]";

    private final List<Map<String, Object>> changes = new ArrayList<>();
    private final Map<String, Object> meta = new LinkedHashMap<>();

    private AdminAuditDetail() {
    }

    /**
     * @return 新构建器
     */
    public static AdminAuditDetail builder() {
        return new AdminAuditDetail();
    }

    /**
     * 记录字段变更；值相等则跳过。CREATE 时 {@code from} 传 {@code null}。
     * 数值比较按数学值（{@link BigDecimal#compareTo}），避免 scale 不同（如 0.7 vs 0.70）误记变更。
     *
     * @param field 业务字段名（camelCase）
     * @param from  变更前（可为 null）
     * @param to    变更后（可为 null）
     * @return this
     */
    public AdminAuditDetail change(String field, Object from, Object to) {
        if (sameValue(from, to)) {
            return this;
        }
        Map<String, Object> entry = new LinkedHashMap<>(3);
        entry.put("field", field);
        entry.put("from", from);
        entry.put("to", to);
        changes.add(entry);
        return this;
    }

    /**
     * 判断前后值是否等价：数值按数学相等，其它走 {@link Objects#equals}。
     */
    static boolean sameValue(Object from, Object to) {
        if (Objects.equals(from, to)) {
            return true;
        }
        if (from instanceof Number && to instanceof Number) {
            return toBigDecimal((Number) from).compareTo(toBigDecimal((Number) to)) == 0;
        }
        return false;
    }

    private static BigDecimal toBigDecimal(Number number) {
        if (number instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (number instanceof Float || number instanceof Double) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return BigDecimal.valueOf(number.longValue());
    }

    /**
     * CREATE 场景：{@code from=null}。
     *
     * @param field 字段
     * @param to    新值
     * @return this
     */
    public AdminAuditDetail created(String field, Object to) {
        return change(field, null, to);
    }

    /**
     * 敏感字段已变更（apiKey / password），不落明文。
     *
     * @param field 字段名
     * @return this
     */
    public AdminAuditDetail sensitiveChanged(String field) {
        return change(field, null, CHANGED);
    }

    /**
     * 附加非 diff 元数据。
     *
     * @param key   键
     * @param value 值
     * @return this
     */
    public AdminAuditDetail meta(String key, Object value) {
        meta.put(key, value);
        return this;
    }

    /**
     * @return 可直接传入 {@link AdminAuditLogRepository#insert} 的 detail Map
     */
    public Map<String, Object> build() {
        Map<String, Object> detail = new LinkedHashMap<>(2);
        detail.put("changes", List.copyOf(changes));
        if (!meta.isEmpty()) {
            detail.put("meta", Map.copyOf(meta));
        }
        return detail;
    }
}
