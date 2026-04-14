package com.multiagent.service.intent;

import com.multiagent.infrastructure.model.ConversationContext;
import com.multiagent.infrastructure.model.RoutingDecision;
import com.multiagent.infrastructure.model.RoutingMode;
import com.multiagent.service.skill.SkillRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 默认意图识别器实现。
 *
 * <p>路由规则：
 * <ol>
 *   <li>{@code @agent-name rest of query} → DIRECT_AGENT</li>
 *   <li>{@code /skill-name param1=value1 param2=value2} → SKILL</li>
 *   <li>自由文本 → 关键词匹配（LLM 分类占位），置信度 &gt; 0.8 → SKILL，否则 → AGENT</li>
 * </ol>
 */
@Service
public class DefaultIntentRecognizer implements IntentRecognizer {

    private static final Logger log = LoggerFactory.getLogger(DefaultIntentRecognizer.class);

    /** @agent-name 模式：@ 后跟 agent id（字母数字和连字符），其余为查询 */
    private static final Pattern DIRECT_AGENT_PATTERN = Pattern.compile("^@([a-zA-Z0-9][a-zA-Z0-9-]*)\\s*(.*)$", Pattern.DOTALL);

    /** /skill-name 模式：/ 后跟 skill id（字母数字和连字符），其余为参数 */
    private static final Pattern SKILL_PATTERN = Pattern.compile("^/([a-zA-Z0-9][a-zA-Z0-9-]*)(.*)$", Pattern.DOTALL);

    /** 参数解析：key=value 对 */
    private static final Pattern PARAM_PATTERN = Pattern.compile("([a-zA-Z0-9_-]+)=([^\\s]+)");

    /** Skill 匹配置信度阈值 */
    private static final double SKILL_CONFIDENCE_THRESHOLD = 0.8;

    private final SkillRegistry skillRegistry;

    public DefaultIntentRecognizer(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    @Override
    public RoutingDecision recognize(String input, ConversationContext context) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("input must not be null or blank");
        }
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }

        String trimmed = input.trim();

        // 1. @agent-name 直接路由
        if (trimmed.startsWith("@")) {
            return parseDirectAgent(trimmed);
        }

        // 2. /skill-name 技能触发
        if (trimmed.startsWith("/")) {
            return parseSkillCommand(trimmed);
        }

        // 3. 自由文本：关键词匹配（LLM 分类占位）
        return routeFreeText(trimmed);
    }

    /**
     * 解析 @agent-name 格式，提取 agentId 和查询内容。
     */
    RoutingDecision parseDirectAgent(String input) {
        Matcher matcher = DIRECT_AGENT_PATTERN.matcher(input);
        if (!matcher.matches()) {
            // 无法解析 agent name，降级为自由文本路由
            log.warn("Failed to parse @agent pattern from input, falling back to free text routing");
            return routeFreeText(input);
        }

        String agentId = matcher.group(1);
        String query = matcher.group(2).trim();

        return RoutingDecision.builder()
                .mode(RoutingMode.DIRECT_AGENT)
                .agentId(agentId)
                .query(query.isEmpty() ? input : query)
                .build();
    }

    /**
     * 解析 /skill-name param1=value1 param2=value2 格式。
     */
    RoutingDecision parseSkillCommand(String input) {
        Matcher matcher = SKILL_PATTERN.matcher(input);
        if (!matcher.matches()) {
            log.warn("Failed to parse /skill pattern from input, falling back to free text routing");
            return routeFreeText(input);
        }

        String skillId = matcher.group(1);
        String paramString = matcher.group(2).trim();
        Map<String, Object> params = parseParams(paramString);

        return RoutingDecision.builder()
                .mode(RoutingMode.SKILL)
                .skillId(skillId)
                .query(input)
                .params(params)
                .build();
    }

    /**
     * 自由文本路由：使用 SkillRegistry 关键词匹配（LLM 分类占位）。
     * 置信度 > 0.8 匹配 Skill，否则路由到 Agent。
     */
    RoutingDecision routeFreeText(String input) {
        // 尝试通过 SkillRegistry 进行语义/关键词匹配
        List<SkillRegistry.ScoredSkill> matches = skillRegistry.match(input, 1);

        if (!matches.isEmpty()) {
            SkillRegistry.ScoredSkill top = matches.get(0);
            if (top.score() > SKILL_CONFIDENCE_THRESHOLD) {
                log.info("Free text matched skill '{}' with confidence {}", top.skill().getId(), top.score());
                return RoutingDecision.builder()
                        .mode(RoutingMode.SKILL)
                        .skillId(top.skill().getId())
                        .agentId(top.skill().getAgentId())
                        .query(input)
                        .build();
            }
        }

        // 置信度不足或无匹配，路由到 Agent
        log.info("Free text routed to AGENT mode");
        return RoutingDecision.builder()
                .mode(RoutingMode.AGENT)
                .query(input)
                .build();
    }

    /**
     * 解析 key=value 参数对。
     */
    Map<String, Object> parseParams(String paramString) {
        Map<String, Object> params = new HashMap<>();
        if (paramString == null || paramString.isBlank()) {
            return params;
        }
        Matcher matcher = PARAM_PATTERN.matcher(paramString);
        while (matcher.find()) {
            params.put(matcher.group(1), matcher.group(2));
        }
        return params;
    }
}
