package com.wuji.assistant.server.admin.user;

import java.util.List;

/**
 * 聊天用户分页。
 *
 * @author liudy
 */
public record AdminChatUserPage(List<AdminChatUserView> items, long total, int page, int size) {
}
