package com.wuji.assistant.server.admin.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.wuji.assistant.agent.model.ApiKeyCipherService;
import com.wuji.assistant.agent.tool.McpToolRegistry;
import com.wuji.assistant.common.auth.AdminAuthUser;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.common.util.IdGenerator;
import com.wuji.assistant.server.admin.audit.AdminAuditDetail;
import com.wuji.assistant.server.admin.audit.AdminAuditLogRepository;
import com.wuji.assistant.server.mcp.McpServerConnection;
import com.wuji.assistant.server.mcp.McpServerConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理台 MCP Server CRUD + 工具绑定（P5.1/P5.2）。
 *
 * @author liudy
 */
@Service
public class AdminMcpService {

    private static final Logger log = LoggerFactory.getLogger(AdminMcpService.class);

    private static final Duration INFO_TIMEOUT = Duration.ofSeconds(10);
    private static final String ROLE_SUPER = "SUPER_ADMIN";

    private final JdbcTemplate jdbcTemplate;
    private final McpServerConnectionRepository connectionRepository;
    private final ApiKeyCipherService apiKeyCipherService;
    private final AdminAuditLogRepository auditLogRepository;
    private final ObjectProvider<McpToolRegistry> mcpToolRegistry;

    public AdminMcpService(
            JdbcTemplate jdbcTemplate,
            McpServerConnectionRepository connectionRepository,
            ApiKeyCipherService apiKeyCipherService,
            AdminAuditLogRepository auditLogRepository,
            ObjectProvider<McpToolRegistry> mcpToolRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.connectionRepository = connectionRepository;
        this.apiKeyCipherService = apiKeyCipherService;
        this.auditLogRepository = auditLogRepository;
        this.mcpToolRegistry = mcpToolRegistry;
    }

    public List<AdminMcpServerView> listServers() {
        List<AdminMcpServerView> out = new ArrayList<>();
        for (McpServerConnection c : connectionRepository.listAll()) {
            out.add(toView(c, null, false));
        }
        return out;
    }

    public AdminMcpServerView getServer(AdminAuthUser admin, String serverCode, boolean revealToken) {
        McpServerConnection c = connectionRepository.findByCode(serverCode)
                .orElseThrow(() -> new WujiException(ErrorCode.NOT_FOUND, "未知 MCP server: " + serverCode));
        return toView(c, admin, revealToken);
    }

