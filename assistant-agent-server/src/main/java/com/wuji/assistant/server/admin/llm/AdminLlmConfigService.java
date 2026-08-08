package com.wuji.assistant.server.admin.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.agent.AgentFactory;
import com.wuji.assistant.agent.config.WujiModelProperties;
import com.wuji.assistant.agent.config.WujiRagProperties;
import com.wuji.assistant.agent.model.ApiKeyCipherService;
import com.wuji.assistant.agent.model.LlmClientFactory;
import com.wuji.assistant.agent.model.LlmConfigRecord;
import com.wuji.assistant.agent.model.LlmConfigRepository;
import com.wuji.assistant.agent.model.OpenAiEmbeddingClient;
import com.wuji.assistant.common.auth.AdminAuthUser;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.server.admin.audit.AdminAuditDetail;
import com.wuji.assistant.server.admin.audit.AdminAuditLogRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 管理台 LLM 配置 CRUD + 缓存失效。
 *
 * @author liudy
 */
@Service
public class AdminLlmConfigService {

    private static final Set<String> KINDS = Set.of(LlmConfigRecord.KIND_CHAT, LlmConfigRecord.KIND_EMBEDDING);
    private static final Set<String> STATUSES = Set.of("ACTIVE", "DISABLED");
    private static final String ROLE_SUPER = "SUPER_ADMIN";

