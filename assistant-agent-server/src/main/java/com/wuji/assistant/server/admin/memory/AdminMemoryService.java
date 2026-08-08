package com.wuji.assistant.server.admin.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.common.auth.AdminAuthUser;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.memory.model.MemoryPage;
import com.wuji.assistant.memory.model.UserProfileView;
import com.wuji.assistant.memory.model.UserSemanticMemoryView;
import com.wuji.assistant.memory.repo.UserProfileRepository;
import com.wuji.assistant.memory.repo.UserSemanticMemoryRepository;
import com.wuji.assistant.rag.ingest.EmbeddingClient;
import com.wuji.assistant.server.admin.audit.AdminAuditDetail;
import com.wuji.assistant.server.admin.audit.AdminAuditLogRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 管理台用户长期记忆（Profile / Semantic）。
 *
 * @author liudy
 */
@Service
public class AdminMemoryService {

    private static final Set<String> PROFILE_TYPES = Set.of("PROFILE", "PREFERENCE");
    private static final Set<String> PROFILE_STATUSES = Set.of("ACTIVE", "INACTIVE", "DELETED", "EXPIRED");
    private static final Set<String> SEMANTIC_STATUSES = Set.of("ACTIVE", "INACTIVE", "DELETED", "EXPIRED");
    private static final double DEFAULT_SIMILAR_MIN_SCORE = 0.35;

