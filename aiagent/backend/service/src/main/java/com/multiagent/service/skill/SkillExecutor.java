package com.multiagent.service.skill;

import com.multiagent.infrastructure.model.SharedContext;
import com.multiagent.infrastructure.model.Skill;

import java.util.Map;

/**
 * 技能执行引擎接口 — 按 Skill Workflow 定义的步骤顺序执行。
 * <p>
 * 支持 TOOL_CALL、LLM_CALL、CONDITION、PARALLEL、HUMAN_CONFIRM 五种步骤类型。
 */
public interface SkillExecutor {

    /**
     * 执行指定 Skill 的 Workflow。
     *
     * @param skill   技能定义（含 workflow steps）
     * @param params  用户提供的输入参数
     * @param context 共享上下文
     * @return 执行结果，包含所有步骤的输出
     * @throws MissingParameterException 若 required 参数缺失
     */
    SkillResult execute(Skill skill, Map<String, Object> params, SharedContext context);
}
