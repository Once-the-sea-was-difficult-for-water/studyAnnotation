package com.multiagent.infrastructure.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Skill Workflow 步骤定义。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStep {

    /** 步骤 ID */
    private String id;

    /** 步骤类型 */
    private StepType type;

    /** 工具名称（TOOL_CALL 使用） */
    private String toolName;

    /** LLM Prompt 模板（LLM_CALL 使用） */
    private String prompt;

    /** 条件表达式（CONDITION 使用） */
    private String condition;

    /** 条件分支列表（CONDITION 使用） */
    private List<Branch> branches;

    /** 并行子步骤列表（PARALLEL 使用） */
    private List<WorkflowStep> children;

    /** 结果存入上下文的 key */
    private String outputKey;
}
