package com.wuji.assistant.rag.router;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 读取 {@code kb_domain}。表尚未迁移时返回空列表。
 *
 * @author liudy
 */
@Repository
public class KbDomainRepository {

    private static final Logger log = LoggerFactory.getLogger(KbDomainRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public KbDomainRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @return 已配置模型的领域，按名称排序
     */
    public List<KbDomain> listAll() {
        try {
            return jdbcTemplate.query("""
                            SELECT domain_name, description, llm_config_id
                            FROM kb_domain
                            WHERE domain_name IS NOT NULL AND btrim(domain_name) <> ''
                              AND llm_config_id IS NOT NULL AND btrim(llm_config_id) <> ''
                            ORDER BY domain_name
                            """,
                    (rs, rowNum) -> new KbDomain(
                            rs.getString("domain_name"),
                            rs.getString("description"),
                            rs.getString("llm_config_id")));
        } catch (DataAccessException e) {
            log.warn("kb_domain unavailable: {}", e.toString());
            return List.of();
        }
    }
}
