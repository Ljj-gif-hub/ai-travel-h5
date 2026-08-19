package org.example.traveljava.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.traveljava.annotation.RateLimit;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);
    private static volatile boolean redisWarned = false;  // 首次 Redis 不可用时记录

    /**
     * 【RATE-2 修复】Lua 脚本：INCR + EXPIRE 原子执行，避免 INCR 成功后 EXPIRE 异常导致 key 永不过期。
     * 返回计数后的值（1 表示首次创建并已设过期）。
     */
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;
    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setScriptText(
            "local count = redis.call('INCR', KEYS[1]) " +
            "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
            "return count"
        );
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 是否信任反向代理注入的 X-Real-IP / X-Forwarded-For（仅当部署在受信代理后时置 true） */
    @Value("${app.rate-limit.trust-proxy:false}")
    private boolean trustProxy;

    /** Redis 不可用时是否放行（fail-open=true）还是拒绝请求（fail-closed=false，默认）。默认拒绝以保住限流保护。 */
    @Value("${app.rate-limit.fail-open:false}")
    private boolean failOpen;

    public RateLimitInterceptor(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 【RATE-1 修复】启动时检查 trust-proxy 配置，若为 false 则提醒线上反代场景需开启。
     */
    @PostConstruct
    public void warnTrustProxy() {
        if (!trustProxy) {
            log.warn("⚠️ app.rate-limit.trust-proxy=false：若部署在 nginx 等反向代理后，所有请求 IP 将合并为代理 IP，" +
                    "全站共享同一限流桶。线上环境请设置 RATE_LIMIT_TRUST_PROXY=true");
        }
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
            // RATE-2 修复：用 Lua 脚本原子执行 INCR+EXPIRE，避免 INCR 成功后 EXPIRE 失败导致 key 永不过期
            Long count = redisTemplate.execute(RATE_LIMIT_SCRIPT, Collections.singletonList(key),
                    String.valueOf(rateLimit.duration()));
            
            if (count != null && count > rateLimit.max()) {
                log.warn("接口限流触发: ip={}, key={}, count={}, max={}", clientIp, key, count, rateLimit.max());
                sendErrorResponse(response, 429, "请求过于频繁，请稍后重试");
                return false;
            }
        } catch (Exception e) {
            if (!redisWarned) {
                redisWarned = true;
                log.warn("Redis 不可用，限流检查异常（{}），按 fail-open={} 处理", e.getMessage(), failOpen);
            }
            // 默认 fail-closed：Redis 不可用时拒绝受限流保护的接口，防止限流保护失效被刷量
            if (!failOpen) {
                sendErrorResponse(response, 429, "服务繁忙，请稍后重试");
                return false;
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
