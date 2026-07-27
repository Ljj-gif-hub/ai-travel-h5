package org.example.traveljava.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.example.traveljava.annotation.RateLimit;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.traveljava.dto.ChatMessage;
import org.example.traveljava.dto.DailyTripDTO;
import org.example.traveljava.dto.GenerateProgressDTO;
import org.example.traveljava.dto.GenerateStep;
import org.example.traveljava.dto.TaskCancelledException;
import org.example.traveljava.dto.TravelPlanDTO;
import org.example.traveljava.dto.TripPlannerRequest;
import org.example.traveljava.service.AIService;
import org.example.traveljava.vo.Result;
import org.example.traveljava.vo.TravelRecommendVO;
import org.slf4j.Logger;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.slf4j.LoggerFactory;
import org.example.traveljava.config.AIProviderConfig;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/travel")
public class TravelController {

    private static final Logger log = LoggerFactory.getLogger(TravelController.class);

    private final AIService aiService;
    private final AIProviderConfig aiConfig;

    /** SSE emitter 注册表：taskId → emitter，用于后台生成线程向 SSE 通道推送进度 */
    private final ConcurrentHashMap<String, SseEmitter> emitterRegistry = new ConcurrentHashMap<>();

    /** 待处理的生成请求：taskId → TripPlannerRequest，SSE连接后才开始生成 */
    private final ConcurrentHashMap<String, TripPlannerRequest> pendingRequests = new ConcurrentHashMap<>();

