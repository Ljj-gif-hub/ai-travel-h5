package org.example.traveljava.service;

import org.example.traveljava.entity.User;
import org.example.traveljava.repository.UserRepository;
import org.example.traveljava.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

/**
 * 刷新令牌服务（新功能：AccessToken 24h + RefreshToken 7d 双令牌体系）。
 *
 * 存储：Redis key=refresh:sha256(token)，value=userId，TTL=7 天（可配 jwt.refresh-expiration）。
 * 只存哈希：Redis 泄露不直接暴露可用令牌（与 TokenBlacklist 同策略）。
 *
 * 旋转刷新：每次刷新先删除旧令牌、再签发新令牌——旧令牌被窃取后至多可用到
 * 下一次刷新，重放会因「先删后查」而失败。
 *
 * 安全语义：Redis 不可用时刷新一律失败（fail-closed）。刷新令牌无法在本地
 * 缓存验证（多实例 + 旋转语义），降级放行会直接击穿旋转机制，故宁可让用户
 * 重新登录也不放行。登录签发的降级策略相反（见 UserService.login：Redis 故障
 * 时仍允许登录，只是不发 refreshToken，前端走纯 access token 模式）。
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final String KEY_PREFIX = "refresh:";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 刷新令牌有效期，默认 7 天（毫秒） */
    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    public RefreshTokenService(StringRedisTemplate redisTemplate, UserRepository userRepository, JwtUtil jwtUtil) {
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    /** 签发新刷新令牌并入库（登录时调用；Redis 故障时向上抛，由调用方降级） */
    public String issue(Long userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes); // 64 位 hex，SecureRandom 强随机
        redisTemplate.opsForValue().set(KEY_PREFIX + hash(token), String.valueOf(userId),
                Duration.ofMillis(refreshExpiration));
        return token;
    }

    /**
     * 旋转刷新：验证旧令牌 → 删除旧令牌（防重放）→ 校验用户 → 签发新令牌对。
     * 旧令牌无效/过期、用户不存在或被禁用、Redis 故障均抛 IllegalArgumentException，
     * 由控制器统一转为业务失败提示。
     */
    public Map<String, Object> refresh(String oldToken) {
        if (oldToken == null || oldToken.isBlank()) {
            throw new IllegalArgumentException("刷新令牌不能为空");
        }
        String key = KEY_PREFIX + hash(oldToken);
        try {
            // 旋转：GETDEL 原子「取并删」（Redis 6.2+，服务器为 redis:7-alpine）。
            // 并发重放同一旧令牌时只有一个请求能取到值，另一个读 null 即失败——
            // 杜绝「GET 与 DELETE 之间被竞态穿插」导致两份新令牌同时生效。
            String userIdStr = redisTemplate.opsForValue().getAndDelete(key);
            if (userIdStr == null) {
                throw new IllegalArgumentException("刷新令牌无效或已过期");
            }
            Long userId = Long.valueOf(userIdStr);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("账号不存在，请重新登录"));
            if (user.getStatus() != 1) {
                throw new IllegalArgumentException("账号已被禁用");
            }
            String newToken = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
            String newRefreshToken = issue(userId);
            log.info("刷新令牌旋转成功：userId={}", userId);
            return Map.of("token", newToken, "refreshToken", newRefreshToken);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("刷新令牌服务异常（Redis 不可用？）", e);
            throw new IllegalArgumentException("刷新服务暂不可用，请稍后重试");
        }
    }

    /** 撤销刷新令牌（退出登录时调用；Redis 故障不阻断退出） */
    public void revoke(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        try {
            redisTemplate.delete(KEY_PREFIX + hash(refreshToken));
        } catch (Exception e) {
            log.warn("撤销刷新令牌失败: {}", e.getMessage());
        }
    }

    private String hash(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return token; // 不应发生；兜底用原文
        }
    }
}
