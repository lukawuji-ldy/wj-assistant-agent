package com.wuji.assistant.server.admin.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.agent.AgentFactory;
import com.wuji.assistant.agent.config.WujiModelProperties;
import com.wuji.assistant.agent.config.WujiRagProperties;
import com.wuji.assistant.agent.config.WujiSecurityProperties;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminLlmConfigService 单元测试。
 *
 * @author liudy
 */
@ExtendWith(MockitoExtension.class)
class AdminLlmConfigServiceTest {

    @Mock
    private LlmConfigRepository llmConfigRepository;
    @Mock
    private LlmClientFactory llmClientFactory;
    @Mock
    private AgentFactory agentFactory;
    @Mock
    private ObjectProvider<OpenAiEmbeddingClient> embeddingClient;
    @Mock
    private OpenAiEmbeddingClient openAiEmbeddingClient;
    @Mock
    private AdminAuditLogRepository auditLogRepository;

    private ApiKeyCipherService apiKeyCipherService;
    private AdminLlmConfigService service;
    private final AdminAuthUser superAdmin = AdminAuthUser.of("a_admin", "admin", "SUPER_ADMIN");
    private final AdminAuthUser operator = AdminAuthUser.of("a_op", "op", "OPERATOR");

    @BeforeEach
    void setUp() {
        WujiSecurityProperties security = new WujiSecurityProperties();
        security.setApiKeySecret("unit-test-api-key-secret");
        apiKeyCipherService = new ApiKeyCipherService(security);
        WujiModelProperties modelProperties = new WujiModelProperties();
        modelProperties.setPrimaryConfigId("llm_primary");
        WujiRagProperties ragProperties = new WujiRagProperties();
        ragProperties.setEmbeddingConfigId("llm_embedding");
        service = new AdminLlmConfigService(
                llmConfigRepository,
                apiKeyCipherService,
                llmClientFactory,
                agentFactory,
                embeddingClient,
                modelProperties,
                ragProperties,
                auditLogRepository,
                new ObjectMapper());
    }

    @Test
    void createEncryptsApiKeyAndInvalidatesCache() {
        when(llmConfigRepository.findByConfigId("llm_new")).thenReturn(Optional.empty());

        AdminLlmConfigCreateRequest req = new AdminLlmConfigCreateRequest();
        req.setConfigId("llm_new");
        req.setName("新模型");
        req.setModelKind("CHAT");
        req.setBaseUrl("https://api.example.com/v1");
        req.setApiKey("sk-secret-key-9999");
        req.setModel("gpt-4o-mini");

        ArgumentCaptor<LlmConfigRecord> captor = ArgumentCaptor.forClass(LlmConfigRecord.class);
        AdminLlmConfigView view = service.create(superAdmin, req);

        verify(llmConfigRepository).insert(captor.capture());
        assertTrue(captor.getValue().getApiKeyCipher().startsWith(ApiKeyCipherService.PREFIX_V1));
        assertEquals("******9999", view.apiKeyMasked());
        assertNull(view.apiKeyPreview());
        verify(llmClientFactory).invalidate("llm_new");
        verify(agentFactory).invalidate("llm_new");
        verify(auditLogRepository).insert(eq("a_admin"), eq("CREATE"), eq("llm_config"), eq("llm_new"), any());
    }

    @Test
    void operatorCannotRevealKey() {
        LlmConfigRecord record = sampleRecord("llm_primary", "CHAT",
                apiKeyCipherService.encrypt("sk-abcdefghijkl"));
        when(llmConfigRepository.findByConfigId("llm_primary")).thenReturn(Optional.of(record));

        AdminLlmConfigView view = service.get(operator, "llm_primary", true);
        assertNull(view.apiKeyPreview());
        assertEquals("******ijkl", view.apiKeyMasked());
    }

    @Test
    void superAdminCanRevealKey() {
        LlmConfigRecord record = sampleRecord("llm_primary", "CHAT",
                apiKeyCipherService.encrypt("sk-abcdefghijkl"));
        when(llmConfigRepository.findByConfigId("llm_primary")).thenReturn(Optional.of(record));

        AdminLlmConfigView view = service.get(superAdmin, "llm_primary", true);
        assertEquals("sk-abcdefghijkl", view.apiKeyPreview());
    }

