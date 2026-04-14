package com.multiagent.service.skill;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简易模板渲染器 — 将 {{key}} 占位符替换为上下文中的实际值。
 */
@Component
public class TemplateRenderer {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*(\\w+)\\s*}}");

    /**
     * 渲染模板，将 {{key}} 替换为 context 中对应的值。
     *
     * @param template 包含 {{key}} 占位符的模板字符串
     * @param context  键值对上下文
     * @return 渲染后的字符串
     */
    public String render(String template, Map<String, Object> context) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        if (context == null || context.isEmpty()) {
            return template;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = context.get(key);
            String replacement = value != null ? value.toString() : matcher.group(0);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
