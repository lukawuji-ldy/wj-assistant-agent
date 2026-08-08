package com.wuji.assistant.server.admin.log.checkpoint;

import java.util.List;

/**
 * Checkpoint 线程分页。
 *
 * @author liudy
 */
public record AdminCheckpointThreadPage(
        List<AdminCheckpointThreadSummary> items,
        long total,
        int page,
        int size
) {
}
