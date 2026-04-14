package com.multiagent.service.paal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Assess 阶段的评估结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Assessment {

    /** 是否通过评估 */
    private boolean passed;

    /** 评估反馈（未通过时包含改进建议） */
    private String feedback;
}
