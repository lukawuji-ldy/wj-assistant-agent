package com.wuji.assistant.agent.kbtest;

import com.wuji.assistant.agent.checkpoint.CheckpointSaverFactory;
import com.wuji.assistant.agent.checkpoint.CheckpointThreadReader;
import com.wuji.assistant.agent.config.WujiAgentProperties;
import com.wuji.assistant.agent.config.WujiModelProperties;
import com.wuji.assistant.agent.config.WujiRagProperties;
import com.wuji.assistant.agent.model.LlmClientFactory;
import com.wuji.assistant.agent.prompt.PromptTemplateService;
import com.wuji.assistant.agent.token.InputBudgetCalculator;
import com.wuji.assistant.agent.token.TokenBudgetEnforcer;
import com.wuji.assistant.agent.tool.DomainKnowledgeTool;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.rag.KnowledgeRetrievalService;
import com.wuji.assistant.rag.diagnose.RetrievalTestOptions;
import com.wuji.assistant.rag.diagnose.RetrievalTrace;
import com.wuji.assistant.rag.router.KbDomain;
import com.wuji.assistant.rag.router.KbDomainRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识库测试执行器：直接检索参数校验与 Agent 模式前置失败语义。
 *
 * @author liudy
 */
class KnowledgeAgentTestServiceTest {

    @Test
    void diagnoseRetrieval_blankQuery_isBadRequest() {
        KnowledgeAgentTestService service = service(mock(KnowledgeRetrievalService.class),
                mock(KbDomainRepository.class), new WujiRagProperties(), new WujiModelProperties());

        WujiException ex = assertThrows(WujiException.class,
                () -> service.diagnoseRetrieval("   ", "sales", null));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void diagnoseRetrieval_blankDomain_isBadRequest() {
        KnowledgeAgentTestService service = service(mock(KnowledgeRetrievalService.class),
                domainRepo(List.of(new KbDomain("sales", "销售", "llm_sales"))),
                new WujiRagProperties(), new WujiModelProperties());

        WujiException ex = assertThrows(WujiException.class,
                () -> service.diagnoseRetrieval("价格", "  ", null));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void diagnoseRetrieval_unknownDomain_isNotFound() {
        KnowledgeAgentTestService service = service(mock(KnowledgeRetrievalService.class),
                domainRepo(List.of(new KbDomain("hr", "人事", "llm_hr"))),
                new WujiRagProperties(), new WujiModelProperties());

        WujiException ex = assertThrows(WujiException.class,
                () -> service.diagnoseRetrieval("价格", "sales", null));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void diagnoseRetrieval_delegatesWithOnlineFallbackOptions() {
        KnowledgeRetrievalService retrieval = mock(KnowledgeRetrievalService.class);
        RetrievalTrace expected = trace("价格");
        when(retrieval.diagnose(eq("价格"), eq("sales"), any())).thenReturn(expected);

        WujiRagProperties rag = new WujiRagProperties();
        rag.setTopK(5);
        rag.setMinReliableScore(0.72d);
        KnowledgeAgentTestService service = service(retrieval,
                domainRepo(List.of(new KbDomain("sales", "销售", "llm_sales"))), rag, new WujiModelProperties());

        RetrievalTrace actual = service.diagnoseRetrieval("  价格  ", "sales", null);
        assertSame(expected, actual);

        ArgumentCaptor<RetrievalTestOptions> captor = ArgumentCaptor.forClass(RetrievalTestOptions.class);
        verify(retrieval).diagnose(eq("价格"), eq("sales"), captor.capture());
        assertEquals(5, captor.getValue().finalTopK().intValue());
        assertEquals(0.72d, captor.getValue().minReliableScore(), 1e-9);
    }

    @Test
    void runAgent_blankQuery_returnsFailedWithoutAgentCall() {
        KnowledgeAgentTestService service = service(mock(KnowledgeRetrievalService.class),
                mock(KbDomainRepository.class), new WujiRagProperties(), new WujiModelProperties());

        KnowledgeAgentTestResult result = service.runAgent("  ", null);

        assertEquals(KbTestStatus.FAILED, result.status());
        assertNull(result.answer());
        assertNull(result.parentThreadId());
        assertTrue(result.parentThreadName().startsWith(KnowledgeAgentTestService.TEST_THREAD_PREFIX));
        assertTrue(result.warnings().contains("query 为空"));
    }

    @Test
    void runAgent_blankPrimaryConfig_returnsFailed() {
        // primaryConfigId 为空 → 主 Agent 构建失败，状态降级为 FAILED
        WujiModelProperties model = new WujiModelProperties();
        model.setPrimaryConfigId(" ");
        KnowledgeAgentTestService service = service(mock(KnowledgeRetrievalService.class),
                mock(KbDomainRepository.class), new WujiRagProperties(), model);

        KnowledgeAgentTestResult result = service.runAgent("价格是多少", null);

        assertEquals(KbTestStatus.FAILED, result.status());
        assertNull(result.answer());
        assertTrue(result.parentThreadName().startsWith(KnowledgeAgentTestService.TEST_THREAD_PREFIX));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("主 Agent 不可用")));
    }

    private static KnowledgeAgentTestService service(KnowledgeRetrievalService retrieval,
                                                     KbDomainRepository domains,
                                                     WujiRagProperties rag,
                                                     WujiModelProperties model) {
        return new KnowledgeAgentTestService(
                retrieval,
                domains,
                mock(LlmClientFactory.class),
                new WujiAgentProperties(),
                model,
                rag,
                mock(CheckpointSaverFactory.class),
                mock(TokenBudgetEnforcer.class),
                new InputBudgetCalculator(),
                mock(PromptTemplateService.class),
                mock(DomainKnowledgeTool.class),
                mock(CheckpointThreadReader.class));
    }

    private static KbDomainRepository domainRepo(List<KbDomain> rows) {
        KbDomainRepository repository = mock(KbDomainRepository.class);
        when(repository.listAll()).thenReturn(rows);
        return repository;
    }

    private static RetrievalTrace trace(String query) {
        return new RetrievalTrace(
                "pgvector",
                "sales",
                query,
                RetrievalTestOptions.defaults(),
                new RetrievalTrace.EmbeddingInfo(true, "llm_embedding", "fp", 1024, 3L),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                8L);
    }
}