    private final LlmConfigRepository llmConfigRepository;
    private final ApiKeyCipherService apiKeyCipherService;
    private final LlmClientFactory llmClientFactory;
    private final AgentFactory agentFactory;
    private final ObjectProvider<OpenAiEmbeddingClient> embeddingClient;
    private final WujiModelProperties modelProperties;
    private final WujiRagProperties ragProperties;
    private final AdminAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AdminLlmConfigService(
            LlmConfigRepository llmConfigRepository,
            ApiKeyCipherService apiKeyCipherService,
            LlmClientFactory llmClientFactory,
            AgentFactory agentFactory,
            ObjectProvider<OpenAiEmbeddingClient> embeddingClient,
            WujiModelProperties modelProperties,
            WujiRagProperties ragProperties,
            AdminAuditLogRepository auditLogRepository,
            ObjectMapper objectMapper) {
        this.llmConfigRepository = llmConfigRepository;
        this.apiKeyCipherService = apiKeyCipherService;
        this.llmClientFactory = llmClientFactory;
        this.agentFactory = agentFactory;
        this.embeddingClient = embeddingClient;
        this.modelProperties = modelProperties;
        this.ragProperties = ragProperties;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 分页列表。
     */
    public AdminLlmConfigPage list(AdminAuthUser admin, String modelKind, String status, int page, int size) {
        validateOptionalKind(modelKind);
        validateOptionalStatus(status);
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        long total = llmConfigRepository.count(blankToNull(modelKind), blankToNull(status));
        List<AdminLlmConfigView> items = llmConfigRepository
                .list(blankToNull(modelKind), blankToNull(status), s, (p - 1) * s)
                .stream()
                .map(r -> toView(r, admin, false))
                .toList();
        return new AdminLlmConfigPage(items, total, p, s);
    }

    /**
     * 详情。
     *
     * @param revealKey SUPER_ADMIN 可解密预览
     */
    public AdminLlmConfigView get(AdminAuthUser admin, String configId, boolean revealKey) {
        LlmConfigRecord record = llmConfigRepository.findByConfigId(configId)
                .orElseThrow(() -> new WujiException(ErrorCode.NOT_FOUND, "LLM 配置不存在: " + configId));
        return toView(record, admin, revealKey);
    }

    /**
     * 创建。
     */
    public AdminLlmConfigView create(AdminAuthUser admin, AdminLlmConfigCreateRequest request) {
        requireText(request.getConfigId(), "configId");
        requireText(request.getName(), "name");
        requireText(request.getBaseUrl(), "baseUrl");
        requireText(request.getModel(), "model");
        requireText(request.getApiKey(), "apiKey");
        String kind = requireKind(request.getModelKind());
        String status = requireStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : "ACTIVE");
        validateExtraJson(request.getExtraJson());

        if (llmConfigRepository.findByConfigId(request.getConfigId().trim()).isPresent()) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "configId 已存在: " + request.getConfigId());
        }

        LlmConfigRecord record = new LlmConfigRecord();
        record.setConfigId(request.getConfigId().trim());
        record.setName(request.getName().trim());
        record.setProvider(StringUtils.hasText(request.getProvider()) ? request.getProvider().trim() : "openai_compatible");
        record.setModelKind(kind);
        record.setBaseUrl(request.getBaseUrl().trim());
        record.setApiKeyCipher(apiKeyCipherService.encrypt(request.getApiKey()));
        record.setModel(request.getModel().trim());
        record.setTemperature(request.getTemperature());
        record.setMaxTokens(request.getMaxTokens());
        record.setExtraJson(request.getExtraJson());
        record.setStatus(status);
        llmConfigRepository.insert(record);

        invalidateCaches(record.getConfigId(), record.getModelKind());
        auditLogRepository.insert(admin.adminId(), "CREATE", "llm_config", record.getConfigId(),
                AdminAuditDetail.builder()
                        .created("configId", record.getConfigId())
                        .created("name", record.getName())
                        .created("provider", record.getProvider())
                        .created("modelKind", record.getModelKind())
                        .created("baseUrl", record.getBaseUrl())
                        .created("model", record.getModel())
                        .created("temperature", record.getTemperature())
                        .created("maxTokens", record.getMaxTokens())
                        .created("extraJson", record.getExtraJson())
                        .created("status", record.getStatus())
                        .sensitiveChanged("apiKey")
                        .build());
        return toView(record, admin, false);
    }

    /**
     * 更新。
     */
    public AdminLlmConfigView update(AdminAuthUser admin, String configId, AdminLlmConfigUpdateRequest request) {
        LlmConfigRecord existing = llmConfigRepository.findByConfigId(configId)
                .orElseThrow(() -> new WujiException(ErrorCode.NOT_FOUND, "LLM 配置不存在: " + configId));

        String beforeName = existing.getName();
        String beforeProvider = existing.getProvider();
        String beforeModelKind = existing.getModelKind();
        String beforeBaseUrl = existing.getBaseUrl();
        String beforeModel = existing.getModel();
        BigDecimal beforeTemperature = existing.getTemperature();
        Integer beforeMaxTokens = existing.getMaxTokens();
        String beforeExtraJson = existing.getExtraJson();
        String beforeStatus = existing.getStatus();

        if (StringUtils.hasText(request.getName())) {
            existing.setName(request.getName().trim());
        }
        if (StringUtils.hasText(request.getProvider())) {
            existing.setProvider(request.getProvider().trim());
        }
        if (StringUtils.hasText(request.getModelKind())) {
            existing.setModelKind(requireKind(request.getModelKind()));
        }
        if (StringUtils.hasText(request.getBaseUrl())) {
            existing.setBaseUrl(request.getBaseUrl().trim());
        }
        if (StringUtils.hasText(request.getModel())) {
            existing.setModel(request.getModel().trim());
        }
        if (request.getTemperature() != null) {
            existing.setTemperature(request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            existing.setMaxTokens(request.getMaxTokens());
        }
        if (request.getExtraJson() != null) {
            validateExtraJson(request.getExtraJson());
            existing.setExtraJson(request.getExtraJson());
        }
        if (StringUtils.hasText(request.getStatus())) {
            existing.setStatus(requireStatus(request.getStatus()));
        }

        boolean updateKey = StringUtils.hasText(request.getApiKey());
        if (updateKey) {
            existing.setApiKeyCipher(apiKeyCipherService.encrypt(request.getApiKey()));
        }
        llmConfigRepository.update(existing, updateKey);

        invalidateCaches(configId, existing.getModelKind());
        AdminAuditDetail detail = AdminAuditDetail.builder()
                .change("name", beforeName, existing.getName())
                .change("provider", beforeProvider, existing.getProvider())
                .change("modelKind", beforeModelKind, existing.getModelKind())
                .change("baseUrl", beforeBaseUrl, existing.getBaseUrl())
                .change("model", beforeModel, existing.getModel())
                .change("temperature", beforeTemperature, existing.getTemperature())
                .change("maxTokens", beforeMaxTokens, existing.getMaxTokens())
                .change("extraJson", beforeExtraJson, existing.getExtraJson())
                .change("status", beforeStatus, existing.getStatus());
        if (updateKey) {
            detail.sensitiveChanged("apiKey");
        }
        auditLogRepository.insert(admin.adminId(), "UPDATE", "llm_config", configId, detail.build());
        return toView(llmConfigRepository.findByConfigId(configId).orElse(existing), admin, false);
    }

    /**
     * 软禁用。被路由引用的配置仍可禁用，但需运维改指向。
     */
    public void delete(AdminAuthUser admin, String configId) {
        LlmConfigRecord existing = llmConfigRepository.findByConfigId(configId)
                .orElseThrow(() -> new WujiException(ErrorCode.NOT_FOUND, "LLM 配置不存在: " + configId));
        String beforeStatus = existing.getStatus();
        llmConfigRepository.softDisable(configId);
        invalidateCaches(configId, existing.getModelKind());
        boolean referenced = referencedConfigIds().contains(configId);
        auditLogRepository.insert(admin.adminId(), "DISABLE", "llm_config", configId,
                AdminAuditDetail.builder()
                        .change("status", beforeStatus, "DISABLED")
                        .meta("referenced", referenced)
                        .build());
    }

    private void invalidateCaches(String configId, String modelKind) {
        llmClientFactory.invalidate(configId);
        agentFactory.invalidate(configId);
        if (LlmConfigRecord.KIND_EMBEDDING.equalsIgnoreCase(modelKind)
                && configId.equals(ragProperties.getEmbeddingConfigId())) {
            OpenAiEmbeddingClient client = embeddingClient.getIfAvailable();
            if (client != null) {
                client.invalidate();
            }
        }
    }

    private Set<String> referencedConfigIds() {
        Set<String> ids = new HashSet<>();
        if (StringUtils.hasText(modelProperties.getPrimaryConfigId())) {
            ids.add(modelProperties.getPrimaryConfigId());
        }
        if (modelProperties.getFallbackConfigIds() != null) {
            ids.addAll(modelProperties.getFallbackConfigIds());
        }
        if (StringUtils.hasText(ragProperties.getEmbeddingConfigId())) {
            ids.add(ragProperties.getEmbeddingConfigId());
        }
        return ids;
    }

    private AdminLlmConfigView toView(LlmConfigRecord r, AdminAuthUser admin, boolean revealKey) {
        String masked = apiKeyCipherService.mask(r.getApiKeyCipher());
        String preview = null;
        if (revealKey && ROLE_SUPER.equals(admin.role())) {
            preview = apiKeyCipherService.decrypt(r.getApiKeyCipher());
        }
        return new AdminLlmConfigView(
                r.getConfigId(),
                r.getName(),
                r.getProvider(),
                r.getModelKind(),
                r.getBaseUrl(),
                r.getModel(),
                r.getTemperature(),
                r.getMaxTokens(),
                r.getExtraJson(),
                r.getStatus(),
                masked,
                preview
        );
    }

    private void validateExtraJson(String extraJson) {
        if (!StringUtils.hasText(extraJson)) {
            return;
        }
        try {
            objectMapper.readTree(extraJson);
        } catch (Exception e) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "extraJson 不是合法 JSON");
        }
    }

    private static String requireKind(String modelKind) {
        if (!StringUtils.hasText(modelKind) || !KINDS.contains(modelKind.trim().toUpperCase())) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "modelKind 须为 CHAT 或 EMBEDDING");
        }
        return modelKind.trim().toUpperCase();
    }

    private static String requireStatus(String status) {
        if (!StringUtils.hasText(status) || !STATUSES.contains(status.trim().toUpperCase())) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "status 须为 ACTIVE 或 DISABLED");
        }
        return status.trim().toUpperCase();
    }

    private static void validateOptionalKind(String modelKind) {
        if (StringUtils.hasText(modelKind) && !KINDS.contains(modelKind.trim().toUpperCase())) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "modelKind 须为 CHAT 或 EMBEDDING");
        }
    }

    private static void validateOptionalStatus(String status) {
        if (StringUtils.hasText(status) && !STATUSES.contains(status.trim().toUpperCase())) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "status 须为 ACTIVE 或 DISABLED");
        }
    }

    private static void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, field + " 不能为空");
        }
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }
}
