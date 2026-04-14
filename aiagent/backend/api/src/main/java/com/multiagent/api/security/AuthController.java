package com.multiagent.api.security;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器 — 提供 JWT Token 签发端点。
 * <p>
 * POST /api/auth/token — 根据 userId 签发 JWT Token，
 * 供前端 WebSocket 握手时在 STOMP connect headers 中传递。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtTokenProvider tokenProvider;

    public AuthController(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @PostMapping("/token")
    public ResponseEntity<Map<String, String>> issueToken(@RequestBody Map<String, String> body) {
        String userId = body.get("userId");
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId is required"));
        }

        String token = tokenProvider.generateToken(userId);
        return ResponseEntity.ok(Map.of(
                "token", token,
                "userId", userId
        ));
    }
}
