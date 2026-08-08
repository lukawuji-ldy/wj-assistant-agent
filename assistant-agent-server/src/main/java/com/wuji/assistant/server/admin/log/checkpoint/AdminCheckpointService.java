package com.wuji.assistant.server.admin.log.checkpoint;

import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 管理台 Checkpoint 只读回放。
 *
 * @author liudy
 */
@Service
public class AdminCheckpointService {

    private final AdminCheckpointRepository repository;
    private final AdminCheckpointStateDecoder stateDecoder;

    public AdminCheckpointService(
            AdminCheckpointRepository repository,
            AdminCheckpointStateDecoder stateDecoder) {
        this.repository = repository;
        this.stateDecoder = stateDecoder;
    }

    /**
     * 线程分页。
     */
    public AdminCheckpointThreadPage listThreads(
            String threadName,
            String userId,
            String conversationId,
            Boolean isReleased,
            Instant savedFrom,
            Instant savedTo,
            int page,
            int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        if (savedFrom != null && savedTo != null && savedFrom.isAfter(savedTo)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "savedFrom 不能晚于 savedTo");
        }
        String resolvedName = resolveThreadName(threadName, userId, conversationId);
        long total = repository.countThreads(resolvedName, isReleased, savedFrom, savedTo);
        List<AdminCheckpointThreadSummary> items =
                repository.listThreads(resolvedName, isReleased, savedFrom, savedTo, s, (p - 1) * s);
        return new AdminCheckpointThreadPage(items, total, p, s);
    }

    /**
     * 线程时间线。
     */
    public AdminCheckpointThreadDetail getThread(String threadId) {
        UUID id = parseUuid(threadId, "threadId");
        AdminCheckpointThreadSummary thread = repository.findThread(id)
                .orElseThrow(() -> new WujiException(ErrorCode.NOT_FOUND, "Checkpoint 线程不存在: " + threadId));
        List<AdminCheckpointStepSummary> steps = AdminCheckpointRepository.orderSteps(repository.listRawSteps(id));
        return new AdminCheckpointThreadDetail(thread, steps);
    }

    /**
     * 单步明细（含 binaryPayload 解码）。
     */
    public AdminCheckpointDetail getCheckpoint(String checkpointId) {
        UUID id = parseUuid(checkpointId, "checkpointId");
        AdminCheckpointRaw raw = repository.findCheckpoint(id)
                .orElseThrow(() -> new WujiException(ErrorCode.NOT_FOUND, "Checkpoint 不存在: " + checkpointId));
        AdminCheckpointStateDecoder.DecodedState decoded = stateDecoder.decode(raw.stateData());
        return new AdminCheckpointDetail(
                raw.checkpointId(),
                raw.parentCheckpointId(),
                raw.threadId(),
                raw.nodeId(),
                raw.nextNodeId(),
                raw.savedAt(),
                raw.stateContentType(),
                raw.stateData(),
                decoded.decodedState(),
                decoded.stateEntries(),
                decoded.messages(),
                decoded.decodeError());
    }

    static String resolveThreadName(String threadName, String userId, String conversationId) {
        if (StringUtils.hasText(threadName)) {
            return threadName.trim();
        }
        boolean hasUser = StringUtils.hasText(userId);
        boolean hasConv = StringUtils.hasText(conversationId);
        if (hasUser && hasConv) {
            return userId.trim() + ":" + conversationId.trim();
        }
        if (hasUser || hasConv) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "userId 与 conversationId 需同时提供");
        }
        return null;
    }

    private static UUID parseUuid(String raw, String field) {
        if (!StringUtils.hasText(raw)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, field + " 不能为空");
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new WujiException(ErrorCode.BAD_REQUEST, field + " 非法 UUID: " + raw);
        }
    }
}
