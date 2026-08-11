package com.wuji.assistant.agent.model;

import org.junit.jupiter.api.Test;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * LLM HTTP 客户端装配。
 *
 * @author liudy
 */
class LlmHttpClientsTest {

    @Test
    void builders_acceptTimeout() {
        RestClient.Builder rest = LlmHttpClients.restClientBuilder(Duration.ofSeconds(60));
        WebClient.Builder web = LlmHttpClients.webClientBuilder(Duration.ofSeconds(60));
        assertNotNull(rest);
        assertNotNull(web);
        assertNotNull(rest.build());
        assertNotNull(web.build());
    }

    @Test
    void noInnerRetry_isUsable() {
        RetryTemplate template = LlmHttpClients.noInnerRetry();
        assertNotNull(template);
        Integer value = template.execute(ctx -> 1);
        assertNotNull(value);
    }
}
