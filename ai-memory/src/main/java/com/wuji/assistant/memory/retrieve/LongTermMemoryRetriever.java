package com.wuji.assistant.memory.retrieve;

import com.wuji.assistant.memory.model.UserProfileMemory;
import com.wuji.assistant.memory.model.UserSemanticHit;
import com.wuji.assistant.memory.repo.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 长期记忆检索：Router + user_profile Top-K + 可选语义向量召回。
 *
 * @author liudy
 */
@Service
public class LongTermMemoryRetriever {

    private static final Logger log = LoggerFactory.getLogger(LongTermMemoryRetriever.class);

    /** freshness 半衰期（天） */
    private static final double FRESHNESS_HALF_LIFE_DAYS = 30.0;

    private static final Pattern COLOR_QUERY = Pattern.compile("颜色|colour|color");
    private static final Pattern COLOR_VALUE = Pattern.compile(
            "[蓝绿红黄白黑紫粉灰橙棕金银青粉]色?|蓝色|绿色|红色|黄色|白色|黑色|紫色|粉色|灰色|橙色");
    private static final Pattern FOOD_QUERY = Pattern.compile("吃|食|口味|刀削面|面食|菜");
    private static final Pattern HOBBY_QUERY = Pattern.compile("爱好|兴趣|足球|象棋|羽毛球|乒乓|运动|hobby|sport");
    private static final Pattern QUESTION_ECHO = Pattern.compile("^(什么|哪|如何|怎样|怎么|请问).{0,20}$");

    private final MemoryRoutePort memoryRouter;
    private final UserProfileRepository userProfileRepository;
    private final SemanticMemorySearchPort semanticSearch;

    public LongTermMemoryRetriever(MemoryRoutePort memoryRouter,
                                   UserProfileRepository userProfileRepository,
                                   @Autowired(required = false) SemanticMemorySearchPort semanticSearch) {
        this.memoryRouter = memoryRouter;
        this.userProfileRepository = userProfileRepository;
        this.semanticSearch = semanticSearch;
    }

    /**
     * 按需检索并格式化为可注入 System 的文本块；失败返回空串。
     *
     * @param userId  用户
     * @param query   本轮用户原文
     * @param options 截断与权重
     * @return 非空则注入；空表示不注入
     */
    public String retrieveBlock(String userId, String query, MemoryRetrieveOptions options) {
        try {
            return doRetrieve(userId, query, options == null ? MemoryRetrieveOptions.defaults() : options);
        } catch (Exception e) {
            log.warn("long-term memory retrieve failed: {}", e.toString());
            return "";
        }
    }

