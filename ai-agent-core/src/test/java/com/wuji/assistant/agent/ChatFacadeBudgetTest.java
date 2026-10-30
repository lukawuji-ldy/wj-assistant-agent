package com.wuji.assistant.agent;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.state.ReplaceAllWith;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.agent.config.WujiAgentProperties;
import com.wuji.assistant.agent.config.WujiMemoryProperties;
import com.wuji.assistant.agent.config.WujiModelProperties;
import com.wuji.assistant.agent.config.WujiRagProperties;
import com.wuji.assistant.agent.dto.ChatStreamRequest;
import com.wuji.assistant.agent.memory.LlmMemoryExtractOrchestrator;
import com.wuji.assistant.agent.memory.LlmSummaryOrchestrator;
import com.wuji.assistant.agent.model.LlmCallAuditor;
import com.wuji.assistant.agent.model.LlmConfigRecord;
import com.wuji.assistant.agent.model.ModelRouter;
import com.wuji.assistant.agent.observability.AgentTelemetry;
import com.wuji.assistant.agent.prompt.PromptTemplateService;
import com.wuji.assistant.agent.stream.StreamSessionRegistry;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.token.TokenEstimator;
import com.wuji.assistant.memory.ShortTermContext;
import com.wuji.assistant.memory.ShortTermMemoryService;
import com.wuji.assistant.memory.extract.MemoryExtractService;
import com.wuji.assistant.memory.model.ChatMessage;
import com.wuji.assistant.memory.model.Conversation;
import com.wuji.assistant.memory.repo.ChatMessageRepository;
import com.wuji.assistant.memory.repo.ConversationRepository;
import com.wuji.assistant.memory.retrieve.LongTermMemoryRetriever;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.Message;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * prepare 只把稳定前缀整段送进图。压缩和丢历史发生在入模门，不在这里提前做。
 *
 * @author liudy
 */
