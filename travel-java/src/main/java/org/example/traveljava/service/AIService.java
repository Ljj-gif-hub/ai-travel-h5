package org.example.traveljava.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.traveljava.dto.ChatMessage;
import org.example.traveljava.dto.ChatRequest;
import org.example.traveljava.dto.ChatResponse;
import org.example.traveljava.dto.BudgetDTO;
import org.example.traveljava.dto.DailyTripDTO;
import org.example.traveljava.dto.GenerateProgressDTO;
import org.example.traveljava.dto.GenerateStep;
import org.example.traveljava.dto.TaskCancelledException;
import org.example.traveljava.dto.TripTipsDTO;
import org.example.traveljava.dto.SceneImageDTO;
import org.example.traveljava.dto.TravelPlanDTO;
import org.example.traveljava.dto.TripPlannerRequest;
import org.example.traveljava.util.TextCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.example.traveljava.config.AIProviderConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * AI 服务层 — 多供应商通用接口
 * 通过 AIProviderConfig 读取当前活跃供应商，自动适配
 * 所有调用使用标准 OpenAI 兼容格式（/chat/completions）
 *
 * 已测试兼容: DeepSeek | OpenAI | Claude | Gemini | Groq | Ollama | 自定义代理
 */
@Service
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);
    private final ObjectMapper objectMapper;

    private final WebClient webClient;
    private final AIProviderConfig aiConfig;
    private final Map<String, String> planCache = new ConcurrentHashMap<>();
    private final Map<String, String> chatCache = new ConcurrentHashMap<>();
    private final SceneImageService sceneImageService;
    private final ExecutorService imageFetchExecutor;
    /** 用户出行偏好（由 Controller 在生成前设置） */
    private Map<String, Object> userPrefs;

    public AIService(WebClient aiWebClient, AIProviderConfig aiConfig, ObjectMapper objectMapper,
                     SceneImageService sceneImageService,
                     @Qualifier("imageFetchExecutor") ExecutorService imageFetchExecutor) {
        this.webClient = aiWebClient;
        this.aiConfig = aiConfig;
        this.objectMapper = objectMapper;
        this.sceneImageService = sceneImageService;
        this.imageFetchExecutor = imageFetchExecutor;
    }

    /** 当前活跃模型名（从配置动态读取） */
    private String model() {
        return aiConfig.getActiveModel();
    }

    /** 当前活跃供应商的 chat 路径 */
    private String chatPath() {
        AIProviderConfig.ProviderConfig cfg = aiConfig.getActiveConfig();
        return cfg.getChatPath() != null ? cfg.getChatPath() : "/chat/completions";
    }

    /** 设置用户出行偏好（在生成行程前调用） */
    public void setUserPreferences(Map<String, Object> prefs) { this.userPrefs = prefs; }

    /**
     * 流式对话 — 将 AI 返回的文本逐块推送给回调
     * 用于自然语言行程生成
     */
    public void streamChatText(String userPrompt, java.util.function.Consumer<String> onChunk) {
        try {
            ChatRequest req = ChatRequest.builder()
                .model(model()).stream(true).maxTokens(8192).temperature(0.7)
                .messages(Arrays.asList(
                    ChatMessage.builder().role("system").content("你是资深旅行规划师，回复必须详细全面，每个景点至少写2-3句介绍，每天规划不能少于200字，整体回复不少于2000字。用Markdown格式输出，适当使用emoji让排版生动。").build(),
                    ChatMessage.builder().role("user").content(userPrompt).build()
                )).build();

            // 使用 Flux 处理流式 SSE 响应
            final int[] chunkCount = {0};
            final StringBuilder fullContent = new StringBuilder();
            String result = webClient.post().uri(chatPath())
                .contentType(MediaType.APPLICATION_JSON).bodyValue(req)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnNext(line -> {
                    String t = line.trim();
                    if (t.isEmpty()) return;
                    // 尝试直接解析整行 JSON（有些供应商不包 SSE data: 前缀）
                    if (!t.startsWith("data:")) {
                        tryParseChunk(t, onChunk, chunkCount, fullContent);
                        return;
                    }
                    String d = t.startsWith("data: ") ? t.substring(6) : t.substring(5);
                    if (d.equals("[DONE]") || d.isEmpty()) return;
                    tryParseChunk(d, onChunk, chunkCount, fullContent);
                })
                .collectList()
                .map(list -> String.join("", list))
                .block(Duration.ofSeconds(120));

            log.info("Flux完成: {} chunks, {} chars", chunkCount[0], fullContent.length());
            if (fullContent.length() == 0) {
                log.warn("AI返回空内容，使用预设行程");
                onChunk.accept("\n\n> ⚠️ AI返回为空，使用预设行程：\n\n");
                onChunk.accept(buildFallbackText(userPrompt));
            }
        } catch (Exception e) {
            log.error("流式文本生成失败: {}", e.getMessage());
            onChunk.accept("\\n\\n> ⚠️ AI服务繁忙，请稍后重试。以下为预设行程：\\n\\n");
            // 回退：推送预设文本
            onChunk.accept(buildFallbackText(userPrompt));
        }
    }

    private void tryParseChunk(String json, java.util.function.Consumer<String> onChunk, int[] count, StringBuilder full) {
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode choices = node.get("choices");
            if (choices != null && choices.size() > 0) {
                JsonNode delta = choices.get(0).get("delta");
                if (delta == null) {
                    // 尝试 message 字段（非流式格式）
                    JsonNode msg = choices.get(0).get("message");
                    if (msg != null) {
                        JsonNode c = msg.get("content");
                        if (c != null && !c.asText().isEmpty()) {
                            onChunk.accept(c.asText());
                            full.append(c.asText());
                            count[0]++;
                        }
                    }
                    return;
                }
                JsonNode content = delta.get("content");
                if (content != null && !content.asText().isEmpty()) {
                    String text = content.asText();
                    onChunk.accept(text);
                    full.append(text);
                    count[0]++;
                }
            }
        } catch (Exception ex) { /* skip malformed JSON */ }
    }

    private String buildFallbackText(String prompt) {
        // 从prompt提取目的地
        String dest = "目的地";
        if (prompt.contains("我想去")) {
            int s = prompt.indexOf("我想去") + 3;
            int e = prompt.indexOf("旅行", s);
            if (e > s) dest = prompt.substring(s, e);
        }
        return "## " + dest + "行程规划\\n\\n" +
            "### 📋 行程总览\\n" + dest + "是一座充满魅力的旅游城市，建议安排充足时间深度游览。\\n\\n" +
            "### 📅 Day1：抵达" + dest + "\\n- **上午**：抵达" + dest + "，入住酒店，稍作休整\\n- **下午**：游览" + dest + "市中心地标景点（约2小时，费用50元）\\n- **晚上**：品尝" + dest + "当地特色美食（约100元）\\n\\n" +
            "### 📅 Day2：深度探索\\n- **上午**：参观" + dest + "最著名的景点（约3小时，费用80元）\\n- **下午**：体验" + dest + "特色文化活动（约2小时，费用60元）\\n- **晚上**：漫步" + dest + "夜市或古城街区\\n\\n" +
            "### 🏨 酒店推荐\\n建议选择" + dest + "市中心区域，交通便利，周边餐饮丰富。\\n\\n" +
            "### 💡 出行贴士\\n1. 提前预订景点门票\\n2. 注意防晒保暖\\n3. 品尝当地特色小吃\\n4. 保管好随身物品\\n5. 下载离线地图备用\\n";
    }

    /* ==================== 测试连接 ==================== */
    public String testConnection() {
        log.info("AI连接测试 provider={} model={}", aiConfig.getActiveProvider(), model());
        long startTime = System.currentTimeMillis();
        try {
            ChatRequest request = ChatRequest.builder()
                    .model(model())
                    .messages(Arrays.asList(
                            ChatMessage.builder().role("user").content("说你好").build()
                    ))
                    .stream(false)
                    .maxTokens(20)
                    .build();

            ChatResponse response = webClient.post()
                    .uri(chatPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(status -> status.isError(), clientResponse -> {
                        log.error("DeepSeek连接失败，状态码: {}", clientResponse.statusCode());
                        return clientResponse.bodyToMono(String.class)
                                .doOnNext(body -> log.error("错误响应: {}", body))
                                .then(Mono.error(new RuntimeException("DeepSeek API错误: " + clientResponse.statusCode())));
                    })
                    .bodyToMono(ChatResponse.class)
                    .block();

            long costTime = System.currentTimeMillis() - startTime;
            log.info("DeepSeek连接成功，耗时={}ms", costTime);

            if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                return "DeepSeek连接成功: " + response.getChoices().get(0).getMessage().getContent();
            }
            return "DeepSeek连接成功，但响应为空";
        } catch (Exception e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("DeepSeek连接失败，耗时={}ms，错误={}", costTime, e.getMessage(), e);
            throw new RuntimeException("DeepSeek连接测试失败: " + e.getMessage(), e);
        }
    }

    /* ==================== 非流式行程规划 ==================== */
    public String generateTravelPlan(String destination, Long budget, Integer days) {
        String cacheKey = destination + "_" + budget + "_" + days;
        String cached = planCache.get(cacheKey);
        if (cached != null) {
            log.info("从缓存返回行程: {}", cacheKey);
            return cached;
        }

        String prompt = String.format("请为我规划%s%d天的旅行，预算%d元。用Markdown格式，按天列出上午/下午/晚上的安排，包含景点和大致花费。简洁实用。",
                destination, days, budget);

        ChatRequest request = ChatRequest.builder()
                .model(model())
                .messages(Arrays.asList(
                        ChatMessage.builder().role("system").content("你是快速旅行规划助手，回复简洁，直接给行程。").build(),
                        ChatMessage.builder().role("user").content(prompt).build()
                ))
                .stream(false)
                .temperature(0.5)
                .maxTokens(800)
                .build();

        log.info("DeepSeek生成行程: destination={}, days={}, budget={}", destination, days, budget);
        long startTime = System.currentTimeMillis();

        try {
            ChatResponse response = webClient.post()
                    .uri(chatPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(status -> status.isError(), clientResponse -> {
                        log.error("DeepSeek API错误状态: {}", clientResponse.statusCode());
                        return clientResponse.bodyToMono(String.class)
                                .doOnNext(body -> log.error("错误体: {}", body))
                                .then(Mono.error(new RuntimeException("API错误: " + clientResponse.statusCode())));
                    })
                    .bodyToMono(ChatResponse.class)
                    .doOnError(e -> log.error("DeepSeek调用异常: {}", e.getMessage()))
                    .block();

            long costTime = System.currentTimeMillis() - startTime;
            log.info("DeepSeek行程完成，耗时={}ms", costTime);

            String result = "抱歉，AI暂时无法生成行程，请稍后重试。";
            if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                ChatResponse.Choice choice = response.getChoices().get(0);
                if (choice != null && choice.getMessage() != null) {
                    result = choice.getMessage().getContent();
                    planCache.put(cacheKey, result);
                    log.info("行程生成成功，长度={}", result.length());
                }
            } else {
                log.warn("DeepSeek返回空响应");
            }
            return result;
        } catch (Exception e) {
            long costTime = System.currentTimeMillis() - startTime;
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.error("DeepSeek行程失败，耗时={}ms，错误={}", costTime, errorMsg, e);
            return "AI服务暂时不可用：" + errorMsg;
        }
    }

    /* ==================== 非流式聊天 ==================== */
    public String chat(List<ChatMessage> messages) {
        String cacheKey = messages.toString();
        String cached = chatCache.get(cacheKey);
        if (cached != null) {
            log.info("从缓存返回聊天");
            return cached;
        }

        ChatRequest request = ChatRequest.builder()
                .model(model())
                .messages(messages)
                .stream(false)
                .temperature(0.7)
                .maxTokens(1500)
                .build();

        try {
            ChatResponse response = webClient.post()
                    .uri(chatPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ChatResponse.class)
                    .block();

            log.info("DeepSeek聊天响应完成");

            String result = "抱歉，AI暂时无法回复，请稍后重试。";
            if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                ChatResponse.Choice choice = response.getChoices().get(0);
                if (choice != null && choice.getMessage() != null) {
                    result = choice.getMessage().getContent();
                    chatCache.put(cacheKey, result);
                }
            }
            return result;
        } catch (Exception e) {
            log.error("DeepSeek聊天失败", e);
            return "AI服务暂时不可用：" + e.getMessage();
        }
    }

    /* ==================== SSE流式聊天（核心） ==================== */
    public Flux<String> streamChat(List<ChatMessage> messages) {
        log.info("DeepSeek流式聊天，消息数={}", messages.size());

        ChatRequest request = ChatRequest.builder()
                .model(model())
                .messages(messages)
                .stream(true)
                .temperature(0.7)
                .maxTokens(2000)
                .build();

        return webClient.post()
                .uri(chatPath())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> {
                    int code = status.value();
                    return code == 401 || code == 403 || code == 429 || code >= 500;
                }, clientResponse -> {
                    int code = clientResponse.statusCode().value();
                    String errMsg;
                    if (code == 401 || code == 403) {
                        errMsg = "API密钥无效或已过期，请联系管理员";
                    } else if (code == 429) {
                        errMsg = "AI服务调用过于频繁，请稍后重试";
                    } else if (code >= 500) {
                        errMsg = "AI服务暂时繁忙，请稍后重试";
                    } else {
                        errMsg = "AI服务异常 (" + code + ")";
                    }
                    return Mono.error(new RuntimeException(errMsg));
                })
                .bodyToFlux(String.class)
                .filter(response -> response != null && !response.trim().isEmpty())
                .flatMap(response -> {
                    String trimmed = response.trim();
                    // DeepSeek SSE 结束标记
                    if (trimmed.equals("data: [DONE]") || trimmed.equals("[DONE]")) {
                        return Flux.just("[DONE]");
                    }
                    // 提取 data: 前缀后的 JSON
                    String data = trimmed;
                    if (trimmed.startsWith("data: ")) {
                        data = trimmed.substring(6).trim();
                        if (data.equals("[DONE]")) {
                            return Flux.just("[DONE]");
                        }
                    }
                    try {
                        JsonNode node = objectMapper.readTree(data);
                        if (node.has("choices") && node.get("choices").isArray() && node.get("choices").has(0)) {
                            JsonNode delta = node.get("choices").get(0).get("delta");
                            if (delta != null && delta.has("content")) {
                                String content = delta.get("content").asText();
                                if (content != null && !content.isEmpty()) {
                                    return Flux.just(content);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("SSE解析失败: {}", data);
                    }
                    return Flux.empty();
                })
                .onErrorResume(e -> {
                    log.error("DeepSeek SSE流式失败", e);
                    String msg = e.getMessage() != null ? e.getMessage() : "未知错误";
                    return Flux.just("❌ " + msg);
                });
    }

    /* ==================== 流式行程规划 ==================== */
    public Flux<String> streamTravelPlan(String destination, Long budget, Integer days) {
        String userPrompt = String.format("请为我规划%s%d天的旅行，预算%d元。要求详细：\n\n1. 每天分为：上午、下午、晚上\n2. 每个时段：景点名称、活动内容、游玩时长、预计费用、交通方式、小贴士\n3. 总预算明细：交通、住宿、餐饮、门票、其他\n4. 推荐特色美食和必买纪念品\n5. 注意事项\n\n使用清晰Markdown格式。", destination, days, budget);

        List<ChatMessage> messages = Arrays.asList(
                ChatMessage.builder().role("system").content("你是旅行规划助手，回复简洁实用。").build(),
                ChatMessage.builder().role("user").content(userPrompt).build()
        );

        ChatRequest request = ChatRequest.builder()
                .model(model())
                .messages(messages)
                .stream(false)
                .temperature(0.7)
                .maxTokens(3000)
                .build();

        log.info("DeepSeek行程规划: destination={}, days={}, budget={}", destination, days, budget);

        return webClient.post()
                .uri(chatPath())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.isError(), clientResponse -> {
                    int code = clientResponse.statusCode().value();
                    log.error("DeepSeek行程API错误: {}", code);
                    return clientResponse.bodyToMono(String.class)
                            .doOnNext(body -> log.error("错误体: {}", body))
                            .flatMap(body -> {
                                if (code == 429) return Mono.error(new RuntimeException("AI服务调用频繁，请稍后重试"));
                                if (code == 401 || code == 403) return Mono.error(new RuntimeException("API密钥无效，请检查配置"));
                                if (code >= 500) return Mono.error(new RuntimeException("AI服务暂时繁忙，请稍后重试"));
                                return Mono.error(new RuntimeException("AI请求异常: " + body));
                            });
                })
                .bodyToMono(ChatResponse.class)
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(3))
                        .filter(e -> e instanceof WebClientRequestException))
                .map(response -> {
                    if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                        String content = response.getChoices().get(0).getMessage().getContent();
                        log.info("DeepSeek行程内容长度={}", content != null ? content.length() : 0);
                        return content;
                    }
                    throw new RuntimeException("AI返回内容为空");
                })
                .flatMapMany(content -> {
                    List<String> chunks = new ArrayList<>();
                    int chunkSize = 50;
                    for (int i = 0; i < content.length(); i += chunkSize) {
                        chunks.add(content.substring(i, Math.min(i + chunkSize, content.length())));
                    }
                    log.info("分割为{}个片段", chunks.size());
                    return Flux.fromIterable(chunks);
                })
                .onErrorResume(e -> {
                    log.error("DeepSeek行程生成失败", e);
                    return Flux.just("❌ " + (e.getMessage() != null ? e.getMessage() : "未知错误"));
                });
    }

    /* ==================== 清空缓存 ==================== */
    public void clearCache() {
        planCache.clear();
        chatCache.clear();
        log.info("缓存已清空");
    }

    /* ==================== 结构化行程（JSON格式） ==================== */
    public TravelPlanDTO generateStructuredTravelPlan(String destination, Long budget, Integer days) {
        String cacheKey = "structured_" + destination + "_" + budget + "_" + days;
        String cached = planCache.get(cacheKey);
        if (cached != null) {
            log.info("从缓存返回结构化行程");
            try { return objectMapper.readValue(cached, TravelPlanDTO.class); }
            catch (JsonProcessingException e) { log.error("解析缓存失败", e); }
        }

        String userPrompt = String.format("请为我规划%s%d天的旅行，预算%d元。返回严格JSON格式（不要Markdown标记）：\n\n{\n  \"destination\": \"%s\",\n  \"days\": %d,\n  \"budget\": %d,\n  \"dayPlans\": [{\n    \"day\": 1,\n    \"dayTitle\": \"第1天标题\",\n    \"timeSlots\": [{\n      \"timeOfDay\": \"上午\", \"time\": \"09:00\",\n      \"attraction\": \"景点\", \"activity\": \"活动\",\n      \"duration\": \"2小时\", \"cost\": \"50元\",\n      \"transport\": \"步行\", \"tips\": \"贴士\", \"imageUrl\": \"\"\n    }]\n  }],\n  \"budgetDetail\": {\n    \"transportation\": 0, \"accommodation\": 0,\n    \"food\": 0, \"tickets\": 0, \"other\": 0, \"total\": 0\n  },\n  \"tips\": [\"建议1\"]\n}\n\n每天至少上午+下午两个时段。返回纯JSON。",
                destination, days, budget, destination, days, budget);

        List<ChatMessage> messages = Arrays.asList(
                ChatMessage.builder().role("system").content("你是专业旅行规划师，只返回JSON格式，不要其他文字。").build(),
                ChatMessage.builder().role("user").content(userPrompt).build()
        );

        ChatRequest request = ChatRequest.builder()
                .model(model())
                .messages(messages)
                .stream(false)
                .temperature(0.6)
                .maxTokens(10000)
                .build();

        log.info("DeepSeek结构化行程: destination={}, days={}, budget={}", destination, days, budget);

        try {
            ChatResponse response = webClient.post()
                    .uri(chatPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(status -> status.isError(), clientResponse -> {
                        log.error("DeepSeek结构化API错误: {}", clientResponse.statusCode());
                        return clientResponse.bodyToMono(String.class)
                                .doOnNext(body -> log.error("错误体: {}", body))
                                .then(Mono.error(new RuntimeException("API请求异常")));
                    })
                    .bodyToMono(ChatResponse.class)
                    .block();

            if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                String content = response.getChoices().get(0).getMessage().getContent();
                log.info("结构化行程长度={}", content != null ? content.length() : 0);

                if (content != null && !content.isEmpty()) {
                    String jsonStr = content.trim();
                    if (jsonStr.startsWith("```json")) jsonStr = jsonStr.substring(7);
                    if (jsonStr.endsWith("```")) jsonStr = jsonStr.substring(0, jsonStr.length() - 3);
                    jsonStr = jsonStr.trim();

                    TravelPlanDTO plan = objectMapper.readValue(jsonStr, TravelPlanDTO.class);
                    plan.setDestination(destination);
                    plan.setDays(days);
                    plan.setBudget(budget);
                    if (plan.getDayPlans() == null) plan.setDayPlans(new ArrayList<>());
                    for (TravelPlanDTO.DayPlan dp : plan.getDayPlans()) {
                        if (dp.getTimeSlots() == null) dp.setTimeSlots(new ArrayList<>());
                    }

                    preloadImagesForPlan(plan);

                    String updatedJson = objectMapper.writeValueAsString(plan);
                    planCache.put(cacheKey, updatedJson);
                    return plan;
                }
            }
            throw new RuntimeException("AI返回内容为空");
        } catch (Exception e) {
            log.error("DeepSeek结构化行程失败", e);
            throw new RuntimeException("生成行程失败：" + e.getMessage(), e);
        }
    }

    /* ==================== 图片预加载（不变） ==================== */
    private void preloadImagesForPlan(TravelPlanDTO plan) {
        if (plan == null || plan.getDayPlans() == null) return;

        long startTime = System.currentTimeMillis();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (TravelPlanDTO.DayPlan dayPlan : plan.getDayPlans()) {
            if (dayPlan.getTimeSlots() == null) continue;
            for (TravelPlanDTO.TimeSlot timeSlot : dayPlan.getTimeSlots()) {
                String attractionName = timeSlot.getAttraction();
                if (attractionName == null || attractionName.trim().isEmpty()) continue;

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        SceneImageDTO image = sceneImageService.getSceneImage(attractionName, timeSlot.getActivity());
                        if (image != null && image.getImgUrl() != null) {
                            timeSlot.setImageUrl(image.getImgUrl());
                        }
                    } catch (Exception e) {
                        log.warn("图片预加载失败: {}", attractionName);
                    }
                }, imageFetchExecutor);
                futures.add(future);
            }
        }

        if (!futures.isEmpty()) {
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(5, TimeUnit.SECONDS);
                log.info("图片预加载完成，{}ms，{}个景点", System.currentTimeMillis() - startTime, futures.size());
            } catch (TimeoutException e) {
                log.warn("图片预加载超时(5s)");
            } catch (Exception e) {
                log.error("图片预加载异常", e);
            }
        }
    }

    /* ==================== AI 行程规划器流式对话 ==================== */
    /**
     * 专为 AITripPlanner 前端定制的 SSE 流式行程生成
     * 【修复】文本清洗 + 强制约束Prompt + delayElements + 心跳+超时
     */
    public Flux<String> streamPlannerTrip(TripPlannerRequest req) {
        // 偏好标签最多取3个
        String prefCtx = buildTrimmedPreference(req);
        String monthCtx = (req.getMonths() != null && !req.getMonths().isEmpty())
            ? "出行月份：" + req.getMonths().stream().map(m -> m + "月").reduce((a, b) -> a + "、" + b).orElse("") + "；"
            : "";

        String userPrompt = String.format(
            "目的地：%s，出发地：%s，%d天，预算%d元。%s%s生成纯Markdown行程，禁止特殊符号。",
            req.getDestination(), req.getOrigin(), req.getDays(), req.getBudget(), monthCtx, prefCtx
        );

        // 【核心】System Prompt 强制约束，从源头杜绝脏符号
        String systemPrompt =
            "你是旅行规划师。输出纯Markdown格式行程。\n" +
            "硬性规则：\n" +
            "1. 严禁输出 | △ ▲ ▼ ◆ ◇ ▪ ▫ • ★ ☆ ─ ━ ═ 等特殊符号\n" +
            "2. 表格仅用标准Markdown表格语法：| 列1 | 列2 |\\n|-----|-----|\\n| 值1 | 值2 |\n" +
            "3. 法语/外语地名正常书写，不添加任何占位图标\n" +
            "4. 标题仅用 ## 和 ###\n" +
            "5. 结尾写「祝您旅途愉快！✨」\n" +
            "6. 直接开始输出行程，不要任何开场白";

        List<ChatMessage> messages = Arrays.asList(
            ChatMessage.builder().role("system").content(systemPrompt).build(),
            ChatMessage.builder().role("user").content(userPrompt).build()
        );

        ChatRequest request = ChatRequest.builder()
                .model(model())
                .messages(messages)
                .stream(true)
                .temperature(0.7)
                .maxTokens(4000)
                .build();

        log.info("AI规划器流式: dest={}, days={}", req.getDestination(), req.getDays());

        // 心跳保活
        Flux<String> heartbeat = Flux.interval(Duration.ofSeconds(15))
                .map(i -> ": heartbeat\n\n");

        Flux<String> dataStream = webClient.post()
                .uri(chatPath())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.isError(), clientResponse ->
                    clientResponse.bodyToMono(String.class)
                        .flatMap(body -> Mono.error(new RuntimeException(
                            "AI错误(" + clientResponse.statusCode().value() +
                            "):" + (body != null && body.length() > 200 ? body.substring(0, 200) : body)
                        )))
                )
                .bodyToFlux(String.class)
                .filter(r -> r != null && !r.trim().isEmpty())
                .flatMap(response -> {
                    String t = response.trim();
                    if (t.equals("data: [DONE]") || t.equals("[DONE]")) return Flux.just("[DONE]");
                    String data = t.startsWith("data: ") ? t.substring(6).trim() : t;
                    if (data.equals("[DONE]")) return Flux.just("[DONE]");
                    try {
                        JsonNode node = objectMapper.readTree(data);
                        JsonNode choices = node.get("choices");
                        if (choices != null && choices.isArray() && choices.has(0)) {
                            JsonNode delta = choices.get(0).get("delta");
                            if (delta != null && delta.has("content")) {
                                String raw = delta.get("content").asText();
                                if (raw != null && !raw.isEmpty()) {
                                    // 【后端脏文本清洗】
                                    String cleaned = TextCleaner.cleanChunk(raw);
                                    if (TextCleaner.hasContent(cleaned)) {
                                        return Flux.just(cleaned);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) { /* skip */ }
                    return Flux.empty();
                })
                // 50ms 微延迟，拆分大块防截断
                .delayElements(Duration.ofMillis(50))
                .onErrorResume(e -> {
                    log.error("AI规划器流式失败", e);
                    return Flux.just("❌ " + e.getMessage());
                })
                .timeout(Duration.ofSeconds(120), Flux.just("❌ 生成超时，请减少天数后重试"))
                .doOnComplete(() -> log.info("AI规划器流式完成"))
                .doOnCancel(() -> log.info("AI规划器流式取消"));

        return Flux.merge(heartbeat, dataStream);
    }

    /** 截断偏好标签，最多3个 */
    private String buildTrimmedPreference(TripPlannerRequest req) {
        Map<String, Object> prefs = req.getPreferences();
        if (prefs == null || prefs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        Object companion = prefs.get("companion");
        if (companion != null && !companion.toString().isEmpty())
            sb.append("同行：").append(companion).append("；");
        Object styles = prefs.get("styles");
        if (styles instanceof List && !((List<?>) styles).isEmpty()) {
            List<String> list = (List<String>) styles;
            int max = Math.min(list.size(), 3);  // ← 最多3个
            sb.append("偏好：").append(String.join("、", list.subList(0, max))).append("；");
        }
        return sb.toString();
    }

    /* ==================== 多阶段 SSE 进度生成 ==================== */

    // 任务取消容器（线程安全）
    private final ConcurrentHashMap<String, Boolean> cancelFlags = new ConcurrentHashMap<>();

    public void cancelTask(String taskId) {
        if (taskId != null) { cancelFlags.put(taskId, Boolean.TRUE); }
        log.info("任务已标记取消: {}", taskId);
    }

    public void removeTask(String taskId) {
        if (taskId != null) { cancelFlags.remove(taskId); }
    }

    private void checkTaskCancel(String taskId) {
        if (taskId != null && Boolean.TRUE.equals(cancelFlags.get(taskId))) {
            throw new TaskCancelledException(taskId);
        }
    }

    /**
     * 7阶段串行推送（带取消检测）
     */
    public void streamPlannerWithStages(TripPlannerRequest req,
                                         java.util.function.Consumer<GenerateProgressDTO> onProgress,
                                         String taskId) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<GenerateProgressDTO.StepItem> allSteps = new ArrayList<>();
            for (GenerateStep s : GenerateStep.values()) {
                allSteps.add(new GenerateProgressDTO.StepItem(s.label, s.progress, "wait"));
            }

            GenerateProgressDTO.TripUserPref pref = buildUserPref(req);
            String dest = req.getDestination();
            int days = req.getDays();

            for (GenerateStep step : GenerateStep.values()) {
                checkTaskCancel(taskId); // 每阶段前检查取消

                for (int i = 0; i < allSteps.size(); i++) {
                    int sp = GenerateStep.values()[i].progress;
                    if (sp < step.progress) allSteps.get(i).status = "done";
                    else if (sp == step.progress) allSteps.get(i).status = "doing";
                    else allSteps.get(i).status = "wait";
                }

                Map<String, Object> preview = buildStagePreview(step, dest, days);
                String summary = buildStageSummary(step, preview, dest, days);

                GenerateProgressDTO dto = GenerateProgressDTO.progress(step, summary, preview, allSteps, pref);
                try {
                    onProgress.accept(dto);
                    Thread.sleep(50);
                } catch (Exception e) {
                    log.warn("进度推送失败: {}", e.getMessage());
                    return;
                }

                try { Thread.sleep(800); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            }
        } catch (TaskCancelledException e) {
            log.info("任务被取消: {}", taskId);
            throw e; // 抛给 Controller 处理
        } catch (Exception e) {
            log.error("多阶段生成全局异常", e);
            GenerateProgressDTO errDto = new GenerateProgressDTO();
            errDto.setEventType("stream-error");
            errDto.setSummary("❌ " + e.getMessage());
            try { onProgress.accept(errDto); } catch (Exception ex) {}
        } finally {
            removeTask(taskId);
        }
    }

    private GenerateProgressDTO.TripUserPref buildUserPref(TripPlannerRequest req) {
        GenerateProgressDTO.TripUserPref p = new GenerateProgressDTO.TripUserPref();
        Map<String, Object> prefs = req.getPreferences();
        if (prefs != null) {
            p.companion = str(prefs.get("companion"));
            Object styles = prefs.get("styles");
            if (styles instanceof List) p.styles = (List<String>) styles;
            p.hotelLevel = str(prefs.get("hotelLevel"));
            p.cabinClass = str(prefs.get("cabinClass"));
            p.pace = str(prefs.get("pace"));
            p.schedule = str(prefs.get("schedule"));
        }
        return p;
    }

    private String str(Object o) { return o != null ? o.toString() : null; }

    private Map<String, Object> buildStagePreview(GenerateStep step, String dest, int days) {
        Map<String, Object> m = new HashMap<>();
        switch (step) {
            case DEST_INFO_QUERY:
                m.put("city", dest); m.put("country", "中国");
                // 实际调用AI获取城市简介
                Map<String, Object> info = callAIForJson(dest + "城市简介，20字以内。输出JSON:{\"description\":\"简介\"}");
                m.put("description", info != null ? info.getOrDefault("description", dest+"热门旅游城市") : dest+"热门旅游城市");
                break;
            case SPOT_QUERY:
                // 实际调用AI获取热门景点
                Map<String, Object> spots = callAIForJson(
                    dest + "推荐5个热门景点。输出JSON:{\"spots\":[{\"name\":\"景点\",\"rating\":4.5,\"type\":\"类型\"}]}。" + JSON_RULES);
                if (spots != null && spots.get("spots") instanceof List) {
                    m.put("spots", spots.get("spots"));
                    m.put("count", ((List<?>)spots.get("spots")).size());
                } else {
                    m.put("count", 10 + days * 2);
                }
                break;
            case HOTEL_QUERY:
                Map<String, Object> hotels = callAIForJson(
                    dest + "推荐酒店区域和数量。输出JSON:{\"count\":30,\"range\":\"商圈名称\"}。" + JSON_RULES);
                if (hotels != null) {
                    m.put("count", hotels.getOrDefault("count", 20 + days * 4));
                    m.put("range", hotels.getOrDefault("range", "市中心"));
                } else { m.put("count", 20); m.put("range", "市中心"); }
                break;
            case TRAFFIC_PLAN:
                Map<String, Object> traffic = callAIForJson(
                    "从深圳到" + dest + "的交通方式。输出JSON:{\"flights\":[{\"from\":\"深圳\",\"to\":\""+dest+"\",\"duration\":\"2.5小时\",\"type\":\"直飞\"}]}。" + JSON_RULES);
                if (traffic != null && traffic.get("flights") instanceof List) {
                    m.put("flights", traffic.get("flights"));
                }
                break;
            case TIPS_SORT:
                Map<String, Object> tips = callAIForJson(
                    dest + days + "天旅行贴士3条。输出JSON:{\"tips\":[{\"title\":\"标题\",\"content\":\"内容\"}]}。" + JSON_RULES);
                if (tips != null && tips.get("tips") instanceof List) {
                    m.put("tips", tips.get("tips"));
                } else { m.put("tips", List.of(Map.of("title","提前规划","content","提前预订门票"))); }
                break;
            default: break;
        }
        return m;
    }

    private String buildStageSummary(GenerateStep step, Map<String, Object> preview, String dest, int days) {
        switch (step) {
            case DEMAND_ANALYZE: return "目的地" + dest + "，" + days + "天行程";
            case DEST_INFO_QUERY: return preview.getOrDefault("description", "已获取" + dest + "城市信息").toString();
            case SPOT_QUERY: {
                Object spots = preview.get("spots");
                int n = spots instanceof List ? ((List<?>)spots).size() : (10 + days * 2);
                return "已找到" + n + "个推荐景点";
            }
            case HOTEL_QUERY: return "已筛选" + preview.getOrDefault("count", 20) + "家" + preview.getOrDefault("range", "市中心") + "酒店";
            case TRAFFIC_PLAN: return "已规划" + dest + "出行交通方案";
            case TIPS_SORT: return "已整理" + dest + "旅行贴士";
            case DAILY_ROUTE_ARRANGE: return "行程生成完毕";
            default: return "";
        }
    }

    /* ==================== 分段流式详情推送 ==================== */

    /**
     * 逐天调用 AI 生成行程 → 推送 day-update → budget-update → tips-update
     */
    public void streamGenerateDailyTrip(String dest, int days, Long budget,
                                         java.util.function.Consumer<Object> onPush,
                                         String taskId,
                                         java.util.function.Supplier<Boolean> isAlive) {
        // 阶段1：逐天 AI
        for (int d = 1; d <= days; d++) {
            checkTaskCancel(taskId);
            if (!isAlive.get()) { log.info("通道已关闭，停止生成 Day{}", d); return; }
            Map<String, Object> dayData = generateDayFromAI(dest, d, days, budget);
            if (dayData == null) { dayData = buildMockDayData(dest, d, budget, days); }
            try { onPush.accept(new DailyTripDTO(d, dayData, days)); Thread.sleep(50); }
            catch (Exception e) { log.warn("Day{}推送失败", d); return; }
            try { Thread.sleep(400); } catch (InterruptedException e) { return; }
        }
        // 阶段2：预算
        checkTaskCancel(taskId);
        if (isAlive.get()) {
            BudgetDTO bd = generateBudgetFromAI(dest, days, budget);
            if (bd == null) { int z=budget.intValue(); bd=new BudgetDTO(z*30/100,z*35/100,z*20/100,z-z*30/100-z*35/100-z*20/100,z); }
            try { onPush.accept(bd); Thread.sleep(100); } catch (Exception e) {}
        }
        // 阶段3：贴士
        checkTaskCancel(taskId);
        if (isAlive.get()) {
            TripTipsDTO td = generateTipsFromAI(dest, days);
            if (td == null) { td = new TripTipsDTO(Arrays.asList("预订"+dest+"景点门票",dest+"早晚温差大","品尝当地美食","确认天气","保管财物","下载离线地图")); }
            try { onPush.accept(td); Thread.sleep(100); } catch (Exception e) {}
        }
    }

    /** 调用 AI 生成单日行程 JSON — 含用户偏好 */
    private Map<String, Object> generateDayFromAI(String dest, int dayNum, int totalDays, Long budget) {
        log.info("AI Day{}/{}: {}", dayNum, totalDays, dest);
        // 构建偏好上下文
        String prefStr = "";
        if (userPrefs != null && !userPrefs.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            if (userPrefs.containsKey("companion") && !userPrefs.get("companion").toString().isEmpty())
                sb.append("同行:").append(userPrefs.get("companion")).append("。");
            if (userPrefs.containsKey("styles") && !userPrefs.get("styles").toString().isEmpty())
                sb.append("偏好:").append(userPrefs.get("styles")).append("。");
            if (userPrefs.containsKey("hotel") && !userPrefs.get("hotel").toString().isEmpty())
                sb.append("酒店:").append(userPrefs.get("hotel")).append("。");
            if (userPrefs.containsKey("pace") && !userPrefs.get("pace").toString().isEmpty())
                sb.append("节奏:").append(userPrefs.get("pace")).append("。");
            prefStr = sb.toString();
        }
        String prompt = String.format(
            "你是资深旅行规划师。请为%s第%d天（共%d天，预算%d元）规划详细行程。" +
            (prefStr.isEmpty() ? "" : "出行偏好：" + prefStr) +
            "用真实景点名、真实活动描述，每条duration写具体时长如\"2小时\"，cost写具体金额如\"80元\"，" +
            "transport写\"步行\"/\"地铁\"/\"公交\"/\"打车\"，tips写实用小贴士如\"提前预约免排队\"。" +
            "严格输出JSON:{\"dayTitle\":\"第%d天:简短主题(5-10字)\",\"timeSlots\":[" +
            "{\"timeOfDay\":\"上午\",\"time\":\"09:00\",\"attraction\":\"具体景点名\",\"activity\":\"参观+拍照\",\"duration\":\"2小时\",\"cost\":\"60元\",\"transport\":\"地铁\",\"tips\":\"实用小贴士\"}," +
            "{\"timeOfDay\":\"下午\",\"time\":\"14:00\",\"attraction\":\"具体景点名\",\"activity\":\"深度游览\",\"duration\":\"3小时\",\"cost\":\"120元\",\"transport\":\"步行\",\"tips\":\"实用小贴士\"}," +
            "{\"timeOfDay\":\"晚上\",\"time\":\"19:00\",\"attraction\":\"美食街或夜市\",\"activity\":\"品尝当地美食\",\"duration\":\"2小时\",\"cost\":\"100元\",\"transport\":\"步行\",\"tips\":\"实用小贴士\"}]}" +
            JSON_RULES,
            dest, dayNum, totalDays, budget, dayNum);
        Map<String, Object> r = callAIForJson(prompt);
        log.info("Day{} AI: {}", dayNum, r != null ? "OK" : "fallback");
        return r;
    }

    /** 调用 AI 生成预算明细 */
    private BudgetDTO generateBudgetFromAI(String dest, int days, Long budget) {
        // 让AI返回5个数字的数组，避免JSON对象格式错误
        String prompt = String.format("%s%d天%d元预算，输出5个数字数组如[%d,%d,%d,%d,%d]。只输出数组。",
            dest, days, budget, budget*30/100, budget*35/100, budget*20/100, budget*15/100, budget);
        try {
            String raw = chat(Arrays.asList(
                ChatMessage.builder().role("system").content("只输出数字数组，如[1,2,3]。").build(),
                ChatMessage.builder().role("user").content(prompt).build()
            ));
            if (raw == null || raw.isBlank()) return null;
            String nums = raw.replaceAll("[^0-9,]", "");
            String[] parts = nums.split(",");
            if (parts.length >= 4) {
                return new BudgetDTO(pInt(parts[0]),pInt(parts[1]),pInt(parts[2]),pInt(parts[3]),pInt(parts.length>=5?parts[4]:parts[0]));
            }
        } catch (Exception e) { log.warn("预算解析失败: {}", e.getMessage()); }
        return null;
    }

    private TripTipsDTO generateTipsFromAI(String dest, int days) {
        List<String> tips = callAIForArray(
            String.format("%s%d天旅行6条贴士，输出JSON数组：[\"贴士1\",\"贴士2\",\"贴士3\"]。" + JSON_RULES, dest, days));
        if (tips != null && !tips.isEmpty()) return new TripTipsDTO(tips);
        return null;
    }

    /** 调用AI返回JSON数组 */
    private List<String> callAIForArray(String prompt) {
        try {
            String raw = chat(Arrays.asList(
                ChatMessage.builder().role("system").content("只输出JSON字符串数组，如[\"a\",\"b\"]。").build(),
                ChatMessage.builder().role("user").content(prompt).build()
            ));
            if (raw == null || raw.isBlank()) return null;
            String s = raw.trim().replaceAll("(?s)^```(?:json)?\\s*", "").replaceAll("(?s)\\s*```$", "");
            int b = s.indexOf('['), e = s.lastIndexOf(']');
            if (b < 0 || e <= b) {
                // 兜底：按行或逗号分割
                List<String> list = new ArrayList<>();
                for (String line : s.split("[,\\n]")) {
                    String t = line.trim().replaceAll("^\"|\"$", "").trim();
                    if (!t.isEmpty() && t.length() > 2) list.add(t);
                }
                return list.isEmpty() ? null : list;
            }
            return objectMapper.readValue(s.substring(b, e+1), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) { log.warn("数组解析失败: {}", e.getMessage()); return null; }
    }

    private int pInt(String s) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; } }

    /** 兜底：AI 失败时用静态数据 */
    private Map<String, Object> buildMockDayData(String dest, int dayNum, Long budget, int totalDays) {
        Map<String, Object> day = new HashMap<>();
        day.put("day", dayNum);
        // 城市特色景点库
        String[][] citySpots = getCitySpots(dest);
        int spotIdx = (dayNum - 1) % citySpots.length;
        String[] spot1 = citySpots[spotIdx];
        String[] spot2 = citySpots[(spotIdx + 1) % citySpots.length];
        String dayTitle;
        if (dayNum == 1) dayTitle = "抵达" + dest + "·" + spot1[0];
        else if (dayNum == totalDays) dayTitle = "告别" + dest + "·" + spot2[0];
        else dayTitle = spot1[0] + "&" + spot2[0] + "深度游";
        day.put("dayTitle", "第" + dayNum + "天：" + dayTitle);
        day.put("date", (dayNum < 10 ? "0" : "") + dayNum + "日");

        List<Map<String, String>> slots = new ArrayList<>();
        String[][] templates = {
            {"上午", "09:00", spot1[0], spot1[1], "2小时", spot1[2], spot1[3], spot1[4]},
            {"下午", "14:00", spot2[0], spot2[1], "3小时", spot2[2], spot2[3], spot2[4]},
            {"晚上", "19:00", getFoodStreet(dest), "品尝" + dest + "特色美食", "2小时", "100元", "步行", "人气餐厅需排队"}
        };
        for (String[] s : templates) {
            Map<String, String> sl = new HashMap<>();
            sl.put("timeOfDay", s[0]); sl.put("time", s[1]); sl.put("attraction", s[2]);
            sl.put("activity", s[3]); sl.put("duration", s[4]); sl.put("cost", s[5]);
            sl.put("transport", s[6]); sl.put("tips", s[7]); sl.put("imageUrl", "");
            slots.add(sl);
        }
        day.put("timeSlots", slots);
        day.put("dailyBudget", (budget / totalDays) + "元");
        return day;
    }

    // 景点库：{名称, 活动描述, 费用, 交通, 贴士}
    private String[][] getCitySpots(String city) {
        if (city.contains("北京")) return new String[][]{
            {"故宫博物院","参观皇家宫殿群","60元","地铁1号线","提前预约门票免排队"},
            {"颐和园","漫步皇家园林","30元","地铁4号线","建议上午去人少"},
            {"天坛公园","探访祈年殿","15元","地铁5号线","清晨可看太极"},
            {"八达岭长城","登万里长城","40元","公交877路","穿舒适运动鞋"},
            {"南锣鼓巷","逛胡同小店","免费","地铁6号线","尝老北京炸酱面"},
            {"798艺术区","看当代艺术展","免费","地铁14号线","周末市集很热闹"},
            {"鸟巢水立方","打卡奥运场馆","50元","地铁8号线","夜景灯光很美"},
        };
        if (city.contains("上海")) return new String[][]{
            {"外滩","欣赏万国建筑群","免费","地铁2号线","夜景更美"},
            {"东方明珠","登塔俯瞰浦江","199元","地铁2号线","选晴天能见度高"},
            {"豫园","逛明代园林","40元","地铁10号线","旁边城隍庙小吃多"},
            {"南京路步行街","购物天堂","免费","地铁1号线","晚上霓虹灯璀璨"},
            {"迪士尼乐园","梦幻童话世界","475元","地铁11号线","提前下APP抢FP"},
            {"新天地","石库门里弄风情","免费","地铁10号线","适合拍照打卡"},
            {"田子坊","文艺小店聚集地","免费","地铁9号线","手工艺品值得买"},
        };
        if (city.contains("大理")) return new String[][]{
            {"洱海","环湖骑行赏苍山","免费","租电动车","环海西路风景绝美"},
            {"大理古城","漫步白族古城","免费","步行","人民路小店值得逛"},
            {"苍山","乘索道登顶","280元","景区直通车","带外套山顶凉"},
            {"喜洲古镇","探访白族民居","免费","打车20分钟","喜洲粑粑必尝"},
            {"双廊古镇","洱海边发呆","免费","拼车40分钟","海景咖啡馆很多"},
            {"崇圣寺三塔","观千年古塔","75元","公交","清晨钟声很治愈"},
            {"沙溪古镇","茶马古道驿站","免费","拼车1.5小时","每周五有集市"},
        };
        if (city.contains("成都")) return new String[][]{
            {"宽窄巷子","逛老成都巷子","免费","地铁4号线","掏耳朵体验很舒服"},
            {"大熊猫基地","看国宝大熊猫","55元","地铁3号线转公交","早上9点前到"},
            {"锦里古街","品三国文化","免费","地铁3号线","晚上红灯笼很美"},
            {"武侯祠","三国迷必去","50元","地铁3号线","建议请讲解"},
            {"杜甫草堂","诗人故居","60元","地铁4号线","幽静适合发呆"},
            {"青城山","道教名山","90元","高铁20分钟","前山问道后山观景"},
            {"春熙路","时尚购物中心","免费","地铁2号线","美女如云"},
        };
        if (city.contains("杭州")) return new String[][]{
            {"西湖","泛舟游湖","免费","地铁1号线","断桥残雪最美"},
            {"灵隐寺","千年古刹","45元","公交7路","心诚则灵"},
            {"雷峰塔","白蛇传说","40元","公交4路","俯瞰西湖全景"},
            {"西溪湿地","城市绿肺","80元","公交","坐摇橹船很惬意"},
            {"宋城","穿越回宋朝","320元","公交","千古情演出必看"},
            {"龙井村","品正宗龙井","免费","公交27路","清明前后最热闹"},
            {"河坊街","南宋古街","免费","步行","定胜糕葱包烩好吃"},
        };
        // 默认兜底
        String c = city;
        return new String[][]{
            {c+"古城","漫步古城感受历史","免费","步行","早起避开人流"},
            {c+"博物馆","了解"+c+"历史文化","30元","公交","周一闭馆注意"},
            {c+"公园","城市绿肺散步","免费","地铁","本地人常去"},
            {c+"山","登高望远","50元","打车","穿运动鞋"},
            {c+"湖","湖边漫步","免费","步行","日落时分最美"},
            {c+"老街","逛老街吃小吃","免费","公交","尝地道美食"},
            {c+"夜景","夜游"+c,"免费","步行","带相机拍夜景"},
        };
    }

    private String getFoodStreet(String city) {
        if (city.contains("北京")) return "簋街";
        if (city.contains("上海")) return "城隍庙美食街";
        if (city.contains("大理")) return "人民路美食街";
        if (city.contains("成都")) return "建设巷";
        if (city.contains("杭州")) return "河坊街美食街";
        if (city.contains("西安")) return "回民街";
        if (city.contains("重庆")) return "解放碑好吃街";
        if (city.contains("广州")) return "上下九";
        if (city.contains("深圳")) return "东门町";
        if (city.contains("南京")) return "夫子庙美食街";
        if (city.contains("武汉")) return "户部巷";
        if (city.contains("长沙")) return "坡子街";
        if (city.contains("厦门")) return "中山路";
        if (city.contains("三亚")) return "第一市场";
        return city + "夜市美食街";
    }

    // 严格 JSON 约束 — 所有 AI 请求的 system prompt 必须携带
    private static final String JSON_RULES =
        "【强制JSON规则-违反将导致解析失败】" +
        "1.必须输出合法JSON，以{开头}结尾 " +
        "2.所有字符串值必须用英文双引号\"包裹 " +
        "3.数组用[]包裹，元素间用英文逗号分隔 " +
        "4.对象用{}包裹，键值间用英文冒号:分隔 " +
        "5.禁止输出换行符\\n、禁止输出注释、禁止输出说明文字 " +
        "6.键名严格使用提供的字段名，禁止拼写错误 " +
        "7.tips数组示例：[\"贴士1\",\"贴士2\"]，每条贴士完整包裹在双引号内";

    /**
     * 调用 AI 生成 JSON — 含重试机制（最多2次重试）
     */
    private Map<String, Object> callAIForJson(String prompt) {
        return callAIForJsonWithRetry(prompt, 3, 1200);
    }

    /** 短 JSON（tips/budget）用低 token */
    private Map<String, Object> callAIForJsonShort(String prompt) {
        return callAIForJsonWithRetry(prompt, 2, 400);
    }

    private Map<String, Object> callAIForJsonWithRetry(String prompt, int retries, int maxTokens) {
        long start = System.currentTimeMillis();
        Exception lastErr = null;
        for (int attempt = 0; attempt < retries; attempt++) {
            if (attempt > 0) {
                log.info("AI JSON 重试 {}/{}", attempt + 1, retries);
                try { Thread.sleep(500); } catch (InterruptedException e) { break; }
            }
            try {
                ChatRequest req = ChatRequest.builder()
                    .model(model()).stream(false)
                    .temperature(attempt == 0 ? 0.3 : 0.6).maxTokens(maxTokens)
                    .messages(Arrays.asList(
                        ChatMessage.builder().role("system").content(JSON_RULES).build(),
                        ChatMessage.builder().role("user").content(prompt).build()
                    )).build();
                ChatResponse resp = webClient.post().uri(chatPath())
                    .contentType(MediaType.APPLICATION_JSON).bodyValue(req).retrieve()
                    .bodyToMono(ChatResponse.class).timeout(Duration.ofSeconds(90)).block(Duration.ofSeconds(95));
                if (resp == null || resp.getChoices() == null || resp.getChoices().isEmpty()) { log.warn("AI空响应"); continue; }
                String raw = resp.getChoices().get(0).getMessage().getContent();
                if (raw == null || raw.isBlank()) { log.warn("AI空content"); continue; }
                String json = raw.trim();
                json = json.replaceAll("(?s)^```(?:json)?\\s*", "").replaceAll("(?s)\\s*```$", "");
                int b = json.indexOf('{'), e = json.lastIndexOf('}');
                if (b < 0 || e <= b) { log.warn("无{}边界"); continue; }
                json = json.substring(b, e + 1);
                json = TextCleaner.cleanChunk(json);
                json = fixBrokenJson(json);
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = objectMapper.readValue(json, Map.class);
                    log.info("AI JSON OK ({}ms){}{}", System.currentTimeMillis() - start, attempt > 0 ? " 重试":"", "");
                    return map;
                } catch (Exception parseErr) {
                    lastErr = parseErr;
                    // trim 修复
                    String trial = json;
                    for (int trim = 1; trim <= 10 && trial.length() > 10; trim++) {
                        int last = trial.lastIndexOf('}');
                        if (last < 0) break;
                        trial = trial.substring(0, last);
                        StringBuilder sb = new StringBuilder(trial);
                        int oc = 0, cc = 0;
                        for (char ch : trial.toCharArray()) { if (ch=='{') oc++; if (ch=='}') cc++; }
                        while (cc++ < oc) sb.append('}');
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> m = objectMapper.readValue(sb.toString(), Map.class);
                            log.info("AI JSON trim修复({})", trim);
                            return m;
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) { lastErr = e; }
        }
        log.warn("AI JSON fail ({}ms,{}次): {}", System.currentTimeMillis() - start, retries,
            lastErr != null ? lastErr.getMessage() : "unknown");
        return null;
    }

    /** 修复 AI 输出残缺 JSON */
    private String fixBrokenJson(String s) {
        if (s == null || s.isEmpty()) return s;
        // 1. 中文标点 → 英文
        s = s.replace('：', ':').replace('，', ',').replace('"', '"').replace('"', '"');
        // 2. "key""value" → "key":"value"
        s = s.replaceAll("\"(\\w+)\"\\s*\"", "\"$1\":\"");
        // 3. "key"{ → "key":{  "key"[ → "key":[
        s = s.replaceAll("\"(\\w+)\"\\s*\\{", "\"$1\":{");
        s = s.replaceAll("\"(\\w+)\"\\s*\\[", "\"$1\":[");
        // 4. 修复 "key": 后直接跟字符串（缺引号）：tips数组内的独立字符串项
        // 匹配模式: "任意中文/英文": "值", 下一行又是 "xxx": "yyy" — 这是AI把数组写成对象了
        // 跳过，由callAIForJson尝试解析
        // 5. 字段名拼写纠错
        s = s.replace("\"dayitle\"", "\"dayTitle\"").replace("\"timelots\"", "\"timeSlots\"")
             .replace("\"timefay\"", "\"timeOfDay\"").replace("\"dration\"", "\"duration\"")
             .replace("\"transort\"", "\"transport\"").replace("\"attration\"", "\"attraction\"")
             .replace("\"actiity\"", "\"activity\"");
        // 6. "tips":["a","b"] → "tips":"a,b" (AI 误输出数组格式)
        s = s.replaceAll("\"tips\"\\s*:\\s*\\[([^\\]]*)\\]", "\"tips\":\"tips_placeholder\"");
        // 7. timeSlots 内缺 [
        s = s.replaceAll("\"timeSlots\":\\s*\\{", "\"timeSlots\":[{");
        // 9. 补缺失括号
        int oc = 0, cc = 0, ob = 0, cb = 0;
        for (char c : s.toCharArray()) { if (c=='{')oc++; if (c=='}')cc++; if (c=='[')ob++; if (c==']')cb++; }
        while (cb++ < ob) s += "]";
        while (cc++ < oc) s += "}";
        return s;
    }

    /* ==================== 景点图片搜索 ==================== */
    public String searchAttractionImage(String attractionName) {
        if (attractionName == null || attractionName.trim().isEmpty()) return "";

        String cacheKey = "image_" + attractionName;
        String cached = planCache.get(cacheKey);
        if (cached != null) return cached;

        try {
            String defaultImage = String.format(
                    "https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=%s&image_size=landscape_16_9",
                    java.net.URLEncoder.encode(attractionName + " 景点风景", "UTF-8"));
            planCache.put(cacheKey, defaultImage);
            return defaultImage;
        } catch (java.io.UnsupportedEncodingException e) {
            return "";
        }
    }
}
