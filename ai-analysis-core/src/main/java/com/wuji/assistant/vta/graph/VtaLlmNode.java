package com.wuji.assistant.vta.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.agent.model.ModelRouter;
import com.wuji.assistant.agent.model.ModelRouter.AuditContext;
import com.wuji.assistant.agent.prompt.PromptTemplateService;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 四路分析 LLM 节点：异常一律写成 error JSON，禁止抛出以免中断并行兄弟节点。
 */
public class VtaLlmNode implements NodeAction {

    private final String nodeName;
    private final String promptCode;
    private final String outputKey;
    private final PromptTemplateService promptTemplateService;
    private final ModelRouter modelRouter;
    private final ObjectMapper objectMapper;
    private final Function<String, JsonNode> parse;
    private final Duration nodeTimeout;

    public VtaLlmNode(String nodeName,
                      String promptCode,
                      String outputKey,
                      PromptTemplateService promptTemplateService,
                      ModelRouter modelRouter,
                      ObjectMapper objectMapper,
                      Function<String, JsonNode> parse,
                      Duration nodeTimeout) {
        this.nodeName = nodeName;
        this.promptCode = promptCode;
        this.outputKey = outputKey;
        this.promptTemplateService = promptTemplateService;
        this.modelRouter = modelRouter;
        this.objectMapper = objectMapper;
        this.parse = parse;
        this.nodeTimeout = nodeTimeout;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        JsonNode result;
        try {
            String systemPrompt = promptTemplateService.loadActiveContent(promptCode);
            if (!StringUtils.hasText(systemPrompt)) {
                result = VtaJson.missingPrompt(objectMapper, promptCode);
            } else {
                String userPrompt = state.value(VtaGraphKeys.USER_PROMPT, "");
                String jobId = state.value(VtaGraphKeys.JOB_ID, "");
                String userId = state.value(VtaGraphKeys.USER_ID, "");
                String traceId = state.value(VtaGraphKeys.TRACE_ID, "");
                List<Message> messages = List.of(
                        new SystemMessage(systemPrompt),
                        new UserMessage(userPrompt == null ? "" : userPrompt)
                );
                AuditContext audit = new AuditContext(
                        traceId,
                        jobId,
                        nodeName,
                        userId,
                        "VTA",
                        jobId,
                        systemPrompt,
                        userPrompt
                );
                String raw = VtaTimedLlm.call(modelRouter, messages, audit, nodeTimeout);
                result = parse.apply(raw);
            }
        } catch (WujiException ex) {
            if (ex.getErrorCode() == ErrorCode.MODEL_TIMEOUT) {
                result = VtaJson.timeoutNode(objectMapper, nodeName);
            } else {
                result = VtaJson.errorNode(objectMapper, ex);
            }
        } catch (Exception ex) {
            result = VtaJson.errorNode(objectMapper, ex);
        }
        VtaObserverRegistry.notify(state.value(VtaGraphKeys.JOB_ID, ""), nodeName, result);
        return Map.of(outputKey, result);
    }
}
