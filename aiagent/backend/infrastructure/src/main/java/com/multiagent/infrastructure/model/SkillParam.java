package com.multiagent.infrastructure.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 技能输入参数定义。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillParam {

    /** 参数名称 */
    private String name;

    /** 参数类型 */
    private ParamType type;

    /** 是否必填 */
    private boolean required;

    /** 默认值 */
    private Object defaultValue;

    /** 参数描述 */
    private String description;

    /** 枚举可选值（type 为 ENUM 时使用） */
    private List<String> enumValues;
}
