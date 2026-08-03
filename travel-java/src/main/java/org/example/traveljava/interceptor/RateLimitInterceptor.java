package org.example.traveljava.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.traveljava.annotation.RateLimit;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);
    private static volatile boolean redisWarned = false;  // 首次 Redis 不可用时记录

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    /** 是否信任反向代理注入的 X-Real-IP / X-Forwarded-For（仅当部署在受信代理后时置 true） */
    @Value("${app.rate-limit.trust-proxy:false}")
    private boolean trustProxy;

    public RateLimitInterceptor(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return true;
        }

        String clientIp = getClientIp(request);
        String key = buildKey(request, rateLimit, clientIp);
        
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == 1) {
                redisTemplate.expire(key, rateLimit.duration(), TimeUnit.SECONDS);
            }
            
            if (count > rateLimit.max()) {
                log.warn("接口限流触发: ip={}, key={}, count={}, max={}", clientIp, key, count, rateLimit.max());
                sendErrorResponse(response, 429, "请求过于频繁，请稍后重试");
                return false;
            }
        } catch (Exception e) {
            if (!redisWarned) {
                redisWarned = true;
                log.debug("Redis未连接，跳过限流检查");
            }
        }
        
        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip;
        if (trustProxy) {
            // 受信代理后：优先 X-Real-IP（由网关设置），再退化为 remoteAddr，绝不信任可伪造的 X-Forwarded-For 首段
            ip = request.getHeader("X-Real-IP");
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
        } else {
            // 直连场景：客户端可伪造 X-Forwarded-For，直接用 TCP 对端地址，防止限流绕过
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String buildKey(HttpServletRequest request, RateLimit rateLimit, String clientIp) {
        String key = rateLimit.key();
        if (key.isEmpty()) {
            key = request.getRequestURI();
        }
        return "rate_limit:" + key + ":" + clientIp;
    }

    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        Result<String> result = Result.fail(message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
