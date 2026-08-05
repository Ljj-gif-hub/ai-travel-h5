package org.example.traveljava.controller;

import org.example.traveljava.dto.ChatMessage;
import org.example.traveljava.dto.SavedPlanRequest;
import org.example.traveljava.service.AIService;
import org.example.traveljava.service.SavedTravelPlanService;
import org.example.traveljava.util.AuthUtils;
import org.example.traveljava.util.JwtUtil;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;

/**
 * 行程模块专属 AI 控制器
 * 复用现有 AIService + SavedTravelPlanService + JwtUtil
 */
@RestController
@RequestMapping("/api/trip/ai")
@io.swagger.v3.oas.annotations.tags.Tag(name = "行程规划")
public class TripAIController {

    private static final Logger log = LoggerFactory.getLogger(TripAIController.class);
    private final AIService aiService;
    private final SavedTravelPlanService planService;
    private final JwtUtil jwtUtil;

    public TripAIController(AIService aiService, SavedTravelPlanService planService, JwtUtil jwtUtil) {
        this.aiService = aiService;
        this.planService = planService;
        this.jwtUtil = jwtUtil;
    }

    // ==================== 1. AI 智能生成行程 ====================
    @PostMapping("/generateTrip")
    public Result<Map<String, Object>> generateTrip(@RequestHeader("Authorization") String authHeader,
                                                     @RequestBody Map<String, Object> body) {
        AuthUtils.requireUserId(authHeader, jwtUtil); // 校验登录
        String dest = (String) body.getOrDefault("destination", "");
        int days = ((Number) body.getOrDefault("days", 3)).intValue();
        String origin = (String) body.getOrDefault("origin", "深圳");
        String style = (String) body.getOrDefault("style", "综合");

        String prompt = String.format(
            "为从%s出发去%s的%d天旅行生成完整行程，偏好：%s。生成JSON数组，每天包含3时段(上午/下午/晚上)的景点、活动、交通、住宿。%s",
            origin, dest, days, style, "纯JSON格式，字段：day,dayTitle,timeSlots[{timeOfDay,time,attraction,activity,duration,cost,transport,tips}]");

        try {
            String result = aiService.chat(Arrays.asList(
                ChatMessage.builder().role("system").content("你是旅行规划师，只输出JSON。").build(),
                ChatMessage.builder().role("user").content(prompt).build()
            ));
            Map<String, Object> resp = new HashMap<>();
            resp.put("destination", dest);
            resp.put("days", days);
            resp.put("rawPlan", result);
            return Result.ok(resp);
        } catch (Exception e) {
            log.error("AI生成行程失败", e);
            return Result.fail("生成失败：" + e.getMessage());
        }
    }

    // ==================== 2. AI 顺路优化 ====================
    @PostMapping("/optimizeRoute")
    public Result<Map<String, Object>> optimizeRoute(@RequestHeader("Authorization") String authHeader,
                                                      @RequestBody Map<String, Object> body) {
        AuthUtils.requireUserId(authHeader, jwtUtil);
        Long planId = body.get("planId") != null ? ((Number) body.get("planId")).longValue() : null;

        String prompt = "以下是行程数据：" + body.getOrDefault("tripData", "").toString() +
            "。请根据景点地理位置和开放时间重新排序每天行程，使路线最顺路。返回优化后的JSON。";

        try {
            String result = aiService.chat(Arrays.asList(
                ChatMessage.builder().role("system").content("你是路线优化师，只输出优化后的JSON。").build(),
                ChatMessage.builder().role("user").content(prompt).build()
            ));
            Map<String, Object> resp = new HashMap<>();
            resp.put("optimized", result);
            resp.put("planId", planId);
            return Result.ok(resp);
        } catch (Exception e) {
            return Result.fail("优化失败：" + e.getMessage());
        }
    }

    // ==================== 3. AI 行程问答（SSE流式） ====================
    @PostMapping("/chat")
    public Result<String> tripChat(@RequestHeader("Authorization") String authHeader,
                                    @RequestBody Map<String, Object> body) {
        AuthUtils.requireUserId(authHeader, jwtUtil);
        String question = (String) body.getOrDefault("question", "");
        String context = (String) body.getOrDefault("tripContext", "");
        List<Map<String, String>> history = (List<Map<String, String>>) body.getOrDefault("history", List.of());

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.builder().role("system").content(
            "你是旅行助理。当前行程上下文：" + (context.isEmpty() ? "无" : context) + "。根据此上下文回答用户问题。").build());
        for (Map<String, String> h : history) {
            messages.add(ChatMessage.builder().role(h.get("role")).content(h.get("content")).build());
        }
        messages.add(ChatMessage.builder().role("user").content(question).build());