    private final UserProfileRepository profileRepository;
    private final UserSemanticMemoryRepository semanticRepository;
    private final EmbeddingClient embeddingClient;
    private final AdminAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AdminMemoryService(
            UserProfileRepository profileRepository,
            UserSemanticMemoryRepository semanticRepository,
            ObjectProvider<EmbeddingClient> embeddingClients,
            AdminAuditLogRepository auditLogRepository,
            ObjectMapper objectMapper) {
        this.profileRepository = profileRepository;
        this.semanticRepository = semanticRepository;
        this.embeddingClient = embeddingClients.getIfAvailable(() -> new EmbeddingClient() {
            @Override
            public boolean available() {
                return false;
            }

            @Override
            public float[] embed(String text) {
                return null;
            }
        });
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public AdminProfilePage listProfiles(
            String userId,
            String memoryKey,
            String memoryType,
            String status,
            Instant createTimeFrom,
            Instant createTimeTo,
            int page,
            int size) {
        MemoryPage<UserProfileView> result = profileRepository.pageAdmin(
                userId, memoryKey, memoryType, status, createTimeFrom, createTimeTo, page, size);
        List<AdminProfileView> items = result.items().stream().map(this::toProfileView).toList();
        return new AdminProfilePage(items, result.total(), result.page(), result.size());
    }

    public AdminProfileView createProfile(AdminAuthUser operator, AdminProfileCreateRequest request) {
        if (request == null
                || !StringUtils.hasText(request.userId())
                || !StringUtils.hasText(request.memoryKey())
                || !StringUtils.hasText(request.memoryValue())) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "userId、memoryKey、memoryValue 不能为空");
        }
        String type = normalizeProfileType(request.memoryType());
        String userId = request.userId().trim();
        String key = request.memoryKey().trim();
        if (profileRepository.existsActiveKey(userId, key, null)) {
            throw new WujiException(ErrorCode.MEMORY_KEY_CONFLICT, "该用户已存在 ACTIVE key: " + key);
        }
        float confidence = request.confidence() == null ? 1.0f : clamp01(request.confidence().floatValue());
        float importance = request.importance() == null ? 0.5f : clamp01(request.importance().floatValue());
        String memoryId = profileRepository.insert(
                userId, type, key, request.memoryValue().trim(), confidence, importance, "SYSTEM");
        auditLogRepository.insert(
                operator.adminId(),
                "CREATE",
                "USER_PROFILE",
                memoryId,
                AdminAuditDetail.builder()
                        .created("userId", userId)
                        .created("memoryType", type)
                        .created("memoryKey", key)
                        .created("memoryValue", truncate(request.memoryValue().trim(), 200))
                        .created("status", "ACTIVE")
                        .build());
        return getProfile(memoryId);
    }

    public AdminProfileView updateProfile(AdminAuthUser operator, String memoryId, AdminProfileUpdateRequest request) {
        if (!StringUtils.hasText(memoryId) || request == null) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "memoryId 与请求体不能为空");
        }
        UserProfileView current = profileRepository.findByMemoryId(memoryId.trim())
                .orElseThrow(() -> new WujiException(ErrorCode.MEMORY_NOT_FOUND));
        String type = StringUtils.hasText(request.memoryType())
                ? normalizeProfileType(request.memoryType())
                : current.getMemoryType();
        String key = StringUtils.hasText(request.memoryKey())
                ? request.memoryKey().trim()
                : current.getMemoryKey();
        String value = StringUtils.hasText(request.memoryValue())
                ? request.memoryValue().trim()
                : current.getMemoryValue();
        String status = StringUtils.hasText(request.status())
                ? normalizeProfileStatus(request.status())
                : current.getStatus();
        float confidence = request.confidence() == null
                ? current.getConfidence()
                : clamp01(request.confidence().floatValue());
        float importance = request.importance() == null
                ? current.getImportance()
                : clamp01(request.importance().floatValue());
        if ("ACTIVE".equals(status) && profileRepository.existsActiveKey(current.getUserId(), key, memoryId.trim())) {
            throw new WujiException(ErrorCode.MEMORY_KEY_CONFLICT, "该用户已存在 ACTIVE key: " + key);
        }
        int n = profileRepository.updateByMemoryId(
                memoryId.trim(), type, key, value, status, confidence, importance);
        if (n <= 0) {
            throw new WujiException(ErrorCode.MEMORY_NOT_FOUND);
        }
        auditLogRepository.insert(
                operator.adminId(),
                "UPDATE",
                "USER_PROFILE",
                memoryId.trim(),
                AdminAuditDetail.builder()
                        .change("memoryType", current.getMemoryType(), type)
                        .change("memoryKey", current.getMemoryKey(), key)
                        .change("memoryValue",
                                truncate(current.getMemoryValue(), 200),
                                truncate(value, 200))
                        .change("status", current.getStatus(), status)
                        .change("confidence", current.getConfidence(), confidence)
                        .change("importance", current.getImportance(), importance)
                        .build());
        return getProfile(memoryId.trim());
    }

    public void deleteProfile(AdminAuthUser operator, String memoryId) {
        if (!StringUtils.hasText(memoryId)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "memoryId 不能为空");
        }
        UserProfileView current = profileRepository.findByMemoryId(memoryId.trim())
                .orElseThrow(() -> new WujiException(ErrorCode.MEMORY_NOT_FOUND));
        if ("DELETED".equalsIgnoreCase(current.getStatus())) {
            return;
        }
        int n = profileRepository.softDeleteByMemoryId(memoryId.trim());
        if (n <= 0) {
            throw new WujiException(ErrorCode.MEMORY_NOT_FOUND);
        }
        auditLogRepository.insert(
                operator.adminId(),
                "DELETE",
                "USER_PROFILE",
                memoryId.trim(),
                AdminAuditDetail.builder()
                        .change("status", current.getStatus(), "DELETED")
                        .meta("userId", current.getUserId())
                        .meta("memoryKey", current.getMemoryKey())
                        .build());
    }

    public AdminSemanticPage listSemantics(
            String userId,
            String status,
            String keyword,
            String similarQuery,
            Instant createTimeFrom,
            Instant createTimeTo,
            int page,
            int size) {
        if (StringUtils.hasText(similarQuery)) {
            if (!StringUtils.hasText(userId)) {
                throw new WujiException(ErrorCode.BAD_REQUEST, "相似检索须指定 userId");
            }
            if (!embeddingClient.available()) {
                throw new WujiException(ErrorCode.MODEL_UNAVAILABLE, "Embedding 不可用");
            }
            float[] vector = embeddingClient.embed(similarQuery.trim());
            if (vector == null || vector.length != UserSemanticMemoryRepository.EXPECTED_DIMENSIONS) {
                throw new WujiException(ErrorCode.MODEL_UNAVAILABLE, "Embedding 失败或维度不符");
            }
            String literal = UserSemanticMemoryRepository.toVectorLiteral(vector);
            MemoryPage<UserSemanticMemoryView> result = semanticRepository.pageSimilarAdmin(
                    userId, literal, DEFAULT_SIMILAR_MIN_SCORE, page, size);
            return toSemanticPage(result);
        }
        MemoryPage<UserSemanticMemoryView> result = semanticRepository.pageAdmin(
                userId, status, keyword, createTimeFrom, createTimeTo, page, size);
        return toSemanticPage(result);
    }

    public AdminSemanticView updateSemantic(AdminAuthUser operator, String id, AdminSemanticUpdateRequest request) {
        if (!StringUtils.hasText(id) || request == null) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "id 与请求体不能为空");
        }
        UserSemanticMemoryView current = semanticRepository.findById(id.trim())
                .orElseThrow(() -> new WujiException(ErrorCode.MEMORY_NOT_FOUND));
        String content = StringUtils.hasText(request.content())
                ? request.content().trim()
                : current.getContent();
        String status = StringUtils.hasText(request.status())
                ? normalizeSemanticStatus(request.status())
                : current.getStatus();
        float importance = request.importance() == null
                ? current.getImportance()
                : clamp01(request.importance().floatValue());
        float confidence = request.confidence() == null
                ? current.getConfidence()
                : clamp01(request.confidence().floatValue());
        boolean contentChanged = !Objects.equals(content, current.getContent());
        String vectorLiteral = null;
        if (contentChanged) {
            if (!embeddingClient.available()) {
                throw new WujiException(ErrorCode.MODEL_UNAVAILABLE, "Embedding 不可用，无法更新正文");
            }
            float[] vector = embeddingClient.embed(content);
            if (vector == null || vector.length != UserSemanticMemoryRepository.EXPECTED_DIMENSIONS) {
                throw new WujiException(ErrorCode.MODEL_UNAVAILABLE, "Embedding 失败或维度不符");
            }
            vectorLiteral = UserSemanticMemoryRepository.toVectorLiteral(vector);
        }
        String tagsJson = null;
        if (request.tags() != null) {
            try {
                tagsJson = objectMapper.writeValueAsString(request.tags());
            } catch (Exception e) {
                throw new WujiException(ErrorCode.BAD_REQUEST, "tags 序列化失败");
            }
        }
        int n = semanticRepository.update(
                id.trim(), content, status, importance, confidence, tagsJson, vectorLiteral);
        if (n <= 0) {
            throw new WujiException(ErrorCode.MEMORY_NOT_FOUND);
        }
        AdminAuditDetail detail = AdminAuditDetail.builder()
                .change("content", truncate(current.getContent(), 200), truncate(content, 200))
                .change("status", current.getStatus(), status)
                .change("importance", current.getImportance(), importance)
                .change("confidence", current.getConfidence(), confidence);
        if (contentChanged) {
            detail.meta("reEmbedded", true);
        }
        if (request.tags() != null) {
            detail.change("tags", current.getTagsJson(), tagsJson);
        }
        auditLogRepository.insert(operator.adminId(), "UPDATE", "USER_SEMANTIC", id.trim(), detail.build());
        return toSemanticView(semanticRepository.findById(id.trim())
                .orElseThrow(() -> new WujiException(ErrorCode.MEMORY_NOT_FOUND)));
    }

    public void deleteSemantic(AdminAuthUser operator, String id) {
        if (!StringUtils.hasText(id)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "id 不能为空");
        }
        UserSemanticMemoryView current = semanticRepository.findById(id.trim())
                .orElseThrow(() -> new WujiException(ErrorCode.MEMORY_NOT_FOUND));
        if ("DELETED".equalsIgnoreCase(current.getStatus())) {
            return;
        }
        int n = semanticRepository.softDeleteById(id.trim());
        if (n <= 0) {
            throw new WujiException(ErrorCode.MEMORY_NOT_FOUND);
        }
        auditLogRepository.insert(
                operator.adminId(),
                "DELETE",
                "USER_SEMANTIC",
                id.trim(),
                AdminAuditDetail.builder()
                        .change("status", current.getStatus(), "DELETED")
                        .meta("userId", current.getUserId())
                        .build());
    }

    private AdminProfileView getProfile(String memoryId) {
        return profileRepository.findByMemoryId(memoryId)
                .map(this::toProfileView)
                .orElseThrow(() -> new WujiException(ErrorCode.MEMORY_NOT_FOUND));
    }

    private AdminProfileView toProfileView(UserProfileView v) {
        return new AdminProfileView(
                v.getMemoryId(),
                v.getUserId(),
                v.getMemoryType(),
                v.getMemoryKey(),
                v.getMemoryValue(),
                v.getStatus(),
                v.getConfidence(),
                v.getImportance(),
                v.getSource(),
                v.getVersion(),
                v.getExpireTime(),
                v.getLastUsedTime(),
                v.getCreateTime(),
                v.getUpdateTime());
    }

    private AdminSemanticPage toSemanticPage(MemoryPage<UserSemanticMemoryView> result) {
        List<AdminSemanticView> items = result.items().stream().map(this::toSemanticView).toList();
        return new AdminSemanticPage(items, result.total(), result.page(), result.size());
    }

    private AdminSemanticView toSemanticView(UserSemanticMemoryView v) {
        return new AdminSemanticView(
                v.getId(),
                v.getUserId(),
                v.getContent(),
                v.getMemoryType(),
                v.getStatus(),
                v.getImportance(),
                v.getConfidence(),
                parseTags(v.getTagsJson()),
                v.getSource(),
                v.getSourceMessageId(),
                v.getExpireTime(),
                v.getLastUsedTime(),
                v.getCreateTime(),
                v.getUpdateTime(),
                v.getScore());
    }

    private List<String> parseTags(String tagsJson) {
        if (!StringUtils.hasText(tagsJson) || "null".equalsIgnoreCase(tagsJson.trim())) {
            return List.of();
        }
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String normalizeProfileType(String type) {
        if (!StringUtils.hasText(type)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "memoryType 须为 PROFILE 或 PREFERENCE");
        }
        String t = type.trim().toUpperCase(Locale.ROOT);
        if (!PROFILE_TYPES.contains(t)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "memoryType 须为 PROFILE 或 PREFERENCE");
        }
        return t;
    }

    private static String normalizeProfileStatus(String status) {
        String s = status.trim().toUpperCase(Locale.ROOT);
        if (!PROFILE_STATUSES.contains(s)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "非法 status: " + status);
        }
        return s;
    }

    private static String normalizeSemanticStatus(String status) {
        String s = status.trim().toUpperCase(Locale.ROOT);
        if (!SEMANTIC_STATUSES.contains(s)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "非法 status: " + status);
        }
        return s;
    }

    private static float clamp01(float v) {
        if (v < 0f) {
            return 0f;
        }
        if (v > 1f) {
            return 1f;
        }
        return v;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
