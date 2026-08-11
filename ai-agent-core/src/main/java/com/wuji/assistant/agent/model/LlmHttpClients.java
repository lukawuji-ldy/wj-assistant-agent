package com.wuji.assistant.agent.model;

import io.netty.channel.ChannelOption;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.net.http.HttpClient.Version;
import java.time.Duration;

/**
 * LLM HTTP 客户端：同步走 JDK HttpClient（避免 WebFlux 下 RestClient 套 Reactor block 被 interrupt），
 * 流式走 WebClient + responseTimeout。
 *
 * @author liudy
 */
public final class LlmHttpClients {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);

    private LlmHttpClients() {
    }

    /**
     * 同步 Chat/Embedding（OpenAiApi RestClient）用 JDK 工厂，读超时取 {@code timeout}。
     *
     * @param timeout 响应读超时
     * @return RestClient.Builder
     */
    public static RestClient.Builder restClientBuilder(Duration timeout) {
        Duration readTimeout = timeout == null || timeout.isZero() || timeout.isNegative()
                ? Duration.ofSeconds(60) : timeout;
        java.net.http.HttpClient jdk = java.net.http.HttpClient.newBuilder()
                .version(Version.HTTP_1_1)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(factory);
    }

    /**
     * 流式 completions（OpenAiApi WebClient）用 Reactor Netty，带连接/读超时。
     *
     * @param timeout 响应读超时
     * @return WebClient.Builder
     */
    public static WebClient.Builder webClientBuilder(Duration timeout) {
        Duration readTimeout = timeout == null || timeout.isZero() || timeout.isNegative()
                ? Duration.ofSeconds(60) : timeout;
        HttpClient httpClient = HttpClient.create()
                .compress(true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(CONNECT_TIMEOUT.toMillis()))
                .responseTimeout(readTimeout);
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient));
    }

    /**
     * OpenAiChatModel 内置 Retry 关掉：主备/退避由 {@link ModelRouter} 负责。
     * 否则 InterruptedException 会被再 sleep，变成 BackOffInterruptedException。
     *
     * @return 仅尝试一次的 RetryTemplate
     */
    public static RetryTemplate noInnerRetry() {
        return RetryTemplate.builder().maxAttempts(1).build();
    }
}
