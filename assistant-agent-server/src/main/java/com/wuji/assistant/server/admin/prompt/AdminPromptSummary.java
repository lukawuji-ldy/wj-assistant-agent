package com.wuji.assistant.server.admin.prompt;

/**
 * 按 code 聚合的提示词摘要（主表 + 草稿态）。
 *
 * @param code              模板编码
 * @param name              线上名称
 * @param role              SYSTEM/USER
 * @param publishedVersion  当前已发布版本；无主表则 null
 * @param draftVersion      草稿版本；无则 null
 * @param hasDraft          是否存在草稿
 * @param status            主表 status（ACTIVE/DISABLED）；无主表则 null
 * @param latestVersion     版本表最大 version（含草稿）
 * @author liudy
 */
public record AdminPromptSummary(
        String code,
        String name,
        String role,
        Integer publishedVersion,
        Integer draftVersion,
        boolean hasDraft,
        String status,
        int latestVersion
) {
}
