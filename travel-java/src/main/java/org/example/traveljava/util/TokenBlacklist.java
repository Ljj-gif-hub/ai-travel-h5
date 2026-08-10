package org.example.traveljava.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * JWT 注销黑名单（内存实现，Caffeine）。
 * 退出登录时把 token 的 SHA-256 哈希加入黑名单，直到 token 自然过期。
 *
 * 单实例部署足够；多实例/分布式部署建议改用 Redis（键：tokenhash，TTL=剩余有效期）。
 */
@Component
public class TokenBlacklist {

    /** tokenHash → true；统一按最长 24h 过期（token 实际更早失效，安全余量足够） */
    private final Cache<String, Boolean> blacklist = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofHours(24))
            .build();

    /** 加入黑名单：token 剩余有效期决定实际失效窗口（1s~24h） */
    public void blacklist(String token, long ttlMillis) {
        if (token == null || token.isBlank()) {
            return;
        }
        blacklist.put(hash(token), Boolean.TRUE);
    }

    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return blacklist.getIfPresent(hash(token)) != null;
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
