package com.wuji.assistant.memory.repo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * UserProfileRepository 管理查询单测。
 *
 * @author liudy
 */
@ExtendWith(MockitoExtension.class)
class UserProfileRepositoryAdminTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void existsActiveKey_trueWhenConflict() {
        UserProfileRepository repo = new UserProfileRepository(jdbcTemplate);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("u1"), eq("display_name")))
                .thenReturn(1);
        assertTrue(repo.existsActiveKey("u1", "display_name", null));
    }

    @Test
    void existsActiveKey_falseWhenEmpty() {
        UserProfileRepository repo = new UserProfileRepository(jdbcTemplate);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("u1"), eq("display_name")))
                .thenReturn(0);
        assertFalse(repo.existsActiveKey("u1", "display_name", null));
    }

    @Test
    void existsActiveKey_blankReturnsFalse() {
        UserProfileRepository repo = new UserProfileRepository(jdbcTemplate);
        assertFalse(repo.existsActiveKey("", "k", null));
        assertFalse(repo.existsActiveKey("u1", "", null));
    }
}
