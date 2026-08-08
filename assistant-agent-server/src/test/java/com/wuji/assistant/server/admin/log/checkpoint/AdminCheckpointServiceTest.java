package com.wuji.assistant.server.admin.log.checkpoint;

import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * AdminCheckpointService / 排序工具测试。
 *
 * @author liudy
 */
@ExtendWith(MockitoExtension.class)
class AdminCheckpointServiceTest {

    @Mock
    private AdminCheckpointRepository repository;

    @Mock
    private AdminCheckpointStateDecoder stateDecoder;

    private AdminCheckpointService service;

    @BeforeEach
    void setUp() {
        service = new AdminCheckpointService(repository, stateDecoder);
    }

    @Test
    void resolveThreadNameFromUserAndConversation() {
        assertEquals("u1:c1", AdminCheckpointService.resolveThreadName(null, "u1", "c1"));
        assertEquals("explicit", AdminCheckpointService.resolveThreadName("explicit", "u1", "c1"));
        assertNull(AdminCheckpointService.resolveThreadName(null, null, null));
        WujiException ex = assertThrows(WujiException.class,
                () -> AdminCheckpointService.resolveThreadName(null, "u1", null));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void parseThreadNameSplitsOnColon() {
        AdminCheckpointRepository.ThreadNameParts parts = AdminCheckpointRepository.parseThreadName("userA:convB");
        assertEquals("userA", parts.userId());
        assertEquals("convB", parts.conversationId());
        assertNull(AdminCheckpointRepository.parseThreadName("no-colon").userId());
    }

    @Test
    void orderStepsFollowsParentChainAndComputesDelta() {
        OffsetDateTime t1 = OffsetDateTime.of(2026, 8, 7, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime t2 = OffsetDateTime.of(2026, 8, 7, 10, 0, 1, 0, ZoneOffset.UTC);
        OffsetDateTime t3 = OffsetDateTime.of(2026, 8, 7, 10, 0, 3, 0, ZoneOffset.UTC);
        List<AdminCheckpointRepository.RawStep> raw = List.of(
                new AdminCheckpointRepository.RawStep("c3", "c2", "node3", null, t3, "json"),
                new AdminCheckpointRepository.RawStep("c1", null, "node1", "node2", t1, "json"),
                new AdminCheckpointRepository.RawStep("c2", "c1", "node2", "node3", t2, "json")
        );
        List<AdminCheckpointStepSummary> ordered = AdminCheckpointRepository.orderSteps(raw);
        assertEquals(List.of("c1", "c2", "c3"), ordered.stream().map(AdminCheckpointStepSummary::checkpointId).toList());
        assertNull(ordered.get(0).deltaMs());
        assertEquals(1000L, ordered.get(1).deltaMs());
        assertEquals(2000L, ordered.get(2).deltaMs());
        assertEquals(1, ordered.get(0).stepIndex());
    }

    @Test
    void getThreadNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findThread(id)).thenReturn(Optional.empty());
        WujiException ex = assertThrows(WujiException.class, () -> service.getThread(id.toString()));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void getThreadReturnsOrderedSteps() {
        UUID id = UUID.randomUUID();
        AdminCheckpointThreadSummary head = new AdminCheckpointThreadSummary(
                id.toString(), "u1:c1", "u1", "c1", false, 2, null);
        when(repository.findThread(id)).thenReturn(Optional.of(head));
        OffsetDateTime t1 = OffsetDateTime.of(2026, 8, 7, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime t2 = OffsetDateTime.of(2026, 8, 7, 10, 0, 2, 0, ZoneOffset.UTC);
        when(repository.listRawSteps(eq(id))).thenReturn(List.of(
                new AdminCheckpointRepository.RawStep("a", null, "start", "mid", t1, "json"),
                new AdminCheckpointRepository.RawStep("b", "a", "mid", "end", t2, "json")
        ));

        AdminCheckpointThreadDetail detail = service.getThread(id.toString());
        assertEquals(2, detail.steps().size());
        assertEquals("a", detail.steps().get(0).checkpointId());
        assertEquals(2000L, detail.steps().get(1).deltaMs());
        assertTrue(detail.thread().threadName().contains("u1"));
    }

    @Test
    void getCheckpointDecodesState() {
        UUID id = UUID.randomUUID();
        AdminCheckpointRaw raw = new AdminCheckpointRaw(
                id.toString(), null, UUID.randomUUID().toString(), "node", "next",
                null, "application/json", null);
        when(repository.findCheckpoint(id)).thenReturn(Optional.of(raw));
        when(stateDecoder.decode(null)).thenReturn(new AdminCheckpointStateDecoder.DecodedState(
                null,
                List.of(new AdminCheckpointStateEntry("jump_to", "string", "x", null)),
                List.of(new AdminCheckpointMessageView(0, "USER", "hi", null, null)),
                null));

        AdminCheckpointDetail detail = service.getCheckpoint(id.toString());
        assertEquals(1, detail.messages().size());
        assertEquals("USER", detail.messages().get(0).role());
        assertEquals("jump_to", detail.stateEntries().get(0).key());
    }

    @Test
    void getCheckpointRejectsBadUuid() {
        WujiException ex = assertThrows(WujiException.class, () -> service.getCheckpoint("not-uuid"));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }
}
