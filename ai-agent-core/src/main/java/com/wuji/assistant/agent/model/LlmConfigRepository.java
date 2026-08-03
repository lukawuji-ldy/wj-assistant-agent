package com.wuji.assistant.agent.model;

import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

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
}
