package com.wuji.assistant.agent.checkpoint;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 只读解析 {@code graphthread.thread_id}。知识库测试用它把主线程名还原成可跳转的线程 id。
 * <p>
 * Flyway / schema DDL 未加引号建表，PG 折叠为小写 {@code graphthread}。
 * Saver 为 MemorySaver 或无该表时返回空，不抛异常。
 *
 * @author liudy
 */
@Repository
public class CheckpointThreadReader {

    private final JdbcTemplate jdbcTemplate;

    public CheckpointThreadReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @param threadName 形如 {@code kb-test:{uuid}}
     * @return 线程 id；未落库或表缺失时为空
     */
    public Optional<String> findThreadIdByName(String threadName) {
        if (!StringUtils.hasText(threadName)) {
            return Optional.empty();
        }
        try {
            return jdbcTemplate.query("""
                            SELECT thread_id FROM graphthread WHERE thread_name = ? LIMIT 1
                            """, (rs, rowNum) -> rs.getObject("thread_id").toString(), threadName.trim())
                    .stream()
                    .findFirst();
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }
}
