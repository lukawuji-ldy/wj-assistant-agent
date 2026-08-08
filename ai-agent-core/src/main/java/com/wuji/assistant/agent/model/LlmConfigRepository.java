package com.wuji.assistant.agent.model;

import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.common.util.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * llm_config 仓储。
 *
 * @author liudy
 */
@Repository
public class LlmConfigRepository {

    private static final RowMapper<LlmConfigRecord> MAPPER = (rs, rowNum) -> {
        LlmConfigRecord r = new LlmConfigRecord();
        r.setId(rs.getLong("id"));
        r.setConfigId(rs.getString("config_id"));
        r.setName(rs.getString("name"));
        r.setProvider(rs.getString("provider"));
        r.setModelKind(rs.getString("model_kind"));
        r.setBaseUrl(rs.getString("base_url"));
        r.setApiKeyCipher(rs.getString("api_key_cipher"));
        r.setModel(rs.getString("model"));
        r.setTemperature(rs.getBigDecimal("temperature"));
        int maxTokens = rs.getInt("max_tokens");
        r.setMaxTokens(rs.wasNull() ? null : maxTokens);
        Object extra = rs.getObject("extra_json");
        r.setExtraJson(extra == null ? null : extra.toString());
        r.setStatus(rs.getString("status"));
        Timestamp ct = rs.getTimestamp("create_time");
        Timestamp ut = rs.getTimestamp("update_time");
        if (ct != null) {
            r.setCreateTime(ct.toInstant().atOffset(java.time.ZoneOffset.UTC));
        }
        if (ut != null) {
            r.setUpdateTime(ut.toInstant().atOffset(java.time.ZoneOffset.UTC));
        }
        return r;
    };

    private final JdbcTemplate jdbcTemplate;

    public LlmConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 按 config_id 加载 ACTIVE 配置（不校验 model_kind）。
     *
     * @param configId 配置业务键
     * @return 配置
     */
    public LlmConfigRecord requireActive(String configId) {
        List<LlmConfigRecord> list = jdbcTemplate.query("""
                SELECT * FROM llm_config WHERE config_id = ? AND status = 'ACTIVE'
                """, MAPPER, configId);
        return list.stream().findFirst()
                .orElseThrow(() -> new WujiException(ErrorCode.MODEL_UNAVAILABLE, "未找到可用 LLM 配置: " + configId));
    }

    /**
     * 按 config_id 加载 ACTIVE 配置，并校验 model_kind。
     *
     * @param configId  配置业务键
     * @param modelKind 期望的 CHAT / EMBEDDING
     * @return 配置
     */
    public LlmConfigRecord requireActive(String configId, String modelKind) {
        LlmConfigRecord cfg = requireActive(configId);
        if (!StringUtils.hasText(modelKind)) {
            return cfg;
        }
        if (!modelKind.equalsIgnoreCase(cfg.getModelKind())) {
            throw new WujiException(ErrorCode.MODEL_UNAVAILABLE,
                    "LLM 配置 kind 不匹配: " + configId + " expected=" + modelKind + " actual=" + cfg.getModelKind());
        }
        return cfg;
    }

    /**
     * 按 config_id 查询（任意状态）。
     *
     * @param configId 业务键
     * @return 可选记录
     */
    public Optional<LlmConfigRecord> findByConfigId(String configId) {
        List<LlmConfigRecord> list = jdbcTemplate.query(
                "SELECT * FROM llm_config WHERE config_id = ?", MAPPER, configId);
        return list.stream().findFirst();
    }

    /**
     * 分页列表。
     *
     * @param modelKind 可选 CHAT/EMBEDDING
     * @param status    可选 ACTIVE/DISABLED
     * @param limit     页大小
     * @param offset    偏移
     * @return 行
     */
    public List<LlmConfigRecord> list(String modelKind, String status, int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM llm_config WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(modelKind)) {
            sql.append(" AND model_kind = ?");
            args.add(modelKind);
        }
        if (StringUtils.hasText(status)) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY create_time ASC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    /**
     * 计数。
     *
     * @param modelKind 可选
     * @param status    可选
     * @return 总数
     */
    public long count(String modelKind, String status) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM llm_config WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(modelKind)) {
            sql.append(" AND model_kind = ?");
            args.add(modelKind);
        }
        if (StringUtils.hasText(status)) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        Long total = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return total == null ? 0L : total;
    }

    /**
     * 插入新配置。
     *
     * @param record 记录（含密文）
     */
    public void insert(LlmConfigRecord record) {
        Timestamp now = Timestamp.from(Instant.now());
        long id = record.getId() != null ? record.getId() : IdGenerator.nextLong();
        jdbcTemplate.update("""
                INSERT INTO llm_config
                (id, config_id, name, provider, model_kind, base_url, api_key_cipher, model,
                 temperature, max_tokens, extra_json, status, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?)
                """,
                id,
                record.getConfigId(),
                record.getName(),
                record.getProvider(),
                record.getModelKind(),
                record.getBaseUrl(),
                record.getApiKeyCipher(),
                record.getModel(),
                record.getTemperature(),
                record.getMaxTokens(),
                blankToEmptyJson(record.getExtraJson()),
                record.getStatus(),
                now,
                now);
        record.setId(id);
    }

    /**
     * 更新（api_key_cipher 传 null 表示不改 Key）。
     *
     * @param record         字段
     * @param updateApiKey   是否更新 Key
     */
    public void update(LlmConfigRecord record, boolean updateApiKey) {
        Timestamp now = Timestamp.from(Instant.now());
        if (updateApiKey) {
            jdbcTemplate.update("""
                    UPDATE llm_config SET
                      name = ?, provider = ?, model_kind = ?, base_url = ?, api_key_cipher = ?,
                      model = ?, temperature = ?, max_tokens = ?, extra_json = CAST(? AS jsonb),
                      status = ?, update_time = ?
                    WHERE config_id = ?
                    """,
                    record.getName(),
                    record.getProvider(),
                    record.getModelKind(),
                    record.getBaseUrl(),
                    record.getApiKeyCipher(),
                    record.getModel(),
                    record.getTemperature(),
                    record.getMaxTokens(),
                    blankToEmptyJson(record.getExtraJson()),
                    record.getStatus(),
                    now,
                    record.getConfigId());
        } else {
            jdbcTemplate.update("""
                    UPDATE llm_config SET
                      name = ?, provider = ?, model_kind = ?, base_url = ?,
                      model = ?, temperature = ?, max_tokens = ?, extra_json = CAST(? AS jsonb),
                      status = ?, update_time = ?
                    WHERE config_id = ?
                    """,
                    record.getName(),
                    record.getProvider(),
                    record.getModelKind(),
                    record.getBaseUrl(),
                    record.getModel(),
                    record.getTemperature(),
                    record.getMaxTokens(),
                    blankToEmptyJson(record.getExtraJson()),
                    record.getStatus(),
                    now,
                    record.getConfigId());
        }
    }

    /**
     * 软禁用。
     *
     * @param configId 业务键
     * @return 影响行数
     */
    public int softDisable(String configId) {
        return jdbcTemplate.update("""
                UPDATE llm_config SET status = 'DISABLED', update_time = ? WHERE config_id = ?
                """, Timestamp.from(Instant.now()), configId);
    }

    private static String blankToEmptyJson(String extraJson) {
        if (!StringUtils.hasText(extraJson)) {
            return "{}";
        }
        return extraJson;
    }
}