    @Test
    void updateWithoutKeyDoesNotRewriteCipher() {
        LlmConfigRecord record = sampleRecord("llm_primary", "CHAT", "CHANGE_ME");
        when(llmConfigRepository.findByConfigId("llm_primary")).thenReturn(Optional.of(record));

        AdminLlmConfigUpdateRequest req = new AdminLlmConfigUpdateRequest();
        req.setName("改名");
        service.update(superAdmin, "llm_primary", req);

        verify(llmConfigRepository).update(any(LlmConfigRecord.class), eq(false));
        verify(llmClientFactory).invalidate("llm_primary");
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateAuditsModelFromTo() {
        LlmConfigRecord record = sampleRecord("llm_primary", "CHAT", "CHANGE_ME");
        record.setModel("MiniMax-M2.5");
        when(llmConfigRepository.findByConfigId("llm_primary")).thenReturn(Optional.of(record));

        AdminLlmConfigUpdateRequest req = new AdminLlmConfigUpdateRequest();
        req.setModel("MiniMax-M3");
        service.update(superAdmin, "llm_primary", req);

        ArgumentCaptor<Map<String, ?>> detailCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditLogRepository).insert(eq("a_admin"), eq("UPDATE"), eq("llm_config"),
                eq("llm_primary"), detailCaptor.capture());
        Map<String, ?> detail = detailCaptor.getValue();
        List<Map<String, Object>> changes = (List<Map<String, Object>>) detail.get("changes");
        assertEquals(1, changes.size());
        assertEquals("model", changes.get(0).get("field"));
        assertEquals("MiniMax-M2.5", changes.get(0).get("from"));
        assertEquals("MiniMax-M3", changes.get(0).get("to"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createAuditsSensitiveApiKeyWithoutPlaintext() {
        when(llmConfigRepository.findByConfigId("llm_new")).thenReturn(Optional.empty());

        AdminLlmConfigCreateRequest req = new AdminLlmConfigCreateRequest();
        req.setConfigId("llm_new");
        req.setName("新模型");
        req.setModelKind("CHAT");
        req.setBaseUrl("https://api.example.com/v1");
        req.setApiKey("sk-secret-key-9999");
        req.setModel("gpt-4o-mini");
        service.create(superAdmin, req);

        ArgumentCaptor<Map<String, ?>> detailCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditLogRepository).insert(eq("a_admin"), eq("CREATE"), eq("llm_config"),
                eq("llm_new"), detailCaptor.capture());
        List<Map<String, Object>> changes = (List<Map<String, Object>>) detailCaptor.getValue().get("changes");
        Map<String, Object> apiKeyChange = changes.stream()
                .filter(c -> "apiKey".equals(c.get("field")))
                .findFirst()
                .orElseThrow();
        assertEquals(AdminAuditDetail.CHANGED, apiKeyChange.get("to"));
        assertTrue(changes.stream().noneMatch(c -> String.valueOf(c.get("to")).contains("sk-secret")));
    }

    @Test
    void deleteSoftDisablesAndInvalidatesEmbeddingWhenMatched() {
        LlmConfigRecord record = sampleRecord("llm_embedding", "EMBEDDING", "CHANGE_ME");
        when(llmConfigRepository.findByConfigId("llm_embedding")).thenReturn(Optional.of(record));
        when(embeddingClient.getIfAvailable()).thenReturn(openAiEmbeddingClient);

        service.delete(superAdmin, "llm_embedding");

        verify(llmConfigRepository).softDisable("llm_embedding");
        verify(openAiEmbeddingClient).invalidate();
        verify(llmClientFactory).invalidate("llm_embedding");
    }

    @Test
    void createRejectsDuplicate() {
        when(llmConfigRepository.findByConfigId("llm_primary"))
                .thenReturn(Optional.of(sampleRecord("llm_primary", "CHAT", "x")));
        AdminLlmConfigCreateRequest req = new AdminLlmConfigCreateRequest();
        req.setConfigId("llm_primary");
        req.setName("n");
        req.setModelKind("CHAT");
        req.setBaseUrl("https://x");
        req.setApiKey("k");
        req.setModel("m");
        WujiException ex = assertThrows(WujiException.class, () -> service.create(superAdmin, req));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        verify(llmConfigRepository, never()).insert(any());
    }

    private static LlmConfigRecord sampleRecord(String configId, String kind, String cipher) {
        LlmConfigRecord r = new LlmConfigRecord();
        r.setConfigId(configId);
        r.setName("name");
        r.setProvider("openai_compatible");
        r.setModelKind(kind);
        r.setBaseUrl("https://api.example.com/v1");
        r.setApiKeyCipher(cipher);
        r.setModel("gpt-4o-mini");
        r.setStatus("ACTIVE");
        return r;
    }
}
