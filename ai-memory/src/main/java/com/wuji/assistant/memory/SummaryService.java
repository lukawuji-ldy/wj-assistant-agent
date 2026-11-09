package com.wuji.assistant.memory;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 会话摘要：条数或 watermark 后 token 阈值触发，压缩紧挨旧 watermark 的最旧连续段。
 * 规则 JSON 由本类生成；LLM 合并在 {@code ai-agent-core} 的编排器中，失败时回落到本类。
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
     * 待压缩的一段：旧摘要 + 紧挨 watermark 的最旧消息（不含保留尾部）。
     *
     * @param conversationId 会话
     * @param oldSummaryJson 当前 summary，可空
     * @param toCompress     本次压入摘要的消息（时间正序）
     * @param lastCovered    本段最后一条，watermark 落到它
     */
    public record CompressionBatch(
            String conversationId,
            String oldSummaryJson,
            List<ChatMessage> toCompress,
            ChatMessage lastCovered
    ) {
    }

    /**
     * 达到条数或 token 阈值则用规则压缩；失败只打日志。
     *
     * @param userId                   用户
     * @param conversationId           会话
     * @param compressMessageThreshold watermark 之后未摘要条数阈值
     * @param keepRecentMessages       不压入摘要的尾部条数
     * @param compressTokenThreshold   watermark 之后 token 合计阈值；0 表示不按 token 触发
     */
    public void compressIfNeeded(String userId, String conversationId,
                                 int compressMessageThreshold, int keepRecentMessages,
                                 int compressTokenThreshold) {
        try {
            Optional<CompressionBatch> batch = prepare(
                    userId, conversationId, compressMessageThreshold, keepRecentMessages, compressTokenThreshold);
            if (batch.isEmpty()) {
                return;
            }
            CompressionBatch ready = batch.get();
            String summaryJson = buildRollingSummary(ready.oldSummaryJson(), ready.toCompress());
            commitSummary(ready, summaryJson);
            log.info("summary compressed conversationId={} untilMsg={} messages={} {}",
                    conversationId, ready.lastCovered().getMessageId(), ready.toCompress().size(),
                    countsLabel(summaryJson, objectMapper));
        } catch (Exception e) {
            log.warn("compressIfNeeded failed conversationId={}: {}", conversationId, e.toString());
        }
    }

    /**
     * 选出本次应压缩的最旧连续段。未达阈值或剩余条数不超过保留尾部时为空。
     *
     * @param userId                   用户
     * @param conversationId           会话
     * @param compressMessageThreshold 条数阈值（watermark 之后未摘要条数）
     * @param keepRecentMessages       保留尾部
     * @param compressTokenThreshold   token 阈值
     * @return 待压缩批次
     */
    public Optional<CompressionBatch> prepare(String userId, String conversationId,
                                              int compressMessageThreshold, int keepRecentMessages,
                                              int compressTokenThreshold) {
        Conversation conversation = conversationRepository.requireOwned(userId, conversationId);
        int keep = Math.max(2, keepRecentMessages);
        int messageThreshold = Math.max(2, compressMessageThreshold);
        String untilMessageId = conversation.getSummaryUntilMessageId();
        long outstanding = chatMessageRepository.countAfterWatermark(
                conversationId, userId, conversation.getSummaryUntilTime(), untilMessageId);
        boolean byCount = outstanding >= messageThreshold;
        long tokens = chatMessageRepository.sumTokenCountAfterWatermark(
                conversationId, userId, conversation.getSummaryUntilTime(), untilMessageId);
        boolean byTokens = compressTokenThreshold > 0 && tokens >= compressTokenThreshold;
        if (!byCount && !byTokens) {
            return Optional.empty();
        }
        List<ChatMessage> oldest = chatMessageRepository.listOldestAfterWatermarkAsc(
                conversationId, userId, conversation.getSummaryUntilTime(), untilMessageId,
                messageThreshold + keep);
        if (oldest.size() <= keep) {
            return Optional.empty();
        }
        List<ChatMessage> toCompress = new ArrayList<>(oldest.subList(0, oldest.size() - keep));
        ChatMessage last = toCompress.get(toCompress.size() - 1);
        return Optional.of(new CompressionBatch(
                conversationId, conversation.getSummary(), toCompress, last));
    }

    /**
     * 写入滚动摘要并把 watermark 移到本段最后一条。
     *
     * @param batch       待提交批次
     * @param summaryJson 结构化摘要
     */
    public void commitSummary(CompressionBatch batch, String summaryJson) {
        ChatMessage last = batch.lastCovered();
        conversationRepository.updateSummary(
                batch.conversationId(), summaryJson, last.getCreateTime(), last.getMessageId());
    }

    /**
     * 紧急压缩：不再看条数或 token 阈值，把 watermark 之后除最近 {@code keepRecentMessages} 条以外的消息压成一段。
     *
     * @param userId             用户
     * @param conversationId     会话
     * @param keepRecentMessages 保留尾部
     * @return 待压缩批次；剩余条数不超过保留尾部时为空
     */
    public Optional<CompressionBatch> prepareForced(String userId, String conversationId, int keepRecentMessages) {
        Conversation conversation = conversationRepository.requireOwned(userId, conversationId);
        int keep = Math.max(2, keepRecentMessages);
        String untilMessageId = conversation.getSummaryUntilMessageId();
        long outstanding = chatMessageRepository.countAfterWatermark(
                conversationId, userId, conversation.getSummaryUntilTime(), untilMessageId);
        int limit = (int) Math.min(Math.max(keep + 1L, outstanding), Integer.MAX_VALUE);
        List<ChatMessage> oldest = chatMessageRepository.listOldestAfterWatermarkAsc(
                conversationId, userId, conversation.getSummaryUntilTime(), untilMessageId, limit);
        if (oldest.size() <= keep) {
            return Optional.empty();
        }
        List<ChatMessage> toCompress = new ArrayList<>(oldest.subList(0, oldest.size() - keep));
        ChatMessage last = toCompress.get(toCompress.size() - 1);
        return Optional.of(new CompressionBatch(
                conversationId, conversation.getSummary(), toCompress, last));
    }

    public String buildRollingSummary(String oldSummaryJson, List<ChatMessage> messages) throws Exception {
        Map<String, Object> summary = new LinkedHashMap<>();
        List<String> facts = new ArrayList<>();
        List<String> decisions = new ArrayList<>();
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
                        if (o == null) {
                            continue;
                        }
                        String fact = PostgresText.sanitize(String.valueOf(o));
                        if (StringUtils.hasText(fact)) {
                            facts.add(fact);
                        }
                    }
                }
                // decisions 只有 LLM 路径会产出，规则降级必须原样继承，否则不可恢复
                Object d = old.get("decisions");
                if (d instanceof List<?> list) {
                    for (Object o : list) {
                        if (o != null && decisions.size() < 20) {
                            String decision = PostgresText.sanitize(String.valueOf(o));
                            if (StringUtils.hasText(decision)) {
                                decisions.add(decision);
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
            facts.add(m.getRole() + ": " + snippet);
        }
        facts = dedupe(facts);
        while (facts.size() > 20) {
            facts.remove(0);
        }
        decisions = dedupe(decisions);
        while (decisions.size() > 10) {
            decisions.remove(0);
        }
        pending = dedupe(pending);
        summary.put("goal", goal == null ? "" : goal);
        summary.put("facts", facts);
        summary.put("decisions", decisions);
        summary.put("pending_tasks", pending);
        return PostgresText.sanitizeJson(objectMapper.writeValueAsString(summary));
    }

    private static List<String> dedupe(List<String> items) {
        return new ArrayList<>(new LinkedHashSet<>(items));
    }

    /**
     * 摘要 JSON 的条目计数，供压缩日志观测；解析失败返回占位符。
     *
     * @param summaryJson 结构化摘要
     * @param mapper      JSON 解析器
     * @return {@code facts≈N decisions≈M} 形式的标签
     */
    public static String countsLabel(String summaryJson, ObjectMapper mapper) {
        try {
            JsonNode root = mapper.readTree(summaryJson);
            int facts = root != null && root.path("facts").isArray() ? root.path("facts").size() : 0;
            int decisions = root != null && root.path("decisions").isArray() ? root.path("decisions").size() : 0;
            return "facts≈" + facts + " decisions≈" + decisions;
        } catch (Exception e) {
            return "facts≈? decisions≈?";
        }
    }
}
