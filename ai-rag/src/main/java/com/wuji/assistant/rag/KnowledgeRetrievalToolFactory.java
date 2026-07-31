package com.wuji.assistant.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;

/**
 * KnowledgeRetrievalTool 工厂。
 *
 * @author liudy
 */
@Component
public class KnowledgeRetrievalToolFactory {

    private final KnowledgeRetrievalService retrievalService;
    private final ObjectMapper objectMapper;

    public KnowledgeRetrievalToolFactory(KnowledgeRetrievalService retrievalService, ObjectMapper objectMapper) {
        this.retrievalService = retrievalService;
        this.objectMapper = objectMapper;
    }

    /**
     * 构建检索 Tool。
     *
     * @param topK             topK
     * @param minReliableScore 最低分
     * @return ToolCallback
     */
    public ToolCallback create(int topK, double minReliableScore) {
        Function<Request, String> fn = req -> {
            String q = req == null ? "" : req.query();
            RetrievalResult result = retrievalService.retrieve(q, topK, minReliableScore);
            try {
                if (result.rejected()) {
                    return objectMapper.writeValueAsString(Map.of(
                            "rejected", true,
                            "rejectReason", result.rejectReason() == null ? "无可靠知识命中" : result.rejectReason(),
                            "hits", result.hits()
                    ));
                }
                return objectMapper.writeValueAsString(Map.of(
                        "rejected", false,
                        "hits", result.hits()
                ));
            } catch (Exception e) {
                return "{\"rejected\":true,\"rejectReason\":\"serialize_failed\"}";
            }
        };
        return FunctionToolCallback.builder("knowledge_retrieval", fn)
                .description("Retrieve enterprise knowledge snippets. Use when answering policy/process questions. If rejected=true, do not invent facts.")
                .inputType(Request.class)
                .build();
    }

    /**
     * Tool 入参。
     *
     * @param query 查询文本
     * @author liudy
     */
    public record Request(String query) {
    }
}
