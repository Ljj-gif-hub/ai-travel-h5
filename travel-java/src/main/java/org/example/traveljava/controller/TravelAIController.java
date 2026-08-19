package org.example.traveljava.controller;

import jakarta.validation.Valid;
import org.example.traveljava.annotation.RateLimit;
import org.example.traveljava.dto.ChatMessage;
import org.example.traveljava.dto.PackingListRequest;
import org.example.traveljava.dto.ScoreRequest;
import org.example.traveljava.dto.TravelPlanDTO;
import org.example.traveljava.service.AIService;
import org.example.traveljava.util.AuthUtils;
import org.example.traveljava.util.JwtUtil;
import org.example.traveljava.vo.Result;
import org.example.traveljava.vo.TravelRecommendVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 行程规划 — AI 对话/规划类接口（与 TravelController 同前缀 /api/travel）。
 * 【重构】从 TravelController 拆出，控制单文件行数；路由与行为不变。
 */
@RestController
@RequestMapping("/api/travel")
public class TravelAIController {

    private static final Logger log = LoggerFactory.getLogger(TravelAIController.class);

    private final AIService aiService;
    private final JwtUtil jwtUtil;

    public TravelAIController(AIService aiService, JwtUtil jwtUtil) {
        this.aiService = aiService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/test-ai")
    @RateLimit(max = 10, duration = 60, key = "travel_test_ai")
    public Result<String> testAI(@RequestHeader("Authorization") String authHeader) {
        AuthUtils.requireUserId(authHeader, jwtUtil);
        log.info("测试AI连接");
        try {
            String testResult = aiService.testConnection();
            return Result.ok(testResult);
        } catch (Exception e) {
            log.error("AI连接测试失败", e);
            return Result.fail("AI连接测试失败");
        }
    }

    @PostMapping("/plan")
    @RateLimit(max = 10, duration = 60, key = "travel_plan")
    public Result<String> generatePlan(@RequestHeader("Authorization") String authHeader, @Valid @RequestBody TravelRecommendVO vo) {
        Long userId = AuthUtils.requireUserId(authHeader, jwtUtil);
        log.info("生成旅行规划请求：目的地={}, 预算={}, 天数={}", vo.getDestination(), vo.getBudget(), vo.getDays());
        long startTime = System.currentTimeMillis();
        try {
            // 【修复】计划缓存按用户隔离，传入 userId
            String plan = aiService.generateTravelPlan(
                    userId,
                    vo.getDestination(),
                    vo.getBudget().longValue(),
                    vo.getDays()
            );
            long costTime = System.currentTimeMillis() - startTime;
            log.info("生成旅行规划完成，耗时={}ms，内容长度={}", costTime, plan.length());
            return Result.ok(plan);
        } catch (Exception e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("生成旅行规划失败，耗时={}ms", costTime, e);
            return Result.fail("生成旅行规划失败");
        }
    }

    @PostMapping(value = "/plan/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(max = 5, duration = 60, key = "travel_plan_stream")
    public Flux<String> streamPlan(@RequestHeader("Authorization") String authHeader, @Valid @RequestBody TravelRecommendVO vo) {
        AuthUtils.requireUserId(authHeader, jwtUtil);
        log.info("流式生成旅行规划请求：目的地={}, 预算={}, 天数={}",
                vo.getDestination(), vo.getBudget(), vo.getDays());

        return aiService.streamTravelPlan(
                        vo.getDestination(),
                        vo.getBudget().longValue(),
                        vo.getDays()
                )
                // L-CTRL-3 修复：data: 前缀交给 produces=text/event-stream 的 SSE 编码器统一补，
                // 此处再手拼 data: 会输出双重前缀 data:data:（同 AgentProxyController 已修的坑）
                .filter(chunk -> chunk != null && !chunk.trim().isEmpty())
                .onErrorResume(e -> {
                    log.error("流式生成旅行规划失败", e);
                    return Flux.just("错误：生成失败，请稍后重试");
                });
    }

    @PostMapping("/chat")
    @RateLimit(max = 20, duration = 60, key = "travel_chat")
    public Result<String> chat(@RequestHeader("Authorization") String authHeader, @RequestBody List<ChatMessage> messages) {
        Long userId = AuthUtils.requireUserId(authHeader, jwtUtil);
        // CTRL-1 修复：null 检查前不能调 messages.size()，body 为 null 即 NPE 500
        if (messages == null || messages.isEmpty()) {
            return Result.fail("消息列表不能为空");
        }
        log.info("聊天请求：消息数={}", messages.size());

        if (messages.size() > 50) {
            return Result.fail("消息数量不能超过50条");
        }

        try {
            // L-AI-2：chat 缓存键带 userId，防止跨用户共享缓存回复
            String response = aiService.chat(userId, messages);
            return Result.ok(response);
        } catch (Exception e) {
            log.error("聊天请求失败", e);
            return Result.fail("聊天请求失败");
        }
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(max = 10, duration = 60, key = "travel_chat_stream")
    public Flux<String> streamChat(@RequestHeader("Authorization") String authHeader, @RequestBody List<ChatMessage> messages) {
        AuthUtils.requireUserId(authHeader, jwtUtil);
        // CTRL-1 修复：null 检查前不能调 messages.size()
        if (messages == null || messages.isEmpty()) {
            return Flux.just("data: 错误：消息列表不能为空\n\n");
        }
        log.info("流式聊天请求：消息数={}", messages.size());

        return aiService.streamChat(messages);
    }

    @PostMapping("/recommend")
    @RateLimit(max = 10, duration = 60, key = "travel_recommend")
    public Result<String> recommend(@RequestHeader("Authorization") String authHeader, @Valid @RequestBody TravelRecommendVO vo) {
        Long userId = AuthUtils.requireUserId(authHeader, jwtUtil);
        log.info("推荐请求：目的地={}, 预算={}, 天数={}, 消息={}",
                vo.getDestination(), vo.getBudget(), vo.getDays(), vo.getMessage());
        try {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.builder()
                    .role("system")
                    .content("你是一个专业的旅游规划助手，擅长提供详细、实用的旅行建议。")
                    .build());

            if (vo.getDestination() != null && vo.getBudget() != null && vo.getDays() != null) {
                String context = String.format("我计划去%s旅游，预算%d元，共%d天。",
                        vo.getDestination(), vo.getBudget(), vo.getDays());
                messages.add(ChatMessage.builder()
                        .role("user")
                        .content(context)
                        .build());
            }

            if (vo.getMessage() != null && !vo.getMessage().isEmpty()) {
                messages.add(ChatMessage.builder()
                        .role("user")
                        .content(vo.getMessage())
                        .build());
            }

            // L-AI-2：chat 缓存键带 userId
            String response = aiService.chat(userId, messages);
            return Result.ok(response);
        } catch (Exception e) {
            log.error("推荐请求失败", e);
            return Result.fail("推荐请求失败");
        }
    }

    @PostMapping("/plan/structured")
    @RateLimit(max = 10, duration = 60, key = "travel_plan_struct")
    public Result<TravelPlanDTO> generateStructuredPlan(@RequestHeader("Authorization") String authHeader, @Valid @RequestBody TravelRecommendVO vo) {
        Long userId = AuthUtils.requireUserId(authHeader, jwtUtil);
        log.info("生成结构化旅行规划请求：目的地={}, 预算={}, 天数={}",
                vo.getDestination(), vo.getBudget(), vo.getDays());
        try {
            // 【修复】计划缓存按用户隔离，传入 userId
            TravelPlanDTO plan = aiService.generateStructuredTravelPlan(
                    userId,
                    vo.getDestination(),
                    vo.getBudget().longValue(),
                    vo.getDays()
            );
            return Result.ok(plan);
        } catch (Exception e) {
            log.error("生成结构化旅行规划失败", e);
            return Result.fail("生成旅行规划失败");
        }
    }

    @GetMapping("/image")
    @RateLimit(max = 30, duration = 60, key = "travel_image")
    public Result<String> getAttractionImage(@RequestHeader("Authorization") String authHeader, @RequestParam String name) {
        AuthUtils.requireUserId(authHeader, jwtUtil);
        log.info("获取景点图片：name={}", name);

        if (name == null || name.trim().isEmpty()) {
            return Result.fail("景点名称不能为空");
        }

        if (name.length() > 100) {
            return Result.fail("景点名称长度不能超过100个字符");
        }

        try {
            String imageUrl = aiService.searchAttractionImage(name);
            return Result.ok(imageUrl);
        } catch (Exception e) {
            log.error("获取景点图片失败", e);
            return Result.fail("获取图片失败");
        }
    }

    /* ==================== 【新功能】AI 打包清单 ==================== */

    /**
     * 生成 5 大类出行打包清单（证件/衣物/电子/药品/其他）。
     * AI 失败时返回内置兜底清单（服务端兜底，保证可用）。
     */
    @PostMapping("/ai/packing-list")
    @RateLimit(max = 5, duration = 60, key = "travel_packing_list")
    public Result<Map<String, Object>> packingList(@RequestHeader("Authorization") String authHeader,
                                                   @RequestBody PackingListRequest request) {
        AuthUtils.requireUserId(authHeader, jwtUtil);
        if (request == null || request.getDestination() == null || request.getDestination().isBlank()) {
            return Result.fail("目的地不能为空");
        }
        int days = request.getDays() != null ? request.getDays() : 3;
        if (days < 1 || days > 60) {
            return Result.fail("出行天数需在 1-60 之间");
        }
        log.info("打包清单请求：destination={}, days={}", request.getDestination(), days);
        try {
            Map<String, Object> list = aiService.generatePackingList(request.getDestination(), days, request.getCompanion());
            return Result.ok(list);
        } catch (Exception e) {
            log.error("生成打包清单失败", e);
            return Result.fail("生成失败，请稍后重试");
        }
    }

    /* ==================== 【新功能】AI 行程评分 ==================== */

    /**
     * 对行程计划评分：5 维度（1-10 分）+ 总体评价 + 3 条建议。
     * AI 服务失败时返回 HTTP 502（区别于业务校验的 400）。
     */
    @PostMapping("/ai/score")
    @RateLimit(max = 5, duration = 60, key = "travel_ai_score")
    public ResponseEntity<Result<Map<String, Object>>> scorePlan(@RequestHeader("Authorization") String authHeader,
                                                                 @RequestBody ScoreRequest request) {
        AuthUtils.requireUserId(authHeader, jwtUtil);
        if (request == null || request.getDestination() == null || request.getDestination().isBlank()) {
            return ResponseEntity.badRequest().body(Result.fail("目的地不能为空"));
        }
        int days = request.getDays() != null ? request.getDays() : 1;
        log.info("行程评分请求：destination={}, days={}", request.getDestination(), days);
        try {
            Map<String, Object> score = aiService.scoreTravelPlan(request.getDestination(), days, request.getPlanContent());
            if (score == null) {
                log.warn("AI 评分返回空");
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(Result.fail("AI 评分服务暂时不可用，请稍后重试"));
            }
            return ResponseEntity.ok(Result.ok(score));
        } catch (Exception e) {
            log.error("行程评分失败", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Result.fail("AI 评分服务暂时不可用，请稍后重试"));
        }
    }
}
