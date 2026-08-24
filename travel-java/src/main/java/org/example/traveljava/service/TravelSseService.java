package org.example.traveljava.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.example.traveljava.dto.TaskCancelledException;
import org.example.traveljava.dto.TripPlannerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 【重构】行程规划 SSE 通道与后台生成任务服务（从 TravelController 拆出）。
 * 职责：有界线程池、emitter/待处理请求注册表、进度推送辅助、各端点后台生成任务。
 *
 * 【修复】线程池由 newFixedThreadPool（无界队列）改为有界池：
 * core=CPU数、max=2倍、有界队列 100、CallerRunsPolicy（饱和时由调用线程执行，不丢任务、不无限排队）。
 */
@Service
public class TravelSseService {

    private static final Logger log = LoggerFactory.getLogger(TravelSseService.class);

    private final AIService aiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 【新功能】SSE 连接计数：注册成功后累加（累计开流次数） */
    private final Counter totalStreamsCounter;
    /** 【新功能】SSE 活跃连接数 Gauge（emitterRegistry.size() 实时值） */
    private final Gauge activeConnectionsGauge;

    /** 后台行程生成线程池 — 有界并发，防止 SSE 请求无限 new Thread 耗尽 JVM 线程 */
    private final ExecutorService sseExecutor = new ThreadPoolExecutor(
            Math.max(4, Runtime.getRuntime().availableProcessors()),
            Math.max(8, Runtime.getRuntime().availableProcessors() * 2),
            60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100),
            new ThreadPoolExecutor.CallerRunsPolicy());

    /** SSE emitter 注册表：taskId → emitter，用于后台生成线程向 SSE 通道推送进度 */
    private final ConcurrentHashMap<String, SseEmitter> emitterRegistry = new ConcurrentHashMap<>();

    /** 待处理的生成请求：taskId → 请求（带时间戳），SSE连接后才开始生成 */
    private final ConcurrentHashMap<String, PendingEntry> pendingRequests = new ConcurrentHashMap<>();

    /** 【L-CTRL-6】任务归属：taskId → 归属条目（userId + 创建时间）。
     *  stop/progress 端点据此校验操作者必须是任务创建者，防跨用户停他人任务/接管事件流。
     *  不随生成完成立即删除，而是与 pendingRequests 一样由定时任务按 TTL 清理——
     *  避免 taskId 被复用（/planner/progress → /planner/stream-detail）时误删归属导致本人 stop 失效。 */
    private final ConcurrentHashMap<String, TaskOwnerEntry> taskOwners = new ConcurrentHashMap<>();

    /** 待处理请求条目 — 记录创建时间，供定时清理过期条目（防内存泄漏） */
    public static class PendingEntry {
        private final TripPlannerRequest req;
        final long createdAt = System.currentTimeMillis();
        PendingEntry(TripPlannerRequest req) { this.req = req; }
        public TripPlannerRequest getReq() { return req; }
    }

    /** 任务归属条目 — 记录创建时间，供定时清理过期归属（防 map 无限增长） */
    public static class TaskOwnerEntry {
        private final Long userId;
        final long createdAt = System.currentTimeMillis();
        TaskOwnerEntry(Long userId) { this.userId = userId; }
        public Long getUserId() { return userId; }
    }

    public TravelSseService(AIService aiService, MeterRegistry meterRegistry) {
        this.aiService = aiService;
        // 【新功能】Prometheus 指标注册
        this.totalStreamsCounter = Counter.builder("travel.sse.total_streams")
                .description("SSE 累计开流次数")
                .register(meterRegistry);
        this.activeConnectionsGauge = Gauge.builder("travel.sse.active_connections", emitterRegistry, ConcurrentHashMap::size)
                .description("SSE 当前活跃连接数")
                .register(meterRegistry);
    }

    @PreDestroy
    public void shutdown() {
        sseExecutor.shutdown();
    }

    /** 定期清理超过 10 分钟未被 SSE 消费的待处理请求 + 超过 15 分钟未用的任务归属 */
    @Scheduled(fixedDelay = 60_000)
    public void purgeStalePendingRequests() {
        long now = System.currentTimeMillis();
        int removed = 0;
        for (var it = pendingRequests.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (now - e.getValue().createdAt > 10 * 60_000L) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.info("清理过期待处理请求 {} 条", removed);
        }

        // L-CTRL-6：同步清理过期任务归属（TTL 15 分钟），防 map 无限增长
        int ownerRemoved = 0;
        for (var it = taskOwners.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (now - e.getValue().createdAt > 15 * 60_000L) {
                it.remove();
                ownerRemoved++;
            }
        }
        if (ownerRemoved > 0) {
            log.info("清理过期任务归属 {} 条", ownerRemoved);
        }
    }

    // ==================== 注册表访问（供 Controller 编排使用） ====================

    public void registerEmitter(String taskId, SseEmitter emitter) {
        emitterRegistry.put(taskId, emitter);
        // 【新功能】开流计数（Gauge 由 emitterRegistry.size() 自动反映）
        totalStreamsCounter.increment();
    }

    public void removeEmitter(String taskId) {
        emitterRegistry.remove(taskId);
    }

    public void putPending(String taskId, TripPlannerRequest req) {
        pendingRequests.put(taskId, new PendingEntry(req));
    }

    public PendingEntry takePending(String taskId) {
        return pendingRequests.remove(taskId);
    }

    public void removePending(String taskId) {
        pendingRequests.remove(taskId);
    }

    // ==================== 任务归属（L-CTRL-6 越权防护） ====================

    /**
     * 【L-CTRL-6】登记任务归属。
     *  - taskId 为空 → 生成新 taskId 并登记
     *  - taskId 已存在且属于同一用户（如 /planner/progress → /planner/stream-detail 复用）→ 沿用原 taskId
     *  - taskId 已被他人占用 → 换新 taskId 再登记（不覆盖他人归属，防占坑越权）
     *
     * @return 最终生效的 taskId（调用方用返回值登记/停止，不能用入参）
     */
    public String registerTaskOwner(String desiredTaskId, Long userId) {
        if (userId == null) return desiredTaskId;
        if (desiredTaskId == null || desiredTaskId.isEmpty()) {
            return registerNewTaskOwner(userId);
        }
        TaskOwnerEntry existing = taskOwners.putIfAbsent(desiredTaskId, new TaskOwnerEntry(userId));
        if (existing != null && !existing.getUserId().equals(userId)) {
            log.warn("taskId 已被他人占用，换新: {} -> {}", desiredTaskId, userId);
            return registerNewTaskOwner(userId);
        }
        return desiredTaskId;
    }

    /** 【L-CTRL-6】生成全新 taskId 并登记归属（碰撞则重试，保证不覆盖已有归属） */
    public String registerNewTaskOwner(Long userId) {
        String taskId;
        do {
            taskId = UUID.randomUUID().toString().substring(0, 8);
        } while (taskOwners.putIfAbsent(taskId, new TaskOwnerEntry(userId)) != null);
        return taskId;
    }

    /** 【L-CTRL-6】校验当前用户是否为该任务的创建者（不存在/非本人 → false） */
    public boolean isTaskOwner(String taskId, Long userId) {
        if (taskId == null || userId == null) return false;
        TaskOwnerEntry entry = taskOwners.get(taskId);
        return entry != null && entry.getUserId().equals(userId);
    }

    /** 停止任务：从注册表取出并关闭 emitter */
    public void completeAndRemoveEmitter(String taskId) {
        SseEmitter em = emitterRegistry.remove(taskId);
        if (em != null) {
            try { em.complete(); } catch (Exception ex) {}
        }
    }

    // ==================== SSE 推送辅助 ====================

    public void safeSend(SseEmitter emitter, String data) {
        try {
            emitter.send(SseEmitter.event().data(data, MediaType.TEXT_PLAIN));
        } catch (Exception e) {
            log.debug("SSE send失败(客户端断开?): {}", e.getMessage());
        }
    }

    /**
     * SSE-2 修复：改为返回 boolean —— 推送失败（客户端断开/通道已关闭）返回 false，
     * 供上层 onProgress 回调据此触发取消，停止后续 AI 生成防白烧 token。
     */
    public boolean safeSendJson(SseEmitter emitter, Object data) {
        try {
            if (emitter == null) return false;
            String json = objectMapper.writeValueAsString(data);
            emitter.send(SseEmitter.event().data(json, MediaType.APPLICATION_JSON));
            // 心跳注释强制刷新缓冲区，避免Tomcat缓冲导致前端收不到数据
            emitter.send(SseEmitter.event().comment(""));
            return true;
        } catch (IOException e) {
            log.debug("SSE JSON推送IO异常(客户端断开): {}", e.getMessage());
            return false;
        } catch (Exception e) {
            // already completed 是正常现象（客户端离开/超时），不打印WARN
            String msg = e.getMessage();
            if (msg != null && msg.contains("already completed")) {
                log.debug("SSE通道已关闭(客户端断开)");
            } else {
                log.warn("SSE JSON异常: {}", msg);
            }
            return false;
        }
    }

    public void safeComplete(SseEmitter emitter) {
        try { emitter.send(SseEmitter.event().data("[DONE]")); } catch (Exception e) {}
        try { emitter.complete(); } catch (Exception e) {}
    }

    /** 7 步进度初始步骤列表（供各端点初始握手复用） */
    public List<Map<String, Object>> buildInitSteps() {
        String[] names = {"分析目的地","生成线路概览","规划每日行程","筛选酒店推荐","整理出行贴士","汇总费用明细","全部完成"};
        int[] progs = {5,15,40,65,80,95,100};
        List<Map<String, Object>> steps = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            steps.add(Map.of("name", names[i], "progress", progs[i], "status", "wait"));
        }
        return steps;
    }

    // ==================== 后台生成任务 ====================

    /** /planner/stream：订阅 AI 流并转发到 SSE */
    public void subscribePlannerTrip(SseEmitter emitter, TripPlannerRequest req) {
        reactor.core.Disposable disposable = aiService.streamPlannerTrip(req)
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

        // SSE-1 修复：客户端断开/超时/出错时 dispose 上游，停止从 DeepSeek 继续拉取（防白烧 token + 僵尸流）
        emitter.onCompletion(() -> {
            log.info("SSE正常关闭: dest={}", req.getDestination());
            if (!disposable.isDisposed()) disposable.dispose();
        });
        emitter.onTimeout(() -> {
            log.warn("SSE超时: dest={}", req.getDestination());
            if (!disposable.isDisposed()) disposable.dispose();
        });
        emitter.onError(e -> {
            log.error("SSE异常: dest={}, err={}", req.getDestination(), e.getMessage());
            if (!disposable.isDisposed()) disposable.dispose();
        });
    }

    /** /planner/progress：多阶段 SSE 进度 — 7步串行推送 + 初始握手 */
    public void startPlannerProgressGeneration(SseEmitter emitter, TripPlannerRequest req, String taskId, String dest) {
        sseExecutor.submit(() -> {
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
                    // SSE-2 修复：推送失败（客户端断开）→ 标记取消，让 streamPlannerWithStages
                    // 下一阶段 checkTaskCancel 检测后停止，防客户端离开后继续烧 AI token
                    if (!safeSendJson(emitter, dto)) {
                        aiService.cancelTask(taskId);
                    }
                }, taskId);
                safeSendJson(emitter, Map.of("eventType", "generate-finish", "destination", dest));
            } catch (TaskCancelledException e) {
                log.info("任务被用户终止: {}", taskId);
                safeSendJson(emitter, Map.of("eventType", "task-stop", "message", "行程生成已终止"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("多阶段生成异常", e);
                // SSE-6 修复：不把原始异常透传给前端（可能含内网 URL），固定文案；原文留在服务端日志
                safeSendJson(emitter, Map.of("eventType", "stream-error", "message", "生成失败，请稍后重试"));
            } finally {
                try { emitter.complete(); } catch (Exception ex) {}
                // SSE-2 修复：清理 onCompletion/onTimeout 中 cancelTask 残留的取消标志，防泄漏
                aiService.removeTask(taskId);
            }
        });
    }

    /** /planner/stream-detail：分段流式行程详情 — 逐天推送 Day1→Day2→...DayN */
    public void startDetailGeneration(SseEmitter emitter, String dest, int days, long budget, String taskId) {
        sseExecutor.submit(() -> {
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
                // SSE-6 修复：固定文案，原始异常只进服务端日志
                safeSendJson(emitter, Map.of("eventType", "stream-error", "message", "生成失败，请稍后重试"));
            } finally {
                try { emitter.complete(); } catch (Exception ex) {}
            }
        });
    }

    /** /trip/generate/stream：单端点生成 — 快速进度 → AI 文本 → 交通 → 费用 → 完成 */
    public void startSingleEndpointGeneration(String taskId, String dest, int days, long budget,
                                              String origin, String companion, String styles,
                                              String hotelLevel, String pace, String schedule) {
        sseExecutor.submit(() -> {
            try {
                /* ===== 阶段1: 7步快速进度推送 (0%→100%, 不调AI, 约5秒完成) ===== */
                sendFastProgress(taskId, dest, days);

                /* ===== 阶段2: AI 生成完整行程文本（自然语言+流式输出） ===== */
                SseEmitter em2 = emitterRegistry.get(taskId);
                if (em2 != null) {
                    String fullPrompt = buildAIPrompt(dest, days, budget, origin, companion, styles, hotelLevel, pace, schedule);
                    log.info("AI提示词: {}", fullPrompt.substring(0, Math.min(200, fullPrompt.length())));
                    // SSE-4 修复：传入 taskId，streamChatText 内部在每块前检测取消标志；
                    // 客户端断开（emitter 被移除/onCompletion 触发 cancelTask）后中止剩余 AI 拉取
                    aiService.streamChatText(fullPrompt, chunk -> {
                        SseEmitter em = emitterRegistry.get(taskId);
                        if (em == null) {
                            // 客户端断开：标记取消，让上游 streamChatText 停止拉取，防白烧 token
                            aiService.cancelTask(taskId);
                        } else {
                            safeSendJson(em, Map.of("eventType", "text-update", "text", chunk));
                        }
                    }, taskId);
                }

                /* ===== 阶段3: 交通 ===== */
                SseEmitter emTransport = emitterRegistry.get(taskId);
                if (emTransport != null) {
                    safeSendJson(emTransport, Map.of("eventType", "transport-update", "transport", generateTransport(dest)));
                }

                /* ===== 阶段4: 费用估算（标题栏总价用） ===== */
                SseEmitter emCost = emitterRegistry.get(taskId);
                if (emCost != null) {
                    int z = (int) budget;
                    safeSendJson(emCost, Map.of("eventType", "cost-update", "hotelCost", z*35/100, "ticketCost", z*20/100, "foodCost", z*25/100, "transportCost", z*20/100, "totalCost", z));
                }

                /* ===== 完成 ===== */
                SseEmitter emFinal = emitterRegistry.get(taskId);
                if (emFinal != null) {
                    safeSendJson(emFinal, Map.of(
                        "eventType", "generate-finish", "progress", 100,
                        "stepName", "全部完成", "destination", dest, "days", days
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
                // SSE-6 修复：固定文案，原始异常只进服务端日志
                if (em != null) { safeSendJson(em, Map.of("eventType", "stream-error", "message", "生成失败，请稍后重试")); try { em.complete(); } catch (Exception ex) {} }
            } finally {
                emitterRegistry.remove(taskId);
                // AI-1 修复：清理超时/错误回调 cancelTask 残留的取消标志，防泄漏
                aiService.removeTask(taskId);
            }
        });
    }

    /** /trip/progress/{taskId}：取出暂存请求后启动多阶段生成 */
    public void startStagedGeneration(String taskId, TripPlannerRequest req, String dest, int days) {
        sseExecutor.submit(() -> {
            try {
                aiService.streamPlannerWithStages(req, dto -> {
                    SseEmitter em = emitterRegistry.get(taskId);
                    if (em == null) {
                        // SSE-2 修复：客户端断开（emitter 已被移除）→ 标记取消，停止后续 AI 阶段
                        aiService.cancelTask(taskId);
                    } else if (!safeSendJson(em, dto)) {
                        aiService.cancelTask(taskId);
                    }
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
                // SSE-6 修复：固定文案，原始异常只进服务端日志
                if (em != null) { safeSendJson(em, Map.of("eventType", "stream-error", "message", "生成失败，请稍后重试")); try { em.complete(); } catch (Exception ex) {} }
            } finally {
                emitterRegistry.remove(taskId);
                // SSE-2 修复：清理 onCompletion/onTimeout 中 cancelTask 残留的取消标志，防泄漏
                aiService.removeTask(taskId);
            }
        });
    }

    // ==================== 私有辅助 ====================

    /**
     * 快速进度推送 — 不调 AI，5秒内 0%→100%
     * 步骤顺序对齐内容模块：线路预览 → 每日行程 → 酒店推荐 → 出行贴士 → 费用汇总 → 完成
     */
    private void sendFastProgress(String taskId, String dest, int days) {
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
        sb.append("请帮我设计出详细的行程，用Markdown格式输出，标题用##，尽量详细丰富。\n\n");
        sb.append("行程要求：\n");
        sb.append("- 先写一段200字的行程总览，概述目的地的特色和本次行程的亮点\n");
        sb.append("- 按天详细规划，每天包含上午/下午/晚上三个时段\n");
        sb.append("- 每个时段写明真实的景点名、活动描述、预计时长和大致费用\n");
        sb.append("- 每个景点写2-3句介绍，让行程内容充实丰富\n");
        sb.append("- 每天末尾推荐1-2家当地特色餐厅，附人均消费\n");
        sb.append("- 规划往返交通建议，说明航班或高铁的参考时间和价格\n");
        sb.append("- 最后给出5-8条针对该目的地的实用旅行贴士\n");
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
