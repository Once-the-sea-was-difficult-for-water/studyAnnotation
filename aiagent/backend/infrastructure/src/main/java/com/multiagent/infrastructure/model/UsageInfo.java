package com.multiagent.infrastructure.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token 用量信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageInfo {

    /** 输入 token 数 */
    private int inputTokens;

    /** 输出 token 数 */
    private int outputTokens;

    /** 总 token 数 */
    private int totalTokens;
}
