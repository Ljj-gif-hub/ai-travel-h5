package org.example.traveljava.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统一认证工具 — 所有 Controller 用同一个方法提取当前用户
 * 杜绝各 Controller 各自手写 token 解析导致的不一致/漏校验问题
 */
public final class AuthUtils {

    private static final Logger log = LoggerFactory.getLogger(AuthUtils.class);

    private AuthUtils() {}

    /**
     * 从 Authorization 请求头提取当前登录用户 ID
     * @param authHeader  HTTP 请求头 "Bearer xxx"
     * @param jwtUtil     JWT 工具实例
     * @return 当前登录用户 ID
     * @throws AuthException 未登录或 token 无效时抛出
     */
    public static Long requireUserId(String authHeader, JwtUtil jwtUtil) {
        if (authHeader == null || authHeader.isBlank()) {
            throw new AuthException("请先登录");
        }
        String token;
        if (authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        } else {
            token = authHeader.trim();
        }
        if (token.isEmpty()) {
            throw new AuthException("请先登录");
        }
        if (!jwtUtil.validateToken(token)) {
            throw new AuthException("登录已过期，请重新登录");
        }
        Long userId = jwtUtil.extractUserId(token);
        if (userId == null) {
            throw new AuthException("Token 无效：缺少用户标识");
        }
        log.debug("认证用户：userId={}", userId);
        return userId;
    }

    /**
     * 身份校验失败异常
     */
    public static class AuthException extends RuntimeException {
        public AuthException(String message) {
            super(message);
        }
    }
}
