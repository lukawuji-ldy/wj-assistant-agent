package com.wuji.assistant.agent.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.agent.config.WujiRagProperties;
import com.wuji.assistant.rag.KnowledgeRetrievalService;
import com.wuji.assistant.rag.RetrievalResult;
import com.wuji.assistant.rag.config.RagVectorProperties;
import com.wuji.assistant.rag.ingest.EmbeddingClient;
import com.wuji.assistant.rag.router.KbCollection;
import com.wuji.assistant.rag.router.KbCollectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 路由开启时只检索超过相似度阈值的知识库；关闭时走原检索。
 *
 * @author liudy
 */
@ExtendWith(MockitoExtension.class)
class RagRetrievalCoordinatorTest {

    @Mock
    private KnowledgeRetrievalService retrievalService;
    @Mock
    private KbCollectionRepository collections;
    @Mock
    private EmbeddingClient embeddingClient;
    @Mock
    private RagQueryRewriteOrchestrator queryRewriteOrchestrator;

    private WujiRagProperties ragProperties;
    private RagRetrievalCoordinator coordinator;

    @BeforeEach
    void setUp() {
        ragProperties = new WujiRagProperties();
        ObjectProvider<EmbeddingClient> embeddingClients = org.mockito.Mockito.mock(ObjectProvider.class);
        when(embeddingClients.getIfAvailable()).thenReturn(embeddingClient);
        coordinator = new RagRetrievalCoordinator(
                retrievalService,
                collections,
                embeddingClients,
                queryRewriteOrchestrator,
                ragProperties,
                new RagVectorProperties(),
                Schedulers.immediate(),
                new ObjectMapper());
    }

    @Test
    void routerOffUsesSingleRetrieve() {
        when(queryRewriteOrchestrator.rewrite(any(), any(), eq("请假"))).thenReturn(RagQueryRewrite.original("请假"));
        when(retrievalService.retrieve("请假", 5, 0.5))
                .thenReturn(new RetrievalResult(List.of(), true, "无可靠知识命中"));

        coordinator.retrieve("u1", "c1", "请假", 5, 0.5, true);

        verify(retrievalService).retrieve("请假", 5, 0.5);
        verify(retrievalService, never()).retrieveMany(anyList(), anyList(), anyInt(), anyDouble(), anyInt(), any());
    }

    @Test
    void routerPicksSimilarCollections() {
        ragProperties.getRouter().setEnabled(true);
        ragProperties.getRouter().setTopN(2);
        ragProperties.getRouter().setMinScore(0.6);
        when(queryRewriteOrchestrator.rewrite(isNull(), isNull(), eq("年假"))).thenReturn(RagQueryRewrite.original("年假"));
        when(embeddingClient.available()).thenReturn(true);
        when(embeddingClient.embed("年假")).thenReturn(new float[]{1f, 0f});
        when(embeddingClient.embed("人事假期制度")).thenReturn(new float[]{1f, 0f});
        when(embeddingClient.embed("财务报销")).thenReturn(new float[]{0f, 1f});
        when(collections.listAll()).thenReturn(List.of(
                new KbCollection("hr", "人事假期制度"),
                new KbCollection("fin", "财务报销")));
        when(retrievalService.retrieveMany(eq(List.of("年假")), eq(List.of("hr")), eq(5), eq(0.5), eq(60), any()))
                .thenReturn(new RetrievalResult(List.of(
                        new RetrievalResult.Hit("c1", "年假 5 天", 0.9, Map.of())
                ), false, null));

        RagRetrievalCoordinator.Outcome outcome = coordinator.retrieve(null, null, "年假", 5, 0.5, true);

        assertEquals(1, outcome.result().hits().size());
        verify(retrievalService, never()).retrieve(any(), anyInt(), anyDouble());
    }
}
