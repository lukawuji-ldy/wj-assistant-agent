package com.wuji.assistant.agent.prompt;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 prompt_template 加载并渲染提示词。
 *
 * @author liudy
 */
@Service
public class PromptTemplateService {

    private static final Pattern VAR = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");

    private final JdbcTemplate jdbcTemplate;

    public PromptTemplateService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 加载指定 code 下 ACTIVE 最新版本内容。
     *
     * @param code 模板编码
     * @return 模板正文，缺失时返回空串
     */
    public String loadActiveContent(String code) {
        List<String> list = jdbcTemplate.query("""
                SELECT content FROM prompt_template
                WHERE code = ? AND status = 'ACTIVE'
                ORDER BY version DESC
                LIMIT 1
                """, (rs, rowNum) -> rs.getString("content"), code);
        return list.isEmpty() ? "" : list.get(0);
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
}
