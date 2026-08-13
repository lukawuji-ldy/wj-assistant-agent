package com.wuji.assistant.vta.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.agent.model.ModelRouter;
import com.wuji.assistant.agent.prompt.PromptTemplateService;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.vta.VtaPromptCodes;
import com.wuji.assistant.vta.VtaStructuredOutputParser;

import java.time.Duration;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * 构建并编译 VTA 分析 StateGraph（无 JDBC Saver）。
 */
public class VtaAnalysisGraphFactory {

    private final PromptTemplateService promptTemplateService;
    private final ModelRouter modelRouter;
    private final ObjectMapper objectMapper;
    private final VtaStructuredOutputParser parser;
    private final int maxChars;
    private final Duration nodeTimeout;

    public VtaAnalysisGraphFactory(PromptTemplateService promptTemplateService,
                                   ModelRouter modelRouter,
                                   ObjectMapper objectMapper,
                                   int maxChars,
                                   Duration nodeTimeout) {
        this.promptTemplateService = promptTemplateService;
        this.modelRouter = modelRouter;
        this.objectMapper = objectMapper;
        this.parser = new VtaStructuredOutputParser(objectMapper);
        this.maxChars = maxChars;
        this.nodeTimeout = nodeTimeout == null ? Duration.ofSeconds(60) : nodeTimeout;
    }

    public CompiledGraph compile() {
        try {
            return build().compile();
        } catch (GraphStateException e) {
            throw new WujiException(ErrorCode.INTERNAL_ERROR, "VTA graph compile failed", e);
        }
    }

    StateGraph build() throws GraphStateException {
        KeyStrategyFactory keys = new KeyStrategyFactoryBuilder()
                .addPatternStrategy(VtaGraphKeys.JOB_ID, new ReplaceStrategy())
                .addPatternStrategy(VtaGraphKeys.USER_ID, new ReplaceStrategy())
                .addPatternStrategy(VtaGraphKeys.TRACE_ID, new ReplaceStrategy())
                .addPatternStrategy(VtaGraphKeys.TRANSCRIPT, new ReplaceStrategy())
                .addPatternStrategy(VtaGraphKeys.USER_PROMPT, new ReplaceStrategy())
                .addPatternStrategy(VtaGraphKeys.JOB_STATE, new ReplaceStrategy())
                .addPatternStrategy(VtaGraphKeys.CUSTOMER_TAG, new ReplaceStrategy())
                .addPatternStrategy(VtaGraphKeys.SALES_TAG, new ReplaceStrategy())
                .addPatternStrategy(VtaGraphKeys.CALL_SUMMARY, new ReplaceStrategy())
                .addPatternStrategy(VtaGraphKeys.INTENT_SCORE, new ReplaceStrategy())
                .addPatternStrategy(VtaGraphKeys.PARTIAL_RAW, new ReplaceStrategy())
                .addPatternStrategy(VtaGraphKeys.PARTIAL_FAILURE, new ReplaceStrategy())
                .addPatternStrategy(VtaGraphKeys.AGGREGATE, new ReplaceStrategy())
                .build();

        return new StateGraph("vta-analysis", keys)
                .addNode(VtaGraphKeys.NODE_VALIDATE, node_async(
                        new VtaValidateInputNode(promptTemplateService, maxChars)))
                .addNode(VtaGraphKeys.NODE_CUSTOMER, node_async(new VtaLlmNode(
                        VtaGraphKeys.NODE_CUSTOMER,
                        VtaPromptCodes.CUSTOMER_TAG_SYSTEM,
                        VtaGraphKeys.CUSTOMER_TAG,
                        promptTemplateService,
                        modelRouter,
                        objectMapper,
                        parser::parseCustomerTag,
                        nodeTimeout)))
                .addNode(VtaGraphKeys.NODE_SALES, node_async(new VtaLlmNode(
                        VtaGraphKeys.NODE_SALES,
                        VtaPromptCodes.SALES_TAG_SYSTEM,
                        VtaGraphKeys.SALES_TAG,
                        promptTemplateService,
                        modelRouter,
                        objectMapper,
                        parser::parseSalesTag,
                        nodeTimeout)))
                .addNode(VtaGraphKeys.NODE_SUMMARY, node_async(new VtaLlmNode(
                        VtaGraphKeys.NODE_SUMMARY,
                        VtaPromptCodes.CALL_SUMMARY_SYSTEM,
                        VtaGraphKeys.CALL_SUMMARY,
                        promptTemplateService,
                        modelRouter,
                        objectMapper,
                        parser::parseCallSummary,
                        nodeTimeout)))
                .addNode(VtaGraphKeys.NODE_INTENT, node_async(new VtaLlmNode(
                        VtaGraphKeys.NODE_INTENT,
                        VtaPromptCodes.INTENT_SCORE_SYSTEM,
                        VtaGraphKeys.INTENT_SCORE,
                        promptTemplateService,
                        modelRouter,
                        objectMapper,
                        parser::parseIntentScore,
                        nodeTimeout)))
                .addNode(VtaGraphKeys.NODE_MERGE, node_async(new VtaMergePartialNode(objectMapper)))
                .addNode(VtaGraphKeys.NODE_AGGREGATE, node_async(
                        new VtaAggregateNode(promptTemplateService, modelRouter, parser, objectMapper, nodeTimeout)))
                .addEdge(START, VtaGraphKeys.NODE_VALIDATE)
                .addEdge(VtaGraphKeys.NODE_VALIDATE, VtaGraphKeys.NODE_CUSTOMER)
                .addEdge(VtaGraphKeys.NODE_VALIDATE, VtaGraphKeys.NODE_SALES)
                .addEdge(VtaGraphKeys.NODE_VALIDATE, VtaGraphKeys.NODE_SUMMARY)
                .addEdge(VtaGraphKeys.NODE_VALIDATE, VtaGraphKeys.NODE_INTENT)
                .addEdge(VtaGraphKeys.NODE_CUSTOMER, VtaGraphKeys.NODE_MERGE)
                .addEdge(VtaGraphKeys.NODE_SALES, VtaGraphKeys.NODE_MERGE)
                .addEdge(VtaGraphKeys.NODE_SUMMARY, VtaGraphKeys.NODE_MERGE)
                .addEdge(VtaGraphKeys.NODE_INTENT, VtaGraphKeys.NODE_MERGE)
                .addEdge(VtaGraphKeys.NODE_MERGE, VtaGraphKeys.NODE_AGGREGATE)
                .addEdge(VtaGraphKeys.NODE_AGGREGATE, END);
    }
}
