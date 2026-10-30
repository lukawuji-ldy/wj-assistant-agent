package com.wuji.assistant.agent.model;

import com.wuji.assistant.agent.config.WujiModelProperties;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.common.token.OpenAiTokenCounters;
import com.wuji.assistant.common.token.TokenEstimator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * 启动时按主备行登记词表。
 *
 * @author liudy
 */
@ExtendWith(MockitoExtension.class)
class ModelTokenRegistryBinderTest {

    @Mock
    private LlmConfigRepository llmConfigRepository;

    @Mock
    private WujiModelProperties modelProperties;

    @AfterEach
    void clearRegistry() {
        TokenEstimator.clearProvider();
    }

    @Test
    void registersPrimaryAndFallback() {
        when(modelProperties.getPrimaryConfigId()).thenReturn("llm_primary");
        when(modelProperties.getFallbackConfigIds()).thenReturn(List.of("llm_backup_1"));
        when(llmConfigRepository.requireActive("llm_primary", LlmConfigRecord.KIND_CHAT))
                .thenReturn(chat("llm_primary", "gpt-4o-mini", null));
        when(llmConfigRepository.requireActive("llm_backup_1", LlmConfigRecord.KIND_CHAT))
                .thenReturn(chat("llm_backup_1", "qwen-plus", null));

        new ModelTokenRegistryBinder(llmConfigRepository, modelProperties).run(null);

        String sample = "Spring AI 工厂";
        assertEquals(OpenAiTokenCounters.counter(OpenAiTokenCounters.O200K_BASE).apply(sample),
                TokenEstimator.estimate(sample));
        assertEquals(OpenAiTokenCounters.counter(OpenAiTokenCounters.CL100K_BASE).apply(sample),
                TokenEstimator.estimate(sample, "llm_backup_1"));
    }

    @Test
    void missingPrimaryUsesCl100kDefault() {
        when(modelProperties.getPrimaryConfigId()).thenReturn("llm_primary");
        when(modelProperties.getFallbackConfigIds()).thenReturn(List.of());
        when(llmConfigRepository.requireActive("llm_primary", LlmConfigRecord.KIND_CHAT))
                .thenThrow(new WujiException(ErrorCode.MODEL_UNAVAILABLE, "missing"));

        new ModelTokenRegistryBinder(llmConfigRepository, modelProperties).run(null);

        assertEquals(OpenAiTokenCounters.counter(OpenAiTokenCounters.CL100K_BASE).apply("Spring"),
                TokenEstimator.estimate("Spring"));
    }

    private static LlmConfigRecord chat(String configId, String model, String tokenEncoding) {
        LlmConfigRecord row = new LlmConfigRecord();
        row.setConfigId(configId);
        row.setModelKind(LlmConfigRecord.KIND_CHAT);
        row.setModel(model);
        row.setTokenEncoding(tokenEncoding);
        return row;
    }
}
