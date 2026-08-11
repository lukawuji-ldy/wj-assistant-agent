package com.wuji.assistant.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.function.Function;

/**
 * KnowledgeRetrievalTool 工厂。
 *
 * @author liudy
 */
@Component
public class KnowledgeRetrievalToolFactory {

    /** 库表缺失时的工具说明兜底；线上以 {@code rag.knowledge_retrieval.system} 为准。 */
    public static final String DEFAULT_DESCRIPTION =
            "从已入库知识库检索片段。制度/FAQ/人物经历/产品说明等凡可能被文档回答的问题都必须调用；"
                    + "不要因为问题不像企业制度就跳过。rejected=true 时禁止编造事实。";

    private final KnowledgeRetrievalService retrievalService;
    private final ObjectMapper objectMapper;

    public KnowledgeRetrievalToolFactory(KnowledgeRetrievalService retrievalService, ObjectMapper objectMapper) {
        this.retrievalService = retrievalService;
        this.objectMapper = objectMapper;
    }

    /**
     * 构建检索 Tool（默认说明）。
     *
     * @param topK             topK
     * @param minReliableScore 最低分
     * @return ToolCallback
     */
    public ToolCallback create(int topK, double minReliableScore) {
        return create(topK, minReliableScore, DEFAULT_DESCRIPTION);
    }

    /**
     * 构建检索 Tool。
     *
     * @param topK             topK
     * @param minReliableScore 最低分
     * @param description      工具说明（来自 prompt_template；空则兜底）
     * @return ToolCallback
     */
    public ToolCallback create(int topK, double minReliableScore, String description) {
        String desc = StringUtils.hasText(description) ? description.trim() : DEFAULT_DESCRIPTION;
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
                .description(desc)
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
