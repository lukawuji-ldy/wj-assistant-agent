package com.wuji.assistant.agent.model;

import com.wuji.assistant.agent.config.WujiModelProperties;
import com.wuji.assistant.agent.observability.AgentTelemetry;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ModelRouter 主备切换与重试单元测试。
 *
 * @author liudy
 */
@ExtendWith(MockitoExtension.class)
class ModelRouterTest {

    @Mock
    private LlmClientFactory llmClientFactory;

    @Mock
    private LlmCallAuditor llmCallAuditor;

    private WujiModelProperties modelProperties;
    private ModelRouter modelRouter;

    @BeforeEach
    void setUp() {
        modelProperties = new WujiModelProperties();
        modelProperties.setPrimaryConfigId("llm_primary");
        modelProperties.setFallbackConfigIds(List.of("llm_backup_1"));
        modelProperties.getRetry().setMaxAttempts(1);
        modelProperties.getRetry().setBackoff(Duration.ZERO);
        modelProperties.setRateLimitCodes(List.of(429));
        @SuppressWarnings("unchecked")
        ObjectProvider<Object> empty = mock(ObjectProvider.class);
        when(empty.getIfAvailable(any())).thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());
        AgentTelemetry telemetry = new AgentTelemetry(
                (ObjectProvider) empty,
                (ObjectProvider) empty);
        modelRouter = new ModelRouter(llmClientFactory, modelProperties, llmCallAuditor, telemetry);
    }

    private ModelRouter newRouter() {
        @SuppressWarnings("unchecked")
        ObjectProvider<Object> empty = mock(ObjectProvider.class);
        when(empty.getIfAvailable(any())).thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());
        AgentTelemetry telemetry = new AgentTelemetry(
                (ObjectProvider) empty,
                (ObjectProvider) empty);
        return new ModelRouter(llmClientFactory, modelProperties, llmCallAuditor, telemetry);
    }

    @Test
    void primary429_thenFallbackSucceeds() {
        LlmConfigRecord primary = config("llm_primary", "p1");
        LlmConfigRecord backup = config("llm_backup_1", "p2");
        ChatClient primaryClient = mock(ChatClient.class);
        ChatClient backupClient = mock(ChatClient.class);
        when(llmClientFactory.getChatClient("llm_primary")).thenReturn(primaryClient);
        when(llmClientFactory.getConfig("llm_primary")).thenReturn(primary);
        when(llmClientFactory.getChatClient("llm_backup_1")).thenReturn(backupClient);
        when(llmClientFactory.getConfig("llm_backup_1")).thenReturn(backup);

        ModelRouter.AuditContext audit = new ModelRouter.AuditContext(
                "t1", "c1", "m1", "u1", "sys", "user");

        ModelRouter.RoutedResult<String> result = modelRouter.execute(audit, routed -> {
            if ("llm_primary".equals(routed.configId())) {
                throw WebClientResponseException.create(429, "Too Many Requests", null, null, null);
            }
            return "from-backup";
        });

        assertEquals("from-backup", result.value());
        assertEquals("llm_backup_1", result.configId());
        assertTrue(result.client().fallback());

        ArgumentCaptor<LlmCallAuditor.AuditParams> captor =
                ArgumentCaptor.forClass(LlmCallAuditor.AuditParams.class);
        verify(llmCallAuditor, atLeastOnce()).record(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(p ->
                "FAILED".equals(p.status()) && "llm_primary".equals(p.modelId())));
        assertTrue(captor.getAllValues().stream().anyMatch(p ->
                "SUCCESS".equals(p.status()) && p.fallback() && "llm_backup_1".equals(p.modelId())));
    }

    @Test
    void nonRetryableError_doesNotFailover() {
        LlmConfigRecord primary = config("llm_primary", "p1");
        when(llmClientFactory.getChatClient("llm_primary")).thenReturn(mock(ChatClient.class));
        when(llmClientFactory.getConfig("llm_primary")).thenReturn(primary);

        ModelRouter.AuditContext audit = new ModelRouter.AuditContext(
                "t1", "c1", "m1", "u1", "sys", "user");

        WujiException thrown = assertThrows(WujiException.class, () ->
                modelRouter.execute(audit, routed -> {
                    throw new WujiException(ErrorCode.BAD_REQUEST, "参数非法");
                }));

        assertEquals(ErrorCode.BAD_REQUEST, thrown.getErrorCode());
        verify(llmClientFactory, never()).getChatClient("llm_backup_1");
    }

    @Test
    void emptyFallbacks_allUnavailable_throwsModelUnavailable() {
        modelProperties.setFallbackConfigIds(List.of());
        modelRouter = newRouter();

        when(llmClientFactory.getChatClient("llm_primary"))
                .thenThrow(new WujiException(ErrorCode.MODEL_UNAVAILABLE, "key missing"));

        ModelRouter.AuditContext audit = new ModelRouter.AuditContext(
                "t1", "c1", "m1", "u1", "sys", "user");

        WujiException thrown = assertThrows(WujiException.class, () ->
                modelRouter.execute(audit, routed -> "never"));

        assertEquals(ErrorCode.MODEL_UNAVAILABLE, thrown.getErrorCode());
        verify(llmCallAuditor, never()).record(any());
    }

    @Test
    void isRetryable_rateLimitAndBadRequest() {
        assertTrue(modelRouter.isRetryable(
                WebClientResponseException.create(429, "Too Many", null, null, null)));
        assertFalse(modelRouter.isRetryable(new WujiException(ErrorCode.BAD_REQUEST, "bad")));
        assertTrue(modelRouter.isRetryable(new WujiException(ErrorCode.MODEL_TIMEOUT, "timeout")));
    }

    @Test
    void interrupted_isNotRetryable_mapsToTimeout() {
        ResourceAccessException ex = new ResourceAccessException(
                "I/O error on POST request for \"https://api.edgefn.net/v1/chat/completions\": java.lang.InterruptedException",
                new IOException(new InterruptedException()));
        assertFalse(modelRouter.isRetryable(ex));
        assertEquals(ErrorCode.MODEL_TIMEOUT, modelRouter.mapErrorCode(ex));
    }

    @Test
    void interrupted_doesNotFailover() {
        LlmConfigRecord primary = config("llm_primary", "p1");
        when(llmClientFactory.getChatClient("llm_primary")).thenReturn(mock(ChatClient.class));
        when(llmClientFactory.getConfig("llm_primary")).thenReturn(primary);

        ResourceAccessException ex = new ResourceAccessException(
                "interrupted", new IOException(new InterruptedException()));
        ModelRouter.AuditContext audit = new ModelRouter.AuditContext(
                "t1", "c1", "m1", "u1", "sys", "user");

        WujiException thrown = assertThrows(WujiException.class, () ->
                modelRouter.execute(audit, routed -> {
                    throw ex;
                }));

        assertEquals(ErrorCode.MODEL_TIMEOUT, thrown.getErrorCode());
        verify(llmClientFactory, never()).getChatClient("llm_backup_1");
    }

    @Test
    void mapErrorCode_arraycopyAndUtf8AreInternalNotModelUnavailable() {
        assertEquals(ErrorCode.INTERNAL_ERROR,
                modelRouter.mapErrorCode(new ArrayIndexOutOfBoundsException(
                        "arraycopy: last destination index 22 out of bounds for byte[16]")));
        assertEquals(ErrorCode.INTERNAL_ERROR,
                modelRouter.mapErrorCode(new RuntimeException(
                        "PreparedStatementCallback; 无效的 \"UTF8\" 编码字节顺序: 0x00")));
    }

    @Test
    void orderedConfigIds_dedupes() {
        modelProperties.setFallbackConfigIds(List.of("llm_primary", "llm_backup_1", "llm_backup_1"));
        assertEquals(List.of("llm_primary", "llm_backup_1"), modelRouter.orderedConfigIds());
    }

    @Test
    void sameConfigRetries_thenFailover() {
        modelProperties.getRetry().setMaxAttempts(2);
        modelRouter = newRouter();

        LlmConfigRecord primary = config("llm_primary", "p1");
        LlmConfigRecord backup = config("llm_backup_1", "p2");
        when(llmClientFactory.getChatClient("llm_primary")).thenReturn(mock(ChatClient.class));
        when(llmClientFactory.getConfig("llm_primary")).thenReturn(primary);
        when(llmClientFactory.getChatClient("llm_backup_1")).thenReturn(mock(ChatClient.class));
        when(llmClientFactory.getConfig("llm_backup_1")).thenReturn(backup);

        AtomicInteger primaryHits = new AtomicInteger();
        ModelRouter.AuditContext audit = new ModelRouter.AuditContext(
                "t1", "c1", "m1", "u1", "sys", "user");

        ModelRouter.RoutedResult<String> result = modelRouter.execute(audit, routed -> {
            if ("llm_primary".equals(routed.configId())) {
                primaryHits.incrementAndGet();
                throw new WujiException(ErrorCode.MODEL_RATE_LIMITED, "429");
            }
            return "ok";
        });

        assertEquals(2, primaryHits.get());
        assertEquals("llm_backup_1", result.configId());
    }

    @Test
    void swallowedAgentLlmNode429_thenFallbackSucceeds() {
        LlmConfigRecord primary = config("llm_primary", "p1");
        LlmConfigRecord backup = config("llm_backup_1", "p2");
        when(llmClientFactory.getChatClient("llm_primary")).thenReturn(mock(ChatClient.class));
        when(llmClientFactory.getConfig("llm_primary")).thenReturn(primary);
        when(llmClientFactory.getChatClient("llm_backup_1")).thenReturn(mock(ChatClient.class));
        when(llmClientFactory.getConfig("llm_backup_1")).thenReturn(backup);

        ModelRouter.AuditContext audit = new ModelRouter.AuditContext(
                "t1", "c1", "m1", "u1", "sys", "user");
        ModelRouter.RoutedResult<String> result = modelRouter.execute(audit, routed -> {
            if ("llm_primary".equals(routed.configId())) {
                throw SwallowedLlmErrors.toException(
                        "Exception: 429 - {\"code\":429,\"reason\":\"RateLimitExceeded\"}");
            }
            return "from-backup";
        });

        assertEquals("from-backup", result.value());
        assertEquals("llm_backup_1", result.configId());
        assertTrue(result.client().fallback());
    }

    private static LlmConfigRecord config(String configId, String provider) {
        LlmConfigRecord r = new LlmConfigRecord();
        r.setConfigId(configId);
        r.setProvider(provider);
        r.setModelKind(LlmConfigRecord.KIND_CHAT);
        r.setModel("gpt-test");
        return r;
    }
}
