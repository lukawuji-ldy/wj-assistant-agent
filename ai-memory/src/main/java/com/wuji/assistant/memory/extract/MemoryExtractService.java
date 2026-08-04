package com.wuji.assistant.memory.extract;

import com.wuji.assistant.common.util.IdGenerator;
import com.wuji.assistant.memory.repo.UserSemanticMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * L2 Memory Extract：规则抽取 / 多 Action 落库（禁止整段对话入库）。
 *
 * @author liudy
 */
@Service
public class MemoryExtractService {

    private static final Logger log = LoggerFactory.getLogger(MemoryExtractService.class);

    /** 仅「我叫」写姓名；禁止用「我是」写 display_name。 */
    private static final Pattern CALL_NAME_PATTERN = Pattern.compile(
            "我叫\\s*([\\u4e00-\\u9fa5A-Za-z0-9_]{1,32})");
    /** 籍贯：我来自 X / 我是（一个）X人 */
    private static final Pattern HOMETOWN_FROM_PATTERN = Pattern.compile(
            "我来自\\s*([\\u4e00-\\u9fa5A-Za-z0-9_·]{1,64})");
    private static final Pattern HOMETOWN_REN_PATTERN = Pattern.compile(
            "我是\\s*(?:一个)?\\s*([\\u4e00-\\u9fa5A-Za-z0-9_·]{1,64}?)人");
    /** 现居：在 X 住 / 住在 X */
    private static final Pattern RESIDENCE_AT_PATTERN = Pattern.compile(
            "在\\s*([\\u4e00-\\u9fa5A-Za-z0-9_·]{2,32}?)\\s*住");
    private static final Pattern RESIDENCE_LIVE_PATTERN = Pattern.compile(
            "住在\\s*([\\u4e00-\\u9fa5A-Za-z0-9_·]{2,32})");
    private static final Pattern PREF_PATTERN = Pattern.compile("我(?:喜欢|偏好|爱)\\s*(.+)");
    /** 其它「我是…」自我描述（须在籍贯之后匹配） */
    private static final Pattern SELF_DESC_PATTERN = Pattern.compile(
            "我是\\s*(.+)");
    /** 转折：优先其后籍贯为真实 hometown */
    private static final Pattern CONTRAST_PATTERN = Pattern.compile("但实际上|其实|不过");
    /** 对外说法窗口：此前若干字内出现则该「我是…人」不得作 hometown */
    private static final Pattern STATED_AS_PATTERN = Pattern.compile("我会说|如果说|对外");

    private final JdbcTemplate jdbcTemplate;
    private final UserSemanticMemoryRepository userSemanticMemoryRepository;

    @Autowired
    public MemoryExtractService(JdbcTemplate jdbcTemplate,
                                UserSemanticMemoryRepository userSemanticMemoryRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.userSemanticMemoryRepository = userSemanticMemoryRepository;
    }


