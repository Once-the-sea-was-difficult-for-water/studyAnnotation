package com.multiagent.infrastructure.validation;

import com.multiagent.infrastructure.model.Skill;
import com.multiagent.infrastructure.model.StepType;
import com.multiagent.infrastructure.model.WorkflowStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Skill 配置验证器。
 * <p>
 * 验证规则：
 * <ul>
 *   <li>id 必须为 kebab-case 格式</li>
 *   <li>id 在已注册集合中唯一</li>
 *   <li>agentId 必须引用已注册的 Agent</li>
 *   <li>workflow.steps 至少包含一个步骤</li>
 *   <li>CONDITION 类型步骤必须包含 branches</li>
 *   <li>PARALLEL 类型步骤必须包含 children</li>
 * </ul>
 */
public class SkillValidator {

    private static final Pattern KEBAB_CASE_PATTERN = Pattern.compile("^[a-z][a-z0-9]*(-[a-z0-9]+)*$");

    /**
     * 验证 Skill 配置的完整性和正确性。
     *
     * @param skill            待验证的 Skill
     * @param existingSkillIds 已注册的 Skill ID 集合（用于唯一性检查）
     * @param registeredAgentIds 已注册的 Agent ID 集合（用于引用有效性检查）
     * @return 验证错误列表，空列表表示验证通过
     */
    public List<String> validate(Skill skill, Set<String> existingSkillIds, Set<String> registeredAgentIds) {
        List<String> errors = new ArrayList<>();

        validateId(skill, existingSkillIds, errors);
        validateAgentId(skill, registeredAgentIds, errors);
        validateWorkflow(skill, errors);

        return errors;
    }

    private void validateId(Skill skill, Set<String> existingSkillIds, List<String> errors) {
        if (skill.getId() == null || skill.getId().isBlank()) {
            errors.add("Skill id must not be empty");
            return;
        }
        if (!KEBAB_CASE_PATTERN.matcher(skill.getId()).matches()) {
            errors.add("Skill id must be kebab-case: " + skill.getId());
        }
        if (existingSkillIds != null && existingSkillIds.contains(skill.getId())) {
            errors.add("Skill id already exists: " + skill.getId());
        }
    }

    private void validateAgentId(Skill skill, Set<String> registeredAgentIds, List<String> errors) {
        if (skill.getAgentId() == null || skill.getAgentId().isBlank()) {
            errors.add("Skill agentId must not be empty");
            return;
        }
        if (registeredAgentIds != null && !registeredAgentIds.contains(skill.getAgentId())) {
            errors.add("Skill agentId references unregistered agent: " + skill.getAgentId());
        }
    }

    private void validateWorkflow(Skill skill, List<String> errors) {
        if (skill.getWorkflow() == null
                || skill.getWorkflow().getSteps() == null
                || skill.getWorkflow().getSteps().isEmpty()) {
            errors.add("Skill workflow must contain at least one step");
            return;
        }
        for (WorkflowStep step : skill.getWorkflow().getSteps()) {
            validateStep(step, errors);
        }
    }

    private void validateStep(WorkflowStep step, List<String> errors) {
        if (step.getType() == null) {
            errors.add("WorkflowStep type must not be null for step: " + step.getId());
            return;
        }
        if (step.getType() == StepType.CONDITION) {
            if (step.getBranches() == null || step.getBranches().isEmpty()) {
                errors.add("CONDITION step must contain branches: " + step.getId());
            }
        }
        if (step.getType() == StepType.PARALLEL) {
            if (step.getChildren() == null || step.getChildren().isEmpty()) {
                errors.add("PARALLEL step must contain children: " + step.getId());
            }
        }
    }
}
