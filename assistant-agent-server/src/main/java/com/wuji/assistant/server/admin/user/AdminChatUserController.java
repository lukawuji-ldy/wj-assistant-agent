package com.wuji.assistant.server.admin.user;

import com.wuji.assistant.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 聊天用户只读 API（记忆/日志筛选用）。
 *
 * @author liudy
 */
@RestController
@RequestMapping("/api/admin/chat-users")
public class AdminChatUserController {

    private final AdminChatUserService adminChatUserService;

    public AdminChatUserController(AdminChatUserService adminChatUserService) {
        this.adminChatUserService = adminChatUserService;
    }

    @GetMapping
    public Mono<ApiResponse<AdminChatUserPage>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return Mono.fromCallable(() -> ApiResponse.ok(adminChatUserService.list(keyword, page, size)))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
