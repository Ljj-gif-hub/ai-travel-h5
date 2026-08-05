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
     * 提取裸 token（去掉 "Bearer " 前缀），校验失败抛 AuthException
     */
    private static String extractToken(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            throw new AuthException("请先登录");
        }
        String token = authHeader.startsWith("Bearer ")
                ? authHeader.substring(7).trim()
                : authHeader.trim();
        if (token.isEmpty()) {
            throw new AuthException("请先登录");
        }
        return token;
    }

    /**
     * 可选登录：能提取出用户则返回 userId，未登录 / token 无效返回 null（不抛异常）。
     * 用于"登录个性化、未登录给通用结果"的接口（如推荐）。
     */
    public static Long optionalUserId(String authHeader, JwtUtil jwtUtil) {
        try {
            return requireUserId(authHeader, jwtUtil);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 管理员鉴权 — 先校验登录，再校验 JWT 中的 role claim 必须为 ADMIN
     * @return 当前管理员用户 ID
     * @throws AuthException 未登录或非管理员时抛出
     */
    public static Long requireAdmin(String authHeader, JwtUtil jwtUtil) {
        Long userId = requireUserId(authHeader, jwtUtil);
        String token = extractToken(authHeader);
        String role = jwtUtil.extractClaim(token, claims -> claims.get("role", String.class));
        if (!"ADMIN".equals(role)) {
            throw new ForbiddenException("无权限执行该操作");
        }
        return userId;
    }

    /**
     * 权限不足异常 — 已登录但角色无权（区别于未登录的 AuthException）
     */
    public static class ForbiddenException extends RuntimeException {
        public ForbiddenException(String message) {
            super(message);
        }
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
