package com.wuji.assistant.agent.tool;

import com.wuji.assistant.agent.config.WujiAgentProperties;
import com.wuji.assistant.agent.config.WujiModelProperties;
import com.wuji.assistant.agent.config.WujiRagProperties;
import com.wuji.assistant.agent.model.LlmClientFactory;
import com.wuji.assistant.agent.prompt.PromptTemplateService;
import com.wuji.assistant.agent.rag.RagQueryRewriteOrchestrator;
import com.wuji.assistant.agent.token.InputBudgetCalculator;
import com.wuji.assistant.agent.token.TokenBudgetEnforcer;
import com.wuji.assistant.agent.token.TurnRetrievalCache;
import com.wuji.assistant.rag.KnowledgeRetrievalService;
import com.wuji.assistant.rag.RetrievalResult;
import com.wuji.assistant.agent.checkpoint.AgentCheckpointLink;
import com.wuji.assistant.agent.checkpoint.AgentCheckpointLinkRepository;
import com.wuji.assistant.agent.checkpoint.AgentCheckpointLinkWrite;
import com.wuji.assistant.agent.checkpoint.CheckpointSaverFactory;
import com.wuji.assistant.rag.router.KbDomain;
import com.wuji.assistant.rag.router.KbDomainRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.scheduler.Schedulers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 领域解析和本轮精确缓存。检索范围是整个领域。
 *
 * @author liudy
 */
class DomainKnowledgeToolTest {

    @Test
    void unknownDomain_isRejected() {
        DomainKnowledgeTool tool = tool(List.of());
        assertNull(tool.resolve("sales"));
    }

    @Test
    void exactCache_skipsSecondRetrieval() {
        KbDomainRepository repository = mock(KbDomainRepository.class);
        when(repository.listAll()).thenReturn(List.of(new KbDomain("sales", "销售", "llm_sales")));
        KnowledgeRetrievalService retrieval = mock(KnowledgeRetrievalService.class);
        when(retrieval.retrieve(eq("价格"), anyInt(), anyDouble(), eq("sales")))
                .thenReturn(new RetrievalResult(List.of(new RetrievalResult.Hit("1", "一百元", 0.9, null)), false, null));
        DomainKnowledgeTool tool = tool(repository, retrieval);
        DomainKnowledgeTool.DomainSpec spec = tool.resolve("sales");
        TurnRetrievalCache cache = new TurnRetrievalCache();
        String first = tool.searchDomain(spec, "价格", cache, "c1", "u1");
        String second = tool.searchDomain(spec, "价格", cache, "c1", "u1");
        assertTrue(first.contains("一百元"));
        assertEquals(first, second);
        verify(retrieval, times(1)).retrieve(eq("价格"), anyInt(), anyDouble(), eq("sales"));
    }

