package com.wuji.assistant.server.mcp;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * {@code mcp_server_ref} 读写。
 *
 * @author liudy
 */
@Repository
public class McpServerConnectionRepository {

    private static final RowMapper<McpServerConnection> ROW_MAPPER = (rs, i) -> new McpServerConnection(
            rs.getString("server_code"),
            rs.getString("display_name"),
            rs.getString("base_url"),
            rs.getString("sse_endpoint"),
            rs.getString("auth_type"),
            rs.getString("auth_token_cipher"),
            rs.getString("status"),
            rs.getInt("sort_order"));

    private final JdbcTemplate jdbcTemplate;

    public McpServerConnectionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<McpServerConnection> listAll() {
        return jdbcTemplate.query("""
                SELECT server_code, display_name, base_url, sse_endpoint, auth_type, auth_token_cipher,
                       status, sort_order
                FROM mcp_server_ref
                ORDER BY sort_order ASC, server_code ASC
                """, ROW_MAPPER);
    }

    public List<McpServerConnection> listActive() {
        return jdbcTemplate.query("""
                SELECT server_code, display_name, base_url, sse_endpoint, auth_type, auth_token_cipher,
                       status, sort_order
                FROM mcp_server_ref
                WHERE status = 'ACTIVE'
                ORDER BY sort_order ASC, server_code ASC
                """, ROW_MAPPER);
    }

    public Optional<McpServerConnection> findByCode(String serverCode) {
        if (!StringUtils.hasText(serverCode)) {
            return Optional.empty();
        }
        List<McpServerConnection> rows = jdbcTemplate.query("""
                SELECT server_code, display_name, base_url, sse_endpoint, auth_type, auth_token_cipher,
                       status, sort_order
                FROM mcp_server_ref
                WHERE server_code = ?
                """, ROW_MAPPER, serverCode.trim());
        return rows.stream().findFirst();
    }

    public int countActive() {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mcp_server_ref WHERE status = 'ACTIVE'", Integer.class);
        return n == null ? 0 : n;
    }

    public void insert(long id, McpServerConnection conn) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO mcp_server_ref
                (id, server_code, display_name, base_url, sse_endpoint, auth_type, auth_token_cipher,
                 status, sort_order, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                conn.serverCode(),
                conn.displayName(),
                conn.baseUrl(),
                blankToNull(conn.sseEndpoint()),
                StringUtils.hasText(conn.authType()) ? conn.authType() : "NONE",
                conn.authTokenCipher(),
                StringUtils.hasText(conn.status()) ? conn.status() : "ACTIVE",
                conn.sortOrder(),
                now,
                now);
    }

    public void update(McpServerConnection conn, boolean updateToken) {
        Timestamp now = Timestamp.from(Instant.now());
        if (updateToken) {
            jdbcTemplate.update("""
                    UPDATE mcp_server_ref
                    SET display_name = ?, base_url = ?, sse_endpoint = ?, auth_type = ?,
                        auth_token_cipher = ?, status = ?, sort_order = ?, update_time = ?
                    WHERE server_code = ?
                    """,
                    conn.displayName(),
                    conn.baseUrl(),
                    blankToNull(conn.sseEndpoint()),
                    conn.authType(),
                    conn.authTokenCipher(),
                    conn.status(),
                    conn.sortOrder(),
                    now,
                    conn.serverCode());
        } else {
            jdbcTemplate.update("""
                    UPDATE mcp_server_ref
                    SET display_name = ?, base_url = ?, sse_endpoint = ?, auth_type = ?,
                        status = ?, sort_order = ?, update_time = ?
                    WHERE server_code = ?
                    """,
                    conn.displayName(),
                    conn.baseUrl(),
                    blankToNull(conn.sseEndpoint()),
                    conn.authType(),
                    conn.status(),
                    conn.sortOrder(),
                    now,
                    conn.serverCode());
        }
    }

    public int countBindings(String serverCode) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mcp_tool_binding WHERE server_code = ?", Integer.class, serverCode);
        return n == null ? 0 : n;
    }

    public void delete(String serverCode) {
        jdbcTemplate.update("DELETE FROM mcp_server_ref WHERE server_code = ?", serverCode);
    }

    private static String blankToNull(String v) {
        return StringUtils.hasText(v) ? v.trim() : null;
    }
}