        try {
            String reply = aiService.chat(messages);
            return Result.ok(reply);
        } catch (Exception e) {
            return Result.fail("AI回复失败：" + e.getMessage());
        }
    }

    // ==================== 4. AI 生成出行备注 ====================
    @PostMapping("/generateRemark")
    public Result<Map<String, Object>> generateRemark(@RequestHeader("Authorization") String authHeader,
                                                       @RequestBody Map<String, Object> body) {
        AuthUtils.requireUserId(authHeader, jwtUtil);
        String dest = (String) body.getOrDefault("destination", "");
        String type = (String) body.getOrDefault("type", "城市");

        String prompt = String.format(
            "为%s旅行（类型：%s）生成5条出行备注提醒。包含防晒、穿搭、避坑、蚊虫、饮食等方面。返回JSON:{\"remarks\":[\"备注1\",\"备注2\",...]}", dest, type);

        try {
            String result = aiService.chat(Arrays.asList(
                ChatMessage.builder().role("system").content("只输出JSON，不要其他文字。").build(),
                ChatMessage.builder().role("user").content(prompt).build()
            ));
            Map<String, Object> resp = new HashMap<>();
            resp.put("remarks", result);
            return Result.ok(resp);
        } catch (Exception e) {
            return Result.fail("生成失败：" + e.getMessage());
        }
    }

    // ==================== 5. AI 旅行灵感 ====================
    @PostMapping("/travelInspiration")
    public Result<Map<String, Object>> travelInspiration(@RequestHeader("Authorization") String authHeader,
                                                          @RequestBody Map<String, Object> body) {
        AuthUtils.requireUserId(authHeader, jwtUtil);
        String dest = (String) body.getOrDefault("destination", "");
        int days = ((Number) body.getOrDefault("days", 3)).intValue();

        String prompt = String.format(
            "为%s%d天旅行生成旅行灵感，包含：美食推荐、小众景点、本地文化体验。返回JSON:{\"food\":[\"美食1\"],\"spots\":[\"景点1\"],\"culture\":[\"文化1\"]}",
            dest, days);

        try {
            String result = aiService.chat(Arrays.asList(
                ChatMessage.builder().role("system").content("只输出JSON。").build(),
                ChatMessage.builder().role("user").content(prompt).build()
            ));
            Map<String, Object> resp = new HashMap<>();
            resp.put("inspiration", result);
            return Result.ok(resp);
        } catch (Exception e) {
            return Result.fail("生成失败：" + e.getMessage());
        }
    }

    // ==================== 6. 行程问答 SSE 流式 ====================
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter tripChatStream(@RequestHeader("Authorization") String authHeader,
                                      @RequestBody Map<String, Object> body) {
        AuthUtils.requireUserId(authHeader, jwtUtil);
        String question = (String) body.getOrDefault("question", "");
        String context = (String) body.getOrDefault("tripContext", "");

        SseEmitter emitter = new SseEmitter(120_000L);
        List<ChatMessage> messages = Arrays.asList(
            ChatMessage.builder().role("system").content("你是旅行助理。行程上下文：" + (context.isEmpty() ? "无" : context)).build(),
            ChatMessage.builder().role("user").content(question).build()
        );

        aiService.streamChat(messages)
            .subscribe(
                chunk -> {
                    try { emitter.send(SseEmitter.event().data(chunk)); } catch (Exception e) {}
                },
                error -> {
                    try { emitter.send(SseEmitter.event().data("❌ " + error.getMessage())); emitter.complete(); } catch (Exception e) {}
                },
                () -> { try { emitter.complete(); } catch (Exception e) {} }
            );

        return emitter;
    }

    // ==================== 7. 一键保存AI行程到数据库 ====================
    @PostMapping("/saveToPlan")
    public Result<Map<String, Object>> saveToPlan(@RequestHeader("Authorization") String authHeader,
                                                   @RequestBody Map<String, Object> body) {
        Long userId = AuthUtils.requireUserId(authHeader, jwtUtil);
        SavedPlanRequest request = new SavedPlanRequest();
        request.setDestination((String) body.get("destination"));
        if (body.get("days") instanceof Number d) request.setDays(d.intValue());
        if (body.get("budget") instanceof Number b) request.setBudget(b.longValue());
        if (body.get("people") instanceof Number p) request.setPeople(p.intValue());
        request.setPlanData(body.get("planData"));
        request.setSource("AI");

        log.info("用户{}保存AI行程: {}", userId, body.get("destination"));
        try {
            return Result.ok(planService.toResponseMap(planService.savePlan(userId, request)));
        } catch (Exception e) {
            log.error("保存AI行程失败", e);
            return Result.fail("保存失败：" + e.getMessage());
        }
    }
}
