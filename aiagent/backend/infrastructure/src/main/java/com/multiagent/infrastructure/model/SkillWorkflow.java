package com.multiagent.infrastructure.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 技能工作流定义。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillWorkflow {

    /** 工作流步骤列表 */
    private List<WorkflowStep> steps;
}
