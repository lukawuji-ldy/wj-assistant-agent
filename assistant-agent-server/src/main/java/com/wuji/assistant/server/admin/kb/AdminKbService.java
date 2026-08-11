package com.wuji.assistant.server.admin.kb;

import com.wuji.assistant.common.auth.AdminAuthUser;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.rag.ingest.ContentTypeCatalog;
import com.wuji.assistant.rag.ingest.DocumentIngestService;
import com.wuji.assistant.rag.ingest.IngestRequest;
import com.wuji.assistant.rag.ingest.IngestResult;
import com.wuji.assistant.rag.ingest.KeepSeparator;
import com.wuji.assistant.rag.ingest.KbChunkRevisionView;
import com.wuji.assistant.rag.ingest.KbChunkService;
import com.wuji.assistant.rag.ingest.KbChunkView;
import com.wuji.assistant.rag.ingest.KbChunkWriteResult;
import com.wuji.assistant.rag.ingest.KbDocumentQueryService;
import com.wuji.assistant.rag.ingest.KbChunkEmbeddingService;
import com.wuji.assistant.rag.ingest.KbVersionEmbeddingView;
import com.wuji.assistant.rag.ingest.PdfTextExtractor;
import com.wuji.assistant.rag.ingest.PreprocessOptions;
import com.wuji.assistant.rag.ingest.SectionTitleMode;
import com.wuji.assistant.rag.ingest.SplitOptions;
import com.wuji.assistant.rag.ingest.SplitPreviewResult;
import com.wuji.assistant.server.admin.audit.AdminAuditDetail;
import com.wuji.assistant.server.admin.audit.AdminAuditLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
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
    private final List<String> aclRoleSuggestions;

    public AdminKbService(DocumentIngestService ingestService,
                          KbDocumentQueryService queryService,
                          KbChunkService chunkService,
                          KbChunkEmbeddingService embeddingService,
                          PdfTextExtractor pdfTextExtractor,
                          AdminAuditLogRepository auditLogRepository,
                          @Value("${wuji.admin.kb.acl-role-suggestions:admin,viewer}") String aclRoleSuggestions) {
        this.ingestService = ingestService;
        this.queryService = queryService;
        this.chunkService = chunkService;
        this.embeddingService = embeddingService;
        this.pdfTextExtractor = pdfTextExtractor;
        this.auditLogRepository = auditLogRepository;
        this.aclRoleSuggestions = parseCsv(aclRoleSuggestions);
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

    public List<String> listCollections() {
        return queryService.listCollections();
    }

    public Map<String, Object> splitPresetsMeta() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("contentTypes", ContentTypeCatalog.listContentTypes());
        // 兼容旧前端字段名
        out.put("presets", ContentTypeCatalog.listContentTypes());
        out.put("aclRoleSuggestions", aclRoleSuggestions);
        return out;
    }

    public IngestResult ingestText(AdminAuthUser admin, AdminKbIngestTextRequest request) {
        if (request == null || !StringUtils.hasText(request.content())) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "content 不能为空");
        }
        SplitOptions split = toSplitOptions(request);
        PreprocessOptions pre = toPreprocessOptions(request);
        IngestResult result = ingestService.ingest(new IngestRequest(
                request.docId(),
                request.title(),
                request.collection(),
                request.content(),
                StringUtils.hasText(request.source()) ? request.source() : "admin-api",
                request.aclRoles(),
                split,
                pre,
                request.source(),
                "plaintext"));
        auditIngest(admin, result, "plaintext", request.title());
        return result;
    }

    public IngestResult ingestFile(AdminAuthUser admin, String filename, byte[] bytes, AdminKbIngestForm form) {
        ParsedFile parsed = parseFile(filename, bytes);
        String t = form != null && StringUtils.hasText(form.title()) ? form.title() : parsed.filename();
        String collection = form == null ? null : form.collection();
        String docId = form == null ? null : form.docId();
        List<String> aclRoles = form == null ? List.of() : form.aclRoles();
        SplitOptions split = form == null ? null : form.toSplitOptions();
        PreprocessOptions pre = form == null ? null : form.toPreprocessOptions();
        IngestResult result = ingestService.ingest(new IngestRequest(
                docId, t, collection, parsed.content(), parsed.filename(),
                aclRoles, split, pre, parsed.filename(), parsed.parser()));
        auditIngest(admin, result, parsed.parser(), t);
        return result;
    }

    /**
     * 兼容旧测试签名。
     */
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
        return ingestFile(admin, filename, bytes, new AdminKbIngestForm(
                title, collection, docId, aclRoles, null, null,
                chunkSize, overlap, minChunkLengthToKeep, chapterSplitEnabled,
                null, null, null, null,
                null, null, null, null, null, null));
    }

    public SplitPreviewResult previewSplitText(AdminKbIngestTextRequest request) {
        if (request == null || !StringUtils.hasText(request.content())) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "content 不能为空");
        }
        return ingestService.previewSplit(request.content(), toSplitOptions(request), toPreprocessOptions(request));
    }

    public SplitPreviewResult previewSplit(String filename, byte[] bytes, AdminKbIngestForm form) {
        ParsedFile parsed = parseFile(filename, bytes);
        SplitOptions split = form == null ? null : form.toSplitOptions();
        PreprocessOptions pre = form == null ? null : form.toPreprocessOptions();
        return ingestService.previewSplit(parsed.content(), split, pre);
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

    public static List<String> parseAclRoles(String aclRoles) {
        if (!StringUtils.hasText(aclRoles)) {
            return List.of();
        }
        String t = aclRoles.trim();
        if (t.startsWith("[")) {
            return AdminKbIngestForm.parseStringList(t) == null
                    ? List.of()
                    : AdminKbIngestForm.parseStringList(t);
        }
        return Arrays.stream(t.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private ParsedFile parseFile(String filename, byte[] bytes) {
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
        return new ParsedFile(name, parser, content);
    }

    private static SplitOptions toSplitOptions(AdminKbIngestTextRequest request) {
        String strategy = blankToNull(request.contentType());
        if (strategy == null) {
            strategy = blankToNull(request.preset());
        }
        return new SplitOptions(
                request.chunkSize(),
                request.overlap(),
                request.minChunkLengthToKeep(),
                request.chapterSplitEnabled(),
                blankToNull(request.chapterPattern()),
                SectionTitleMode.parse(request.sectionTitleMode()),
                request.separators(),
                KeepSeparator.parse(request.keepSeparator()),
                strategy);
    }

    private static PreprocessOptions toPreprocessOptions(AdminKbIngestTextRequest request) {
        return new PreprocessOptions(
                request.normalizeNewlines(),
                request.stripPageNumbers(),
                request.mergeCjkHardWrap(),
                request.collapseBlankLines(),
                request.trimOutsideChapters(),
                request.trailingNoiseMarkers(),
                blankToNull(request.chapterPattern()));
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static List<String> parseCsv(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
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

    private record ParsedFile(String filename, String parser, String content) {
    }
}
