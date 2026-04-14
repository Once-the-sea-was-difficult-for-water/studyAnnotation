package com.multiagent.service.skill;

import com.multiagent.infrastructure.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.Reader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 从 YAML 配置加载 Skill 定义。
 * <p>
 * 支持从 InputStream 或 Reader 解析 YAML 格式的 Skill 配置，
 * 将其转换为 {@link Skill} 对象。
 */
public class SkillYamlLoader {

    private static final Logger log = LoggerFactory.getLogger(SkillYamlLoader.class);

    private final Yaml yaml;

    public SkillYamlLoader() {
        this.yaml = new Yaml();
    }

    /**
     * 从 InputStream 加载单个 Skill。
     */
    public Skill load(InputStream inputStream) {
        Map<String, Object> data = yaml.load(inputStream);
        return parseSkill(data);
    }

    /**
     * 从 Reader 加载单个 Skill。
     */
    public Skill load(Reader reader) {
        Map<String, Object> data = yaml.load(reader);
        return parseSkill(data);
    }

    /**
     * 从 YAML 字符串加载单个 Skill。
     */
    public Skill loadFromString(String yamlContent) {
        Map<String, Object> data = yaml.load(yamlContent);
        return parseSkill(data);
    }

    @SuppressWarnings("unchecked")
    private Skill parseSkill(Map<String, Object> data) {
        if (data == null) {
            throw new IllegalArgumentException("YAML content is empty or invalid");
        }

        Skill.SkillBuilder builder = Skill.builder()
                .id(getString(data, "id"))
                .name(getString(data, "name"))
                .description(getString(data, "description"))
                .icon(getString(data, "icon"))
                .agentId(getString(data, "agentId"));

        // category
        String categoryStr = getString(data, "category");
        if (categoryStr != null) {
            builder.category(SkillCategory.valueOf(categoryStr.toUpperCase()));
        }

        // inputParams
        List<Map<String, Object>> paramsList = (List<Map<String, Object>>) data.get("inputParams");
        if (paramsList != null) {
            builder.inputParams(paramsList.stream()
                    .map(this::parseSkillParam)
                    .collect(Collectors.toList()));
        }

        // workflow
        Map<String, Object> workflowMap = (Map<String, Object>) data.get("workflow");
        if (workflowMap != null) {
            builder.workflow(parseWorkflow(workflowMap));
        }

        // display
        Map<String, Object> displayMap = (Map<String, Object>) data.get("display");
        if (displayMap != null) {
            builder.display(parseDisplayConfig(displayMap));
        }

        // permissions
        List<String> permissions = (List<String>) data.get("permissions");
        if (permissions != null) {
            builder.permissions(permissions);
        }

        // rateLimit
        Map<String, Object> rateLimitMap = (Map<String, Object>) data.get("rateLimit");
        if (rateLimitMap != null) {
            builder.rateLimit(parseRateLimitConfig(rateLimitMap));
        }

        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private SkillParam parseSkillParam(Map<String, Object> data) {
        SkillParam.SkillParamBuilder builder = SkillParam.builder()
                .name(getString(data, "name"))
                .description(getString(data, "description"))
                .required(Boolean.TRUE.equals(data.get("required")))
                .defaultValue(data.get("defaultValue"));

        String typeStr = getString(data, "type");
        if (typeStr != null) {
            builder.type(ParamType.valueOf(typeStr.toUpperCase()));
        }

        List<String> enumValues = (List<String>) data.get("enumValues");
        if (enumValues != null) {
            builder.enumValues(enumValues);
        }

        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private SkillWorkflow parseWorkflow(Map<String, Object> data) {
        List<Map<String, Object>> stepsList = (List<Map<String, Object>>) data.get("steps");
        List<WorkflowStep> steps = (stepsList != null)
                ? stepsList.stream().map(this::parseWorkflowStep).collect(Collectors.toList())
                : Collections.emptyList();
        return SkillWorkflow.builder().steps(steps).build();
    }

    @SuppressWarnings("unchecked")
    private WorkflowStep parseWorkflowStep(Map<String, Object> data) {
        WorkflowStep.WorkflowStepBuilder builder = WorkflowStep.builder()
                .id(getString(data, "id"))
                .toolName(getString(data, "toolName"))
                .prompt(getString(data, "prompt"))
                .condition(getString(data, "condition"))
                .outputKey(getString(data, "outputKey"));

        String typeStr = getString(data, "type");
        if (typeStr != null) {
            builder.type(StepType.valueOf(typeStr.toUpperCase()));
        }

        // branches
        List<Map<String, Object>> branchesList = (List<Map<String, Object>>) data.get("branches");
        if (branchesList != null) {
            builder.branches(branchesList.stream()
                    .map(this::parseBranch)
                    .collect(Collectors.toList()));
        }

        // children (parallel sub-steps)
        List<Map<String, Object>> childrenList = (List<Map<String, Object>>) data.get("children");
        if (childrenList != null) {
            builder.children(childrenList.stream()
                    .map(this::parseWorkflowStep)
                    .collect(Collectors.toList()));
        }

        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private Branch parseBranch(Map<String, Object> data) {
        Branch.BranchBuilder builder = Branch.builder()
                .when(getString(data, "when"));

        List<Map<String, Object>> thenList = (List<Map<String, Object>>) data.get("then");
        if (thenList != null) {
            builder.then(thenList.stream()
                    .map(this::parseWorkflowStep)
                    .collect(Collectors.toList()));
        }

        return builder.build();
    }

    private DisplayConfig parseDisplayConfig(Map<String, Object> data) {
        return DisplayConfig.builder()
                .color(getString(data, "color"))
                .order(getInteger(data, "order"))
                .showOnHome((Boolean) data.get("showOnHome"))
                .build();
    }

    private RateLimitConfig parseRateLimitConfig(Map<String, Object> data) {
        return RateLimitConfig.builder()
                .maxCallsPerMinute(getInteger(data, "maxCallsPerMinute"))
                .maxCallsPerUserPerMinute(getInteger(data, "maxCallsPerUserPerMinute"))
                .build();
    }

    private String getString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }

    private Integer getInteger(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }
}
