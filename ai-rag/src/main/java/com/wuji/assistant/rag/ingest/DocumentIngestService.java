package com.wuji.assistant.rag.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.common.util.IdGenerator;
import com.wuji.assistant.rag.vector.VectorIndexPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 知识库入库：预处理 → 切分 → kb_chunk / revision → 写入 kb_chunk.embedding。
 *
 * @author liudy
 */
@Service
public class DocumentIngestService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestService.class);
    public static final int PREVIEW_CHUNK_LIMIT = 200;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DocumentPreprocessor preprocessor;
    private final ChineseRecursiveTextSplitter splitter;
    private final KbChunkEmbeddingService embeddingService;
    private final VectorIndexPort vectorIndexPort;

    public DocumentIngestService(JdbcTemplate jdbcTemplate,
                                 ObjectMapper objectMapper,
                                 DocumentPreprocessor preprocessor,
                                 ChineseRecursiveTextSplitter splitter,
                                 KbChunkEmbeddingService embeddingService,
                                 VectorIndexPort vectorIndexPort) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.preprocessor = preprocessor;
        this.splitter = splitter;
        this.embeddingService = embeddingService;
        this.vectorIndexPort = vectorIndexPort;
    }

    /**
     * 入库并激活版本。
     */
    public IngestResult ingest(IngestRequest request) {
        PreparedSplit prepared = prepareSplit(request.content(), request.splitOptions(), request.preprocessOptions());
        if (prepared.chunks().isEmpty()) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "切分后无有效内容");
        }

        String title = StringUtils.hasText(request.title()) ? request.title().trim() : "未命名文档";
        String collection = StringUtils.hasText(request.collection()) ? request.collection().trim() : "kb_default";
        String docId = StringUtils.hasText(request.docId()) ? request.docId().trim() : IdGenerator.nextBizId("doc_");

        List<String> aclRoles = normalizeAcl(request.aclRoles());
        String aclJson = toJson(aclRoles);
        String ingestOptionsJson = toJson(buildIngestOptions(prepared, request));

        Timestamp now = Timestamp.from(Instant.now());
        ensureDocument(docId, collection, title, now);
        String version = nextVersion(docId);
        long versionId = IdGenerator.nextLong();
        deprecateActiveVersions(docId, now);
        jdbcTemplate.update("""
                INSERT INTO kb_document_version
                (id, doc_id, version, status, source, acl_roles, ingest_options, published_at, create_time, update_time)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?::jsonb, ?::jsonb, ?, ?, ?)
                """,
                versionId, docId, version,
                request.source() == null ? "api" : request.source(),
                aclJson, ingestOptionsJson,
                now, now, now);
        jdbcTemplate.update("""
                UPDATE kb_document SET current_version_id = ?, title = ?, update_time = ?
                WHERE doc_id = ?
                """, versionId, title, now, docId);

        int i = 0;
        for (TextSplitter.TextChunk chunk : prepared.chunks()) {
            i++;
            UUID chunkId = UUID.randomUUID();
            String chunkKey = docId + "_v" + version + "_c" + i;
            String content = chunk.content() == null ? "" : chunk.content();
            String hash = ContentHashes.sha256Hex(content);
            String section = chunk.section() == null ? "" : chunk.section();
            String summary = summaryOf(content);
            jdbcTemplate.update("""
                    INSERT INTO kb_chunk
                    (chunk_id, version_id, doc_id, collection, chunk_seq, chunk_key,
                     current_revision, section, summary, status, create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, 'ACTIVE', ?, ?)
                    """,
                    chunkId, versionId, docId, collection, i, chunkKey,
                    section, summary, now, now);
            jdbcTemplate.update("""
                    INSERT INTO kb_chunk_revision
                    (chunk_id, revision, content, content_hash, status, create_time)
                    VALUES (?, 1, ?, ?, 'ACTIVE', ?)
                    """, chunkId, content, hash, now);
        }

        int embeddedCount = 0;
        try {
            embeddedCount = embeddingService.embedVersion(versionId);
        } catch (Exception e) {
            // 版本与 chunk 已落库；向量失败不回滚，避免前端长时间无响应/整单 500
            log.warn("ingest embedding failed docId={} versionId={}: {}", docId, versionId, e.toString());
        }
        boolean embedded = embeddedCount > 0;

        log.info("ingested docId={} version={} chunks={} embedded={} embeddedCount={}",
                docId, version, prepared.chunks().size(), embedded, embeddedCount);
        return new IngestResult(docId, versionId, version, prepared.chunks().size(), embedded);
    }

    /**
     * 预览切分：不落库、不 Embedding。
     */
    public SplitPreviewResult previewSplit(String content, SplitOptions splitOptions, PreprocessOptions preprocessOptions) {
        PreparedSplit prepared = prepareSplit(content, splitOptions, preprocessOptions);
        List<TextSplitter.TextChunk> all = prepared.chunks();
        boolean truncated = all.size() > PREVIEW_CHUNK_LIMIT;
        List<TextSplitter.TextChunk> limited = truncated
                ? all.subList(0, PREVIEW_CHUNK_LIMIT) : all;
        List<SplitPreviewResult.PreviewChunk> previewChunks = new ArrayList<>();
        int seq = 0;
        for (TextSplitter.TextChunk c : limited) {
            seq++;
            String body = c.content() == null ? "" : c.content();
            previewChunks.add(new SplitPreviewResult.PreviewChunk(
                    seq, c.section() == null ? "" : c.section(), body.length(), body));
        }
        return new SplitPreviewResult(
                all.size(),
                truncated,
                prepared.cleaned().length(),
                prepared.resolvedOptionsMap(),
                previewChunks,
                prepared.warnings());
    }

    /**
     * 预处理 + 切分（入库与预览共用）。
     */
    public PreparedSplit prepareSplit(String content, SplitOptions splitOptions, PreprocessOptions preprocessOptions) {
        if (!StringUtils.hasText(content)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "content 不能为空");
        }
        // 统一走内容类型目录：无 strategy 时 normalize→narrative，旧四字段仅作覆盖项
        String strategyRaw = splitOptions != null && StringUtils.hasText(splitOptions.preset())
                ? splitOptions.preset().trim() : null;
        SplitPresetCatalog.PresetBundle merged =
                ContentTypeCatalog.merge(strategyRaw, splitOptions, preprocessOptions);

        SplitOptions resolvedSplit = splitter.resolve(merged.split());
        PreprocessOptions resolvedPre = preprocessor.resolve(merged.preprocess());
        try {
            ChineseRecursiveTextSplitter.validateOverlap(resolvedSplit);
        } catch (IllegalArgumentException ex) {
            throw new WujiException(ErrorCode.BAD_REQUEST, ex.getMessage());
        }

        String cleaned = preprocessor.preprocess(content, resolvedPre);
        List<TextSplitter.TextChunk> chunks = splitter.split(cleaned, resolvedSplit);
        List<String> warnings = new ArrayList<>();
        String contentType = ContentTypeCatalog.normalize(
                resolvedSplit.preset() != null ? resolvedSplit.preset() : strategyRaw);
        if (Boolean.TRUE.equals(resolvedSplit.chapterSplitEnabled())
                && StringUtils.hasText(resolvedSplit.chapterPattern())
                && !Pattern.compile(resolvedSplit.chapterPattern()).matcher(cleaned).find()) {
            if (ContentTypeCatalog.FAQ_QA.equals(contentType)) {
                warnings.add("未识别到 FAQ 边界（Q:/问：），已退化为整段切分；请检查文档格式或改用其它类型");
            } else if (ContentTypeCatalog.POLICY_CLAUSE.equals(contentType)) {
                warnings.add("未识别到「第X章/第X条」条款标题，已退化为整段切分");
            } else {
                warnings.add("未检测到章节标题，已按纯叙述切分");
            }
        }
        if (ContentTypeCatalog.CODE_STRUCTURE.equals(contentType)) {
            warnings.add("代码类型为启发式占位切分，完整 AST 切分尚未落地");
        }
        if (resolvedSplit.overlap() != null && resolvedSplit.chunkSize() != null
                && resolvedSplit.overlap() * 2 > resolvedSplit.chunkSize()) {
            warnings.add("重叠量过高（超过 chunkSize 的 50%），可能导致数据冗余");
        }
        Map<String, Object> resolvedMap = buildResolvedOptionsMap(resolvedSplit, resolvedPre, contentType);
        return new PreparedSplit(cleaned, chunks, resolvedSplit, resolvedPre, resolvedMap, warnings);
    }

    /**
     * 停用版本（不删历史，改 status）。
     */
    public void deprecate(String docId, long versionId) {
        Timestamp now = Timestamp.from(Instant.now());
        int n = jdbcTemplate.update("""
                UPDATE kb_document_version
                SET status = 'DEPRECATED', deprecated_at = ?, update_time = ?
                WHERE doc_id = ? AND id = ? AND status = 'ACTIVE'
                """, now, now, docId, versionId);
        if (n == 0) {
            throw new WujiException(ErrorCode.NOT_FOUND, "版本不存在或已停用");
        }
        jdbcTemplate.update("""
                UPDATE kb_chunk SET status = 'DEPRECATED', update_time = ?
                WHERE version_id = ? AND status = 'ACTIVE'
                """, now, versionId);
        vectorIndexPort.deprecateVersion(versionId);
        jdbcTemplate.update("""
                UPDATE vector_store
                SET metadata = jsonb_set(metadata::jsonb, '{status}', '"DEPRECATED"'::jsonb, true)
                WHERE metadata ->> 'doc_id' = ? AND metadata ->> 'version_id' = ?
                """, docId, String.valueOf(versionId));
    }

    private Map<String, Object> buildIngestOptions(PreparedSplit prepared, IngestRequest request) {
        Map<String, Object> opts = new LinkedHashMap<>(prepared.resolvedOptionsMap());
        if (StringUtils.hasText(request.sourceFile())) {
            opts.put("sourceFile", request.sourceFile().trim());
        } else if (StringUtils.hasText(request.source())) {
            opts.put("sourceFile", request.source().trim());
        }
        if (StringUtils.hasText(request.parser())) {
            opts.put("parser", request.parser().trim());
        } else {
            opts.put("parser", "plaintext");
        }
        return opts;
    }

    private static Map<String, Object> buildResolvedOptionsMap(SplitOptions split, PreprocessOptions pre,
                                                               String contentType) {
        Map<String, Object> opts = new LinkedHashMap<>();
        opts.put("contentType", contentType);
        opts.put("strategyId", contentType);
        opts.putAll(SplitPresetCatalog.toSplitMap(split));
        opts.put("preprocess", SplitPresetCatalog.toPreprocessMap(pre));
        return opts;
    }

    private static List<String> normalizeAcl(List<String> aclRoles) {
        if (aclRoles == null || aclRoles.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String role : aclRoles) {
            if (StringUtils.hasText(role)) {
                out.add(role.trim());
            }
        }
        return List.copyOf(out);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new WujiException(ErrorCode.INTERNAL_ERROR, "json serialize failed", e);
        }
    }

    private void ensureDocument(String docId, String collection, String title, Timestamp now) {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kb_document WHERE doc_id = ?", Integer.class, docId);
        if (cnt != null && cnt > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO kb_document
                (id, doc_id, collection, title, current_version_id, create_time, update_time)
                VALUES (?, ?, ?, ?, NULL, ?, ?)
                """, IdGenerator.nextLong(), docId, collection, title, now, now);
    }

    private void deprecateActiveVersions(String docId, Timestamp now) {
        List<Long> activeIds = jdbcTemplate.queryForList("""
                SELECT id FROM kb_document_version WHERE doc_id = ? AND status = 'ACTIVE'
                """, Long.class, docId);
        jdbcTemplate.update("""
                UPDATE kb_document_version
                SET status = 'DEPRECATED', deprecated_at = ?, update_time = ?
                WHERE doc_id = ? AND status = 'ACTIVE'
                """, now, now, docId);
        for (Long vid : activeIds) {
            jdbcTemplate.update("""
                    UPDATE kb_chunk SET status = 'DEPRECATED', update_time = ?
                    WHERE version_id = ? AND status = 'ACTIVE'
                    """, now, vid);
            vectorIndexPort.deprecateVersion(vid);
        }
        jdbcTemplate.update("""
                UPDATE vector_store
                SET metadata = jsonb_set(metadata::jsonb, '{status}', '"DEPRECATED"'::jsonb, true)
                WHERE metadata ->> 'doc_id' = ? AND metadata ->> 'status' = 'ACTIVE'
                """, docId);
    }

    private String nextVersion(String docId) {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kb_document_version WHERE doc_id = ?", Integer.class, docId);
        int n = cnt == null ? 0 : cnt;
        return "v" + (n + 1);
    }

    public static String summaryOf(String content) {
        if (content == null) {
            return "";
        }
        String s = content.trim().replace('\n', ' ');
        return s.length() <= 80 ? s : s.substring(0, 80);
    }

    /**
     * 入库时间 ISO-8601 UTC，毫秒精度（如 2026-08-07T10:11:12.345Z）。
     */
    static String formatIngestedAt(Instant instant) {
        if (instant == null) {
            return null;
        }
        Instant truncated = instant.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        return truncated.toString();
    }

    public static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * 预处理+切分中间结果。
     */
    public record PreparedSplit(
            String cleaned,
            List<TextSplitter.TextChunk> chunks,
            SplitOptions resolvedSplit,
            PreprocessOptions resolvedPreprocess,
            Map<String, Object> resolvedOptionsMap,
            List<String> warnings
    ) {
    }
}
