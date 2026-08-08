package com.wuji.assistant.server.admin.user;

/**
 * 聊天用户只读视图（记忆/日志筛选）。
 *
 * @author liudy
 */
public record AdminChatUserView(
        String userId,
        String username,
        String nickname,
        String status
) {
}