    @Test
    void relationalRewrite_prefixesRelationHint() {
        KbDomainRepository repository = mock(KbDomainRepository.class);
        when(repository.listAll()).thenReturn(List.of(new KbDomain("sales", "销售", "llm_sales")));
        KnowledgeRetrievalService retrieval = mock(KnowledgeRetrievalService.class);
        when(retrieval.retrieveMany(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList(),
                anyInt(), anyDouble(), anyInt(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new RetrievalResult(List.of(new RetrievalResult.Hit("1", "甲与乙", 0.9, null)), false, null));
        RagQueryRewriteOrchestrator rewrite = mock(RagQueryRewriteOrchestrator.class);
        when(rewrite.rewrite(eq("u1"), eq("c1"), eq("甲和乙的关系")))
                .thenReturn(new com.wuji.assistant.agent.rag.RagQueryRewrite(
                        List.of("甲和乙的关系", "甲 乙 对比"), true, true));
        when(rewrite.relationHint()).thenReturn("按实体、关系和依据回答");
        WujiRagProperties rag = new WujiRagProperties();
        rag.getQueryRewrite().setEnabled(true);
        DomainKnowledgeTool tool = tool(repository, retrieval, rewrite, rag);
        DomainKnowledgeTool.DomainSpec spec = tool.resolve("sales");
        String text = tool.searchDomain(spec, "甲和乙的关系", new TurnRetrievalCache(), "c1", "u1");
        assertTrue(text.startsWith("按实体、关系和依据回答"));
        assertTrue(text.contains("甲与乙"));
    }

    @Test
    void blankModel_isOmittedFromToolDescription() {
        DomainKnowledgeTool tool = tool(List.of(
                new KbDomain("orphan", "未分组", " "),
                new KbDomain("sales", "销售制度", "llm_sales")));
        String description = tool.callback("按领域检索").getToolDefinition().description();
        assertTrue(description.contains("sales"));
        assertTrue(description.contains("销售制度"));
        assertFalse(description.contains("orphan"));
    }

    @Test
    void mainRouteModel_isOmittedFromDomainCatalog() {
        KbDomain row = new KbDomain("sales", "销售", "llm_primary");
        WujiModelProperties primary = new WujiModelProperties();
        primary.setPrimaryConfigId("llm_primary");
        assertNull(tool(List.of(row), primary).resolve("sales"));

        WujiModelProperties fallback = new WujiModelProperties();
        fallback.setPrimaryConfigId("llm_other");
        fallback.setFallbackConfigIds(List.of("llm_primary"));
        assertNull(tool(List.of(row), fallback).resolve("sales"));
    }

    @Test
    void domainPrompt_isTheActiveTemplate() {
        PromptTemplateService prompts = mock(PromptTemplateService.class);
        when(prompts.loadActiveContent("rag.domain_agent.system")).thenReturn("只用证据。");
        KbDomainRepository repository = mock(KbDomainRepository.class);
        when(repository.listAll()).thenReturn(List.of(new KbDomain("sales", "销售", "llm_kb")));
        DomainKnowledgeTool tool = tool(repository, mock(KnowledgeRetrievalService.class), prompts, new WujiModelProperties());
        DomainKnowledgeTool.DomainSpec spec = tool.resolve("sales");
        assertEquals("只用证据。", tool.systemFor(spec));
    }

    @Test
    void subAgentTurn_isOnlyTheQuery() {
        List<Message> turn = DomainKnowledgeTool.subAgentMessages("价格");
        assertEquals(1, turn.size());
        assertInstanceOf(UserMessage.class, turn.get(0));
        assertEquals("价格", turn.get(0).getText());
    }

    @Test
    void searchDomain_writesRagLinkOnce() {
        KbDomainRepository repository = mock(KbDomainRepository.class);
        when(repository.listAll()).thenReturn(List.of(new KbDomain("sales", "销售", "llm_sales")));
        KnowledgeRetrievalService retrieval = mock(KnowledgeRetrievalService.class);
        when(retrieval.retrieve(eq("价格"), anyInt(), anyDouble(), eq("sales")))
                .thenReturn(new RetrievalResult(List.of(new RetrievalResult.Hit("1", "一百元", 0.9, null)), false, null));
        AgentCheckpointLinkRepository links = mock(AgentCheckpointLinkRepository.class);
        DomainKnowledgeTool tool = tool(repository, retrieval, mock(RagQueryRewriteOrchestrator.class),
                new WujiRagProperties(), mock(PromptTemplateService.class), new WujiModelProperties(), links);
        DomainKnowledgeTool.DomainSpec spec = tool.resolve("sales");
        AgentCheckpointLink link = new AgentCheckpointLink("u1:c1", "call-1", AgentCheckpointLink.newChildThreadName());
        TurnRetrievalCache cache = new TurnRetrievalCache();
        tool.searchDomain(spec, "价格", cache, "c1", "u1", link);
        tool.searchDomain(spec, "价格", cache, "c1", "u1", link);
        ArgumentCaptor<AgentCheckpointLinkWrite> captor = ArgumentCaptor.forClass(AgentCheckpointLinkWrite.class);
        verify(links, times(1)).insert(captor.capture());
        assertEquals(AgentCheckpointLink.KIND_RAG, captor.getValue().kind());
        assertEquals("u1:c1", captor.getValue().parentThreadName());
        assertEquals("call-1", captor.getValue().parentToolCallId());
        assertEquals(link.childThreadName(), captor.getValue().childThreadName());
        assertEquals(1, captor.getValue().hitCount());
        assertTrue(captor.getValue().childThreadName().startsWith("kb:"));
    }

    private static DomainKnowledgeTool tool(List<KbDomain> rows) {
        KbDomainRepository repository = mock(KbDomainRepository.class);
        when(repository.listAll()).thenReturn(rows);
        return tool(repository, mock(KnowledgeRetrievalService.class));
    }

    private static DomainKnowledgeTool tool(KbDomainRepository repository, KnowledgeRetrievalService retrieval) {
        return tool(repository, retrieval, mock(RagQueryRewriteOrchestrator.class), new WujiRagProperties());
    }

    private static DomainKnowledgeTool tool(List<KbDomain> rows, WujiModelProperties model) {
        KbDomainRepository repository = mock(KbDomainRepository.class);
        when(repository.listAll()).thenReturn(rows);
        return tool(repository, mock(KnowledgeRetrievalService.class), mock(PromptTemplateService.class), model);
    }

    private static DomainKnowledgeTool tool(KbDomainRepository repository, KnowledgeRetrievalService retrieval,
                                            RagQueryRewriteOrchestrator rewrite, WujiRagProperties rag) {
        return tool(repository, retrieval, rewrite, rag, mock(PromptTemplateService.class), new WujiModelProperties());
    }

    private static DomainKnowledgeTool tool(KbDomainRepository repository, KnowledgeRetrievalService retrieval,
                                            PromptTemplateService prompts, WujiModelProperties model) {
        return tool(repository, retrieval, mock(RagQueryRewriteOrchestrator.class), new WujiRagProperties(), prompts, model);
    }

    private static DomainKnowledgeTool tool(KbDomainRepository repository, KnowledgeRetrievalService retrieval,
                                            RagQueryRewriteOrchestrator rewrite, WujiRagProperties rag,
                                            PromptTemplateService prompts, WujiModelProperties model) {
        return tool(repository, retrieval, rewrite, rag, prompts, model,
                mock(AgentCheckpointLinkRepository.class));
    }

    private static DomainKnowledgeTool tool(KbDomainRepository repository, KnowledgeRetrievalService retrieval,
                                            RagQueryRewriteOrchestrator rewrite, WujiRagProperties rag,
                                            PromptTemplateService prompts, WujiModelProperties model,
                                            AgentCheckpointLinkRepository links) {
        return new DomainKnowledgeTool(
                repository,
                retrieval,
                mock(LlmClientFactory.class),
                new InputBudgetCalculator(),
                mock(TokenBudgetEnforcer.class),
                rewrite,
                new WujiAgentProperties(),
                rag,
                model,
                prompts,
                Schedulers.immediate(),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(CheckpointSaverFactory.class),
                links);
    }
}
