package com.wuji.assistant.vta;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wuji.assistant.agent.model.ModelRouter;
import com.wuji.assistant.agent.prompt.PromptTemplateService;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.vta.graph.VtaAnalysisGraphFactory;
import com.wuji.assistant.vta.graph.VtaGraphKeys;
import com.wuji.assistant.vta.graph.VtaJson;
import com.wuji.assistant.vta.graph.VtaObserverRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * VTA 分析编排：StateGraph（四路并行 → mergePartial → aggregate）。
 */
@Service
public class VtaAnalysisExecutor {

    private final ObjectMapper objectMapper;
    private final CompiledGraph compiled;
    private final Duration jobTimeout;

    public VtaAnalysisExecutor(PromptTemplateService promptTemplateService,
                               ModelRouter modelRouter,
                               ObjectMapper objectMapper) {
        this(promptTemplateService, modelRouter, objectMapper, 20_000,
                Duration.ofSeconds(150), Duration.ofSeconds(60));
    }

    @Autowired
    public VtaAnalysisExecutor(PromptTemplateService promptTemplateService,
                               ModelRouter modelRouter,
                               ObjectMapper objectMapper,
                               @Value("${wuji.vta.input.max-chars:20000}") int maxChars,
                               @Value("${wuji.vta.job-timeout:150s}") Duration jobTimeout,
                               @Value("${wuji.vta.node-timeout:60s}") Duration nodeTimeout) {
        this.objectMapper = objectMapper;
        this.jobTimeout = jobTimeout == null ? Duration.ofSeconds(150) : jobTimeout;
        Duration boundedNode = nodeTimeout == null ? Duration.ofSeconds(60) : nodeTimeout;
        this.compiled = new VtaAnalysisGraphFactory(
                promptTemplateService, modelRouter, objectMapper, maxChars, boundedNode).compile();
    }

    public VtaAnalysisResult execute(String jobId,
                                     String userId,
                                     String transcript,
                                     String traceId,
                                     VtaAnalysisObserver observer) {
        try {
            return runGraph(jobId, userId, transcript, traceId, observer);
        } catch (WujiException e) {
            throw e;
        } catch (Exception e) {
            throw new WujiException(ErrorCode.INTERNAL_ERROR, "VTA graph failed", e);
        }
    }

    private VtaAnalysisResult runGraph(String jobId,
                                       String userId,
                                       String transcript,
                                       String traceId,
                                       VtaAnalysisObserver observer) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(VtaGraphKeys.JOB_ID, jobId);
        inputs.put(VtaGraphKeys.USER_ID, userId);
        inputs.put(VtaGraphKeys.TRACE_ID, traceId);
        inputs.put(VtaGraphKeys.TRANSCRIPT, transcript == null ? "" : transcript);

        RunnableConfig config = RunnableConfig.builder()
                .threadId("vta:" + userId + ":" + jobId)
                .build();
        Optional<OverAllState> end;
        VtaObserverRegistry.register(jobId, observer);
        try {
            OverAllState state = compiled.stream(inputs, config)
                    .last()
                    .map(NodeOutput::state)
                    .block(jobTimeout);
            end = Optional.ofNullable(state);
        } catch (IllegalStateException e) {
            throw new WujiException(ErrorCode.MODEL_TIMEOUT, "VTA job timed out", e);
        } catch (WujiException e) {
            throw e;
        } catch (Exception e) {
            throw new WujiException(ErrorCode.INTERNAL_ERROR, "VTA graph invoke failed", e);
        } finally {
            VtaObserverRegistry.clear(jobId);
        }
        OverAllState state = end.orElseThrow(
                () -> new WujiException(ErrorCode.INTERNAL_ERROR, "VTA graph returned empty state"));

        JsonNode customer = VtaJson.asJson(objectMapper, state.value(VtaGraphKeys.CUSTOMER_TAG).orElse(null));
        JsonNode sales = VtaJson.asJson(objectMapper, state.value(VtaGraphKeys.SALES_TAG).orElse(null));
        JsonNode summary = VtaJson.asJson(objectMapper, state.value(VtaGraphKeys.CALL_SUMMARY).orElse(null));
        JsonNode intent = VtaJson.asJson(objectMapper, state.value(VtaGraphKeys.INTENT_SCORE).orElse(null));
        JsonNode aggregateParsed = VtaJson.asJson(objectMapper, state.value(VtaGraphKeys.AGGREGATE).orElse(null));
        JsonNode partialRaw = VtaJson.asJson(objectMapper, state.value(VtaGraphKeys.PARTIAL_RAW).orElse(null));

        boolean hasAnyError = VtaJson.hasError(customer) || VtaJson.hasError(sales)
                || VtaJson.hasError(summary) || VtaJson.hasError(intent);

        String aggregateText = "";
        JsonNode aggregateRaw;
        if (aggregateParsed != null && aggregateParsed.hasNonNull("aggregateText")
                && aggregateParsed.hasNonNull("raw") && !VtaJson.hasError(aggregateParsed)) {
            aggregateText = aggregateParsed.path("aggregateText").asText("");
            aggregateRaw = aggregateParsed.path("raw");
        } else if (aggregateParsed != null && aggregateParsed.hasNonNull("raw")) {
            aggregateText = aggregateParsed.path("aggregateText").asText("");
            aggregateRaw = aggregateParsed.path("raw");
        } else if (partialRaw != null && partialRaw.isObject() && !partialRaw.isEmpty()) {
            aggregateRaw = partialRaw;
        } else {
            ObjectNode fallbackRaw = objectMapper.createObjectNode();
            fallbackRaw.set(VtaGraphKeys.CUSTOMER_TAG, customer);
            fallbackRaw.set(VtaGraphKeys.SALES_TAG, sales);
            fallbackRaw.set(VtaGraphKeys.CALL_SUMMARY, summary);
            fallbackRaw.set(VtaGraphKeys.INTENT_SCORE, intent);
            aggregateRaw = fallbackRaw;
        }

        ObjectNode rawNodeOutputs = objectMapper.createObjectNode();
        rawNodeOutputs.set(VtaGraphKeys.CUSTOMER_TAG, customer);
        rawNodeOutputs.set(VtaGraphKeys.SALES_TAG, sales);
        rawNodeOutputs.set(VtaGraphKeys.CALL_SUMMARY, summary);
        rawNodeOutputs.set(VtaGraphKeys.INTENT_SCORE, intent);
        rawNodeOutputs.set(VtaGraphKeys.PARTIAL_FAILURE,
                VtaJson.asJson(objectMapper, state.value(VtaGraphKeys.PARTIAL_FAILURE).orElse(null)));
        rawNodeOutputs.set(VtaGraphKeys.AGGREGATE, aggregateParsed);

        VtaAnalysisStatus status;
        boolean aggregateOk = aggregateParsed != null && aggregateParsed.hasNonNull("raw")
                && !VtaJson.hasError(aggregateParsed);
        if (!hasAnyError && aggregateOk) {
            status = VtaAnalysisStatus.SUCCEEDED;
        } else if (hasAnyError || (aggregateParsed != null && VtaJson.hasError(aggregateParsed))) {
            status = VtaAnalysisStatus.PARTIAL;
        } else {
            status = VtaAnalysisStatus.FAILED;
        }

        return new VtaAnalysisResult(
                jobId,
                status,
                aggregateText,
                customer,
                sales,
                summary,
                intent,
                aggregateRaw,
                rawNodeOutputs
        );
    }
}
