package com.wuji.assistant.agent.token;

/**
 * 按 configId 取计数器。词表登记仍由 {@code ModelTokenRegistryBinder} 写入 {@code TokenEstimator}。
 *
 * @author liudy
 */
public final class TokenCounterRegistry {

    private TokenCounterRegistry() {
    }

    /**
     * @param configId llm_config.config_id，可空
     * @return 绑定该配置的计数器
     */
    public static TokenCounter forConfig(String configId) {
        return new EstimatingTokenCounter(configId);
    }
}
