package com.wuji.assistant.server.admin.mcp;

import com.wuji.assistant.common.api.ApiResponse;
import com.wuji.assistant.server.admin.security.CurrentAdmin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * 管理台 MCP Server / 工具绑定 API（P5.2）。
 *
 * @author liudy
 */
@RestController
@RequestMapping("/api/admin/mcp")
public class AdminMcpController {

    private final AdminMcpService adminMcpService;

    public AdminMcpController(AdminMcpService adminMcpService) {
        this.adminMcpService = adminMcpService;
    }

    @GetMapping("/servers")
    public Mono<ApiResponse<List<AdminMcpServerView>>> listServers() {
        return Mono.fromCallable(() -> ApiResponse.ok(adminMcpService.listServers()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/servers/{serverCode}")
    public Mono<ApiResponse<AdminMcpServerView>> getServer(
            @PathVariable String serverCode,
            @RequestParam(defaultValue = "false") boolean revealToken) {
        return CurrentAdmin.require().flatMap(op ->
                Mono.fromCallable(() -> ApiResponse.ok(adminMcpService.getServer(op, serverCode, revealToken)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @PostMapping("/servers")
    public Mono<ApiResponse<AdminMcpServerView>> createServer(@RequestBody AdminMcpServerCreateRequest request) {
        return CurrentAdmin.require().flatMap(op ->
                Mono.fromCallable(() -> ApiResponse.ok(adminMcpService.createServer(op, request)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @PutMapping("/servers/{serverCode}")
    public Mono<ApiResponse<AdminMcpServerView>> updateServer(
            @PathVariable String serverCode,
            @RequestBody AdminMcpServerUpdateRequest request) {
        return CurrentAdmin.require().flatMap(op ->
                Mono.fromCallable(() -> ApiResponse.ok(adminMcpService.updateServer(op, serverCode, request)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @DeleteMapping("/servers/{serverCode}")
    public Mono<ApiResponse<Void>> deleteServer(@PathVariable String serverCode) {
        return CurrentAdmin.require().flatMap(op ->
                Mono.fromCallable(() -> {
                            adminMcpService.deleteServer(op, serverCode);
                            return ApiResponse.<Void>ok(null);
                        })
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @GetMapping("/servers/{serverCode}/tools")
    public Mono<ApiResponse<AdminMcpToolsResponse>> listTools(@PathVariable String serverCode) {
        return Mono.fromCallable(() -> ApiResponse.ok(adminMcpService.listTools(serverCode)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/servers/{serverCode}/tools/{toolName}")
    public Mono<ApiResponse<AdminMcpToolDetailView>> getToolDetail(
            @PathVariable String serverCode,
            @PathVariable String toolName) {
        return Mono.fromCallable(() -> ApiResponse.ok(adminMcpService.getToolDetail(serverCode, toolName)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PutMapping("/servers/{serverCode}/tools")
    public Mono<ApiResponse<AdminMcpToolsResponse>> updateTools(
            @PathVariable String serverCode,
            @RequestBody AdminMcpToolsUpdateRequest request) {
        return CurrentAdmin.require().flatMap(op ->
                Mono.fromCallable(() -> ApiResponse.ok(adminMcpService.updateTools(op, serverCode, request)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }
}
