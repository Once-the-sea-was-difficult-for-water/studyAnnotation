package com.multiagent.infrastructure.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Skill 限流配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitConfig {

    /** 每分钟最大调用次数 */
    private Integer maxCallsPerMinute;

    /** 每用户每分钟最大调用次数 */
    private Integer maxCallsPerUserPerMinute;
}
