package org.example.traveljava.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.example.traveljava.annotation.RateLimit;
import org.example.traveljava.config.AIProviderConfig;
import org.example.traveljava.dto.TripPlannerRequest;
import org.example.traveljava.service.AIService;
import org.example.traveljava.service.TravelSseService;
import org.example.traveljava.util.AuthUtils;
import org.example.traveljava.util.JwtUtil;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 行程规划 — 行程生成 SSE 端点（供 TripMapView 等调用）。
 * 【重构】SSE 通道管理、线程池与后台生成逻辑已拆到 TravelSseService；
 * AI 对话/规划类接口拆到 TravelAIController。本类只保留路由、参数校验与鉴权。
 */
@RestController
@RequestMapping("/api/travel")
@io.swagger.v3.oas.annotations.tags.Tag(name = "行程规划")
public class TravelController {

    private static final Logger log = LoggerFactory.getLogger(TravelController.class);

    private final AIService aiService;
    private final TravelSseService sseService;
    private final AIProviderConfig aiConfig;
    private final JwtUtil jwtUtil;

    public TravelController(AIService aiService, TravelSseService sseService,
                            AIProviderConfig aiConfig, JwtUtil jwtUtil) {
        this.aiService = aiService;
        this.sseService = sseService;
        this.aiConfig = aiConfig;
        this.jwtUtil = jwtUtil;
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
        return Result.ok(result);
    }

    /**
     * AI 行程规划器 — SSE 流式（SseEmitter + UTF-8 + 脏文本清洗）
     */
    @PostMapping("/planner/stream")
    @RateLimit(max = 5, duration = 60, key = "travel_planner_stream")
    public SseEmitter streamPlanner(@RequestHeader("Authorization") String authHeader, @RequestBody TripPlannerRequest req) {
        AuthUtils.requireUserId(authHeader, jwtUtil);
        SseEmitter emitter = new SseEmitter(120_000L);

        // 生命周期日志
        emitter.onCompletion(() -> log.info("SSE正常关闭: dest={}", req.getDestination()));
        emitter.onTimeout(() -> log.warn("SSE超时: dest={}", req.getDestination()));
        emitter.onError(e -> log.error("SSE异常: dest={}, err={}", req.getDestination(), e.getMessage()));

        if (req.getDestination() == null || req.getDestination().trim().isEmpty()) {
            sseService.safeSend(emitter, "❌ 请输入目的地");
            emitter.complete();
            return emitter;
        }
        if (req.getDays() == null || req.getDays() < 1) {
            sseService.safeSend(emitter, "❌ 请选择出行天数");
            emitter.complete();
            return emitter;
        }

        log.info("SSE开始: dest={}, days={}", req.getDestination(), req.getDays());

        sseService.subscribePlannerTrip(emitter, req);
        return emitter;
    }

    /**
     * 多阶段 SSE 进度 — 7步串行推送 + 初始握手
     */
    @PostMapping("/planner/progress")
    @RateLimit(max = 5, duration = 60, key = "travel_planner_progress")
    public SseEmitter streamPlannerProgress(@RequestHeader("Authorization") String authHeader, @RequestBody TripPlannerRequest req, HttpServletResponse response) {
        AuthUtils.requireUserId(authHeader, jwtUtil);
        // SSE 标准响应头
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);

        SseEmitter emitter = new SseEmitter(600_000L); // 10分钟
        String dest = req.getDestination();
        // 提前生成任务ID，供断开回调取消后台 AI 生成，避免客户端断开后仍继续消耗 token
        String taskId = UUID.randomUUID().toString().substring(0, 8);

        emitter.onCompletion(() -> log.info("进度SSE完成: {}", dest));
        emitter.onTimeout(() -> { aiService.cancelTask(taskId); log.warn("进度SSE超时: {}", dest); });
        emitter.onError(e -> { aiService.cancelTask(taskId); log.error("进度SSE异常: {}", e.getMessage()); });

        if (dest == null || dest.trim().isEmpty()) {
            sseService.safeSendJson(emitter, Map.of("eventType", "stream-error", "message", "请输入目的地"));
            emitter.complete();
            return emitter;
        }

