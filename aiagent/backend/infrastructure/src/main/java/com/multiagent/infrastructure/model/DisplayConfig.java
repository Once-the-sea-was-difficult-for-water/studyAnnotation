package com.multiagent.infrastructure.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Skill 展示配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisplayConfig {

    /** 卡片颜色 */
    private String color;

    /** 排序权重 */
    private Integer order;

    /** 是否在首页展示 */
    private Boolean showOnHome;
}
