package com.wuji.assistant.server.admin.kb;

import com.wuji.assistant.common.api.ApiResponse;
import com.wuji.assistant.rag.ingest.IngestResult;
import com.wuji.assistant.rag.ingest.KbChunkRevisionView;
import com.wuji.assistant.rag.ingest.KbChunkView;
import com.wuji.assistant.rag.ingest.KbChunkWriteResult;
import com.wuji.assistant.rag.ingest.KbVersionEmbeddingView;
import com.wuji.assistant.server.admin.security.CurrentAdmin;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * 管理台知识库 API。
 *
 * @author liudy
 */
@RestController
@RequestMapping("/api/admin/kb")
public class AdminKbController {

    private final AdminKbService adminKbService;

    public AdminKbController(AdminKbService adminKbService) {
        this.adminKbService = adminKbService;
    }

    @GetMapping("/documents")
    public Mono<ApiResponse<AdminKbDocumentPage>> list(
            @RequestParam(required = false) String collection,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return CurrentAdmin.require().flatMap(admin ->
                Mono.fromCallable(() -> ApiResponse.ok(
                                adminKbService.listDocuments(collection, status, page, size)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @GetMapping("/documents/{docId}")
    public Mono<ApiResponse<Map<String, Object>>> get(@PathVariable String docId) {
        return CurrentAdmin.require().flatMap(admin ->
                Mono.fromCallable(() -> ApiResponse.ok(adminKbService.getDocument(docId)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @PostMapping(value = "/documents", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<IngestResult>> ingestJson(@RequestBody AdminKbIngestTextRequest request) {
        return CurrentAdmin.require().flatMap(admin ->
                Mono.fromCallable(() -> ApiResponse.ok(adminKbService.ingestText(admin, request)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ApiResponse<IngestResult>> ingestFile(
            @RequestPart("file") FilePart file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "collection", required = false) String collection,
            @RequestParam(value = "docId", required = false) String docId,
            @RequestParam(value = "aclRoles", required = false) String aclRoles,
            @RequestParam(value = "chunkSize", required = false) Integer chunkSize,
            @RequestParam(value = "overlap", required = false) Integer overlap,
            @RequestParam(value = "minChunkLengthToKeep", required = false) Integer minChunkLengthToKeep,
            @RequestParam(value = "chapterSplitEnabled", required = false) Boolean chapterSplitEnabled) {
        String filename = file.filename() == null ? "upload.txt" : file.filename();
        return CurrentAdmin.require().flatMap(admin ->
                DataBufferUtils.join(file.content()).flatMap(dataBuffer -> {
                    try {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        return Mono.fromCallable(() -> ApiResponse.ok(adminKbService.ingestFile(
                                        admin, filename, bytes, title, collection, docId,
                                        AdminKbService.parseAclRoles(aclRoles),
                                        chunkSize, overlap, minChunkLengthToKeep, chapterSplitEnabled)))
                                .subscribeOn(Schedulers.boundedElastic());
                    } finally {
                        DataBufferUtils.release(dataBuffer);
                    }
                }));
    }

    @PostMapping("/documents/{docId}/versions/{versionId}/deprecate")
    public Mono<ApiResponse<Void>> deprecate(@PathVariable String docId, @PathVariable long versionId) {
        return CurrentAdmin.require().flatMap(admin ->
                Mono.fromCallable(() -> {
                    adminKbService.deprecate(admin, docId, versionId);
                    return ApiResponse.<Void>ok(null);
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @GetMapping("/documents/{docId}/versions/{versionId}/chunks")
    public Mono<ApiResponse<List<KbChunkView>>> listChunks(
            @PathVariable String docId,
            @PathVariable long versionId,
            @RequestParam(required = false) String status) {
        return CurrentAdmin.require().flatMap(admin ->
                Mono.fromCallable(() -> ApiResponse.ok(adminKbService.listChunks(versionId, status)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @PostMapping("/documents/{docId}/versions/{versionId}/chunks")
    public Mono<ApiResponse<KbChunkWriteResult>> createChunk(
            @PathVariable String docId,
            @PathVariable long versionId,
            @RequestBody AdminKbChunkRequest request) {
        return CurrentAdmin.require().flatMap(admin ->
                Mono.fromCallable(() -> ApiResponse.ok(adminKbService.createChunk(admin, versionId, request)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @PutMapping("/chunks/{chunkId}")
    public Mono<ApiResponse<KbChunkWriteResult>> updateChunk(
            @PathVariable String chunkId,
            @RequestBody AdminKbChunkRequest request) {
        return CurrentAdmin.require().flatMap(admin ->
                Mono.fromCallable(() -> ApiResponse.ok(adminKbService.updateChunk(admin, chunkId, request)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @DeleteMapping("/chunks/{chunkId}")
    public Mono<ApiResponse<Void>> deleteChunk(@PathVariable String chunkId) {
        return CurrentAdmin.require().flatMap(admin ->
                Mono.fromCallable(() -> {
                    adminKbService.deleteChunk(admin, chunkId);
                    return ApiResponse.<Void>ok(null);
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @GetMapping("/chunks/{chunkId}/revisions")
    public Mono<ApiResponse<List<KbChunkRevisionView>>> listRevisions(@PathVariable String chunkId) {
        return CurrentAdmin.require().flatMap(admin ->
                Mono.fromCallable(() -> ApiResponse.ok(adminKbService.listRevisions(chunkId)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @PostMapping("/chunks/{chunkId}/revisions/{revision}/rollback")
    public Mono<ApiResponse<KbChunkWriteResult>> rollback(
            @PathVariable String chunkId, @PathVariable int revision) {
        return CurrentAdmin.require().flatMap(admin ->
                Mono.fromCallable(() -> ApiResponse.ok(adminKbService.rollback(admin, chunkId, revision)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @GetMapping("/documents/{docId}/versions/{versionId}/embedding")
    public Mono<ApiResponse<KbVersionEmbeddingView>> getVersionEmbedding(
            @PathVariable String docId, @PathVariable long versionId) {
        return CurrentAdmin.require().flatMap(admin ->
                Mono.fromCallable(() -> ApiResponse.ok(adminKbService.getVersionEmbedding(versionId)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @PostMapping("/documents/{docId}/versions/{versionId}/embedding/rebuild")
    public Mono<ApiResponse<KbVersionEmbeddingView>> rebuildEmbedding(
            @PathVariable String docId, @PathVariable long versionId) {
        return CurrentAdmin.require().flatMap(admin ->
                Mono.fromCallable(() -> ApiResponse.ok(adminKbService.rebuildEmbedding(admin, versionId)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }
}
