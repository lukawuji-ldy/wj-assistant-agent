package com.wuji.assistant.vta.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.wuji.assistant.agent.prompt.PromptTemplateService;
import com.wuji.assistant.vta.VtaPromptCodes;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 校验并截断 transcript，渲染用户侧模板。
 */
public class VtaValidateInputNode implements NodeAction {

    private final PromptTemplateService promptTemplateService;
    private final int maxChars;

    public VtaValidateInputNode(PromptTemplateService promptTemplateService, int maxChars) {
        this.promptTemplateService = promptTemplateService;
        this.maxChars = Math.max(1, maxChars);
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String raw = state.value(VtaGraphKeys.TRANSCRIPT, "");
        String transcript = raw == null ? "" : raw.trim();
        boolean truncated = transcript.length() > maxChars;
        if (truncated) {
            transcript = transcript.substring(0, maxChars);
        }

        String userTemplate = promptTemplateService.loadActiveContent(VtaPromptCodes.TRANSCRIPT_USER);
        String userPrompt;
        if (StringUtils.hasText(userTemplate)) {
            userPrompt = promptTemplateService.loadAndRender(
                    VtaPromptCodes.TRANSCRIPT_USER,
                    Map.of("transcript", transcript),
                    transcript);
        } else {
            userPrompt = transcript;
        }

        Map<String, Object> jobState = new HashMap<>();
        jobState.put("truncated", truncated);
        jobState.put("maxChars", maxChars);
        jobState.put("length", transcript.length());

        Map<String, Object> out = new HashMap<>();
        out.put(VtaGraphKeys.TRANSCRIPT, transcript);
        out.put(VtaGraphKeys.USER_PROMPT, userPrompt);
        out.put(VtaGraphKeys.JOB_STATE, jobState);
        return out;
    }
}
