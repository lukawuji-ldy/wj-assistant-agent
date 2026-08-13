package com.wuji.assistant.vta.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wuji.assistant.agent.model.ModelRouter;
import com.wuji.assistant.agent.model.ModelRouter.AuditContext;
import com.wuji.assistant.agent.prompt.PromptTemplateService;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.vta.VtaPromptCodes;
import com.wuji.assistant.vta.VtaStructuredOutputParser;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 汇总节点：一次 LLM；失败则 raw 透传四路结果。
 */
public class VtaAggregateNode implements NodeAction {

    private final PromptTemplateService promptTemplateService;
    private final ModelRouter modelRouter;
    private final VtaStructuredOutputParser parser;
    private final ObjectMapper objectMapper;
    private final Duration nodeTimeout;

    public VtaAggregateNode(PromptTemplateService promptTemplateService,
                            ModelRouter modelRouter,
                            VtaStructuredOutputParser parser,
                            ObjectMapper objectMapper,
                            Duration nodeTimeout) {
        this.promptTemplateService = promptTemplateService;
        this.modelRouter = modelRouter;
        this.parser = parser;
        this.objectMapper = objectMapper;
        this.nodeTimeout = nodeTimeout;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        JsonNode partialRaw = VtaJson.asJson(objectMapper, state.value(VtaGraphKeys.PARTIAL_RAW).orElse(null));
        JsonNode result;
        try {
            String systemPrompt = promptTemplateService.loadActiveContent(VtaPromptCodes.AGGREGATE_SYSTEM);
            if (!StringUtils.hasText(systemPrompt)) {
                result = fallback(partialRaw, VtaJson.missingPrompt(objectMapper, VtaPromptCodes.AGGREGATE_SYSTEM));
            } else {
                String jobId = state.value(VtaGraphKeys.JOB_ID, "");
                String userId = state.value(VtaGraphKeys.USER_ID, "");
                String traceId = state.value(VtaGraphKeys.TRACE_ID, "");
                String userPrompt = partialRaw.toString();
                List<Message> messages = List.of(
                        new SystemMessage(systemPrompt),
                        new UserMessage(userPrompt)
                );
                AuditContext audit = new AuditContext(
                        traceId,
                        jobId,
                        VtaGraphKeys.NODE_AGGREGATE,
                        userId,
                        "VTA",
                        jobId,
                        systemPrompt,
                        userPrompt
                );
                String raw = VtaTimedLlm.call(modelRouter, messages, audit, nodeTimeout);
                JsonNode parsed = parser.parseAggregate(raw);
                if (VtaJson.hasError(parsed) || !parsed.hasNonNull("aggregateText") || !parsed.hasNonNull("raw")) {
                    result = fallback(partialRaw, parsed);
                } else {
                    result = parsed;
                }
            }
        } catch (WujiException ex) {
            JsonNode err = ex.getErrorCode() == ErrorCode.MODEL_TIMEOUT
                    ? VtaJson.timeoutNode(objectMapper, VtaGraphKeys.NODE_AGGREGATE)
                    : VtaJson.errorNode(objectMapper, ex);
            result = fallback(partialRaw, err);
        } catch (Exception ex) {
            result = fallback(partialRaw, VtaJson.errorNode(objectMapper, ex));
        }
        VtaObserverRegistry.notify(state.value(VtaGraphKeys.JOB_ID, ""), VtaGraphKeys.NODE_AGGREGATE, result);
        return Map.of(VtaGraphKeys.AGGREGATE, result);
    }

    private ObjectNode fallback(JsonNode partialRaw, JsonNode errorOrParsed) {
        ObjectNode out = objectMapper.createObjectNode();
        if (errorOrParsed != null && errorOrParsed.isObject()) {
            if (errorOrParsed.hasNonNull("error")) {
                out.set("error", errorOrParsed.get("error"));
                if (errorOrParsed.has("detail")) {
                    out.set("detail", errorOrParsed.get("detail"));
                }
            }
        }
        out.put("aggregateText", "");
        out.set("raw", partialRaw == null ? objectMapper.createObjectNode() : partialRaw);
        return out;
    }
}
