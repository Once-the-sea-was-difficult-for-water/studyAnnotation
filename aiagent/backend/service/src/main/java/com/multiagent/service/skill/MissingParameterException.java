package com.multiagent.service.skill;

/**
 * 必填参数缺失异常 — 当 Skill 的 required 参数未提供时抛出。
 */
public class MissingParameterException extends RuntimeException {

    private final String parameterName;
    private final String skillId;

    public MissingParameterException(String skillId, String parameterName) {
        super("Skill '" + skillId + "' 缺少必填参数: " + parameterName);
        this.skillId = skillId;
        this.parameterName = parameterName;
    }

    public String getParameterName() {
        return parameterName;
    }

    public String getSkillId() {
        return skillId;
    }
}
