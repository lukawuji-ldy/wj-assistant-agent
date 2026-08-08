package com.wuji.assistant.server.admin.mcp;

import com.wuji.assistant.agent.model.ApiKeyCipherService;
import com.wuji.assistant.agent.tool.McpToolRegistry;
import com.wuji.assistant.common.auth.AdminAuthUser;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.server.admin.audit.AdminAuditLogRepository;
import com.wuji.assistant.server.mcp.McpServerConnection;
import com.wuji.assistant.server.mcp.McpServerConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminMcpService P5.1/P5.2 契约测试。
 *
 * @author liudy
 */
@ExtendWith(MockitoExtension.class)
class AdminMcpServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private McpServerConnectionRepository connectionRepository;
    @Mock
    private ApiKeyCipherService apiKeyCipherService;
    @Mock
    private AdminAuditLogRepository auditLogRepository;
    @Mock
    private ObjectProvider<McpToolRegistry> mcpToolRegistry;
    @Mock
    private McpToolRegistry registry;

    private AdminMcpService service;
    private final AdminAuthUser operator = AdminAuthUser.of("a_op", "op", "SUPER_ADMIN");

    private static final McpServerConnection WUJI = new McpServerConnection(
            "wuji-mcp", "无忌 MCP", "http://127.0.0.1:8081", "/sse",
            "NONE", null, "ACTIVE", 0);

    @BeforeEach
    void setUp() {
        service = new AdminMcpService(
                jdbcTemplate, connectionRepository, apiKeyCipherService, auditLogRepository, mcpToolRegistry);
    }

    @Test
    void rejectEnableWhenUnbound() {
        when(connectionRepository.findByCode("wuji-mcp")).thenReturn(Optional.of(WUJI));
        AdminMcpToolsUpdateRequest req = new AdminMcpToolsUpdateRequest(List.of(
                new AdminMcpToolUpdateItem("echo_ping", false, true)));
        WujiException ex = assertThrows(WujiException.class, () -> service.updateTools(operator, "wuji-mcp", req));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        verify(jdbcTemplate, never()).update(contains("DELETE"), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void unbindDeletesRowAndReloads() {
        when(connectionRepository.findByCode("wuji-mcp")).thenReturn(Optional.of(WUJI));
        when(jdbcTemplate.query(contains("SELECT enabled FROM mcp_tool_binding"),
                any(ResultSetExtractor.class), eq("wuji-mcp"), eq("echo_ping")))
                .thenReturn(false);
        when(jdbcTemplate.update(contains("DELETE FROM mcp_tool_binding"), eq("wuji-mcp"), eq("echo_ping")))
                .thenReturn(1);
        stubListToolsEmptyBindings();
        when(mcpToolRegistry.getIfAvailable()).thenReturn(registry);

        AdminMcpToolsUpdateRequest req = new AdminMcpToolsUpdateRequest(List.of(
                new AdminMcpToolUpdateItem("echo_ping", false, false)));
        service.updateTools(operator, "wuji-mcp", req);

        verify(jdbcTemplate).update(contains("DELETE FROM mcp_tool_binding"), eq("wuji-mcp"), eq("echo_ping"));
        verify(registry).reloadServersAndRefresh();
        verify(auditLogRepository).insert(eq("a_op"), eq("UPDATE"), eq("MCP_TOOL_BINDING"), eq("wuji-mcp"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void bindInsertsDisabledByDefault() {
        when(connectionRepository.findByCode("wuji-mcp")).thenReturn(Optional.of(WUJI));
        when(jdbcTemplate.query(contains("SELECT enabled FROM mcp_tool_binding"),
                any(ResultSetExtractor.class), eq("wuji-mcp"), eq("echo_ping")))
                .thenReturn(null);
        when(jdbcTemplate.update(contains("INSERT INTO mcp_tool_binding"),
                any(), eq("wuji-mcp"), eq("echo_ping"), eq(false), any()))
                .thenReturn(1);
        stubListToolsEmptyBindings();
        when(mcpToolRegistry.getIfAvailable()).thenReturn(registry);

        AdminMcpToolsUpdateRequest req = new AdminMcpToolsUpdateRequest(List.of(
                new AdminMcpToolUpdateItem("echo_ping", true, false)));
        service.updateTools(operator, "wuji-mcp", req);

        verify(jdbcTemplate).update(contains("INSERT INTO mcp_tool_binding"),
                any(), eq("wuji-mcp"), eq("echo_ping"), eq(false), any());
        verify(registry).reloadServersAndRefresh();
    }

    @Test
    void deleteServer_rejectsWhenBindingsExist() {
        when(connectionRepository.findByCode("wuji-mcp")).thenReturn(Optional.of(WUJI));
        when(connectionRepository.countBindings("wuji-mcp")).thenReturn(2);
        WujiException ex = assertThrows(WujiException.class, () -> service.deleteServer(operator, "wuji-mcp"));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        verify(connectionRepository, never()).delete(any());
    }

    @SuppressWarnings("unchecked")
    private void stubListToolsEmptyBindings() {
        when(jdbcTemplate.query(contains("SELECT tool_name, enabled FROM mcp_tool_binding"),
                any(RowMapper.class), eq("wuji-mcp")))
                .thenReturn(Collections.emptyList());
    }
}
