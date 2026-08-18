package org.example.traveljava.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.example.traveljava.annotation.RateLimit;
import org.example.traveljava.util.AuthUtils;
import org.example.traveljava.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import io.netty.channel.ChannelOption;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Agent 微服务透传代理
 *
 * 将 /api/agent/** 的请求转发到 Python Agent 服务（默认 localhost:3201）
 * 支持：
 *   - 同步请求透传（JSON → JSON）
 *   - SSE 流式透传（SSE → SSE，保持实时推送）
 *   - Agent 服务不可用时自动降级
 *
 * 架构：
 *   前端 → Spring Boot (3200) → Agent Proxy → Python Agent (3201)
 */
@RestController
@RequestMapping("/api/agent")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Agent")
public class AgentProxyController {

    private static final Logger log = LoggerFactory.getLogger(AgentProxyController.class);

    private final WebClient agentWebClient;
    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;

    /** Agent 服务地址（默认 localhost:3201） */
    @Value("${agent.service.url:http://localhost:3201}")
    private String agentServiceUrl;

    /** 与 Agent 服务约定的共享密钥（透传时附加 X-Agent-Key，防绕过本服务直接调用） */
    @Value("${app.agent.api-key:}")
    private String agentApiKey;

    public AgentProxyController(ObjectMapper objectMapper, JwtUtil jwtUtil, WebClient.Builder webClientBuilder) {
        this.objectMapper = objectMapper;
        this.jwtUtil = jwtUtil;
        // 注入 Spring 托管的 WebClient.Builder：自带连接池 + 连接超时。
        // 不设全局响应超时——SSE 长流会被它掐断；同步调用在各请求上单独 .timeout()。
        HttpClient httpClient = HttpClient.create(ConnectionProvider.builder("agent-pool")
                        .maxConnections(50)
                        .pendingAcquireMaxCount(100)
                        .pendingAcquireTimeout(Duration.ofSeconds(10))
                        .build())
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);
        this.agentWebClient = webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(config -> config.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
    }

    /**
     * 用共享密钥对 userId 做 HMAC-SHA256 签名（hex），Agent 端据此绑定用户身份，
     * 防伪造 user_id 越权读写他人长期记忆。
     */
    private static String hmacUserSig(String key, String userId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(userId.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC 签名失败", e);
        }
    }

    /**
     * 透传请求的鉴权头：
     * - 配置了共享密钥：附加 X-Agent-Key + HMAC 用户绑定（X-User-Id/X-User-Sig），
     *   不透传用户 JWT（用户身份由本服务校验后以签名形式重新签发）。
     * - 未配置共享密钥：回退透传原 Authorization，由 Agent 端自行校验 JWT。
     */
    private Consumer<HttpHeaders> agentAuthHeaders(String authHeader, Long userId) {
        return headers -> {
            if (agentApiKey != null && !agentApiKey.isBlank()) {
                headers.set("X-Agent-Key", agentApiKey);
                if (userId != null) {
                    headers.set("X-User-Id", String.valueOf(userId));
                    headers.set("X-User-Sig", hmacUserSig(agentApiKey, String.valueOf(userId)));
                }
            } else if (authHeader != null && !authHeader.isBlank()) {
                headers.set("Authorization", authHeader);
            }
        };
    }

    /**
     * 健康检查 — 检测 Agent 服务是否可用
     */
    @GetMapping("/health")
    public Map<String, Object> agentHealth() {
        try {
            String response = agentWebClient.get()
                    .uri(agentServiceUrl + "/api/agent/health")
                    .headers(h -> {
                        if (agentApiKey != null && !agentApiKey.isBlank()) h.set("X-Agent-Key", agentApiKey);
                    })
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            Map<String, Object> result = objectMapper.readValue(response, Map.class);
            result.put("proxyStatus", "ok");
            return result;
        } catch (Exception e) {
            log.warn("Agent 服务不可用: {} — {}", agentServiceUrl, e.getMessage());
            return Map.of(
                "proxyStatus", "error",
                "message", "Agent 服务不可用"
            );
        }
    }

    /**
     * 同步生成行程 — 透传到 Agent 服务（需登录）
     */
    @PostMapping("/plan")
    @RateLimit(max = 20, duration = 60, key = "agent_plan")
    public Map<String, Object> generatePlanSync(@RequestHeader("Authorization") String authHeader,
                                                @RequestBody Map<String, Object> body) {
        Long userId = AuthUtils.requireUserId(authHeader, jwtUtil);
        log.info("Agent 同步规划: {}", body.getOrDefault("destination", "unknown"));

        try {
            String response = agentWebClient.post()
                    .uri(agentServiceUrl + "/api/agent/plan")
                    .headers(agentAuthHeaders(authHeader, userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    // 超时兜底，避免同步代理长时间占用 Tomcat 线程
                    .timeout(Duration.ofSeconds(120))
                    .block();

            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(response, Map.class);
            return result;

        } catch (Exception e) {
            log.error("Agent 同步规划失败", e);
            // 安全：不向外透传内部异常细节
            return Map.of(
                "code", -1,
                "message", "Agent 规划服务暂时不可用，请稍后重试"
            );
        }
    }

    /**
     * SSE 流式生成行程 — 透传 SSE 流
     *
     * 前端可以直接连接此端点，Agent 的每一步思考过程都会实时推送
     */
    @PostMapping(value = "/plan/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(max = 20, duration = 60, key = "agent_plan")
    public Flux<String> generatePlanStream(@RequestHeader("Authorization") String authHeader,
                                           @RequestBody Map<String, Object> body,
                                           HttpServletResponse response) {
        Long userId = AuthUtils.requireUserId(authHeader, jwtUtil);
        String dest = (String) body.getOrDefault("destination", "unknown");
        log.info("Agent SSE 流式规划: {}", dest);

        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("X-Accel-Buffering", "no");
        // CORS 由全局 WebConfig 白名单统一处理（不在此硬编码 *）

        try {
            return agentWebClient.post()
                    .uri(agentServiceUrl + "/api/agent/plan/stream-sse")
                    .headers(agentAuthHeaders(authHeader, userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .retrieve()
                    .bodyToFlux(String.class)
                    // 只透传裸 JSON，data: 前缀由本接口 produces=text/event-stream 的
                    // SSE 编码器统一补一个（此处再拼 data: 会输出双重前缀 data:data:，
                    // 前端 JSON.parse 失败，永久卡在"正在连接 AI Agent"）。
                    .filter(chunk -> chunk != null && !chunk.trim().isEmpty())
                    .map(String::trim)
                    .onErrorResume(e -> {
                        log.error("Agent SSE 流异常", e);
                        // 安全：不透传内部异常；裸 JSON，前缀交给 SSE 编码器补
                        return Flux.just("{\"event_type\":\"error\",\"message\":\"Agent 服务异常，请稍后重试\"}");
                    });
        } catch (Exception e) {
            log.error("Agent SSE 连接失败", e);
            return Flux.just(
                "{\"event_type\":\"error\",\"message\":\"无法连接Agent服务\"}"
            );
        }
    }
}
