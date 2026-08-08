package com.wuji.assistant.server.admin.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天用户只读查询（sys_user）。
 *
 * @author liudy
 */
@Service
public class AdminChatUserService {

    private static final RowMapper<AdminChatUserView> MAPPER = (rs, rowNum) -> new AdminChatUserView(
            rs.getString("user_id"),
            rs.getString("username"),
            rs.getString("nickname"),
            rs.getString("status")
    );

    private final JdbcTemplate jdbcTemplate;

    public AdminChatUserService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 分页列表；keyword 匹配 user_id / username / nickname。
     *
     * @param keyword 可选关键字
     * @param page    页码
     * @param size    页大小
     * @return 分页
     */
    public AdminChatUserPage list(String keyword, int page, int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim() + "%";
            where.append(" AND (user_id ILIKE ? OR username ILIKE ? OR nickname ILIKE ?)");
            args.add(like);
            args.add(like);
            args.add(like);
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user" + where, Long.class, args.toArray());
        long t = total == null ? 0L : total;
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(s);
        pageArgs.add((p - 1) * s);
        List<AdminChatUserView> items = jdbcTemplate.query("""
                SELECT user_id, username, nickname, status
                FROM sys_user
                """ + where + """
                 ORDER BY create_time ASC
                LIMIT ? OFFSET ?
                """, MAPPER, pageArgs.toArray());
        return new AdminChatUserPage(items, t, p, s);
    }
}
