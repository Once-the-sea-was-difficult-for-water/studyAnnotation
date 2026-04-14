package com.multiagent.infrastructure.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 条件分支定义 — CONDITION 步骤使用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Branch {

    /** 分支匹配条件 */
    private String when;

    /** 匹配后执行的步骤列表 */
    private List<WorkflowStep> then;
}
