package org.example.traveljava.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.traveljava.util.AuthUtils;
import org.example.traveljava.util.JwtUtil;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 【新功能】AI 用量配额拦截器：基于 Redis 的按用户按天限流。
 *
 * - 配额键：quota:ai:{userId}:{yyyyMMdd}
 * - agent 类接口（/api/agent/**）每日配额 app.quota.agent-daily（默认 20）
 * - travel AI 类接口（/api/travel/ai/**、/api/travel/stream/**）每日配额 app.quota.ai-daily（默认 30）
 * - 超限返回 429：「今日 AI 用量已达上限，请明天再试」
 * - fail-open：未登录或 Redis 不可用时放行（不因基础设施故障阻断业务）
 * - 计数原子性：INCR + 首次置过期（当天 23:59:59 之后过期即可，取 24h 简化）
 */
@Component
public class AiQuotaInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AiQuotaInterceptor.class);
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String QUOTA_KEY_PREFIX = "quota:ai:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;

    @Value("${app.quota.agent-daily:20}")
    private long agentDailyQuota;

    @Value("${app.quota.ai-daily:30}")
    private long aiDailyQuota;

    public AiQuotaInterceptor(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper,
                              JwtUtil jwtUtil) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();

        long quota;
        if (path.startsWith("/api/agent/")) {
            quota = agentDailyQuota;
        } else if (path.startsWith("/api/travel/ai/") || path.startsWith("/api/travel/stream/")) {
            quota = aiDailyQuota;
        } else {
            // 非 AI 接口不校验配额
            return true;
        }

        // fail-open：未登录不校验（AI 接口自身会鉴权）
        Long userId;
        try {
            userId = AuthUtils.requireUserId(request.getHeader("Authorization"), jwtUtil);
        } catch (Exception e) {
            return true;
        }

        String key = QUOTA_KEY_PREFIX + userId + ":" + LocalDate.now().format(DAY_FORMAT);
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == 1) {
                // 当天结束前过期即可，24h 简化处理
                redisTemplate.expire(key, 24, TimeUnit.HOURS);
            }
            if (count > quota) {
                log.warn("AI 配额超限: userId={}, path={}, count={}, quota={}", userId, path, count, quota);
                sendErrorResponse(response, 429, "今日 AI 用量已达上限，请明天再试");
                return false;
            }
        } catch (Exception e) {
            // fail-open：Redis 故障时放行，不阻断业务
            log.warn("AI 配额检查异常，按 fail-open 放行: {}", e.getMessage());
        }
        return true;
    }

    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        Result<String> result = Result.fail(message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
