package com.wuji.assistant.server.admin.kb;

import java.util.List;
import java.util.Map;

/**
 * 文档分页。
 *
 * @author liudy
 */
public record AdminKbDocumentPage(
        List<Map<String, Object>> items,
        long total,
        int page,
        int size
) {
}
