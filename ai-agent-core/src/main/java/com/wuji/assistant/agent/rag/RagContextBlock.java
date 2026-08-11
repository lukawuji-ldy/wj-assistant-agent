package com.wuji.assistant.agent.rag;

import com.wuji.assistant.rag.RetrievalResult;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 将预检索结果格式化为注入 System 的文本块（不依赖模型是否调用 Tool）。
 *
 * @author liudy
 */
public final class RagContextBlock {

    static final String HITS_HEADING = "【知识库已召回片段】";
    static final String MISS_HEADING = "【知识库预检索】";
    private static final int MAX_CHARS_PER_HIT = 1500;

    private RagContextBlock() {
    }

    /**
     * @param result 检索结果，可为 null
     * @return 可拼入 System 的块；无内容时返回空串
     */
    public static String format(RetrievalResult result) {
        if (result == null) {
            return "";
        }
        if (result.rejected() || result.hits() == null || result.hits().isEmpty()) {
            return MISS_HEADING + "本轮未召回可靠片段。若用户问题可能被已入库文档回答，仍须调用 knowledge_retrieval；"
                    + "仅当工具也返回 rejected=true 时才能说不在知识库范围内。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(HITS_HEADING)
                .append("下列片段已从知识库召回。必须依据这些片段回答并标注来源（doc/章节）；")
                .append("只要片段能回答用户问题，禁止说「不在知识库范围内」。\n");
        int i = 1;
        for (RetrievalResult.Hit hit : result.hits()) {
            sb.append('\n').append(i++).append(". ");
            sb.append(sourceLine(hit)).append('\n');
            String body = hit.content() == null ? "" : hit.content().trim();
            if (body.length() > MAX_CHARS_PER_HIT) {
                body = body.substring(0, MAX_CHARS_PER_HIT) + "…";
            }
            sb.append(body).append('\n');
        }
        return sb.toString();
    }

    private static String sourceLine(RetrievalResult.Hit hit) {
        Map<String, Object> meta = hit.metadata() == null ? Map.of() : hit.metadata();
        String docId = stringVal(meta.get("doc_id"));
        String section = stringVal(meta.get("section"));
        StringBuilder line = new StringBuilder("来源");
        if (StringUtils.hasText(docId)) {
            line.append(" doc=").append(docId);
        }
        if (StringUtils.hasText(section)) {
            line.append(" 章节=").append(section);
        }
        line.append(" score=").append(String.format("%.2f", hit.score()));
        return line.toString();
    }

    private static String stringVal(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
