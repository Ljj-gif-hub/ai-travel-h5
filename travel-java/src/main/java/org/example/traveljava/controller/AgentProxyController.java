package org.example.traveljava.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.example.traveljava.annotation.RateLimit;
import org.example.traveljava.util.AuthUtils;
import org.example.traveljava.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.Map;

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
public class AgentProxyController {

    private static final Logger log = LoggerFactory.getLogger(AgentProxyController.class);

    private final WebClient agentWebClient;
    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;

    /** Agent 服务地址（默认 localhost:3201） */
    @Value("${agent.service.url:http://localhost:3201}")
    private String agentServiceUrl;

    public AgentProxyController(ObjectMapper objectMapper, JwtUtil jwtUtil) {
        this.objectMapper = objectMapper;
        this.jwtUtil = jwtUtil;
        this.agentWebClient = WebClient.builder()
                .codecs(config -> config.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
    }

    /**
     * 健康检查 — 检测 Agent 服务是否可用
     */
    @GetMapping("/health")
    public Map<String, Object> agentHealth() {
        try {
            String response = agentWebClient.get()
                    .uri(agentServiceUrl + "/api/agent/health")
                    .retrieve()
                    .bodyToMono(String.class)
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
        AuthUtils.requireUserId(authHeader, jwtUtil);
        log.info("Agent 同步规划: {}", body.getOrDefault("destination", "unknown"));

        try {
            String response = agentWebClient.post()
                    .uri(agentServiceUrl + "/api/agent/plan")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(response, Map.class);
            return result;

        } catch (Exception e) {
            log.error("Agent 同步规划失败: {}", e.getMessage());
            return Map.of(
                "code", -1,
                "message", "Agent 规划服务暂时不可用: " + e.getMessage()
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
        AuthUtils.requireUserId(authHeader, jwtUtil);
        String dest = (String) body.getOrDefault("destination", "unknown");
        log.info("Agent SSE 流式规划: {}", dest);

        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Access-Control-Allow-Origin", "*");

        try {
            return agentWebClient.post()
                    .uri(agentServiceUrl + "/api/agent/plan/stream-sse")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .map(chunk -> {
                        // Spring 的 SSE reader 会剥掉 data: 前缀（chunk 是裸 JSON），这里补回，保证前端能解析
                        if (chunk != null && !chunk.trim().isEmpty()) {
                            return "data: " + chunk.trim() + "\n\n";
                        }
                        return "";
                    })
                    .onErrorResume(e -> {
                        log.error("Agent SSE 流异常: {}", e.getMessage());
                        return Flux.just("data: {\"event_type\":\"error\",\"message\":\"Agent服务异常: " +
                                e.getMessage().replace("\"", "\\\"") + "\"}\n\n");
                    });
        } catch (Exception e) {
            log.error("Agent SSE 连接失败: {}", e.getMessage());
            return Flux.just(
                "data: {\"event_type\":\"error\",\"message\":\"无法连接Agent服务\"}\n\n"
            );
        }
    }
}
