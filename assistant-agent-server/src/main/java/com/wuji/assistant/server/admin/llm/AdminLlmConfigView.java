package com.wuji.assistant.server.admin.llm;

import java.math.BigDecimal;

/**
 * LLM 配置对外视图（含脱敏 Key）。
 *
 * @param configId       业务键
 * @param name           名称
 * @param provider       提供商
 * @param modelKind      CHAT/EMBEDDING
 * @param baseUrl        Base URL
 * @param model          模型名
 * @param temperature    温度
 * @param maxTokens      最大 token
 * @param extraJson      扩展 JSON
 * @param status         ACTIVE/DISABLED
 * @param apiKeyMasked   脱敏 Key
 * @param apiKeyPreview  明文预览（仅 SUPER_ADMIN + reveal）
 * @author liudy
 */
public record AdminLlmConfigView(
        String configId,
        String name,
        String provider,
        String modelKind,
        String baseUrl,
        String model,
        BigDecimal temperature,
        Integer maxTokens,
        String extraJson,
        String status,
        String apiKeyMasked,
        String apiKeyPreview
) {
}