    /**
     * 单测兼容：无语义仓时仅规则路径可用。
     *
     * @param jdbcTemplate JDBC
     */
    public MemoryExtractService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null);
    }

    /**
     * 异步语义入口：由调用方在 boundedElastic 触发。
     *
     * @param conversationId 会话
     * @param messageId      助手消息 id（幂等键）
     * @param userId         用户
     * @param userText       用户原文
     * @param assistantText  助手原文（仅作上下文，不整段入库）
     */
    public void extractAsync(String conversationId, String messageId, String userId,
                             String userText, String assistantText) {
        try {
            extract(conversationId, messageId, userId, userText, assistantText);
        } catch (Exception e) {
            log.warn("memory extract failed messageId={}: {}", messageId, e.toString());
        }
    }

    /**
     * 同步提取（单测 / 显式调用）。
     */
    public MemoryActionItem extract(String conversationId, String messageId, String userId,
                                    String userText, String assistantText) {
        if (!StringUtils.hasText(messageId) || !StringUtils.hasText(userId)) {
            return new MemoryActionItem(MemoryAction.IGNORE, "NONE", null, null);
        }
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM memory_extract_log WHERE message_id = ?", Integer.class, messageId);
        if (exists != null && exists > 0) {
            log.debug("memory extract skip duplicate messageId={}", messageId);
            return new MemoryActionItem(MemoryAction.IGNORE, "NONE", null, null);
        }

        MemoryActionItem item = decide(userText);
        Timestamp now = Timestamp.from(Instant.now());
        try {
            if (item.action() != MemoryAction.IGNORE
                    && ("PROFILE".equals(item.resultType()) || "PREFERENCE".equals(item.resultType()))) {
                upsertProfile(userId, item, now);
            }
            // 旁路：现居与主 Action 分 key 并存（同一 message_id 只记一条 extract_log）
            if (!"residence".equals(item.memoryKey())) {
                String residence = extractResidence(userText);
                if (residence != null) {
                    upsertProfile(userId,
                            new MemoryActionItem(MemoryAction.UPDATE, "PROFILE", "residence", residence),
                            now);
                }
            }
            insertLog(conversationId, messageId, userId, "SUCCESS", item, null, now);
        } catch (Exception e) {
            insertLog(conversationId, messageId, userId, "FAILED", item, truncate(e.getMessage()), now);
            throw e;
        }
        return item;
    }

    public MemoryActionItem decide(String userText) {
        if (!StringUtils.hasText(userText)) {
            return new MemoryActionItem(MemoryAction.IGNORE, "NONE", null, null);
        }
        String text = userText.trim();

        Matcher callName = CALL_NAME_PATTERN.matcher(text);
        if (callName.find()) {
            return new MemoryActionItem(MemoryAction.UPDATE, "PROFILE", "display_name", callName.group(1));
        }

        String hometown = extractHometown(text);
        if (hometown != null) {
            return new MemoryActionItem(MemoryAction.UPDATE, "PROFILE", "hometown", hometown);
        }

        String residence = extractResidence(text);
        if (residence != null) {
            return new MemoryActionItem(MemoryAction.UPDATE, "PROFILE", "residence", residence);
        }

        Matcher pref = PREF_PATTERN.matcher(text);
        if (pref.find()) {
            String value = clip(pref.group(1).trim(), 200);
            return new MemoryActionItem(MemoryAction.UPDATE, "PREFERENCE", "preference.note", value);
        }

        // 显式「记住」由 L1 rememberExplicit 处理；此处不再 IGNORE 后丢弃——若 L2 单独跑到则写入 note
        String explicit = ExplicitRememberDetector.detectContent(text);
        if (explicit != null) {
            return new MemoryActionItem(MemoryAction.UPDATE, "PREFERENCE", "preference.explicit", explicit);
        }

        Matcher selfDesc = SELF_DESC_PATTERN.matcher(text);
        if (selfDesc.find()) {
            String value = clip(selfDesc.group(1).trim(), 200);
            if (StringUtils.hasText(value)) {
                return new MemoryActionItem(MemoryAction.UPDATE, "PROFILE", "self_desc", value);
            }
        }
        return new MemoryActionItem(MemoryAction.IGNORE, "NONE", null, null);
    }

    /**
     * 籍贯决议：转折后优先；对外说法候选丢弃；多候选无转折赢家则不写（避免错误覆盖）。
     */
    static String extractHometown(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        List<HometownCandidate> all = collectHometownCandidates(text);
        List<HometownCandidate> usable = new ArrayList<>();
        for (HometownCandidate c : all) {
            if (!c.statedAs()) {
                usable.add(c);
            }
        }
        if (usable.isEmpty()) {
            return null;
        }

        int contrastAt = findLastContrastIndex(text);
        if (contrastAt >= 0) {
            HometownCandidate after = null;
            for (HometownCandidate c : usable) {
                if (c.start() >= contrastAt) {
                    after = c;
                }
            }
            if (after != null) {
                return after.value();
            }
        }

        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (HometownCandidate c : usable) {
            distinct.add(c.value());
        }
        if (distinct.size() == 1) {
            return usable.get(0).value();
        }
        // 多冲突且无转折赢家 → 不覆盖
        return null;
    }

    /**
     * 现居：在 X 住 / 住在 X。
     */
    static String extractResidence(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher at = RESIDENCE_AT_PATTERN.matcher(text);
        if (at.find()) {
            return cleanPlace(at.group(1));
        }
        Matcher live = RESIDENCE_LIVE_PATTERN.matcher(text);
        if (live.find()) {
            return cleanPlace(live.group(1));
        }
        return null;
    }

    static List<HometownCandidate> collectHometownCandidates(String text) {
        List<HometownCandidate> list = new ArrayList<>();
        Matcher from = HOMETOWN_FROM_PATTERN.matcher(text);
        while (from.find()) {
            String cleaned = cleanHometown(from.group(1));
            if (cleaned != null && isPlausibleHometown(cleaned)) {
                list.add(new HometownCandidate(cleaned, from.start(), isStatedAsContext(text, from.start())));
            }
        }
        Matcher ren = HOMETOWN_REN_PATTERN.matcher(text);
        while (ren.find()) {
            String raw = ren.group(1);
            if (raw != null && raw.contains("的")) {
                continue;
            }
            String cleaned = cleanHometown(raw);
            if (cleaned != null && isPlausibleHometown(cleaned)) {
                list.add(new HometownCandidate(cleaned, ren.start(), isStatedAsContext(text, ren.start())));
            }
        }
        return list;
    }

    static boolean isStatedAsContext(String text, int matchStart) {
        int from = Math.max(0, matchStart - 12);
        String window = text.substring(from, matchStart);
        return STATED_AS_PATTERN.matcher(window).find();
    }

    static int findLastContrastIndex(String text) {
        Matcher m = CONTRAST_PATTERN.matcher(text);
        int last = -1;
        while (m.find()) {
            last = m.start();
        }
        return last;
    }

    static boolean isPlausibleHometown(String cleaned) {
        if (!StringUtils.hasText(cleaned)) {
            return false;
        }
        if (cleaned.endsWith("男") || cleaned.endsWith("女")
                || "老".equals(cleaned) || "好".equals(cleaned) || "坏".equals(cleaned)
                || "年轻".equals(cleaned)) {
            return false;
        }
        return true;
    }

    static String cleanHometown(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String s = raw.trim();
        if (s.startsWith("一个")) {
            s = s.substring(2).trim();
        }
        if (s.endsWith("人") && s.length() > 1) {
            s = s.substring(0, s.length() - 1).trim();
        }
        return StringUtils.hasText(s) ? clip(s, 64) : null;
    }

    static String cleanPlace(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return clip(raw.trim(), 64);
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * 应用 LLM / 编排层产出的多 Action（幂等键仍为 message_id）。
     *
     * @param conversationId 会话
     * @param messageId      助手消息
     * @param userId         用户
     * @param actions        动作列表（可空）
     * @return 实际尝试落库的条数（含闸门拒绝前计数中已 accept 的）
     */
    public int applyActions(String conversationId, String messageId, String userId,
                            List<MemoryActionItem> actions) {
        if (!StringUtils.hasText(messageId) || !StringUtils.hasText(userId)) {
            return 0;
        }
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM memory_extract_log WHERE message_id = ?", Integer.class, messageId);
        if (exists != null && exists > 0) {
            log.debug("memory applyActions skip duplicate messageId={}", messageId);
            return 0;
        }
        List<MemoryActionItem> safe = actions == null ? List.of() : actions;
        Timestamp now = Timestamp.from(Instant.now());
        MemoryActionItem logItem = new MemoryActionItem(MemoryAction.IGNORE, "NONE", null, null);
        int applied = 0;
        try {
            for (MemoryActionItem item : safe) {
                if (item == null || !MemoryActionGate.accept(item)) {
                    continue;
                }
                String type = item.resultType() == null ? "" : item.resultType().trim().toUpperCase();
                if ("SEMANTIC".equals(type)) {
                    if (item.action() == MemoryAction.DELETE) {
                        continue;
                    }
                    if (userSemanticMemoryRepository == null || !StringUtils.hasText(item.embeddingVectorLiteral())) {
                        log.warn("skip SEMANTIC without embedding messageId={}", messageId);
                        continue;
                    }
                    int dims = countVectorDimensions(item.embeddingVectorLiteral());
                    String id = userSemanticMemoryRepository.insert(
                            userId, item.content(), item.importance(), item.confidence(),
                            messageId, item.embeddingVectorLiteral(), dims);
                    if (id != null) {
                        applied++;
                        logItem = item;
                    }
                } else if ("PROFILE".equals(type) || "PREFERENCE".equals(type)) {
                    if (item.action() == MemoryAction.DELETE) {
                        softDeleteProfile(userId, item.memoryKey(), now);
                        applied++;
                        logItem = item;
                    } else {
                        upsertProfile(userId, item, now);
                        applied++;
                        logItem = item;
                    }
                }
            }
            insertLog(conversationId, messageId, userId, "SUCCESS", logItem, null, now);
        } catch (Exception e) {
            insertLog(conversationId, messageId, userId, "FAILED", logItem, truncate(e.getMessage()), now);
            throw e;
        }
        return applied;
    }

    /**
     * LLM 模式失败：仅写 FAILED 审计（不影响幂等成功路径）。
     */
    public void recordFailed(String conversationId, String messageId, String userId, String error) {
        if (!StringUtils.hasText(messageId) || !StringUtils.hasText(userId)) {
            return;
        }
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM memory_extract_log WHERE message_id = ?", Integer.class, messageId);
        if (exists != null && exists > 0) {
            return;
        }
        Timestamp now = Timestamp.from(Instant.now());
        insertLog(conversationId, messageId, userId, "FAILED",
                new MemoryActionItem(MemoryAction.IGNORE, "NONE", null, null),
                truncate(error), now);
    }

    /**
     * L1：用户显式记忆指令，同步写入（source=USER_DIRECT）。
     *
     * @param conversationId 会话
     * @param messageId      用户消息 id（幂等）
     * @param userId         用户
     * @param utterance      原文
     * @return action
     */
    public MemoryActionItem rememberExplicit(String conversationId, String messageId,
                                             String userId, String utterance) {
        String content = ExplicitRememberDetector.detectContent(utterance);
        if (content == null || !StringUtils.hasText(userId) || !StringUtils.hasText(messageId)) {
            return new MemoryActionItem(MemoryAction.IGNORE, "NONE", null, null);
        }
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM memory_extract_log WHERE message_id = ?", Integer.class, messageId);
        if (exists != null && exists > 0) {
            return new MemoryActionItem(MemoryAction.IGNORE, "NONE", null, null);
        }
        MemoryActionItem item = new MemoryActionItem(
                MemoryAction.UPDATE, "PREFERENCE", "preference.explicit", content);
        Timestamp now = Timestamp.from(Instant.now());
        try {
            upsertProfile(userId, item, now, "USER_DIRECT");
            insertLog(conversationId, messageId, userId, "SUCCESS", item, null, now);
        } catch (Exception e) {
            insertLog(conversationId, messageId, userId, "FAILED", item, truncate(e.getMessage()), now);
            throw e;
        }
        return item;
    }

    private void upsertProfile(String userId, MemoryActionItem item, Timestamp now) {
        upsertProfile(userId, item, now, "extract");
    }

    private void upsertProfile(String userId, MemoryActionItem item, Timestamp now, String source) {
        float confidence = item.confidence() == null ? 0.90f : item.confidence().floatValue();
        float importance = item.importance() == null ? 0.80f : item.importance().floatValue();
        Integer cnt = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM user_profile
                WHERE user_id = ? AND memory_key = ? AND status = 'ACTIVE'
                """, Integer.class, userId, item.memoryKey());
        if (cnt != null && cnt > 0) {
            String newValue = item.memoryValue();
            if (item.action() == MemoryAction.MERGE) {
                String old = jdbcTemplate.query("""
                        SELECT memory_value FROM user_profile
                        WHERE user_id = ? AND memory_key = ? AND status = 'ACTIVE'
                        LIMIT 1
                        """, rs -> rs.next() ? rs.getString(1) : null, userId, item.memoryKey());
                if (StringUtils.hasText(old) && StringUtils.hasText(newValue) && !old.contains(newValue)) {
                    newValue = old + "; " + newValue;
                } else if (StringUtils.hasText(old) && !StringUtils.hasText(newValue)) {
                    newValue = old;
                }
            }
            jdbcTemplate.update("""
                    UPDATE user_profile
                    SET memory_value = ?, confidence = ?, importance = ?,
                        update_time = ?, version = version + 1, source = ?
                    WHERE user_id = ? AND memory_key = ? AND status = 'ACTIVE'
                    """, newValue, confidence, importance, now, source, userId, item.memoryKey());
        } else {
            jdbcTemplate.update("""
                    INSERT INTO user_profile
                    (id, memory_id, user_id, memory_type, memory_key, memory_value, status,
                     confidence, importance, source, version, create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, 1, ?, ?)
                    """,
                    IdGenerator.nextLong(),
                    IdGenerator.nextBizId("mem_"),
                    userId,
                    item.resultType(),
                    item.memoryKey(),
                    item.memoryValue(),
                    confidence,
                    importance,
                    source,
                    now, now);
        }
    }

    private void softDeleteProfile(String userId, String memoryKey, Timestamp now) {
        jdbcTemplate.update("""
                UPDATE user_profile
                SET status = 'DELETED', update_time = ?, version = version + 1
                WHERE user_id = ? AND memory_key = ? AND status = 'ACTIVE'
                """, now, userId, memoryKey);
    }

    static int countVectorDimensions(String vectorLiteral) {
        if (!StringUtils.hasText(vectorLiteral)) {
            return 0;
        }
        String s = vectorLiteral.trim();
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1).trim();
        }
        if (!StringUtils.hasText(s)) {
            return 0;
        }
        int n = 1;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == ',') {
                n++;
            }
        }
        return n;
    }

    private void insertLog(String conversationId, String messageId, String userId, String status,
                           MemoryActionItem item, String error, Timestamp now) {
        jdbcTemplate.update("""
                INSERT INTO memory_extract_log
                (id, user_id, conversation_id, message_id, status, action, result_type,
                 error_message, retry_count, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                """,
                IdGenerator.nextLong(),
                userId,
                conversationId,
                messageId,
                status,
                item.action().name(),
                item.resultType(),
                error,
                now, now);
    }

    private static String truncate(String msg) {
        if (msg == null) {
            return null;
        }
        return msg.length() <= 500 ? msg : msg.substring(0, 500);
    }

    /**
     * 籍贯候选（含位置与是否对外说法）。
     */
    record HometownCandidate(String value, int start, boolean statedAs) {
    }
}
