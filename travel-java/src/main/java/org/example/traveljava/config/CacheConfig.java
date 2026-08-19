package org.example.traveljava.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 【新功能】统一本地缓存层（Caffeine）。
 *
 * 缓存定义：
 *  - ai-plan     AI 行程规划结果（缓存键含 userId，按用户隔离）：1000 条 / 1 小时
 *  - ai-chat     AI 聊天结果：500 条 / 10 分钟
 *  - ai-image    景点图片 URL：2000 条 / 10 分钟（L-AI-3 修复：独立缓存，用户可控的景点名不再挤掉行程缓存）
 *  - city-image  城市图片 URL：2000 条 / 1 小时
 *
 * 替代 AIService 的手工 planCache/chatCache 与 CityService 的 synchronizedMap，
 * 容量/过期策略集中管理、可观测（spring cache 统一入口）。
 */
@Configuration
public class CacheConfig {

    public static final String CACHE_AI_PLAN = "ai-plan";
    public static final String CACHE_AI_CHAT = "ai-chat";
    public static final String CACHE_AI_IMAGE = "ai-image";
    public static final String CACHE_CITY_IMAGE = "city-image";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache(CACHE_AI_PLAN, Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build());
        manager.registerCustomCache(CACHE_AI_CHAT, Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build());
        // L-AI-3 修复：景点图独立缓存，短 TTL + 独立容量，防止用户可控键污染行程缓存
        manager.registerCustomCache(CACHE_AI_IMAGE, Caffeine.newBuilder()
                .maximumSize(2000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build());
        manager.registerCustomCache(CACHE_CITY_IMAGE, Caffeine.newBuilder()
                .maximumSize(2000)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build());
        return manager;
    }
}