    private String doRetrieve(String userId, String query, MemoryRetrieveOptions options) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(query)) {
            return "";
        }
        MemoryRouteDecision decision = memoryRouter.route(query);
        if (!decision.needMemory()) {
            log.debug("memory router skip userId={}", userId);
            return "";
        }
        String q = query.trim();
        Instant now = Instant.now();
        List<String> sections = new ArrayList<>();

        Set<String> profileTypes = profileTypesOnly(decision.memoryTypes());
        if (!profileTypes.isEmpty()) {
            String profileBlock = retrieveProfile(userId, q, options, now, profileTypes, decision.memoryTypes());
            if (StringUtils.hasText(profileBlock)) {
                sections.add(profileBlock);
            }
        }

        boolean needSemantic = decision.memoryTypes().contains("SEMANTIC") && options.semanticEnabled();
        if (needSemantic) {
            String semanticBlock = retrieveSemantic(userId, q, options, now);
            if (StringUtils.hasText(semanticBlock)) {
                sections.add(semanticBlock);
            }
        }

        if (sections.isEmpty()) {
            log.warn("memory retrieve empty after router hit types={} userId={}",
                    decision.memoryTypes(), userId);
            return "";
        }
        return String.join("\n\n", sections);
    }

    private String retrieveProfile(String userId, String q, MemoryRetrieveOptions options,
                                   Instant now, Set<String> profileTypes, Set<String> allTypes) {
        List<UserProfileMemory> rows = userProfileRepository.listActive(userId, profileTypes);
        if (rows.isEmpty()) {
            return "";
        }
        List<UserProfileMemory> candidates = rows.stream()
                .filter(m -> !isQuestionEchoJunk(m))
                .toList();
        if (candidates.isEmpty()) {
            return "";
        }
        List<UserProfileMemory> top = candidates.stream()
                .sorted(Comparator.comparingDouble((UserProfileMemory m) -> score(m, q, options, now)).reversed())
                .limit(options.topK())
                .toList();
        List<String> keys = top.stream().map(UserProfileMemory::getMemoryKey).toList();
        try {
            userProfileRepository.touchLastUsed(userId, keys);
        } catch (Exception e) {
            log.debug("touch profile last_used failed: {}", e.toString());
        }
        log.info("memory retrieve profile hit count={} keys={} types={}",
                top.size(), keys, allTypes);
        return formatProfileBlock(top);
    }

    private String retrieveSemantic(String userId, String q, MemoryRetrieveOptions options, Instant now) {
        if (semanticSearch == null) {
            log.debug("semantic search port unavailable, skip SEMANTIC");
            return "";
        }
        List<UserSemanticHit> hits;
        try {
            hits = semanticSearch.search(userId, q, options.semanticTopK(), options.semanticMinScore());
        } catch (Exception e) {
            log.warn("semantic memory search failed: {}", e.toString());
            return "";
        }
        if (hits == null || hits.isEmpty()) {
            return "";
        }
        List<UserSemanticHit> top = hits.stream()
                .sorted(Comparator.comparingDouble((UserSemanticHit h) -> scoreSemantic(h, options, now)).reversed())
                .limit(options.semanticTopK())
                .toList();
        // touch 由 EmbeddingSemanticMemorySearch / 仓负责；此处若 hit 含 id 则再触达一次无妨，由实现方 touch
        log.info("memory retrieve semantic hit count={}", top.size());
        return formatSemanticBlock(top);
    }

    static Set<String> profileTypesOnly(Set<String> types) {
        if (types == null || types.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String t : types) {
            if (t == null) {
                continue;
            }
            String u = t.trim().toUpperCase(Locale.ROOT);
            if ("PROFILE".equals(u) || "PREFERENCE".equals(u)) {
                out.add(u);
            }
        }
        return out;
    }

    /**
     * 综合分 = 问句相关性 * w1 + confidence * w2 + freshness * w3 + importance * w4。
     */
    static double score(UserProfileMemory m, String query, MemoryRetrieveOptions options, Instant now) {
        double relevance = queryRelevance(m, query);
        double confidence = clamp01(m.getConfidence());
        double importance = clamp01(m.getImportance());
        double freshness = freshness(m.getLastUsedTime(), m.getUpdateTime(), now);
        return options.weightSimilarity() * relevance
                + options.weightConfidence() * confidence
                + options.weightFreshness() * freshness
                + options.weightImportance() * importance;
    }

    /** 兼容旧单测：无 query 时相关性按 0。 */
    static double score(UserProfileMemory m, MemoryRetrieveOptions options, Instant now) {
        return score(m, "", options, now);
    }

    static double scoreSemantic(UserSemanticHit h, MemoryRetrieveOptions options, Instant now) {
        double similarity = clamp01(h.getScore());
        double confidence = clamp01(h.getConfidence());
        double importance = clamp01(h.getImportance());
        double freshness = freshness(h.getLastUsedTime(), h.getUpdateTime(), now);
        return options.weightSimilarity() * similarity
                + options.weightConfidence() * confidence
                + options.weightFreshness() * freshness
                + options.weightImportance() * importance;
    }

    /**
     * 问句与 key/value 的规则相关性（0～1），保证「问颜色」优先命中 favorite_color。
     */
    static double queryRelevance(UserProfileMemory m, String query) {
        if (!StringUtils.hasText(query) || m == null) {
            return 0.0;
        }
        String key = m.getMemoryKey() == null ? "" : m.getMemoryKey().toLowerCase(Locale.ROOT);
        String value = m.getMemoryValue() == null ? "" : m.getMemoryValue().trim();
        String q = query.trim();

        if (COLOR_QUERY.matcher(q).find()) {
            if (key.contains("color") || key.contains("colour")) {
                return 1.0;
            }
            if (COLOR_VALUE.matcher(value).find() && value.length() <= 16) {
                return 0.95;
            }
            if (key.contains("note") && value.contains("颜色")) {
                return 0.05;
            }
            return 0.0;
        }
        if (FOOD_QUERY.matcher(q).find()) {
            if (key.contains("food") || key.contains("diet") || key.contains("taste")) {
                return 1.0;
            }
            if (value.contains("吃") || value.contains("面") || value.contains("菜")) {
                return 0.7;
            }
        }
        if (HOBBY_QUERY.matcher(q).find()) {
            if (key.contains("hobby") || key.contains("sport")) {
                return 1.0;
            }
        }
        if (value.length() >= 2 && value.length() <= 32 && q.contains(value)) {
            return 0.6;
        }
        return 0.0;
    }

    /**
     * 过滤抽取误写入的问句碎片（如 preference.note=什么颜色）。
     */
    static boolean isQuestionEchoJunk(UserProfileMemory m) {
        if (m == null || !StringUtils.hasText(m.getMemoryValue())) {
            return true;
        }
        String key = m.getMemoryKey() == null ? "" : m.getMemoryKey();
        String value = m.getMemoryValue().trim();
        if (!key.contains("note") && !key.endsWith(".explicit")) {
            return false;
        }
        return QUESTION_ECHO.matcher(value).matches() || value.contains("什么颜色") && value.length() <= 12;
    }

    static double freshness(Instant lastUsed, Instant updateTime, Instant now) {
        Instant ref = lastUsed != null ? lastUsed : (updateTime != null ? updateTime : now);
        long days = Math.max(0, Duration.between(ref, now).toDays());
        return Math.pow(0.5, days / FRESHNESS_HALF_LIFE_DAYS);
    }

    static double freshness(UserProfileMemory m, Instant now) {
        return freshness(m.getLastUsedTime(), m.getUpdateTime(), now);
    }

    static String formatBlock(List<UserProfileMemory> rows) {
        return formatProfileBlock(rows);
    }

    static String formatProfileBlock(List<UserProfileMemory> rows) {
        String body = rows.stream()
                .map(m -> m.getMemoryKey() + "=" + (m.getMemoryValue() == null ? "" : m.getMemoryValue().trim()))
                .collect(Collectors.joining("\n"));
        return "已知用户长期记忆（回答「我喜欢/我的…」须优先依据下列事实；禁止把用户偏好理解成助手自身偏好）:\n"
                + body;
    }

    static String formatSemanticBlock(List<UserSemanticHit> hits) {
        String body = hits.stream()
                .map(h -> "- " + (h.getContent() == null ? "" : h.getContent().trim()))
                .collect(Collectors.joining("\n"));
        return "用户语义记忆（叙述性经历，回答相关问题时可引用）:\n" + body;
    }

    private static double clamp01(double v) {
        if (v < 0.0) {
            return 0.0;
        }
        if (v > 1.0) {
            return 1.0;
        }
        return v;
    }

    private static double clamp01(float v) {
        return clamp01((double) v);
    }
}
