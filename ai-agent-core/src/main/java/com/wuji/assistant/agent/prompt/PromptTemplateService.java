package com.wuji.assistant.agent.prompt;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 prompt_template 加载并渲染提示词（按 code 进程内缓存线上 ACTIVE 内容；不读版本草稿）。
 *
 * @author liudy
 */
@Service
public class PromptTemplateService {

    private static final Pattern VAR = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");

    private final JdbcTemplate jdbcTemplate;
    private final ConcurrentHashMap<String, String> contentCache = new ConcurrentHashMap<>();

    public PromptTemplateService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 加载指定 code 线上副本内容（主表 status=ACTIVE；带缓存）。
     *
     * @param code 模板编码
     * @return 模板正文，缺失时返回空串
     */
    public String loadActiveContent(String code) {
        if (!StringUtils.hasText(code)) {
            return "";
        }
        return contentCache.computeIfAbsent(code, this::loadActiveContentFromDb);
    }

    /**
     * 使指定 code 缓存失效。
     *
     * @param code 模板编码
     */
    public void invalidate(String code) {
        if (StringUtils.hasText(code)) {
            contentCache.remove(code);
        }
    }

    /**
     * 清空全部提示词缓存。
     */
    public void invalidateAll() {
        contentCache.clear();
    }

    /**
     * 渲染模板变量 {{key}}。
     *
     * @param template 模板
     * @param vars     变量表
     * @return 渲染结果
     */
    public String render(String template, Map<String, String> vars) {
        if (!StringUtils.hasText(template)) {
            return "";
        }
        Matcher matcher = VAR.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = vars.getOrDefault(key, "");
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 加载并渲染。
     *
     * @param code 模板编码
     * @param vars 变量
     * @return 结果；库缺失时返回 fallback
     */
    public String loadAndRender(String code, Map<String, String> vars, String fallback) {
        String content = loadActiveContent(code);
        if (!StringUtils.hasText(content)) {
            content = fallback;
        }
        return render(content, vars);
    }

    private String loadActiveContentFromDb(String code) {
        if (jdbcTemplate == null) {
            return "";
        }
        List<String> list = jdbcTemplate.query("""
                SELECT content FROM prompt_template
                WHERE code = ? AND status = 'ACTIVE'
                LIMIT 1
                """, (rs, rowNum) -> rs.getString("content"), code);
        return list.isEmpty() ? "" : list.get(0);
    }
}