@ExtendWith(MockitoExtension.class)
class ChatFacadeBudgetTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ShortTermMemoryService shortTermMemoryService;
    @Mock
    private PromptTemplateService promptTemplateService;
    @Mock
    private ModelRouter modelRouter;
    @Mock
    private AgentFactory agentFactory;
    @Mock
    private LlmCallAuditor llmCallAuditor;
    @Mock
    private MemoryExtractService memoryExtractService;
    @Mock
    private LlmMemoryExtractOrchestrator memoryExtractOrchestrator;
    @Mock
    private LlmSummaryOrchestrator summaryOrchestrator;
    @Mock
    private LongTermMemoryRetriever longTermMemoryRetriever;
    @Mock
    private AgentTelemetry agentTelemetry;

    @BeforeEach
    void useCharTokens() {
        TokenEstimator.setProvider("char-count", text -> text == null || text.isEmpty() ? 0 : text.length());
    }

    @AfterEach
    void clearProvider() {
        TokenEstimator.setProvider("char-count", null);
    }

    @Test
    void prepare_sendsFullPrefixAndDoesNotCompress() throws Exception {
        ChatFacade facade = facade(new WujiMemoryProperties(), graph());
        ServerSentEvent<String> meta = facade.stream("u1", new ChatStreamRequest(
                        "c1", "hi", null, null, null, null))
                .blockFirst(Duration.ofSeconds(5));

        assertNotNull(meta);
        assertEquals("meta", meta.event());
        assertNotNull(meta.data());
        assertFalse(meta.data().contains("truncation_info"));
        ArgumentCaptor<OverAllState> inputs = ArgumentCaptor.forClass(OverAllState.class);
        verify(graph).streamFromInitialNode(inputs.capture(), any());
        ReplaceAllWith<?> replaced = assertInstanceOf(ReplaceAllWith.class, inputs.getValue().data().get("messages"));
        assertEquals(9, replaced.newValues().size());
        Message current = (Message) replaced.newValues().get(replaced.newValues().size() - 1);
        assertEquals("hi", current.getText());
        verify(summaryOrchestrator, never()).submitUrgent(any(), anyString(), anyString());
        verify(summaryOrchestrator, never()).compressUrgent(anyString(), anyString(), any());
    }

    @Test
    void prepare_syncSummaryFlagDoesNotCompressBeforeTheGraph() throws Exception {
        WujiMemoryProperties memory = new WujiMemoryProperties();
        memory.getShort().setSummarySyncEnabled(true);
        when(shortTermMemoryService.loadContext(anyString(), anyString(), anyInt()))
                .thenReturn(new ShortTermContext(null, longHistory()));

        ChatFacade facade = facade(memory, graph(), false);
        ServerSentEvent<String> meta = facade.stream("u1", new ChatStreamRequest(
                        "c1", "hi", null, null, null, null))
                .blockFirst(Duration.ofSeconds(5));

        assertNotNull(meta);
        verify(shortTermMemoryService).loadContext(anyString(), anyString(), anyInt());
        verify(summaryOrchestrator, never()).compressUrgent(anyString(), anyString(), any());
        verify(summaryOrchestrator, never()).submitUrgent(any(), anyString(), anyString());
    }

    @Test
    void contextLengthExceeded_repacksOnlyOnce() {
        CompiledGraph compiled = graph();
        AtomicInteger calls = new AtomicInteger();
        when(compiled.streamFromInitialNode(any(), any())).thenAnswer(invocation -> {
            calls.incrementAndGet();
            return Flux.error(new IllegalStateException("context_length_exceeded"));
        });

        ChatFacade facade = facade(new WujiMemoryProperties(), compiled);
        List<ServerSentEvent<String>> events = facade.stream("u1", new ChatStreamRequest(
                        "c1", "hi", null, null, null, null))
                .collectList()
                .block(Duration.ofSeconds(5));

        assertEquals(2, calls.get());
        assertNotNull(events);
        assertTrue(events.stream().anyMatch(event -> "error".equals(event.event())));
    }

    @Test
    void contextLengthMessage_isRecognized() {
        assertTrue(ChatFacade.isContextLengthExceeded(
                new IllegalStateException("model error", new RuntimeException("context_length_exceeded"))));
    }

    private CompiledGraph graph;

    private CompiledGraph graph() {
        graph = mock(CompiledGraph.class);
        when(graph.getKeyStrategyMap()).thenReturn(Map.of());
        when(graph.streamFromInitialNode(any(), any())).thenReturn(Flux.empty());
        return graph;
    }

    private ChatFacade facade(WujiMemoryProperties memory, CompiledGraph compiledGraph) {
        return facade(memory, compiledGraph, true);
    }

    private ChatFacade facade(WujiMemoryProperties memory, CompiledGraph compiledGraph, boolean stubHistory) {
        WujiAgentProperties agentProperties = new WujiAgentProperties();
        agentProperties.getStream().setHeartbeatInterval(Duration.ZERO);
        memory.getExtract().setExplicitDetectEnabled(false);
        WujiRagProperties rag = new WujiRagProperties();

        LlmConfigRecord cfg = new LlmConfigRecord();
        cfg.setConfigId("llm_primary");
        cfg.setContextWindowSize(6000);
        cfg.setOutputMaxTokens(100);
        cfg.setModel("fixture");
        cfg.setProvider("openai_compatible");
        when(modelRouter.openFrom(0)).thenReturn(Optional.of(new ModelRouter.IndexedClient(
                0, new ModelRouter.RoutedClient("llm_primary", cfg, null, false))));

        Conversation conversation = new Conversation();
        conversation.setConversationId("c1");
        when(conversationRepository.requireOwned("u1", "c1")).thenReturn(conversation);
        when(chatMessageRepository.insert(anyString(), anyString(), anyString(), any(), anyString()))
                .thenAnswer(invocation -> {
                    ChatMessage row = new ChatMessage();
                    row.setMessageId("m-" + invocation.getArgument(2));
                    row.setRole(invocation.getArgument(2));
                    row.setContent(invocation.getArgument(3));
                    return row;
                });
        if (stubHistory) {
            when(shortTermMemoryService.loadContext(anyString(), anyString(), anyInt()))
                    .thenReturn(new ShortTermContext(null, longHistory()));
        }
        when(promptTemplateService.loadAndRender(anyString(), any(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        when(longTermMemoryRetriever.retrieveItems(anyString(), anyString(), any())).thenReturn(List.of());
        ReactAgent reactAgent = mock(ReactAgent.class);
        when(reactAgent.getAndCompileGraph()).thenReturn(compiledGraph);
        when(agentFactory.getOrCreate(anyString())).thenReturn(reactAgent);
        lenient().when(modelRouter.mapErrorCode(any())).thenReturn(ErrorCode.INTERNAL_ERROR);
        lenient().when(modelRouter.isRetryable(any())).thenReturn(false);

        return new ChatFacade(
                conversationRepository,
                chatMessageRepository,
                shortTermMemoryService,
                promptTemplateService,
                modelRouter,
                agentFactory,
                llmCallAuditor,
                new WujiModelProperties(),
                agentProperties,
                memory,
                rag,
                memoryExtractService,
                memoryExtractOrchestrator,
                summaryOrchestrator,
                longTermMemoryRetriever,
                new StreamSessionRegistry(agentProperties),
                new ObjectMapper(),
                agentTelemetry,
                Schedulers.immediate(),
                Schedulers.immediate(),
                Schedulers.immediate());
    }

    private static List<ChatMessage> longHistory() {
        List<ChatMessage> history = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            ChatMessage row = new ChatMessage();
            row.setRole("user");
            row.setContent("x".repeat(2000));
            history.add(row);
        }
        return history;
    }
}