    public AdminMcpServerView createServer(AdminAuthUser operator, AdminMcpServerCreateRequest request) {
        if (request == null || !StringUtils.hasText(request.serverCode()) || !StringUtils.hasText(request.baseUrl())) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "serverCode / baseUrl 不能为空");
        }
        String code = request.serverCode().trim();
        if (connectionRepository.findByCode(code).isPresent()) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "serverCode 已存在: " + code);
        }
        String authType = normalizeAuthType(request.authType());
        String cipher = null;
        if ("BEARER".equals(authType)) {
            if (!StringUtils.hasText(request.authToken())) {
                throw new WujiException(ErrorCode.BAD_REQUEST, "BEARER 时 authToken 必填");
            }
            cipher = apiKeyCipherService.encrypt(request.authToken().trim());
        }
        McpServerConnection conn = new McpServerConnection(
                code,
                StringUtils.hasText(request.displayName()) ? request.displayName().trim() : code,
                request.baseUrl().trim(),
                blankToNull(request.sseEndpoint()),
                authType,
                cipher,
                "ACTIVE",
                request.sortOrder() == null ? 0 : request.sortOrder());
        connectionRepository.insert(IdGenerator.nextLong(), conn);
        AdminAuditDetail detail = AdminAuditDetail.builder()
                .change("serverCode", null, code)
                .change("baseUrl", null, conn.baseUrl())
                .change("authType", null, authType);
        if (cipher != null) {
            detail.sensitiveChanged("authToken");
        }
        auditLogRepository.insert(operator.adminId(), "CREATE", "MCP_SERVER", code, detail.build());
        reloadRegistry();
        return toView(conn, operator, false);
    }

    public AdminMcpServerView updateServer(AdminAuthUser operator, String serverCode, AdminMcpServerUpdateRequest request) {
        McpServerConnection existing = connectionRepository.findByCode(serverCode)
                .orElseThrow(() -> new WujiException(ErrorCode.NOT_FOUND, "未知 MCP server: " + serverCode));
        if (request == null) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "body 不能为空");
        }
        String authType = StringUtils.hasText(request.authType())
                ? normalizeAuthType(request.authType()) : existing.authType();
        boolean updateToken = StringUtils.hasText(request.authToken());
        String cipher = existing.authTokenCipher();
        if ("BEARER".equals(authType) && updateToken) {
            cipher = apiKeyCipherService.encrypt(request.authToken().trim());
        } else if ("NONE".equals(authType)) {
            cipher = null;
            updateToken = true;
        } else if ("BEARER".equals(authType) && !StringUtils.hasText(cipher) && !updateToken) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "BEARER 需要 authToken");
        }
        String status = StringUtils.hasText(request.status()) ? request.status().trim().toUpperCase() : existing.status();
        if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "status 须为 ACTIVE|DISABLED");
        }
        McpServerConnection updated = new McpServerConnection(
                existing.serverCode(),
                StringUtils.hasText(request.displayName()) ? request.displayName().trim() : existing.displayName(),
                StringUtils.hasText(request.baseUrl()) ? request.baseUrl().trim() : existing.baseUrl(),
                request.sseEndpoint() != null ? blankToNull(request.sseEndpoint()) : existing.sseEndpoint(),
                authType,
                cipher,
                status,
                request.sortOrder() == null ? existing.sortOrder() : request.sortOrder());
        connectionRepository.update(updated, updateToken || "NONE".equals(authType));
        AdminAuditDetail detail = AdminAuditDetail.builder()
                .change("displayName", existing.displayName(), updated.displayName())
                .change("baseUrl", existing.baseUrl(), updated.baseUrl())
                .change("authType", existing.authType(), updated.authType())
                .change("status", existing.status(), updated.status());
        if (updateToken && StringUtils.hasText(request.authToken())) {
            detail.sensitiveChanged("authToken");
        }
        auditLogRepository.insert(operator.adminId(), "UPDATE", "MCP_SERVER", serverCode, detail.build());
        reloadRegistry();
        return toView(updated, operator, false);
    }

    public void deleteServer(AdminAuthUser operator, String serverCode) {
        McpServerConnection existing = connectionRepository.findByCode(serverCode)
                .orElseThrow(() -> new WujiException(ErrorCode.NOT_FOUND, "未知 MCP server: " + serverCode));
        if (connectionRepository.countBindings(serverCode) > 0) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "仍有工具绑定，请先解绑或改用禁用 status=DISABLED");
        }
        connectionRepository.delete(serverCode);
        auditLogRepository.insert(
                operator.adminId(),
                "DELETE",
                "MCP_SERVER",
                serverCode,
                AdminAuditDetail.builder().change("serverCode", existing.serverCode(), null).build());
        reloadRegistry();
    }

    public AdminMcpToolsResponse listTools(String serverCode) {
        McpServerConnection conn = requireServer(serverCode);
        Map<String, BindingRow> bindings = loadBindings(serverCode);
        RemoteCatalog remote = fetchRemoteCatalog(conn);
        Map<String, AdminMcpToolView> merged = new LinkedHashMap<>();
        if (remote != null && remote.tools() != null) {
            for (RemoteTool t : remote.tools()) {
                if (t == null || !StringUtils.hasText(t.name())) {
                    continue;
                }
                String name = t.name().trim();
                BindingRow b = bindings.remove(name);
                boolean bound = b != null;
                boolean enabled = bound && b.enabled();
                merged.put(name, new AdminMcpToolView(name, t.description(), enabled, bound));
            }
        }
        for (Map.Entry<String, BindingRow> e : bindings.entrySet()) {
            BindingRow b = e.getValue();
            merged.put(e.getKey(), new AdminMcpToolView(e.getKey(), null, b.enabled(), true));
        }
        String source = remote == null ? "DB_ONLY" : "REMOTE_MERGED";
        return new AdminMcpToolsResponse(List.copyOf(merged.values()), source);
    }

    public AdminMcpToolDetailView getToolDetail(String serverCode, String toolName) {
        McpServerConnection conn = requireServer(serverCode);
        if (!StringUtils.hasText(toolName)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "toolName 不能为空");
        }
        String name = toolName.trim();
        BindingRow binding = loadBindings(serverCode).get(name);
        boolean bound = binding != null;
        boolean enabled = bound && binding.enabled();
        RemoteCatalog remote = fetchRemoteCatalog(conn);
        if (remote != null && remote.tools() != null) {
            for (RemoteTool t : remote.tools()) {
                if (t != null && name.equals(t.name())) {
                    return new AdminMcpToolDetailView(
                            name,
                            t.description(),
                            t.inputSchema(),
                            bound,
                            enabled,
                            serverCode,
                            remote.serverVersion(),
                            remote.toolHash(),
                            "REMOTE_MERGED");
                }
            }
        }
        if (bound) {
            return new AdminMcpToolDetailView(
                    name, null, null, true, enabled, serverCode, null, null, "DB_ONLY");
        }
        return new AdminMcpToolDetailView(
                name, null, null, false, false, serverCode, null, null, "NOT_FOUND");
    }

    public AdminMcpToolsResponse updateTools(AdminAuthUser operator, String serverCode, AdminMcpToolsUpdateRequest request) {
        requireServer(serverCode);
        if (request == null || request.tools() == null || request.tools().isEmpty()) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "tools 不能为空");
        }
        Timestamp now = Timestamp.from(Instant.now());
        AdminAuditDetail detail = AdminAuditDetail.builder();
        for (AdminMcpToolUpdateItem item : request.tools()) {
            if (item == null || !StringUtils.hasText(item.toolName())) {
                continue;
            }
            if (item.enabled() && !item.bound()) {
                throw new WujiException(ErrorCode.BAD_REQUEST, "未绑定工具不可启用: " + item.toolName().trim());
            }
            String toolName = item.toolName().trim();
            Boolean enabledBefore = jdbcTemplate.query("""
                    SELECT enabled FROM mcp_tool_binding WHERE server_code = ? AND tool_name = ?
                    """, rs -> rs.next() ? rs.getBoolean(1) : null, serverCode, toolName);
            boolean boundBefore = enabledBefore != null;

            if (!item.bound()) {
                jdbcTemplate.update("""
                        DELETE FROM mcp_tool_binding WHERE server_code = ? AND tool_name = ?
                        """, serverCode, toolName);
                detail.change("bound:" + toolName, boundBefore, false);
                detail.change("enabled:" + toolName, enabledBefore, false);
                continue;
            }

            boolean enabled = item.enabled();
            if (boundBefore) {
                jdbcTemplate.update("""
                        UPDATE mcp_tool_binding SET enabled = ?, update_time = ?
                        WHERE server_code = ? AND tool_name = ?
                        """, enabled, now, serverCode, toolName);
            } else {
                jdbcTemplate.update("""
                        INSERT INTO mcp_tool_binding (id, server_code, tool_name, enabled, update_time)
                        VALUES (?, ?, ?, ?, ?)
                        """, IdGenerator.nextLong(), serverCode, toolName, enabled, now);
            }
            detail.change("bound:" + toolName, boundBefore, true);
            detail.change("enabled:" + toolName, enabledBefore, enabled);
        }
        auditLogRepository.insert(
                operator.adminId(),
                "UPDATE",
                "MCP_TOOL_BINDING",
                serverCode,
                detail.meta("count", request.tools().size()).build());
        reloadRegistry();
        return listTools(serverCode);
    }

    private void reloadRegistry() {
        McpToolRegistry registry = mcpToolRegistry.getIfAvailable();
        if (registry != null) {
            registry.reloadServersAndRefresh();
        }
    }

    private McpServerConnection requireServer(String serverCode) {
        return connectionRepository.findByCode(serverCode)
                .orElseThrow(() -> new WujiException(ErrorCode.NOT_FOUND, "未知 MCP server: " + serverCode));
    }

    private AdminMcpServerView toView(McpServerConnection c, AdminAuthUser admin, boolean revealToken) {
        String masked = apiKeyCipherService.mask(c.authTokenCipher());
        String preview = null;
        if (revealToken && admin != null && ROLE_SUPER.equals(admin.role())
                && StringUtils.hasText(c.authTokenCipher())) {
            preview = apiKeyCipherService.decrypt(c.authTokenCipher());
        }
        return new AdminMcpServerView(
                c.serverCode(),
                c.displayName(),
                c.status(),
                c.baseUrl(),
                c.sseEndpoint(),
                c.authType(),
                masked,
                preview,
                c.sortOrder());
    }

    private Map<String, BindingRow> loadBindings(String serverCode) {
        List<BindingRow> rows = jdbcTemplate.query("""
                SELECT tool_name, enabled FROM mcp_tool_binding WHERE server_code = ?
                """, (rs, i) -> new BindingRow(rs.getString("tool_name"), rs.getBoolean("enabled")), serverCode);
        Map<String, BindingRow> map = new LinkedHashMap<>();
        for (BindingRow row : rows) {
            if (row != null && StringUtils.hasText(row.toolName())) {
                map.put(row.toolName().trim(), row);
            }
        }
        return map;
    }

    private RemoteCatalog fetchRemoteCatalog(McpServerConnection conn) {
        try {
            WebClient.Builder builder = WebClient.builder().baseUrl(conn.baseUrl());
            if (conn.bearer() && StringUtils.hasText(conn.authTokenCipher())) {
                String plain = apiKeyCipherService.decrypt(conn.authTokenCipher());
                if (StringUtils.hasText(plain)) {
                    builder.defaultHeader("Authorization", "Bearer " + plain);
                }
            }
            return builder.build()
                    .get()
                    .uri("/mcp/info")
                    .retrieve()
                    .bodyToMono(RemoteCatalog.class)
                    .timeout(INFO_TIMEOUT)
                    .block();
        } catch (WebClientResponseException ex) {
            log.warn("MCP /mcp/info unreachable serverCode={} url={} status={} authType={} hint={}",
                    conn.serverCode(),
                    conn.baseUrl(),
                    ex.getStatusCode().value(),
                    conn.authType(),
                    ex.getStatusCode().value() == 401
                            ? "mcp-server 可能开启了 Bearer，请在管理台把 authType 设为 BEARER 并填写与 WUJI_MCP_API_KEY 一致的 token"
                            : ex.getMessage());
            return null;
        } catch (Exception ex) {
            log.warn("MCP /mcp/info unreachable serverCode={} url={} authType={}: {}",
                    conn.serverCode(), conn.baseUrl(), conn.authType(), ex.toString());
            return null;
        }
    }

    private static String normalizeAuthType(String authType) {
        if (!StringUtils.hasText(authType)) {
            return "NONE";
        }
        String t = authType.trim().toUpperCase();
        if (!"NONE".equals(t) && !"BEARER".equals(t)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "authType 须为 NONE|BEARER");
        }
        return t;
    }

    private static String blankToNull(String v) {
        return StringUtils.hasText(v) ? v.trim() : null;
    }

    private record BindingRow(String toolName, boolean enabled) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RemoteCatalog(String serverVersion, String toolHash, List<RemoteTool> tools) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RemoteTool(String name, String description, JsonNode inputSchema) {
    }
}
