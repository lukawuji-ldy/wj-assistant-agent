package com.wuji.assistant.memory.retrieve;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 长期记忆 Router（rule：关键词 / 简单意图）。
 *
 * @author liudy
 */
@Component
public class MemoryRouter implements MemoryRoutePort {

    private static final Pattern PREFERENCE_HINT = Pattern.compile(
            "我喜欢|偏好|我的颜色|喜欢什么|喜欢哪|按我喜欢|记得我|我的习惯|我的爱好|我的兴趣|"
                    + "什么颜色|什么爱好|什么运动|爱吃|喜欢吃");
    private static final Pattern PROFILE_HINT = Pattern.compile(
            "我叫什么|我的名字|我是谁|我的家乡|籍贯|我来自哪|住在哪|我的职业|我做什么|"
                    + "我的目标|介绍一下我|我的画像|我的资料");
    private static final Pattern SEMANTIC_HINT = Pattern.compile(
            "记得吗|记得不|我说过|上次|那次|经历过|我的经历|以前聊|之前说过|你还记得");
    /** 明显纯知识问：无第一人称画像意图时 skip */
    private static final Pattern KNOWLEDGE_ONLY = Pattern.compile(
            "^(什么是|如何|怎样|怎么|请解释|介绍一下(?!我)|为什么).{0,40}$");

    /**
     * 规则路由。
     *
     * @param query 用户本轮原文
     * @return 决策
     */
    @Override
    public MemoryRouteDecision route(String query) {
        if (!StringUtils.hasText(query)) {
            return MemoryRouteDecision.skip();
        }
        String text = query.trim();
        Set<String> types = new LinkedHashSet<>();
        if (PREFERENCE_HINT.matcher(text).find()) {
            types.add("PREFERENCE");
        }
        if (PROFILE_HINT.matcher(text).find()) {
            types.add("PROFILE");
        }
        if (SEMANTIC_HINT.matcher(text).find()) {
            types.add("SEMANTIC");
        }
        if (!types.isEmpty()) {
            return MemoryRouteDecision.of(types);
        }
        // 「我的…」宽兜底：拉 preference + profile
        String lower = text.toLowerCase(Locale.ROOT);
        if (text.contains("我的") || text.startsWith("我") && (text.contains("？") || text.contains("?"))) {
            if (!KNOWLEDGE_ONLY.matcher(text).find() && !looksLikePureKnowledge(lower)) {
                return MemoryRouteDecision.of(Set.of("PROFILE", "PREFERENCE"));
            }
        }
        return MemoryRouteDecision.skip();
    }

    private static boolean looksLikePureKnowledge(String lower) {
        return lower.startsWith("什么是") || lower.startsWith("如何") || lower.startsWith("怎样")
                || lower.startsWith("怎么") || lower.startsWith("请解释");
    }
}
