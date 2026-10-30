package com.wuji.assistant.rag.router;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code kb_collection} 领域列映射。
 *
 * @author liudy
 */
class KbCollectionRepositoryTest {

    @Test
    void listAll_readsDomainColumns() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            assertTrue(sql.contains("domain_name"));
            assertTrue(sql.contains("domain_description"));
            assertTrue(sql.contains("llm_config_id"));
            @SuppressWarnings("unchecked")
            RowMapper<KbCollection> mapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("collection")).thenReturn("sales_faq");
            when(rs.getString("description")).thenReturn("产品");
            when(rs.getString("domain_name")).thenReturn("sales");
            when(rs.getString("domain_description")).thenReturn("销售");
            when(rs.getString("llm_config_id")).thenReturn("llm_sales");
            return List.of(mapper.mapRow(rs, 0));
        });
        List<KbCollection> rows = new KbCollectionRepository(jdbc).listAll();
        assertEquals(1, rows.size());
        KbCollection row = rows.get(0);
        assertEquals("sales_faq", row.collection());
        assertEquals("产品", row.description());
        assertEquals("sales", row.domainName());
        assertEquals("销售", row.domainDescription());
        assertEquals("llm_sales", row.llmConfigId());
    }
}
