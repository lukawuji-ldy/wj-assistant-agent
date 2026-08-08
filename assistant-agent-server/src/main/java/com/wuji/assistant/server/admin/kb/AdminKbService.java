package com.wuji.assistant.server.admin.kb;

import com.wuji.assistant.common.auth.AdminAuthUser;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.rag.ingest.DocumentIngestService;
import com.wuji.assistant.rag.ingest.IngestRequest;
import com.wuji.assistant.rag.ingest.IngestResult;
import com.wuji.assistant.rag.ingest.KbChunkRevisionView;
import com.wuji.assistant.rag.ingest.KbChunkService;
import com.wuji.assistant.rag.ingest.KbChunkView;
import com.wuji.assistant.rag.ingest.KbChunkWriteResult;
import com.wuji.assistant.rag.ingest.KbDocumentQueryService;
import com.wuji.assistant.rag.ingest.KbChunkEmbeddingService;
import com.wuji.assistant.rag.ingest.KbVersionEmbeddingView;
import com.wuji.assistant.rag.ingest.PdfTextExtractor;
import com.wuji.assistant.rag.ingest.SplitOptions;
import com.wuji.assistant.server.admin.audit.AdminAuditDetail;
import com.wuji.assistant.server.admin.audit.AdminAuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 管理台知识库：文档入库 / 版本停用 / chunk CRUD。
 *
 * @author liudy
 */
@Service
public class AdminKbService {

    private static final String RES_DOC = "KB_DOCUMENT";
    private static final String RES_CHUNK = "KB_CHUNK";

    private final DocumentIngestService ingestService;
    private final KbDocumentQueryService queryService;
    private final KbChunkService chunkService;
    private final KbChunkEmbeddingService embeddingService;
    private final PdfTextExtractor pdfTextExtractor;
    private final AdminAuditLogRepository auditLogRepository;

    public AdminKbService(DocumentIngestService ingestService,
                          KbDocumentQueryService queryService,
                          KbChunkService chunkService,
                          KbChunkEmbeddingService embeddingService,
                          PdfTextExtractor pdfTextExtractor,
                          AdminAuditLogRepository auditLogRepository) {
        this.ingestService = ingestService;
        this.queryService = queryService;
        this.chunkService = chunkService;
        this.embeddingService = embeddingService;
        this.pdfTextExtractor = pdfTextExtractor;
        this.auditLogRepository = auditLogRepository;
    }

    public AdminKbDocumentPage listDocuments(String collection, String status, int page, int size) {
        KbDocumentQueryService.KbDocumentPage p = queryService.listDocuments(collection, status, page, size);
        return new AdminKbDocumentPage(p.items(), p.total(), p.page(), p.size());
    }

