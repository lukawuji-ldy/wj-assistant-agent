package com.wuji.assistant.rag.router;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 读取 {@code kb_collection}。表尚未迁移时返回空列表，路由退回全库检索。
 *
 * @author liudy
 */
@Repository
public class KbCollectionRepository {

    private static final Logger log = LoggerFactory.getLogger(KbCollectionRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public KbCollectionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @return 有说明文本的知识库，按 collection 排序
     */
    public List<KbCollection> listAll() {
        try {
            return jdbcTemplate.query("""
                            SELECT collection, description, domain_name, domain_description, llm_config_id
                            FROM kb_collection
                            WHERE description IS NOT NULL AND btrim(description) <> ''
                            ORDER BY collection
                            """,
                    (rs, rowNum) -> new KbCollection(
                            rs.getString("collection"),
                            rs.getString("description"),
                            rs.getString("domain_name"),
                            rs.getString("domain_description"),
                            rs.getString("llm_config_id")));
        } catch (DataAccessException e) {
            log.warn("kb_collection unavailable: {}", e.toString());
            return List.of();
        }
    }
}
