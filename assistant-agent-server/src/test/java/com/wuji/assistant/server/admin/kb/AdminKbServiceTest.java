package com.wuji.assistant.server.admin.kb;

import com.wuji.assistant.common.auth.AdminAuthUser;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.rag.ingest.DocumentIngestService;
import com.wuji.assistant.rag.ingest.IngestRequest;
import com.wuji.assistant.rag.ingest.IngestResult;
import com.wuji.assistant.rag.ingest.KbChunkService;
import com.wuji.assistant.rag.ingest.KbChunkView;
import com.wuji.assistant.rag.ingest.KbChunkWriteResult;
import com.wuji.assistant.rag.ingest.KbDocumentQueryService;
import com.wuji.assistant.rag.ingest.KbChunkEmbeddingService;
import com.wuji.assistant.rag.ingest.PdfTextExtractor;
import com.wuji.assistant.server.admin.audit.AdminAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminKbService 单测。
 *
 * @author liudy
 */
@ExtendWith(MockitoExtension.class)
class AdminKbServiceTest {

    @Mock
    private DocumentIngestService ingestService;
    @Mock
    private KbDocumentQueryService queryService;
    @Mock
    private KbChunkService chunkService;
    @Mock
    private KbChunkEmbeddingService embeddingService;
    @Mock
    private PdfTextExtractor pdfTextExtractor;
    @Mock
    private AdminAuditLogRepository auditLogRepository;

    private AdminKbService service;
    private final AdminAuthUser admin = AdminAuthUser.of("a_admin", "admin", "SUPER_ADMIN");

    @BeforeEach
    void setUp() {
        service = new AdminKbService(ingestService, queryService, chunkService, embeddingService,
                pdfTextExtractor, auditLogRepository);
    }

    @Test
    void ingestFileRejectsUnsupportedExtension() {
        WujiException ex = assertThrows(WujiException.class, () ->
                service.ingestFile(admin, "a.docx", "x".getBytes(StandardCharsets.UTF_8),
                        null, null, null, null, null, null, null, null));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("pdf") || ex.getMessage().contains("md"));
    }

    @Test
    void ingestTextPassesSplitOptions() {
        when(ingestService.ingest(any())).thenReturn(new IngestResult("doc_1", 9L, "v1", 2, false));
        AdminKbIngestTextRequest req = new AdminKbIngestTextRequest(
                "doc_1", "t", "kb_default", "hello world content", null,
                List.of("role_a"), 120, 10, 20, false);
        IngestResult result = service.ingestText(admin, req);
        assertEquals("doc_1", result.docId());

        ArgumentCaptor<IngestRequest> cap = ArgumentCaptor.forClass(IngestRequest.class);
        verify(ingestService).ingest(cap.capture());
        assertEquals(120, cap.getValue().splitOptions().chunkSize());
        assertEquals(List.of("role_a"), cap.getValue().aclRoles());
        verify(auditLogRepository).insert(eq("a_admin"), eq("INGEST"), eq("KB_DOCUMENT"), eq("doc_1"), any());
    }

    @Test
    void deleteChunkAudits() {
        when(chunkService.getById("uuid-1")).thenReturn(new KbChunkView(
                "uuid-1", "doc_1_v1_c1", 1, "2026-08-07T10:00:00.000Z",
                "c", "", "s", "ACTIVE", "kb_default", "doc_1", 1L, "v1", 1, "hash1"));
        service.deleteChunk(admin, "uuid-1");
        verify(chunkService).delete("uuid-1");
        verify(auditLogRepository).insert(eq("a_admin"), eq("DELETE_CHUNK"), eq("KB_CHUNK"), eq("uuid-1"), any());
    }

    @Test
    void parseAclRolesSupportsCommaAndJsonish() {
        assertEquals(List.of("a", "b"), AdminKbService.parseAclRoles("a, b"));
        assertEquals(List.of("a", "b"), AdminKbService.parseAclRoles("[\"a\",\"b\"]"));
        assertTrue(AdminKbService.parseAclRoles(null).isEmpty());
    }

    @Test
    void getDocumentNotFound() {
        when(queryService.getDocument("x")).thenReturn(Map.of());
        assertThrows(WujiException.class, () -> service.getDocument("x"));
    }

    @Test
    void createChunkDelegates() {
        when(chunkService.create(eq(3L), eq("body"), eq("sec")))
                .thenReturn(new KbChunkWriteResult(
                        new KbChunkView("id", "k", 1, "2026-08-07T10:00:00.123Z",
                                "body", "sec", "body", "ACTIVE", "kb", "d", 3L, "v1", 1, "hash1"),
                        false));
        KbChunkWriteResult r = service.createChunk(admin, 3L, new AdminKbChunkRequest("body", "sec"));
        assertEquals("id", r.view().id());
        verify(auditLogRepository).insert(eq("a_admin"), eq("CREATE_CHUNK"), eq("KB_CHUNK"), eq("id"), any());
    }
}
