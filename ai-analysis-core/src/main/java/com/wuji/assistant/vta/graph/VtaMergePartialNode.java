package com.wuji.assistant.vta.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * 合并四路结构化结果，标记失败节点。
 */
public class VtaMergePartialNode implements NodeAction {

    private final ObjectMapper objectMapper;

    public VtaMergePartialNode(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        JsonNode customer = VtaJson.asJson(objectMapper, state.value(VtaGraphKeys.CUSTOMER_TAG).orElse(null));
        JsonNode sales = VtaJson.asJson(objectMapper, state.value(VtaGraphKeys.SALES_TAG).orElse(null));
        JsonNode summary = VtaJson.asJson(objectMapper, state.value(VtaGraphKeys.CALL_SUMMARY).orElse(null));
        JsonNode intent = VtaJson.asJson(objectMapper, state.value(VtaGraphKeys.INTENT_SCORE).orElse(null));

        ObjectNode partialRaw = objectMapper.createObjectNode();
        partialRaw.set(VtaGraphKeys.CUSTOMER_TAG, customer);
        partialRaw.set(VtaGraphKeys.SALES_TAG, sales);
        partialRaw.set(VtaGraphKeys.CALL_SUMMARY, summary);
        partialRaw.set(VtaGraphKeys.INTENT_SCORE, intent);

        ArrayNode failures = objectMapper.createArrayNode();
        if (VtaJson.hasError(customer)) {
            failures.add(VtaGraphKeys.CUSTOMER_TAG);
        }
        if (VtaJson.hasError(sales)) {
            failures.add(VtaGraphKeys.SALES_TAG);
        }
        if (VtaJson.hasError(summary)) {
            failures.add(VtaGraphKeys.CALL_SUMMARY);
        }
        if (VtaJson.hasError(intent)) {
            failures.add(VtaGraphKeys.INTENT_SCORE);
        }

        ObjectNode partialFailure = objectMapper.createObjectNode();
        partialFailure.set("nodes", failures);
        partialFailure.put("any", failures.size() > 0);

        return Map.of(
                VtaGraphKeys.PARTIAL_RAW, partialRaw,
                VtaGraphKeys.PARTIAL_FAILURE, partialFailure
        );
    }
}
