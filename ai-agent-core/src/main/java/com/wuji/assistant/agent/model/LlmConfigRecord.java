package com.wuji.assistant.agent.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * llm_config 行映射。
 *
 * @author liudy
 */
public class LlmConfigRecord {

    public static final String KIND_CHAT = "CHAT";
    public static final String KIND_EMBEDDING = "EMBEDDING";

    /** CHAT 行未配置输入窗口时的回落值。 */
    public static final int DEFAULT_CHAT_CONTEXT_WINDOW = 32768;

    /** EMBEDDING 行未配置输入窗口时的回落值。 */
    public static final int DEFAULT_EMBEDDING_CONTEXT_WINDOW = 8192;

    private Long id;
    private String configId;
    private String name;
    private String provider;
    private String modelKind;
    private String baseUrl;
    private String apiKeyCipher;
    private String model;
    private BigDecimal temperature;
    /** 输出上限。发给模型的 max_tokens，并从上下文窗口中预留。 */
    private Integer outputMaxTokens;
    /** 输入上下文窗口（token）。与 {@link #outputMaxTokens}（输出上限）分开。 */
    private Integer contextWindowSize;
    private String extraJson;
    private String tokenEncoding;
    private String status;
    private OffsetDateTime createTime;
    private OffsetDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getConfigId() {
        return configId;
    }

    public void setConfigId(String configId) {
        this.configId = configId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModelKind() {
        return modelKind;
    }

    public void setModelKind(String modelKind) {
        this.modelKind = modelKind;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKeyCipher() {
        return apiKeyCipher;
    }

    public void setApiKeyCipher(String apiKeyCipher) {
        this.apiKeyCipher = apiKeyCipher;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public BigDecimal getTemperature() {
        return temperature;
    }

    public void setTemperature(BigDecimal temperature) {
        this.temperature = temperature;
    }

    public Integer getOutputMaxTokens() {
        return outputMaxTokens;
    }

    public void setOutputMaxTokens(Integer outputMaxTokens) {
        this.outputMaxTokens = outputMaxTokens;
    }

    public Integer getContextWindowSize() {
        return contextWindowSize;
    }

    public void setContextWindowSize(Integer contextWindowSize) {
        this.contextWindowSize = contextWindowSize;
    }

    /**
     * 写入用的窗口：已配置正数则用之，否则 CHAT 32768、EMBEDDING 8192。
     *
     * @return 正整数窗口
     */
    public int contextWindowOrDefault() {
        if (contextWindowSize != null && contextWindowSize > 0) {
            return contextWindowSize;
        }
        if (KIND_EMBEDDING.equalsIgnoreCase(modelKind)) {
            return DEFAULT_EMBEDDING_CONTEXT_WINDOW;
        }
        return DEFAULT_CHAT_CONTEXT_WINDOW;
    }

    public String getExtraJson() {
        return extraJson;
    }

    public void setExtraJson(String extraJson) {
        this.extraJson = extraJson;
    }

    public String getTokenEncoding() {
        return tokenEncoding;
    }

    public void setTokenEncoding(String tokenEncoding) {
        this.tokenEncoding = tokenEncoding;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(OffsetDateTime createTime) {
        this.createTime = createTime;
    }

    public OffsetDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(OffsetDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