    public TravelController(AIService aiService, AIProviderConfig aiConfig) {
        this.aiService = aiService;
        this.aiConfig = aiConfig;
    }

    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.ok("hello travel");
    }

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "ok");
        result.put("provider", aiConfig.getActiveProvider());
        result.put("model", aiConfig.getActiveModel());
        result.put("apiKeyConfigured", aiConfig.getActiveApiKey() != null && aiConfig.getActiveApiKey().length() > 8 ? "yes" : "no");
        return Result.ok(result);
    }

    @GetMapping("/test-ai")
    @RateLimit(max = 10, duration = 60, key = "travel_test_ai")
    public Result<String> testAI() {
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
    public Result<String> generatePlan(@Valid @RequestBody TravelRecommendVO vo) {
        log.info("生成旅行规划请求：目的地={}, 预算={}, 天数={}", vo.getDestination(), vo.getBudget(), vo.getDays());
        long startTime = System.currentTimeMillis();
        try {
            String plan = aiService.generateTravelPlan(
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
    public Flux<String> streamPlan(@Valid @RequestBody TravelRecommendVO vo) {
        log.info("流式生成旅行规划请求：目的地={}, 预算={}, 天数={}",
                vo.getDestination(), vo.getBudget(), vo.getDays());

        return aiService.streamTravelPlan(
                        vo.getDestination(),
                        vo.getBudget().longValue(),
                        vo.getDays()
                )
                .map(chunk -> "data: " + chunk + "\n\n")
                .onErrorResume(e -> {
                    log.error("流式生成旅行规划失败", e);
                    return Flux.just("data: 错误：生成失败，请稍后重试\n\n");
                });
    }

    @PostMapping("/chat")
    @RateLimit(max = 20, duration = 60, key = "travel_chat")
    public Result<String> chat(@RequestBody List<ChatMessage> messages) {
        log.info("聊天请求：消息数={}", messages.size());
        
        if (messages == null || messages.isEmpty()) {
            return Result.fail("消息列表不能为空");
        }
        
        if (messages.size() > 50) {
            return Result.fail("消息数量不能超过50条");
        }

        try {
            String response = aiService.chat(messages);
            return Result.ok(response);
        } catch (Exception e) {
            log.error("聊天请求失败", e);
            return Result.fail("聊天请求失败");
        }
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(max = 10, duration = 60, key = "travel_chat_stream")
    public Flux<String> streamChat(@RequestBody List<ChatMessage> messages) {
        log.info("流式聊天请求：消息数={}", messages.size());
        
        if (messages == null || messages.isEmpty()) {
            return Flux.just("data: 错误：消息列表不能为空\n\n");
        }
        
        return aiService.streamChat(messages);
    }

    @PostMapping("/recommend")
    @RateLimit(max = 10, duration = 60, key = "travel_recommend")
    public Result<String> recommend(@Valid @RequestBody TravelRecommendVO vo) {
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

            String response = aiService.chat(messages);
            return Result.ok(response);
        } catch (Exception e) {
            log.error("推荐请求失败", e);
            return Result.fail("推荐请求失败");
        }
    }

    @PostMapping("/plan/structured")
    @RateLimit(max = 10, duration = 60, key = "travel_plan_struct")
    public Result<TravelPlanDTO> generateStructuredPlan(@Valid @RequestBody TravelRecommendVO vo) {
        log.info("生成结构化旅行规划请求：目的地={}, 预算={}, 天数={}",
                vo.getDestination(), vo.getBudget(), vo.getDays());
        try {
            TravelPlanDTO plan = aiService.generateStructuredTravelPlan(
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
    public Result<String> getAttractionImage(@RequestParam String name) {
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

    /**
     * AI 行程规划器 — SSE 流式（SseEmitter + UTF-8 + 脏文本清洗）
     */
    @PostMapping("/planner/stream")
    @RateLimit(max = 5, duration = 60, key = "travel_planner_stream")
    public SseEmitter streamPlanner(@RequestBody TripPlannerRequest req) {
        SseEmitter emitter = new SseEmitter(120_000L);

        // 生命周期日志
        emitter.onCompletion(() -> log.info("SSE正常关闭: dest={}", req.getDestination()));
        emitter.onTimeout(() -> log.warn("SSE超时: dest={}", req.getDestination()));
        emitter.onError(e -> log.error("SSE异常: dest={}, err={}", req.getDestination(), e.getMessage()));

        if (req.getDestination() == null || req.getDestination().trim().isEmpty()) {
            safeSend(emitter, "❌ 请输入目的地");
            emitter.complete();
            return emitter;
        }
        if (req.getDays() == null || req.getDays() < 1) {
            safeSend(emitter, "❌ 请选择出行天数");
            emitter.complete();
            return emitter;
        }

        log.info("SSE开始: dest={}, days={}", req.getDestination(), req.getDays());

        aiService.streamPlannerTrip(req)
            .subscribe(
                chunk -> {
                    if ("[DONE]".equals(chunk)) {
                        safeComplete(emitter);
                    } else if (chunk.startsWith(": heartbeat")) {
                        // 心跳不转发前端
                    } else {
                        // 前端SseEmitter.send() 默认 UTF-8
                        safeSend(emitter, chunk);
                    }
                },
                error -> {
                    log.error("SSE AI流异常", error);
                    safeSend(emitter, "❌ " + error.getMessage());
                    safeComplete(emitter);
                },
                () -> safeComplete(emitter)
            );

        return emitter;
    }

    private void safeSend(SseEmitter emitter, String data) {
        try {
            emitter.send(SseEmitter.event().data(data, MediaType.TEXT_PLAIN));
        } catch (Exception e) {
            log.debug("SSE send失败(客户端断开?): {}", e.getMessage());
        }
    }

    /**
     * 多阶段 SSE 进度 — 7步串行推送 + 初始握手
     */
    @PostMapping("/planner/progress")
    @RateLimit(max = 5, duration = 60, key = "travel_planner_progress")
    public SseEmitter streamPlannerProgress(@RequestBody TripPlannerRequest req, HttpServletResponse response) {
        // SSE 标准响应头
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);

        SseEmitter emitter = new SseEmitter(600_000L); // 10分钟
        String dest = req.getDestination();

        emitter.onCompletion(() -> log.info("进度SSE完成: {}", dest));
        emitter.onTimeout(() -> log.warn("进度SSE超时: {}", dest));
        emitter.onError(e -> log.error("进度SSE异常: {}", e.getMessage()));

        if (dest == null || dest.trim().isEmpty()) {
            safeSendJson(emitter, Map.of("eventType", "stream-error", "message", "请输入目的地"));
            emitter.complete();
            return emitter;
        }

        // 生成唯一任务ID
        String taskId = UUID.randomUUID().toString().substring(0, 8);

        new Thread(() -> {
            try {
                Map<String, Object> init = new HashMap<>();
                init.put("eventType", "progress-update");
                init.put("progress", 0);
                init.put("stepName", "正在连接...");
                init.put("summary", "准备生成" + dest + "行程");
                init.put("allStepList", buildInitSteps());
                init.put("previewData", new HashMap<>());
                init.put("userPref", new HashMap<>());
                init.put("taskId", taskId);
                safeSendJson(emitter, init);
                Thread.sleep(150);

                aiService.streamPlannerWithStages(req, dto -> {
                    safeSendJson(emitter, dto);
                }, taskId);
                safeSendJson(emitter, Map.of("eventType", "generate-finish", "destination", dest));
            } catch (TaskCancelledException e) {
                log.info("任务被用户终止: {}", taskId);
                safeSendJson(emitter, Map.of("eventType", "task-stop", "message", "行程生成已终止"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("多阶段生成异常", e);
                safeSendJson(emitter, Map.of("eventType", "stream-error", "message", e.getMessage()));
            } finally {
                try { emitter.complete(); } catch (Exception ex) {}
            }
        }, "planner-stage-worker").start();

        return emitter;
    }

    private List<Map<String, Object>> buildInitSteps() {
        String[] names = {"分析目的地","生成线路概览","规划每日行程","筛选酒店推荐","整理出行贴士","汇总费用明细","全部完成"};
        int[] progs = {5,15,40,65,80,95,100};
        List<Map<String, Object>> steps = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            steps.add(Map.of("name", names[i], "progress", progs[i], "status", "wait"));
        }
        return steps;
    }

    /**
     * 分段流式行程详情 — 逐天推送 Day1→Day2→...DayN
     */
    @PostMapping("/planner/stream-detail")
    @RateLimit(max = 10, duration = 60, key = "travel_stream_detail")
    public SseEmitter streamTripDetail(@RequestBody Map<String, Object> body, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);

        String dest = (String) body.getOrDefault("destination", "");
        int days = ((Number) body.getOrDefault("days", 1)).intValue();
        long budget = ((Number) body.getOrDefault("budget", 5000)).longValue();
        String taskId = (String) body.getOrDefault("taskId", UUID.randomUUID().toString().substring(0, 8));

        SseEmitter emitter = new SseEmitter(600_000L); // 10分钟
        emitter.onCompletion(() -> log.info("详情SSE完成: {} d{}", dest, days));
        emitter.onTimeout(() -> log.warn("详情SSE超时: {}", dest));
        emitter.onError(e -> log.error("详情SSE异常: {}", e.getMessage()));

        new Thread(() -> {
            try {
                aiService.streamGenerateDailyTrip(dest, days, budget, obj -> {
                    safeSendJson(emitter, obj);
                }, taskId, () -> {
                    try { emitter.send(SseEmitter.event().comment("")); return true; }
                    catch (Exception ex) { return false; }
                });
                safeSendJson(emitter, Map.of("eventType", "detail-finish", "destination", dest, "days", days));
            } catch (TaskCancelledException e) {
                safeSendJson(emitter, Map.of("eventType", "task-stop", "message", "生成已终止"));
            } catch (Exception e) {
                log.error("详情流式异常", e);
                safeSendJson(emitter, Map.of("eventType", "stream-error", "message", e.getMessage()));
            } finally {
                try { emitter.complete(); } catch (Exception ex) {}
            }
        }, "detail-stream-worker").start();

        return emitter;
    }

    /**
     * 终止规划任务 — 前端点「停止生成」或返回时调用
     */
    @PostMapping("/planner/stop")
    public Result<String> stopPlanner(@RequestBody Map<String, String> body) {
        String taskId = body != null ? body.get("taskId") : null;
        if (taskId != null) {
            aiService.cancelTask(taskId);
            log.info("规划任务已终止: taskId={}", taskId);
        }
        return Result.ok("已终止");
    }

    /* ==================== 新版行程生成接口（供 TripMapView 调用） ==================== */

        /**
     * 【推荐】单端点SSE行程生成 — POST后直接在本连接接收流式进度
     * 无竞态条件，一条HTTP请求完成全部：创建任务→注册emitter→启动生成→推送进度
     */
    @PostMapping(value = "/trip/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(max = 10, duration = 60, key = "trip_sse")
    public SseEmitter generateAndStream(@RequestBody Map<String, Object> body, HttpServletResponse response) {
        String destination = (String) body.getOrDefault("destination", "");
        int days = ((Number) body.getOrDefault("days", 3)).intValue();
        int people = ((Number) body.getOrDefault("people", 2)).intValue();
        long budget = ((Number) body.getOrDefault("budget", 5000L)).longValue();
        String origin = (String) body.getOrDefault("origin", "");
        String companion = (String) body.getOrDefault("companion", "");
        String styles = (String) body.getOrDefault("styles", "");
        String hotelLevel = (String) body.getOrDefault("hotel", "");
        String pace = (String) body.getOrDefault("pace", "");
        String schedule = (String) body.getOrDefault("schedule", "");

        if (destination == null || destination.trim().isEmpty()) {
            SseEmitter err = new SseEmitter();
            safeSendJson(err, Map.of("eventType", "stream-error", "message", "请输入目的地"));
            err.complete();
            return err;
        }
        if (days < 1) days = 1;
        if (days > 14) { days = 14; log.warn("天数超限，已截断为14天"); }
        final int finalDays = days;
        final long finalBudget = budget;
        final String finalDest = destination;

        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setBufferSize(0); // 禁用响应缓冲，强制实时输出
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);

        String taskId = UUID.randomUUID().toString().substring(0, 8);
        SseEmitter emitter = new SseEmitter(600_000L);
        log.info("单端点SSE: dest={}, days={}, taskId={}", destination, days, taskId);

        emitterRegistry.put(taskId, emitter);
        emitter.onCompletion(() -> { emitterRegistry.remove(taskId); log.info("SSE完成:{}", taskId); });
        emitter.onTimeout(() -> { emitterRegistry.remove(taskId); aiService.cancelTask(taskId); });
        emitter.onError(e -> { emitterRegistry.remove(taskId); aiService.cancelTask(taskId); });

        safeSendJson(emitter, Map.of(
            "eventType", "progress-update", "progress", 0,
            "stepName", "正在连接AI...", "summary", "准备生成" + finalDest + "行程",
            "allStepList", buildInitSteps(), "taskId", taskId
        ));

        TripPlannerRequest req = new TripPlannerRequest();
        req.setDestination(finalDest); req.setDays(finalDays); req.setBudget(finalBudget);

        new Thread(() -> {
            try {
                /* ===== 阶段1: 7步快速进度推送 (0%→100%, 不调AI, 约5秒完成) ===== */
                sendFastProgress(emitter, taskId, finalDest, finalDays);

                /* ===== 阶段2: AI 生成完整行程文本（自然语言+流式输出） ===== */
                SseEmitter em2 = emitterRegistry.get(taskId);
                if (em2 != null) {
                    String fullPrompt = buildAIPrompt(finalDest, finalDays, finalBudget, origin, companion, styles, hotelLevel, pace, schedule);
                    log.info("AI提示词: {}", fullPrompt.substring(0, Math.min(200, fullPrompt.length())));
                    aiService.streamChatText(fullPrompt, chunk -> {
                        SseEmitter em = emitterRegistry.get(taskId);
                        if (em != null) safeSendJson(em, Map.of("eventType", "text-update", "text", chunk));
                    });
                }

                /* ===== 阶段3: 交通 ===== */
                SseEmitter emTransport = emitterRegistry.get(taskId);
                if (emTransport != null) {
                    safeSendJson(emTransport, Map.of("eventType", "transport-update", "transport", generateTransport(finalDest)));
                }

                /* ===== 阶段4: 费用估算（标题栏总价用） ===== */
                SseEmitter emCost = emitterRegistry.get(taskId);
                if (emCost != null) {
                    int z = (int)finalBudget;
                    safeSendJson(emCost, Map.of("eventType", "cost-update", "hotelCost", z*35/100, "ticketCost", z*20/100, "foodCost", z*25/100, "transportCost", z*20/100, "totalCost", z));
                }

                /* ===== 完成 ===== */
                SseEmitter emFinal = emitterRegistry.get(taskId);
                if (emFinal != null) {
                    safeSendJson(emFinal, Map.of(
                        "eventType", "generate-finish", "progress", 100,
                        "stepName", "全部完成", "destination", finalDest, "days", finalDays
                    ));
                    try { emFinal.complete(); } catch (Exception ex) {}
                }
                log.info("SSE全部完成:{}", taskId);
            } catch (TaskCancelledException e) {
                SseEmitter em = emitterRegistry.get(taskId);
                if (em != null) { safeSendJson(em, Map.of("eventType", "task-stop")); try { em.complete(); } catch (Exception ex) {} }
            } catch (Exception e) {
                log.error("生成异常:{}", taskId, e);
                SseEmitter em = emitterRegistry.get(taskId);
                if (em != null) { safeSendJson(em, Map.of("eventType", "stream-error", "message", e.getMessage())); try { em.complete(); } catch (Exception ex) {} }
            } finally { emitterRegistry.remove(taskId); }
        }, "trip-gen-" + taskId).start();

        return emitter;
    }


/**
     * 启动行程生成 — 异步执行，立即返回 taskId
     * 前端拿到 taskId 后连接 GET /trip/progress/{taskId} 订阅 SSE 进度
     */
    @PostMapping("/trip/generate")
    @RateLimit(max = 10, duration = 60, key = "trip_generate")
    public Result<Map<String, Object>> generateTrip(@RequestBody Map<String, Object> body) {
        String destination = (String) body.getOrDefault("destination", "");
        int days = ((Number) body.getOrDefault("days", 3)).intValue();
        int people = ((Number) body.getOrDefault("people", 2)).intValue();
        long budget = ((Number) body.getOrDefault("budget", 5000L)).longValue();

        if (destination == null || destination.trim().isEmpty()) {
            return Result.fail("请输入目的地");
        }
        if (days < 1 || days > 30) {
            return Result.fail("出行天数需在1-30之间");
        }

        String taskId = UUID.randomUUID().toString().substring(0, 8);
        log.info("新版行程生成: dest={}, days={}, people={}, budget={}, taskId={}",
                destination, days, people, budget, taskId);

        TripPlannerRequest req = new TripPlannerRequest();
        req.setDestination(destination);
        req.setDays(days);
        req.setBudget(budget);

        // 暂存请求参数，等待SSE连接后启动生成（消除竞态）
        pendingRequests.put(taskId, req);

        Map<String, Object> result = new HashMap<>();
        result.put("taskId", taskId);
        result.put("destination", destination);
        result.put("days", days);
        return Result.ok(result);
    }

    /**
     * SSE 进度订阅 — 核心流式端点
     * 1. 注册 emitter 到注册表
     * 2. 启动后台AI生成线程（在emitter注册之后，消除竞态）
     * 3. 实时推送进度事件到前端
     */
    @GetMapping("/trip/progress/{taskId}")
    @RateLimit(max = 30, duration = 60, key = "trip_progress")
    public SseEmitter streamTripProgress(@PathVariable String taskId, HttpServletResponse response) {
        // SSE响应头：防止缓存 + 禁用缓冲
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);

        SseEmitter emitter = new SseEmitter(600_000L);

        emitter.onCompletion(() -> {
            emitterRegistry.remove(taskId);
            pendingRequests.remove(taskId);
            log.info("SSE完成: taskId={}", taskId);
        });
        emitter.onTimeout(() -> {
            emitterRegistry.remove(taskId);
            pendingRequests.remove(taskId);
            aiService.cancelTask(taskId);
            log.warn("SSE超时: taskId={}", taskId);
        });
        emitter.onError(e -> {
            emitterRegistry.remove(taskId);
            pendingRequests.remove(taskId);
            aiService.cancelTask(taskId);
            log.error("SSE异常: taskId={}", taskId, e);
        });

        // 第一步：注册 emitter
        emitterRegistry.put(taskId, emitter);

        // 第二步：发送初始握手事件
        Map<String, Object> init = new HashMap<>();
        init.put("eventType", "progress-update");
        init.put("progress", 0);
        init.put("stepName", "正在连接AI...");
        init.put("summary", "准备生成行程");
        init.put("allStepList", buildInitSteps());
        init.put("taskId", taskId);
        safeSendJson(emitter, init);

        // 第三步：取出暂存的请求参数，启动生成线程
        TripPlannerRequest req = pendingRequests.remove(taskId);
        if (req == null) {
            safeSendJson(emitter, Map.of("eventType", "stream-error", "message", "任务不存在，请重新发起"));
            try { emitter.complete(); } catch (Exception ex) {}
            return emitter;
        }

        final String dest = req.getDestination();
        final int days = req.getDays();

        new Thread(() -> {
            try {
                aiService.streamPlannerWithStages(req, dto -> {
                    SseEmitter em = emitterRegistry.get(taskId);
                    if (em != null) safeSendJson(em, dto);
                }, taskId);

                SseEmitter em = emitterRegistry.get(taskId);
                if (em != null) {
                    safeSendJson(em, Map.of("eventType", "generate-finish", "progress", 100, "stepName", "全部完成", "destination", dest, "days", days));
                    try { em.complete(); } catch (Exception ex) {}
                }
                log.info("生成完成: taskId={}", taskId);
            } catch (TaskCancelledException e) {
                SseEmitter em = emitterRegistry.get(taskId);
                if (em != null) { safeSendJson(em, Map.of("eventType", "task-stop", "message", "已停止")); try { em.complete(); } catch (Exception ex) {} }
            } catch (Exception e) {
                log.error("生成异常: taskId={}", taskId, e);
                SseEmitter em = emitterRegistry.get(taskId);
                if (em != null) { safeSendJson(em, Map.of("eventType", "stream-error", "message", e.getMessage() != null ? e.getMessage() : "生成失败")); try { em.complete(); } catch (Exception ex) {} }
            } finally {
                emitterRegistry.remove(taskId);
            }
        }, "trip-gen-" + taskId).start();

        log.info("生成线程已启动: taskId={}, dest={}", taskId, dest);
        return emitter;
    }

    /** 停止生成 */
    @PostMapping("/trip/stop/{taskId}")
    public Result<String> stopTrip(@PathVariable String taskId) {
        if (taskId != null && !taskId.isEmpty()) {
            aiService.cancelTask(taskId);
            pendingRequests.remove(taskId);
            SseEmitter em = emitterRegistry.remove(taskId);
            if (em != null) try { em.complete(); } catch (Exception ex) {}
            log.info("已终止: taskId={}", taskId);
        }
        return Result.ok("已终止");
    }

    /**
     * 快速进度推送 — 不调 AI，5秒内 0%→100%
     * 步骤顺序对齐内容模块：线路预览 → 每日行程 → 酒店推荐 → 出行贴士 → 费用汇总 → 完成
     */
    private void sendFastProgress(SseEmitter emitter, String taskId, String dest, int days) {
        String[] stepNames = {"分析目的地", "生成线路概览", "规划每日行程", "筛选酒店推荐", "整理出行贴士", "汇总费用明细", "全部完成"};
        int[] stepProgress = {5, 15, 40, 65, 80, 95, 100};
        List<Map<String, Object>> stepItems = new ArrayList<>();
        for (int i = 0; i < stepNames.length; i++) {
            stepItems.add(Map.of("name", stepNames[i], "progress", stepProgress[i], "status", "wait"));
        }

        for (int i = 0; i < stepNames.length; i++) {
            SseEmitter em = emitterRegistry.get(taskId);
            if (em == null) return;

            for (int j = 0; j < stepItems.size(); j++) {
                Map<String, Object> item = new HashMap<>(stepItems.get(j));
                if (j < i) item.put("status", "done");
                else if (j == i) item.put("status", "doing");
                else item.put("status", "wait");
                stepItems.set(j, item);
            }

            safeSendJson(em, Map.of(
                "eventType", "progress-update",
                "progress", stepProgress[i],
                "stepName", stepNames[i],
                "summary", buildSummaryText(i, dest, days),
                "allStepList", stepItems,
                "taskId", taskId,
                "finish", i == stepNames.length - 1
            ));

            try { Thread.sleep(700); } catch (InterruptedException e) { return; }
        }
    }

    private String buildSummaryText(int idx, String dest, int days) {
        switch (idx) {
            case 0: return "正在了解" + dest + "，规划" + days + "天行程";
            case 1: return "已生成" + dest + days + "天线路概览";
            case 2: return "正在逐天规划" + dest + "深度行程";
            case 3: return "已筛选" + dest + "市中心优质酒店";
            case 4: return "已整理" + dest + "实用旅行贴士";
            case 5: return "正在汇总交通住宿景点费用";
            default: return "行程已生成，正在加载内容...";
        }
    }

    /**
     * 构建自然语言 AI 提示词（含全部用户偏好）
     */
    private String buildAIPrompt(String dest, int days, long budget, String origin, String companion, String styles, String hotel, String pace, String schedule) {
        // budget 参数保留但不再写入提示词，改为让AI自己估算
        StringBuilder sb = new StringBuilder();
        sb.append("我想去").append(dest).append("旅行");
        if (!origin.isEmpty()) sb.append("，从").append(origin).append("出发");
        sb.append("。玩").append(days).append("天。");
        if (!companion.isEmpty()) sb.append(companion).append("，");
        if (!styles.isEmpty()) sb.append("关注").append(styles).append("体验，");
        if (!hotel.isEmpty()) sb.append("最好住").append(hotel).append("，");
        if (!pace.isEmpty()) sb.append("节奏").append(pace).append("，");
        if (!schedule.isEmpty()) sb.append("行程尽量偏").append(schedule.equals("偏晚归") ? "晚归" : "早出").append("，");
        sb.append("交通选择经济舱。");
        sb.append("请给出预算估计。");
        sb.append("请帮我设计出详细的行程，用Markdown格式输出，标题用##，尽量详细丰富。\\n\\n");
        sb.append("行程要求：\\n");
        sb.append("- 先写一段200字的行程总览，概述目的地的特色和本次行程的亮点\\n");
        sb.append("- 按天详细规划，每天包含上午/下午/晚上三个时段\\n");
        sb.append("- 每个时段写明真实的景点名、活动描述、预计时长和大致费用\\n");
        sb.append("- 每个景点写2-3句介绍，让行程内容充实丰富\\n");
        sb.append("- 每天末尾推荐1-2家当地特色餐厅，附人均消费\\n");
        sb.append("- 规划往返交通建议，说明航班或高铁的参考时间和价格\\n");
        sb.append("- 最后给出5-8条针对该目的地的实用旅行贴士\\n");
        sb.append("- 输出一份费用预估汇总表，分项列出交通、住宿、门票、餐饮的预算");
        return sb.toString();
    }

    /**
     * 根据目的地生成往返交通信息
     */
    private Map<String, Object> generateTransport(String city) {
        Map<String, Object> t = new HashMap<>();
        // 根据目的地类型判断出行方式
        boolean isDomestic = !city.contains("巴黎") && !city.contains("东京") && !city.contains("伦敦") && !city.contains("纽约");
        if (isDomestic) {
            t.put("departType", "flight");
            t.put("departIcon", "✈️");
            t.put("departTitle", "飞往" + city);
            t.put("departDetail", "建议选上午航班 · 提前2小时到机场 · 飞行约2-3小时");
            t.put("departPrice", city.contains("北京") ? 800L : city.contains("上海") ? 600L : 900L);
            t.put("returnType", "flight");
            t.put("returnIcon", "✈️");
            t.put("returnTitle", "从" + city + "返程");
            t.put("returnDetail", "建议选傍晚航班 · 预留2小时前往机场");
            t.put("returnPrice", city.contains("北京") ? 750L : city.contains("上海") ? 550L : 850L);
        } else {
            t.put("departType", "flight");
            t.put("departIcon", "✈️");
            t.put("departTitle", "国际航班飞往" + city);
            t.put("departDetail", "建议提前3小时到机场 · 飞行约10-13小时 · 需护照签证");
            t.put("departPrice", 3500L);
            t.put("returnType", "flight");
            t.put("returnIcon", "✈️");
            t.put("returnTitle", "从" + city + "国际返程");
            t.put("returnDetail", "提前3小时到机场 · 预留退税时间");
            t.put("returnPrice", 3200L);
        }
        return t;
    }

    /**
     * 根据目的地生成酒店推荐列表（含模拟数据）
     */
    private List<Map<String, Object>> generateHotelList(String city, int nights) {
        List<Map<String, Object>> hotels = new ArrayList<>();
        String[] names; String[] districts; double[][] coords; long[] prices;
        if (city.contains("北京")) {
            names = new String[]{"王府井希尔顿","国贸大酒店","前门建国饭店","颐和安缦","三里屯洲际","诺金酒店"};
            districts = new String[]{"东城区","朝阳区","西城区","海淀区","朝阳区","朝阳区"};
            coords = new double[][]{{39.914,116.410},{39.909,116.461},{39.900,116.392},{39.998,116.275},{39.932,116.455},{39.971,116.488}};
            prices = new long[]{1280,1580,580,3200,1380,780};
        } else if (city.contains("上海")) {
            names = new String[]{"外滩华尔道夫","浦东丽思卡尔顿","静安瑞吉","新天地朗廷","豫园万丽","虹桥康得思"};
            districts = new String[]{"黄浦区","浦东新区","静安区","黄浦区","黄浦区","闵行区"};
            coords = new double[][]{{31.240,121.490},{31.235,121.502},{31.229,121.450},{31.220,121.473},{31.227,121.489},{31.197,121.317}};
            prices = new long[]{2600,2800,1680,1200,780,880};
        } else {
            names = new String[]{"市中心豪华酒店","商务精品酒店","舒适民宿","景观度假酒店"};
            districts = new String[]{"市中心","商业区","老城区","景区周边"};
            coords = new double[][]{{39.915,116.404},{39.920,116.410},{39.910,116.398},{39.925,116.415}};
            prices = new long[]{980,580,380,1280};
        }
        for (int i = 0; i < names.length && i < coords.length && i < prices.length; i++) {
            Map<String, Object> h = new HashMap<>();
            h.put("id", (long)(i + 1));
            h.put("name", names[i]);
            h.put("district", districts.length > i ? districts[i] : "市中心");
            h.put("pricePerNight", prices[i]);
            h.put("totalPrice", prices[i] * Math.max(1, nights - 1));
            h.put("rating", 4.0 + (i % 3) * 0.3);
            h.put("latitude", coords[i][0]);
            h.put("longitude", coords[i][1]);
            hotels.add(h);
        }
        return hotels;
    }

private void safeSendJson(SseEmitter emitter, Object data) {
        try {
            if (emitter == null) return;
            String json = new ObjectMapper().writeValueAsString(data);
            emitter.send(SseEmitter.event().data(json, MediaType.APPLICATION_JSON));
            // 心跳注释强制刷新缓冲区，避免Tomcat缓冲导致前端收不到数据
            emitter.send(SseEmitter.event().comment(""));
        } catch (IOException e) {
            log.debug("SSE JSON推送IO异常(客户端断开): {}", e.getMessage());
        } catch (Exception e) {
            // already completed 是正常现象（客户端离开/超时），不打印WARN
            String msg = e.getMessage();
            if (msg != null && msg.contains("already completed")) {
                log.debug("SSE通道已关闭(客户端断开)");
            } else {
                log.warn("SSE JSON异常: {}", msg);
            }
        }
    }

    private void safeComplete(SseEmitter emitter) {
        try { emitter.send(SseEmitter.event().data("[DONE]")); } catch (Exception e) {}
        try { emitter.complete(); } catch (Exception e) {}
    }

    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
