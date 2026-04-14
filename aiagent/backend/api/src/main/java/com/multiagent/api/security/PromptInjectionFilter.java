package com.multiagent.api.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 对用户输入进行 Prompt Injection 攻击检测与过滤。
 * 基于常见注入模式的正则匹配，返回清洗后的安全文本。
 */
@Component
public class PromptInjectionFilter {

    private static final Logger log = LoggerFactory.getLogger(PromptInjectionFilter.class);

    /** 常见 Prompt Injection 模式 */
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            // 试图覆盖系统指令
            Pattern.compile("(?i)ignore\\s+(all\\s+)?(previous|above|prior)\\s+(instructions?|prompts?|rules?)"),
            // 试图重新定义角色
            Pattern.compile("(?i)you\\s+are\\s+now\\s+(?:a|an|the)\\s+"),
            // 试图泄露系统 prompt
            Pattern.compile("(?i)(reveal|show|print|output|display|repeat)\\s+(your|the|system)\\s+(prompt|instructions?|rules?)"),
            // 分隔符注入
            Pattern.compile("(?i)---+\\s*(system|assistant|new\\s+instruction)"),
            // 直接指令覆盖
            Pattern.compile("(?i)(forget|disregard|override)\\s+(everything|all|your)\\s+(above|previous|instructions?)?"),
            // DAN / jailbreak 常见关键词
            Pattern.compile("(?i)\\bDAN\\b.*\\bmode\\b"),
            Pattern.compile("(?i)\\bjailbreak\\b")
    );

    /**
     * 检测输入是否包含 Prompt Injection 模式。
     *
     * @return true 表示检测到注入风险
     */
    public boolean containsInjection(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                log.warn("Prompt injection detected, pattern={}", pattern.pattern());
                return true;
            }
        }
        return false;
    }

    /**
     * 清洗用户输入：移除匹配到的注入片段，返回安全文本。
     * 如果整段文本都是注入内容，返回空字符串。
     */
    public String sanitize(String input) {
        if (input == null) {
            return "";
        }
        String sanitized = input;
        for (Pattern pattern : INJECTION_PATTERNS) {
            sanitized = pattern.matcher(sanitized).replaceAll("");
        }
        return sanitized.trim();
    }
}
