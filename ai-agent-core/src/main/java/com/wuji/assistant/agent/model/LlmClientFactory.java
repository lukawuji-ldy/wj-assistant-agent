package com.wuji.assistant.agent.model;

import com.wuji.assistant.agent.config.WujiModelProperties;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 llm_config 构建并缓存 ChatModel / ChatClient；同 configId 进程内只初始化一次。
 *
 * @author liudy
 */
@Component
public class LlmClientFactory {

    private static final Logger log = LoggerFactory.getLogger(LlmClientFactory.class);

    private final LlmConfigRepository llmConfigRepository;
    private final WujiModelProperties modelProperties;
    private final ConcurrentHashMap<String, CachedClients> cache = new ConcurrentHashMap<>();

    public LlmClientFactory(LlmConfigRepository llmConfigRepository, WujiModelProperties modelProperties) {
        this.llmConfigRepository = llmConfigRepository;
        this.modelProperties = modelProperties;
    }

    /**
     * 获取或创建 ChatClient。
     *
     * @param configId 配置业务键
     * @return ChatClient
     */
    public ChatClient getChatClient(String configId) {
        return getOrCreate(configId).chatClient();
    }

    /**
     * 获取或创建 ChatModel。
     *
     * @param configId 配置业务键
     * @return ChatModel
     */
    public ChatModel getChatModel(String configId) {
        return getOrCreate(configId).chatModel();
    }

    /**
     * 读取配置记录（用于审计 model 字段）。
     *
     * @param configId 配置键
     * @return 配置
     */
    public LlmConfigRecord getConfig(String configId) {
        return getOrCreate(configId).config();
    }

    /**
     * 使缓存失效，下次重新从库加载。
     *
     * @param configId 配置键
     */
    public void invalidate(String configId) {
        cache.remove(configId);
        log.info("LLM client cache invalidated, configId={}", configId);
    }

    private CachedClients getOrCreate(String configId) {
        return cache.computeIfAbsent(configId, this::build);
    }

    private CachedClients build(String configId) {
        LlmConfigRecord cfg = llmConfigRepository.requireActive(configId);
        String apiKey = resolveApiKey(cfg);
        if (!StringUtils.hasText(apiKey) || "CHANGE_ME".equals(apiKey)) {
            throw new WujiException(ErrorCode.MODEL_UNAVAILABLE,
                    "LLM API Key 未配置，请更新 llm_config 或设置环境变量 WUJI_LLM_API_KEY");
        }
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(cfg.getBaseUrl())
                .apiKey(apiKey)
                .build();
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(cfg.getModel());
        if (cfg.getTemperature() != null) {
            optionsBuilder.temperature(cfg.getTemperature().doubleValue());
        }
        if (cfg.getMaxTokens() != null) {
            optionsBuilder.maxTokens(cfg.getMaxTokens());
        }
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(optionsBuilder.build())
                .build();
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        log.info("LLM client initialized, configId={}, model={}", configId, cfg.getModel());
        return new CachedClients(cfg, chatModel, chatClient);
    }

    private String resolveApiKey(LlmConfigRecord cfg) {
        if (StringUtils.hasText(modelProperties.getApiKeyOverride())) {
            return modelProperties.getApiKeyOverride().trim();
        }
        String cipher = cfg.getApiKeyCipher();
        if (cipher == null) {
            return "";
        }
        // 首期：未加 enc: 前缀时视为明文（仅本地开发）
        if (cipher.startsWith("enc:")) {
            return cipher.substring(4);
        }
        return cipher;
    }

    private record CachedClients(LlmConfigRecord config, ChatModel chatModel, ChatClient chatClient) {
    }
}
