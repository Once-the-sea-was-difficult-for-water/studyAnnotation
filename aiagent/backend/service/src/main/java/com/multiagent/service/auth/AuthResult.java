package com.multiagent.service.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 授权结果 — 分级授权网关的返回值。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResult {

    /** 是否批准 */
    private boolean approved;

    /** 结果说明 */
    private String reason;

    /** 创建一个批准结果 */
    public static AuthResult approved(String reason) {
        return AuthResult.builder()
                .approved(true)
                .reason(reason)
                .build();
    }

    /** 创建一个拒绝结果 */
    public static AuthResult denied(String reason) {
        return AuthResult.builder()
                .approved(false)
                .reason(reason)
                .build();
    }
}
