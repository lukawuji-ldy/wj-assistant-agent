package com.wuji.assistant.memory.extract;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * L1 显式记忆指令检测。
 *
 * @author liudy
 */
public final class ExplicitRememberDetector {

    private static final Pattern REMEMBER = Pattern.compile(
            "(?:请)?记住(?:一下)?[：:\\s]*(.+)", Pattern.DOTALL);
    private static final Pattern ALWAYS = Pattern.compile(
            "以后都(?:要)?[：:\\s]*(.+)", Pattern.DOTALL);

    private ExplicitRememberDetector() {
    }

    /**
     * @param utterance 用户原文
     * @return 命中则返回待写入内容，否则 null
     */
    public static String detectContent(String utterance) {
        if (!StringUtils.hasText(utterance)) {
            return null;
        }
        String text = utterance.trim();
        Matcher m1 = REMEMBER.matcher(text);
        if (m1.find()) {
            return clean(m1.group(1));
        }
        Matcher m2 = ALWAYS.matcher(text);
        if (m2.find()) {
            return clean(m2.group(1));
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("记住") || lower.contains("以后都")) {
            // 宽匹配：整句作为记忆值
            return clean(text);
        }
        return null;
    }

    public static boolean matches(String utterance) {
        return detectContent(utterance) != null;
    }

    private static String clean(String v) {
        if (v == null) {
            return null;
        }
        String s = v.trim();
        if (s.length() > 500) {
            s = s.substring(0, 500);
        }
        return s.isEmpty() ? null : s;
    }
}
