package com.wuji.assistant.agent.model;

import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

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
        r.setBaseUrl(rs.getString("base_url"));
        r.setApiKeyCipher(rs.getString("api_key_cipher"));
        r.setModel(rs.getString("model"));
        r.setTemperature(rs.getBigDecimal("temperature"));
        int maxTokens = rs.getInt("max_tokens");
        r.setMaxTokens(rs.wasNull() ? null : maxTokens);
        r.setStatus(rs.getString("status"));
        return r;
    };

    private final JdbcTemplate jdbcTemplate;

    public LlmConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 按 config_id 加载 ACTIVE 配置。
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
}
