package com.wuji.assistant.server.admin.llm;

import java.math.BigDecimal;

/**
 * 更新 LLM 配置请求；apiKey 为空则不改密文。
 *
 * @author liudy
 */
public class AdminLlmConfigUpdateRequest {

    private String name;
    private String provider;
    private String modelKind;
    private String baseUrl;
    private String apiKey;
    private String model;
    private BigDecimal temperature;
    private Integer maxTokens;
    private String extraJson;
    private String status;

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

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
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

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public String getExtraJson() {
        return extraJson;
    }

    public void setExtraJson(String extraJson) {
        this.extraJson = extraJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