        sseService.startPlannerProgressGeneration(emitter, req, taskId, dest);
        return emitter;
    }

    /**
     * 分段流式行程详情 — 逐天推送 Day1→Day2→...DayN
     */
    @PostMapping("/planner/stream-detail")
    @RateLimit(max = 10, duration = 60, key = "travel_stream_detail")
    public SseEmitter streamTripDetail(@RequestHeader("Authorization") String authHeader, @RequestBody Map<String, Object> body, HttpServletResponse response) {
        AuthUtils.requireUserId(authHeader, jwtUtil);
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);

        String dest = (String) body.getOrDefault("destination", "");
        int rawDays = ((Number) body.getOrDefault("days", 1)).intValue();
        // 天数截断到 1-14，防止恶意传超大值刷爆逐天 AI 调用
        if (rawDays < 1) rawDays = 1;
        if (rawDays > 14) rawDays = 14;
        final int days = rawDays;
        long budget = ((Number) body.getOrDefault("budget", 5000)).longValue();
        String taskId = (String) body.getOrDefault("taskId", UUID.randomUUID().toString().substring(0, 8));

        SseEmitter emitter = new SseEmitter(600_000L); // 10分钟
        emitter.onCompletion(() -> log.info("详情SSE完成: {} d{}", dest, days));
        emitter.onTimeout(() -> { aiService.cancelTask(taskId); log.warn("详情SSE超时: {}", dest); });
        emitter.onError(e -> { aiService.cancelTask(taskId); log.error("详情SSE异常: {}", e.getMessage()); });

        sseService.startDetailGeneration(emitter, dest, days, budget, taskId);
        return emitter;
    }

    /**
     * 终止规划任务 — 前端点「停止生成」或返回时调用
     */
    @PostMapping("/planner/stop")
    public Result<String> stopPlanner(@RequestHeader("Authorization") String authHeader, @RequestBody Map<String, String> body) {
        AuthUtils.requireUserId(authHeader, jwtUtil);
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
    public SseEmitter generateAndStream(@RequestHeader("Authorization") String authHeader, @RequestBody Map<String, Object> body, HttpServletResponse response) {
        AuthUtils.requireUserId(authHeader, jwtUtil);
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
            sseService.safeSendJson(err, Map.of("eventType", "stream-error", "message", "请输入目的地"));
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
        // CORS 由全局 WebConfig 白名单统一处理（不在此硬编码 *）
        response.setBufferSize(0); // 禁用响应缓冲，强制实时输出
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);

        String taskId = UUID.randomUUID().toString().substring(0, 8);
        SseEmitter emitter = new SseEmitter(600_000L);
        log.info("单端点SSE: dest={}, days={}, taskId={}", destination, days, taskId);

        sseService.registerEmitter(taskId, emitter);
        emitter.onCompletion(() -> { sseService.removeEmitter(taskId); log.info("SSE完成:{}", taskId); });
        emitter.onTimeout(() -> { sseService.removeEmitter(taskId); aiService.cancelTask(taskId); });
        emitter.onError(e -> { sseService.removeEmitter(taskId); aiService.cancelTask(taskId); });

        sseService.safeSendJson(emitter, Map.of(
            "eventType", "progress-update", "progress", 0,
            "stepName", "正在连接AI...", "summary", "准备生成" + finalDest + "行程",
            "allStepList", sseService.buildInitSteps(), "taskId", taskId
        ));

        TripPlannerRequest req = new TripPlannerRequest();
        req.setDestination(finalDest); req.setDays(finalDays); req.setBudget(finalBudget);

        sseService.startSingleEndpointGeneration(taskId, finalDest, finalDays, finalBudget,
                origin, companion, styles, hotelLevel, pace, schedule);
        return emitter;
    }

    /**
     * 启动行程生成 — 异步执行，立即返回 taskId
     * 前端拿到 taskId 后连接 GET /trip/progress/{taskId} 订阅 SSE 进度
     */
    @PostMapping("/trip/generate")
    @RateLimit(max = 10, duration = 60, key = "trip_generate")
    public Result<Map<String, Object>> generateTrip(@RequestHeader("Authorization") String authHeader, @RequestBody Map<String, Object> body) {
        AuthUtils.requireUserId(authHeader, jwtUtil);
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
        sseService.putPending(taskId, req);

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
    public SseEmitter streamTripProgress(@RequestHeader("Authorization") String authHeader, @PathVariable String taskId, HttpServletResponse response) {
        AuthUtils.requireUserId(authHeader, jwtUtil);
        // SSE响应头：防止缓存 + 禁用缓冲
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");
        // CORS 由全局 WebConfig 白名单统一处理（不在此硬编码 *）
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);

        SseEmitter emitter = new SseEmitter(600_000L);

        emitter.onCompletion(() -> {
            sseService.removeEmitter(taskId);
            sseService.removePending(taskId);
            log.info("SSE完成: taskId={}", taskId);
        });
        emitter.onTimeout(() -> {
            sseService.removeEmitter(taskId);
            sseService.removePending(taskId);
            aiService.cancelTask(taskId);
            log.warn("SSE超时: taskId={}", taskId);
        });
        emitter.onError(e -> {
            sseService.removeEmitter(taskId);
            sseService.removePending(taskId);
            aiService.cancelTask(taskId);
            log.error("SSE异常: taskId={}", taskId, e);
        });

        // 第一步：注册 emitter
        sseService.registerEmitter(taskId, emitter);

        // 第二步：发送初始握手事件
        Map<String, Object> init = new HashMap<>();
        init.put("eventType", "progress-update");
        init.put("progress", 0);
        init.put("stepName", "正在连接AI...");
        init.put("summary", "准备生成行程");
        init.put("allStepList", sseService.buildInitSteps());
        init.put("taskId", taskId);
        sseService.safeSendJson(emitter, init);

        // 第三步：取出暂存的请求参数，启动生成线程
        TravelSseService.PendingEntry entry = sseService.takePending(taskId);
        if (entry == null) {
            sseService.safeSendJson(emitter, Map.of("eventType", "stream-error", "message", "任务不存在，请重新发起"));
            try { emitter.complete(); } catch (Exception ex) {}
            return emitter;
        }
        final TripPlannerRequest req = entry.getReq();

        final String dest = req.getDestination();
        final int days = req.getDays();

        sseService.startStagedGeneration(taskId, req, dest, days);
        log.info("生成线程已启动: taskId={}, dest={}", taskId, dest);
        return emitter;
    }

    /** 停止生成 */
    @PostMapping("/trip/stop/{taskId}")
    public Result<String> stopTrip(@RequestHeader("Authorization") String authHeader, @PathVariable String taskId) {
        AuthUtils.requireUserId(authHeader, jwtUtil);
        if (taskId != null && !taskId.isEmpty()) {
            aiService.cancelTask(taskId);
            sseService.removePending(taskId);
            sseService.completeAndRemoveEmitter(taskId);
            log.info("已终止: taskId={}", taskId);
        }
        return Result.ok("已终止");
    }
}
