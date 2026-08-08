package com.wuji.assistant.server.admin.prompt;

import java.time.Instant;

/**
 * 单版本提示词（来自 prompt_template_version）。
 *
 * @param id          主键
 * @param code        编码
 * @param name        名称
 * @param role        SYSTEM/USER
 * @param content     正文
 * @param version     版本号
 * @param status      DRAFT|PUBLISHED|SUPERSEDED
 * @param changeNote  变更说明
 * @param createdBy   操作者 admin_id
 * @param createTime  创建/草稿更新时间
 * @param publishTime 发布时间；草稿为 null
 * @author liudy
 */
public record AdminPromptVersionView(
        long id,
        String code,
        String name,
        String role,
        String content,
        int version,
        String status,
        String changeNote,
        String createdBy,
        Instant createTime,
        Instant publishTime
) {
}
