package com.wuji.assistant.agent.model;

import com.wuji.assistant.agent.config.WujiModelProperties;
import com.wuji.assistant.agent.config.WujiRagProperties;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * OpenAiEmbeddingClient 配置校验测试。
 *
 * @author liudy
 */
@ExtendWith(MockitoExtension.class)
class OpenAiEmbeddingClientTest {

    @Mock
    private LlmConfigRepository llmConfigRepository;

    private WujiModelProperties modelProperties;
    private WujiRagProperties ragProperties;
    private ApiKeyCipherService apiKeyCipherService;
    private OpenAiEmbeddingClient client;

    @BeforeEach
    void setUp() {
        modelProperties = new WujiModelProperties();
        ragProperties = new WujiRagProperties();
        ragProperties.setEmbeddingConfigId("llm_embedding");
        com.wuji.assistant.agent.config.WujiSecurityProperties security =
                new com.wuji.assistant.agent.config.WujiSecurityProperties();
        apiKeyCipherService = new ApiKeyCipherService(security);
        client = new OpenAiEmbeddingClient(llmConfigRepository, modelProperties, ragProperties, apiKeyCipherService);
    }

    @Test
    void available_falseWhenChatConfigRejected() {
        when(llmConfigRepository.requireActive(eq("llm_embedding"), eq(LlmConfigRecord.KIND_EMBEDDING)))
                .thenThrow(new WujiException(ErrorCode.MODEL_UNAVAILABLE, "kind mismatch"));
        assertFalse(client.available());
    }

    @Test
    void available_falseWhenBlankEmbeddingConfigId() {
        ragProperties.setEmbeddingConfigId("");
        client = new OpenAiEmbeddingClient(llmConfigRepository, modelProperties, ragProperties, apiKeyCipherService);
        assertFalse(client.available());
    }

    @Test
    void modelUsesConfigFromDb_notYml() {
        LlmConfigRecord emb = new LlmConfigRecord();
        emb.setConfigId("llm_embedding");
        emb.setModelKind(LlmConfigRecord.KIND_EMBEDDING);
        emb.setModel("text-embedding-3-small");
        emb.setBaseUrl("https://api.openai.com/v1");
        emb.setApiKeyCipher("CHANGE_ME");
        when(llmConfigRepository.requireActive(eq("llm_embedding"), eq(LlmConfigRecord.KIND_EMBEDDING)))
                .thenReturn(emb);
        // key missing → available false, but kind+model already from DB path
        assertFalse(client.available());
        assertEquals("text-embedding-3-small", emb.getModel());
    }
}
