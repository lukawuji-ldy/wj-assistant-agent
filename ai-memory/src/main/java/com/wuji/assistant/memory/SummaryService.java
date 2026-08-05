package com.wuji.assistant.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.common.util.PostgresText;
import com.wuji.assistant.memory.model.ChatMessage;
import com.wuji.assistant.memory.model.Conversation;
import com.wuji.assistant.memory.repo.ChatMessageRepository;
import com.wuji.assistant.memory.repo.ConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话摘要：条数/token 阈值触发滚动合并（MVP：规则结构化 JSON，不强制 LLM）。
 *
 * @author liudy
 */
@Service
public class SummaryService {

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ObjectMapper objectMapper;

    public SummaryService(ConversationRepository conversationRepository,
                          ChatMessageRepository chatMessageRepository,
                          ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 达到阈值则压缩；失败只打日志。
     *
     * @param userId                   用户
     * @param conversationId           会话
     * @param compressMessageThreshold 消息条数阈值
     * @param keepRecentMessages       压缩后仍保留在窗口外最近条数（不压入摘要的尾部）
     */
    public void compressIfNeeded(String userId, String conversationId,
                                 int compressMessageThreshold, int keepRecentMessages) {
        try {
            Conversation conversation = conversationRepository.requireOwned(userId, conversationId);
            if (conversation.getMessageCount() < Math.max(2, compressMessageThreshold)) {
                return;
            }
            int keep = Math.max(2, keepRecentMessages);
            // 取 watermark 后足够多的消息，压掉「除最近 keep 条」以外的段
            List<ChatMessage> after = chatMessageRepository.listAfterWatermarkAsc(
                    conversationId, userId, conversation.getSummaryUntilTime(),
                    compressMessageThreshold + keep);
            if (after.size() <= keep) {
                return;
            }
            List<ChatMessage> toCompress = after.subList(0, after.size() - keep);
            ChatMessage last = toCompress.get(toCompress.size() - 1);
            String summaryJson = buildRollingSummary(conversation.getSummary(), toCompress);
            conversationRepository.updateSummary(
                    conversationId, summaryJson, last.getCreateTime(), last.getMessageId());
            log.info("summary compressed conversationId={} untilMsg={} facts≈{}",
                    conversationId, last.getMessageId(), toCompress.size());
        } catch (Exception e) {
            log.warn("compressIfNeeded failed conversationId={}: {}", conversationId, e.toString());
        }
    }

    String buildRollingSummary(String oldSummaryJson, List<ChatMessage> messages) throws Exception {
        Map<String, Object> summary = new LinkedHashMap<>();
        List<String> facts = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        String goal = null;

        if (StringUtils.hasText(oldSummaryJson)) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> old = objectMapper.readValue(oldSummaryJson, Map.class);
                Object g = old.get("goal");
                if (g != null) {
                    goal = PostgresText.sanitize(String.valueOf(g));
                }
                Object f = old.get("facts");
                if (f instanceof List<?> list) {
                    for (Object o : list) {
                        if (o != null && facts.size() < 20) {
                            String fact = PostgresText.sanitize(String.valueOf(o));
                            if (StringUtils.hasText(fact)) {
                                facts.add(fact);
                            }
                        }
                    }
                }
                Object p = old.get("pending_tasks");
                if (p instanceof List<?> list) {
                    for (Object o : list) {
                        if (o != null && pending.size() < 10) {
                            String task = PostgresText.sanitize(String.valueOf(o));
                            if (StringUtils.hasText(task)) {
                                pending.add(task);
                            }
                        }
                    }
                }
            } catch (Exception ignore) {
                // 旧摘要损坏则重建
            }
        }

        for (ChatMessage m : messages) {
            if (m.getContent() == null || m.getContent().isBlank()) {
                continue;
            }
            // 历史脏数据或未清洗路径可能仍含 NUL；摘要落库 / 再入模审计均需可安全序列化
            String snippet = PostgresText.sanitize(m.getContent()).trim();
            if (snippet.isBlank()) {
                continue;
            }
            if (snippet.length() > 120) {
                snippet = snippet.substring(0, 120);
            }
            if ("user".equalsIgnoreCase(m.getRole()) && goal == null) {
                goal = snippet;
            }
            if (facts.size() < 20) {
                facts.add(m.getRole() + ": " + snippet);
            }
        }
        summary.put("goal", goal == null ? "" : goal);
        summary.put("facts", facts);
        summary.put("decisions", List.of());
        summary.put("pending_tasks", pending);
        return PostgresText.sanitizeJson(objectMapper.writeValueAsString(summary));
    }
}