    public Map<String, Object> getDocument(String docId) {
        Map<String, Object> detail = queryService.getDocument(docId);
        if (detail.isEmpty()) {
            throw new WujiException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return detail;
    }

    public IngestResult ingestText(AdminAuthUser admin, AdminKbIngestTextRequest request) {
        if (request == null || !StringUtils.hasText(request.content())) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "content 不能为空");
        }
        SplitOptions split = new SplitOptions(
                request.chunkSize(), request.overlap(),
                request.minChunkLengthToKeep(), request.chapterSplitEnabled());
        IngestResult result = ingestService.ingest(new IngestRequest(
                request.docId(),
                request.title(),
                request.collection(),
                request.content(),
                StringUtils.hasText(request.source()) ? request.source() : "admin-api",
                request.aclRoles(),
                split,
                request.source(),
                "plaintext"));
        auditIngest(admin, result, "plaintext", request.title());
        return result;
    }

    public IngestResult ingestFile(AdminAuthUser admin,
                                   String filename,
                                   byte[] bytes,
                                   String title,
                                   String collection,
                                   String docId,
                                   List<String> aclRoles,
                                   Integer chunkSize,
                                   Integer overlap,
                                   Integer minChunkLengthToKeep,
                                   Boolean chapterSplitEnabled) {
        if (bytes == null || bytes.length == 0) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "文件内容为空");
        }
        String name = StringUtils.hasText(filename) ? filename.trim() : "upload.txt";
        String lower = name.toLowerCase(Locale.ROOT);
        String parser;
        String content;
        if (lower.endsWith(".pdf")) {
            parser = "pdfbox";
            content = pdfTextExtractor.extract(bytes);
        } else if (lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".txt")) {
            parser = "plaintext";
            content = new String(bytes, StandardCharsets.UTF_8);
        } else {
            throw new WujiException(ErrorCode.BAD_REQUEST, "仅支持 .md / .txt / .pdf");
        }
        if (!StringUtils.hasText(content)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "文件未提取到有效文本");
        }
        String t = StringUtils.hasText(title) ? title : name;
        SplitOptions split = new SplitOptions(chunkSize, overlap, minChunkLengthToKeep, chapterSplitEnabled);
        IngestResult result = ingestService.ingest(new IngestRequest(
                docId, t, collection, content, name, aclRoles, split, name, parser));
        auditIngest(admin, result, parser, t);
        return result;
    }

    public void deprecate(AdminAuthUser admin, String docId, long versionId) {
        ingestService.deprecate(docId, versionId);
        auditLogRepository.insert(admin.adminId(), "DEPRECATE", RES_DOC, docId,
                AdminAuditDetail.builder()
                        .meta("versionId", versionId)
                        .build());
    }

    public List<KbChunkView> listChunks(long versionId, String status) {
        return chunkService.listByVersion(versionId, status);
    }

    public KbChunkWriteResult createChunk(AdminAuthUser admin, long versionId, AdminKbChunkRequest request) {
        if (request == null) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "请求体不能为空");
        }
        KbChunkWriteResult result = chunkService.create(versionId, request.content(), request.section());
        auditLogRepository.insert(admin.adminId(), "CREATE_CHUNK", RES_CHUNK, result.view().id(),
                AdminAuditDetail.builder()
                        .created("chunkKey", result.view().chunkKey())
                        .created("contentLength", result.view().content().length())
                        .meta("versionId", versionId)
                        .meta("embedded", result.embedded())
                        .build());
        return result;
    }

    public KbChunkWriteResult updateChunk(AdminAuthUser admin, String chunkId, AdminKbChunkRequest request) {
        if (request == null) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "请求体不能为空");
        }
        KbChunkView before = chunkService.getById(chunkId);
        KbChunkWriteResult result = chunkService.update(chunkId, request.content(), request.section());
        auditLogRepository.insert(admin.adminId(), "UPDATE_CHUNK", RES_CHUNK, chunkId,
                AdminAuditDetail.builder()
                        .change("contentLength", before.content().length(), result.view().content().length())
                        .change("section", before.section(), result.view().section())
                        .meta("embedded", result.embedded())
                        .build());
        return result;
    }

    public void deleteChunk(AdminAuthUser admin, String chunkId) {
        KbChunkView before = chunkService.getById(chunkId);
        chunkService.delete(chunkId);
        auditLogRepository.insert(admin.adminId(), "DELETE_CHUNK", RES_CHUNK, chunkId,
                AdminAuditDetail.builder()
                        .meta("chunkKey", before.chunkKey())
                        .meta("docId", before.docId())
                        .build());
    }

    public List<KbChunkRevisionView> listRevisions(String chunkId) {
        return chunkService.listRevisions(chunkId);
    }

    public KbChunkWriteResult rollback(AdminAuthUser admin, String chunkId, int revision) {
        KbChunkView before = chunkService.getById(chunkId);
        KbChunkWriteResult result = chunkService.rollback(chunkId, revision);
        auditLogRepository.insert(admin.adminId(), "ROLLBACK_CHUNK", RES_CHUNK, chunkId,
                AdminAuditDetail.builder()
                        .change("currentRevision", before.currentRevision(), result.view().currentRevision())
                        .meta("targetRevision", revision)
                        .meta("embedded", result.embedded())
                        .build());
        return result;
    }

    public KbVersionEmbeddingView getVersionEmbedding(long versionId) {
        return embeddingService.getVersionEmbedding(versionId);
    }

    public KbVersionEmbeddingView rebuildEmbedding(AdminAuthUser admin, long versionId) {
        KbVersionEmbeddingView view = embeddingService.rebuildForVersion(versionId);
        auditLogRepository.insert(admin.adminId(), "REBUILD_EMBEDDING", RES_DOC,
                String.valueOf(versionId),
                AdminAuditDetail.builder()
                        .meta("versionId", versionId)
                        .meta("embeddingModelVersion", view.embeddingModelVersion())
                        .meta("embeddedChunkCount", view.embeddedChunkCount())
                        .build());
        return view;
    }

    /**
     * 解析 aclRoles 表单：JSON 数组或逗号分隔。
     */
    public static List<String> parseAclRoles(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        String s = raw.trim();
        if (s.startsWith("[")) {
            // 粗解析：去括号后按逗号拆（完整 JSON 由控制器也可直接传 List）
            s = s.substring(1, s.endsWith("]") ? s.length() - 1 : s.length());
        }
        return Arrays.stream(s.split(","))
                .map(String::trim)
                .map(v -> v.replaceAll("^\"|\"$", ""))
                .filter(StringUtils::hasText)
                .toList();
    }

    private void auditIngest(AdminAuthUser admin, IngestResult result, String parser, String title) {
        auditLogRepository.insert(admin.adminId(), "INGEST", RES_DOC, result.docId(),
                AdminAuditDetail.builder()
                        .created("title", title)
                        .created("version", result.version())
                        .created("chunkCount", result.chunkCount())
                        .meta("versionId", result.versionId())
                        .meta("parser", parser)
                        .meta("embedded", result.embedded())
                        .build());
    }
}
