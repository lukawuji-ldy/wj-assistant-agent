package com.wuji.assistant.agent.model;

import com.wuji.assistant.agent.config.WujiModelProperties;
import com.wuji.assistant.agent.config.WujiRagProperties;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.rag.ingest.EmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.atomic.AtomicReference;

/**
 * OpenAI Compatible EmbeddingClient：从 llm_config（model_kind=EMBEDDING）加载。
 *
 * @author liudy
 */
@Component
@ConditionalOnProperty(prefix = "wuji.rag", name = "embedding-enabled", havingValue = "true", matchIfMissing = true)
public class OpenAiEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingClient.class);

    private final LlmConfigRepository llmConfigRepository;
    private final WujiModelProperties modelProperties;
    private final WujiRagProperties ragProperties;
    private final ApiKeyCipherService apiKeyCipherService;
    private final AtomicReference<EmbeddingModel> cached = new AtomicReference<>();

    public OpenAiEmbeddingClient(LlmConfigRepository llmConfigRepository,
                                 WujiModelProperties modelProperties,
                                 WujiRagProperties ragProperties,
                                 ApiKeyCipherService apiKeyCipherService) {
        this.llmConfigRepository = llmConfigRepository;
        this.modelProperties = modelProperties;
        this.ragProperties = ragProperties;
        this.apiKeyCipherService = apiKeyCipherService;
    }

    /**
     * 使 Embedding 缓存失效，下次重新从库加载。
     */
    public void invalidate() {
        cached.set(null);
        log.info("EmbeddingModel cache invalidated");
    }

    @Override
    public boolean available() {
        try {
            return model() != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public float[] embed(String text) {
        EmbeddingModel model = model();
        if (model == null || !StringUtils.hasText(text)) {
            return null;
        }
        EmbeddingResponse response = model.embedForResponse(java.util.List.of(text));
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput();
    }

    @Override
    public String embeddingConfigId() {
        String configId = ragProperties.getEmbeddingConfigId();
        return StringUtils.hasText(configId) ? configId.trim() : "unavailable";
    }

    @Override
    public String embeddingModelVersion() {
        String configId = embeddingConfigId();
        try {
            LlmConfigRecord cfg = llmConfigRepository.requireActive(configId, LlmConfigRecord.KIND_EMBEDDING);
            String model = StringUtils.hasText(cfg.getModel()) ? cfg.getModel().trim() : "unknown";
            Integer dims = LlmExtraJson.integer(cfg.getExtraJson(), "dimensions");
            String dimPart = dims == null ? "1536" : String.valueOf(dims);
            return configId + "|" + model + "|" + dimPart;
        } catch (Exception e) {
            return configId + "|unknown|1536";
        }
    }

    private EmbeddingModel model() {
        EmbeddingModel existing = cached.get();
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (cached.get() != null) {
                return cached.get();
            }
            String configId = ragProperties.getEmbeddingConfigId();
            if (!StringUtils.hasText(configId)) {
                throw new WujiException(ErrorCode.MODEL_UNAVAILABLE,
                        "未配置 wuji.rag.embedding-config-id，须指向 model_kind=EMBEDDING 的 llm_config");
            }
            LlmConfigRecord cfg = llmConfigRepository.requireActive(configId, LlmConfigRecord.KIND_EMBEDDING);
            String apiKey = resolveApiKey(cfg);
            if (!StringUtils.hasText(apiKey) || "CHANGE_ME".equals(apiKey)) {
                log.debug("embedding key missing, configId={}", configId);
                return null;
            }
            String embeddingModel = cfg.getModel();
            if (!StringUtils.hasText(embeddingModel)) {
                throw new WujiException(ErrorCode.MODEL_UNAVAILABLE,
                        "EMBEDDING 配置缺少 model: " + configId);
            }

            OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
                    .baseUrl(cfg.getBaseUrl())
                    .apiKey(apiKey)
                    .restClientBuilder(LlmHttpClients.restClientBuilder(modelProperties.getTimeout()));
            String embeddingsPath = LlmExtraJson.text(cfg.getExtraJson(), "embeddings_path");
            if (StringUtils.hasText(embeddingsPath)) {
                apiBuilder.embeddingsPath(embeddingsPath);
            }
            OpenAiEmbeddingModel em = new OpenAiEmbeddingModel(
                    apiBuilder.build(),
                    MetadataMode.EMBED,
                    OpenAiEmbeddingOptions.builder().model(embeddingModel).build());
            cached.set(em);
            log.info("EmbeddingModel ready configId={} model={}", configId, embeddingModel);
            return em;
        }
    }

    private String resolveApiKey(LlmConfigRecord cfg) {
        if (StringUtils.hasText(modelProperties.getApiKeyOverride())) {
            return modelProperties.getApiKeyOverride().trim();
        }
        return apiKeyCipherService.decrypt(cfg.getApiKeyCipher());
    }
}
