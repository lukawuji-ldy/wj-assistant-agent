package com.wuji.assistant.server.kb;

import com.wuji.assistant.common.api.ApiResponse;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.rag.ingest.DocumentIngestService;
import com.wuji.assistant.rag.ingest.IngestRequest;
import com.wuji.assistant.rag.ingest.IngestResult;
import com.wuji.assistant.rag.ingest.KbDocumentQueryService;
import com.wuji.assistant.server.security.CurrentUser;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 知识库最小管理 API（JWT；禁信任 body userId）。
 *
 * @author liudy
 */
@RestController
@RequestMapping("/api/kb/documents")
public class KbDocumentController {

    private final DocumentIngestService ingestService;
    private final KbDocumentQueryService queryService;

    public KbDocumentController(DocumentIngestService ingestService, KbDocumentQueryService queryService) {
        this.ingestService = ingestService;
        this.queryService = queryService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<IngestResult>> ingestJson(@RequestBody IngestTextBody body) {
        return CurrentUser.require().flatMap(user -> Mono.fromCallable(() -> {
            IngestResult result = ingestService.ingest(new IngestRequest(
                    body.docId(), body.title(), body.collection(), body.content(), body.source()));
            return ApiResponse.ok(result);
        }).subscribeOn(Schedulers.boundedElastic()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ApiResponse<IngestResult>> ingestFile(
            @RequestPart("file") FilePart file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "collection", required = false) String collection,
            @RequestParam(value = "docId", required = false) String docId) {
        String name = file.filename() == null ? "upload.txt" : file.filename();
        if (!(name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".markdown"))) {
            return Mono.error(new WujiException(ErrorCode.BAD_REQUEST, "仅支持 .md / .txt"));
        }
        String t = StringUtils.hasText(title) ? title : name;
        return CurrentUser.require().flatMap(user ->
                DataBufferUtils.join(file.content()).flatMap(dataBuffer -> {
                    try {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        String content = new String(bytes, StandardCharsets.UTF_8);
                        return Mono.fromCallable(() -> {
                            IngestResult result = ingestService.ingest(new IngestRequest(
                                    docId, t, collection, content, name));
                            return ApiResponse.ok(result);
                        }).subscribeOn(Schedulers.boundedElastic());
                    } finally {
                        DataBufferUtils.release(dataBuffer);
                    }
                }));
    }

    @GetMapping
    public Mono<ApiResponse<Object>> list(@RequestParam(value = "collection", required = false) String collection) {
        return CurrentUser.require().flatMap(user -> Mono.fromCallable(() ->
                ApiResponse.ok((Object) queryService.listDocuments(collection))
        ).subscribeOn(Schedulers.boundedElastic()));
    }

    @GetMapping("/{docId}")
    public Mono<ApiResponse<Object>> detail(@PathVariable String docId) {
        return CurrentUser.require().flatMap(user -> Mono.fromCallable(() -> {
            Map<String, Object> detail = queryService.getDocument(docId);
            if (detail.isEmpty()) {
                throw new WujiException(ErrorCode.NOT_FOUND, "文档不存在");
            }
            return ApiResponse.ok((Object) detail);
        }).subscribeOn(Schedulers.boundedElastic()));
    }

    @PostMapping("/{docId}/versions/{versionId}/deprecate")
    public Mono<ApiResponse<Void>> deprecate(@PathVariable String docId, @PathVariable long versionId) {
        return CurrentUser.require().flatMap(user -> Mono.fromCallable(() -> {
            ingestService.deprecate(docId, versionId);
            return ApiResponse.<Void>ok(null);
        }).subscribeOn(Schedulers.boundedElastic()));
    }

    /**
     * JSON 入库体。
     */
    public record IngestTextBody(
            String docId,
            String title,
            String collection,
            String content,
            String source
    ) {
    }
}
