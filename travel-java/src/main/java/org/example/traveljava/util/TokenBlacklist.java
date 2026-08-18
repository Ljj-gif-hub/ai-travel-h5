package org.example.traveljava.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * JWT 注销黑名单：Redis 存储（key=blacklist:sha256(token)，TTL=剩余有效期），
 * 多实例共享，重启不丢。
 *
 * 优雅降级：Redis 不可用时回退到本地 Caffeine 内存实现（单实例兜底），
 * 接口语义不变（blacklist / isBlacklisted），调用方（JwtUtil）无感知。
 */
@Component
public class TokenBlacklist {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklist.class);
    private static final String KEY_PREFIX = "blacklist:";

    private final StringRedisTemplate redisTemplate;

    /** Redis 不可用时的本地降级缓存：key → 绝对过期时间戳（epoch millis） */
    private final Cache<String, Long> fallback = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofDays(30))
            .build();

    public TokenBlacklist(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 加入黑名单：按 token 剩余有效期（ttlMillis）设置 Redis TTL */
    public void blacklist(String token, long ttlMillis) {
        if (token == null || token.isBlank()) {
            return;
        }
        if (ttlMillis <= 0) {
            // 与内存实现的语义一致：无剩余有效期的 token 立即过期，无需入黑名单
            return;
        }
        String key = KEY_PREFIX + hash(token);
        try {
            redisTemplate.opsForValue().set(key, "1", Duration.ofMillis(ttlMillis));
        } catch (Exception e) {
            log.warn("Redis 不可用，TokenBlacklist 降级内存缓存: err={}", e.getMessage());
            fallback.put(key, System.currentTimeMillis() + ttlMillis);
        }
    }

    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String key = KEY_PREFIX + hash(token);
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            // Redis 不可用：回退查本地降级缓存
            Long expiresAt = fallback.getIfPresent(key);
            return expiresAt != null && expiresAt > System.currentTimeMillis();
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
