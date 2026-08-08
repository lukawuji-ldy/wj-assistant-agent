package com.wuji.assistant.server.admin.log.checkpoint;

import java.util.List;

/**
 * 线程头 + 有序步骤摘要。
 *
 * @author liudy
 */
public record AdminCheckpointThreadDetail(
        AdminCheckpointThreadSummary thread,
        List<AdminCheckpointStepSummary> steps
) {
}
