# AI 智能旅游助手 — 全部知识点手册

> 覆盖前后端所有技术栈，按知识体系分类，便于系统学习

---

## 目录

1. [Java 高级特性](#1-java-高级特性)
2. [Spring Boot 核心](#2-spring-boot-核心)
3. [JPA / 数据库](#3-jpa--数据库)
4. [REST API 设计](#4-rest-api-设计)
5. [认证与安全](#5-认证与安全)
6. [SSE 流式与响应式编程](#6-sse-流式与响应式编程)
7. [AI / LLM 集成](#7-ai--llm-集成)
8. [异常处理与降级](#8-异常处理与降级)
9. [设计模式汇总](#9-设计模式汇总)
10. [Vue 3 Composition API](#10-vue-3-composition-api)
11. [Vue Router 路由设计](#11-vue-router-路由设计)
12. [Vite 工程化](#12-vite-工程化)
13. [CSS 高级技巧](#13-css-高级技巧)
14. [SSE 前端流式处理](#14-sse-前端流式处理)
15. [移动端适配](#15-移动端适配)
16. [Web API](#16-web-api)
17. [前端安全](#17-前端安全)
18. [状态管理与存储](#18-状态管理与存储)
19. [组件设计模式](#19-组件设计模式)
20. [性能优化](#20-性能优化)
21. [地图集成](#21-地图集成)
22. [Vant 4 使用技巧](#22-vant-4-使用技巧)

---

## 1. Java 高级特性

### 1.1 Stream API

```java
// 筛选 + 转换 + 收集
List<String> names = users.stream()
    .filter(u -> u.getStatus() == 1)
    .map(User::getNickname)
    .collect(Collectors.toList());

// 分页
List<Hotel> page = hotels.stream()
    .filter(h -> h.getPricePerNight().compareTo(max) <= 0)
    .skip(offset)
    .limit(pageSize)
    .toList();
```

**知识点**：`filter`、`map`、`collect`、`skip`、`limit`、`toList()`（Java 16+）、`forEach`、`findFirst`、`anyMatch`

### 1.2 Optional

```java
// 防御式 null 处理
User user = userRepository.findById(id)
    .orElseThrow(() -> new RuntimeException("用户不存在"));

// 条件执行
Optional.ofNullable(name).ifPresent(user::setNickname);
```

**知识点**：`ofNullable`、`orElseThrow`、`ifPresent`、`isPresent`、`map`、`orElse`

### 1.3 CompletableFuture 并行异步

```java
// 并行预加载图片
List<CompletableFuture<Void>> futures = dayPlans.stream()
    .flatMap(dp -> dp.getTimeSlots().stream())
    .map(slot -> CompletableFuture.runAsync(() -> {
        slot.setImageUrl(sceneImageService.getImage(slot.getAttraction()));
    }, imageFetchExecutor))
    .toList();

CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
    .get(5, TimeUnit.SECONDS);  // 超时控制
```

**知识点**：`runAsync`、`allOf`、`get(timeout)`、自定义线程池

### 1.4 BigDecimal 精确运算

```java
BigDecimal total = hotelCost.add(ticketCost).add(foodCost);
BigDecimal daily = total.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
```

**知识点**：`add`、`multiply`、`divide`、`setScale`、`compareTo`（禁止用 `equals`，因为 `0.0` vs `0.00` 不相等）

### 1.5 Enum 高级用法

```java
public enum GenerateStep {
    DEMAND_ANALYZE("需求分析", 10),
    DEST_INFO_QUERY("目的地信息查询", 15),
    // ...
    DAILY_ROUTE_ARRANGE("每日路线编排", 100);

    public final String label;
    public final int progress;
    GenerateStep(String label, int progress) { this.label = label; this.progress = progress; }
}

// 遍历枚举
for (GenerateStep step : GenerateStep.values()) { ... }
```

### 1.6 ConcurrentHashMap

```java
// 原子操作
private final ConcurrentHashMap<String, WebClient> clientCache = new ConcurrentHashMap<>();
WebClient client = clientCache.computeIfAbsent(provider, this::buildClient);

// 取消容器
private final ConcurrentHashMap<String, Boolean> cancelFlags = new ConcurrentHashMap<>();
cancelFlags.put(taskId, true);
if (Boolean.TRUE.equals(cancelFlags.get(taskId))) throw new TaskCancelledException(taskId);
```

### 1.7 Pattern 预编译

```java
private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");
private static final Pattern COMPRESS_SPACES = Pattern.compile(" {3,}");
// ...
cleaned = CONTROL_CHARS.matcher(text).replaceAll("");
```

### 1.8 Map.of / List.of 不可变集合

```java
Map.of("status", "ok", "endpointId", endpointId);
List.of("api.map.baidu.com", "picsum.photos", "trae-api-cn.mchost.guru");
```

---

## 2. Spring Boot 核心

### 2.1 启动类

```java
@SpringBootApplication  // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class TravelJavaApplication {
    public static void main(String[] args) {
        SpringApplication.run(TravelJavaApplication.class, args);
    }
}
```

### 2.2 @ConfigurationProperties 配置绑定

```java
@ConfigurationProperties(prefix = "ai")
public class AIProviderConfig {
    private Map<String, ProviderConfig> providers = new LinkedHashMap<>();
    // getter/setter...
}
```

`application.yml` 中 `ai.providers.deepseek.*` 自动映射到 `providers["deepseek"]`。

### 2.3 @PostConstruct 初始化回调

```java
@PostConstruct
public void autoDetect() {
    providers.forEach((name, config) -> {
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            // 激活第一个可用供应商
        }
    });
}
```

### 2.4 CommandLineRunner 启动后执行

```java
@Component
public class CityMaterialService implements CommandLineRunner {
    @Override
    public void run(String... args) {
        // 初始化城市素材数据
    }
}
```

### 2.5 WebMvcConfigurer 定制 MVC

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadPath.toUri().toString());
    }
}
```

**关键点**：实现接口而非继承，不破坏 Spring Boot 自动配置。

### 2.6 Filter vs Interceptor

| 层面 | 类型 | 用途 |
|------|------|------|
| Servlet | `Filter` | 安全响应头注入（`SecurityHeaderFilter`） |
| Spring MVC | `HandlerInterceptor` | 限流（`RateLimitInterceptor`） |
| Controller | `@ExceptionHandler` | 异常统一处理（`GlobalExceptionHandler`） |

### 2.7 WebClient 连接池

```java
ConnectionProvider provider = ConnectionProvider.builder("ai-provider-pool")
    .maxConnections(100)
    .pendingAcquireMaxCount(200)
    .maxIdleTime(Duration.ofSeconds(30))
    .build();
```

### 2.8 线程池配置

```java
@Bean("imageFetchExecutor")
public ThreadPoolExecutor imageFetchExecutor() {
    return new ThreadPoolExecutor(4, 8, 60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(32),
        new ThreadFactoryBuilder().setDaemon(true).setNameFormat("image-fetch-worker-%d").build(),
        new ThreadPoolExecutor.CallerRunsPolicy());  // 队列满时由调用线程执行
}
```

拒绝策略对比：

| 策略 | 行为 |
|------|------|
| `CallerRunsPolicy` | 调用线程执行任务（提供背压） |
| `AbortPolicy` | 抛出异常（默认） |
| `DiscardPolicy` | 静默丢弃 |
| `DiscardOldestPolicy` | 丢弃最旧任务 |

---

## 3. JPA / 数据库

### 3.1 核心注解

```java
@Entity
@Table(name = "users",
    uniqueConstraints = @UniqueConstraint(columnNames = {"username"})
)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(precision = 10, scale = 2)  // BigDecimal
    private BigDecimal totalSpent;
}
```

### 3.2 生命周期回调

```java
@PrePersist
protected void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
}

@PreUpdate
protected void onUpdate() {
    updatedAt = LocalDateTime.now();
}
```

### 3.3 Derived Query Methods

```java
// 方法命名自动生成 SQL
List<Hotel> findByCityAndPricePerNightBetween(String city, BigDecimal min, BigDecimal max);
Optional<User> findByUsername(String username);
long countByUserIdAndStatus(Long userId, String status);
boolean existsByUsername(String username);
void deleteByNoteIdAndUserId(Long noteId, Long userId);
```

**命名规则**：`findBy` + 字段名 + `And/Or` + 字段名 + `OrderBy` + 字段名 + `Desc/Asc` + `Between` + `After/Before`

### 3.4 联合唯一约束

```java
@Table(name = "follows", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"follower_id", "following_id"})
})
// 确保同一用户不能重复关注同一人
```

### 3.5 @Index 索引

```java
@Table(name = "city_images", indexes = {
    @Index(name = "idx_city_name", columnList = "cityName", unique = true)
})
```

### 3.6 H2 数据库配置

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/travel_plans;MODE=MySQL;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE
```

| 参数 | 含义 |
|------|------|
| `MODE=MySQL` | SQL 兼容 MySQL 语法 |
| `DB_CLOSE_DELAY=-1` | 进程存活期间保持连接 |
| `AUTO_SERVER=TRUE` | 允许多客户端连接 |

### 3.7 JPA 配置要点

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update    # 开发阶段自动建表，生产应改为 validate
    show-sql: false       # 生产关闭
    open-in-view: false   # 禁用 OSIV，防止长事务和连接泄漏
```

### 3.8 Flyway 数据库迁移

```sql
-- V2__trip_map_init.sql
CREATE TABLE IF NOT EXISTS hotels (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    price_per_night DECIMAL(10,2),
    rating DOUBLE,
    ...
);

-- H2 的 upsert 语法
MERGE INTO hotels KEY(id) VALUES
(1, '北京饭店', '北京', '东城区', 580.00, 4.5, ...);
```

---

## 4. REST API 设计

### 4.1 统一响应体

```java
public class Result<T> {
    private int code;      // 0=成功, -1=失败
    private String message;
    private T data;

    public static <T> Result<T> ok(T data) { ... }
    public static <T> Result<T> fail(String message) { ... }
}
```

### 4.2 参数校验

```java
// DTO 层
public class TravelRecommendVO {
    @NotBlank(message = "目的地不能为空")
    private String destination;

    @Min(value = 100, message = "预算不能低于100元")
    private Long budget;

    @Min(value = 1, message = "天数至少1天")
    private Integer days;
}

// Controller 层
@PostMapping("/plan")
public Result<String> plan(@Valid @RequestBody TravelRecommendVO vo) { ... }
```

### 4.3 参数绑定

| 注解 | 来源 | 示例 |
|------|------|------|
| `@RequestBody` | JSON Body | `TravelRecommendVO vo` |
| `@RequestParam` | Query String | `?city=北京` |
| `@PathVariable` | URL 路径 | `/saved/{id}` |
| `@RequestHeader` | HTTP 头 | `Authorization` |

### 4.4 RESTful URL 设计

```
GET    /api/notes          # 列表
POST   /api/notes          # 创建
GET    /api/notes/{id}     # 详情
PUT    /api/notes/{id}     # 更新
DELETE /api/notes/{id}     # 删除
POST   /api/notes/{id}/like  # 子资源操作
```

### 4.5 CORS 配置

```java
CorsFilter filter = new CorsFilter(source -> {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOriginPatterns(List.of("http://localhost:5173"));
    config.setAllowCredentials(true);
    config.setAllowedHeaders(List.of("*"));
    config.setMaxAge(3600L);
    source.registerCorsConfiguration("/api/**", config);
});
```

**知识点**：`setAllowedOriginPatterns`（支持通配符）、`setAllowCredentials`、`setMaxAge`（预检缓存）

---

## 5. 认证与安全

### 5.1 JWT 认证流程

```
客户端登录 → 服务端校验密码 → 签发 JWT → 客户端存储 Token
    ↓
后续请求 → Authorization: Bearer <token> → 拦截器验证 → 提取 userId
```

### 5.2 JWT 工具（jjwt 0.12.5）

```java
// 生成
String token = Jwts.builder()
    .subject(username)
    .claim("userId", userId)
    .claim("role", role)
    .issuedAt(new Date())
    .expiration(new Date(System.currentTimeMillis() + expiration))
    .signWith(key)
    .compact();

// 解析
Claims claims = Jwts.parser()
    .verifyWith(key)
    .build()
    .parseSignedClaims(token)
    .getPayload();
```

### 5.3 BCrypt 密码加密

```java
BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
String hashed = encoder.encode(rawPassword);       // 注册
boolean matched = encoder.matches(raw, hashed);    // 登录
```

### 5.4 Redis 滑动窗口限流

```java
public boolean preHandle(HttpServletRequest request, ...) {
    RateLimit annotation = handlerMethod.getMethodAnnotation(RateLimit.class);
    String key = "rate_limit:" + annotation.key() + ":" + uri + ":" + ip;

    Long count = redisTemplate.opsForValue().increment(key);
    if (count == 1) redisTemplate.expire(key, annotation.duration(), TimeUnit.SECONDS);

    if (count > annotation.max()) {
        response.setStatus(429);  // Too Many Requests
        response.getWriter().write("{\"code\":-1,\"message\":\"请求过于频繁\"}");
        return false;
    }
    return true;
}
```

### 5.5 真实 IP 获取链

```java
String ip = request.getHeader("X-Forwarded-For");      // Nginx 代理
if (ip == null) ip = request.getHeader("X-Real-IP");    // 备选
if (ip == null) ip = request.getRemoteAddr();            // 直连兜底
if (ip != null && ip.contains(",")) ip = ip.split(",")[0].trim();  // 取第一个
```

### 5.6 安全响应头

```java
response.setHeader("X-Content-Type-Options", "nosniff");        // 防 MIME 嗅探
response.setHeader("X-Frame-Options", "DENY");                  // 防点击劫持
response.setHeader("X-XSS-Protection", "1; mode=block");         // XSS 过滤器
response.setHeader("Strict-Transport-Security", "max-age=31536000");  // HSTS
response.setHeader("Referrer-Policy", "no-referrer-when-downgrade");
response.setHeader("Content-Security-Policy", "...");
```

### 5.7 路径穿越防护

```java
// 文件访问
Path uploadDir = Paths.get("uploads").toAbsolutePath().normalize();
if (!resolvedPath.startsWith(uploadDir)) {
    throw new SecurityException("非法路径访问");
}

// 图片代理
if (url.contains("..")) {
    return Result.fail("非法URL");
}
```

### 5.8 图片代理白名单

```java
private static final List<String> ALLOWED_DOMAINS = List.of(
    "api.map.baidu.com", "picsum.photos", "trae-api-cn.mchost.guru"
);
URL urlObj = new URL(url);
String host = urlObj.getHost();
if (ALLOWED_DOMAINS.stream().noneMatch(host::endsWith)) return fail;
```

---

## 6. SSE 流式与响应式编程

### 6.1 SSE 概念

Server-Sent Events：HTTP 长连接，服务端向客户端**单向**推送数据流，客户端通过 `EventSource` API 或 `fetch` + `ReadableStream` 接收。

### 6.2 SseEmitter（Servlet 方式）

```java
SseEmitter emitter = new SseEmitter(60000L);  // 60s 超时

emitter.onCompletion(() -> log("完成"));
emitter.onTimeout(() -> log("超时"));
emitter.onError(e -> log("错误"));

// 发送事件
emitter.send(SseEmitter.event().data(json).name("progress"));

// 发送心跳（强制刷新缓冲区）
emitter.send(SseEmitter.event().comment(""));

emitter.complete();
```

### 6.3 Flux（WebFlux 响应式）

```java
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> stream() {
    return Flux.interval(Duration.ofSeconds(15))  // 每 15s 心跳
        .map(tick -> "event: heartbeat\ndata: " + tick + "\n\n")
        .mergeWith(dataFlux)
        .doOnComplete(() -> log("流完成"))
        .doOnCancel(() -> log("客户端断开"))
        .timeout(Duration.ofSeconds(120));
}
```

### 6.4 WebClient 流式调用

```java
webClient.post()
    .uri(chatPath)
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(chatRequest)
    .retrieve()
    .bodyToFlux(String.class)
    .flatMap(line -> {
        if (line.startsWith("data: ")) {
            JsonNode node = objectMapper.readTree(line.substring(6));
            String content = node.at("/choices/0/delta/content").asText();
            return content.isEmpty() ? Flux.empty() : Flux.just(content);
        }
        return Flux.empty();
    })
    .onErrorResume(e -> Flux.just("[AI 服务暂时不可用]"));
```

### 6.5 SSE 响应头配置

```java
response.setHeader("Cache-Control", "no-cache");
response.setHeader("X-Accel-Buffering", "no");  // 禁用 Nginx 缓冲
response.setHeader("Connection", "keep-alive");
response.setBufferSize(0);  // 禁用响应缓冲
```

---

## 7. AI / LLM 集成

### 7.1 多供应商配置抽象

```yaml
ai:
  providers:
    deepseek:
      api-key: ${DEEPSEEK_API_KEY:}
      base-url: https://api.deepseek.com
      model: deepseek-v4-flash
    openai:
      api-key: ${OPENAI_API_KEY:}
      base-url: https://api.openai.com
      model: gpt-4o
    claude:
      api-key: ${CLAUDE_API_KEY:}
      base-url: https://api.anthropic.com
      model: claude-sonnet-4-20250514
      extra-headers:
        Authorization-Header: x-api-key           # 非标准认证头
        anthropic-version: "2023-06-01"
    gemini:
      api-key: ${GEMINI_API_KEY:}
      base-url: https://generativelanguage.googleapis.com
      model: gemini-2.5-flash
      chat-path: /v1beta/models/${ai.providers.gemini.model}:generateContent
    custom:
      api-key: ${CUSTOM_API_KEY:}
      base-url: https://your-proxy.com
      model: your-model
```

**核心思想**：所有供应商统一使用 OpenAI 兼容的 `/chat/completions` 协议，通过 `baseUrl` + `chatPath` 解决差异。

### 7.2 自动供应商检测

```java
@PostConstruct
public void autoDetect() {
    String envProvider = System.getenv("AI_PROVIDER");
    if (envProvider == null || "auto".equalsIgnoreCase(envProvider)) {
        // 按优先级扫描第一个有 key 的供应商
        providers.forEach((name, config) -> {
            if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
                if (activeProvider == null) activeProvider = name;
            }
        });
    } else {
        activeProvider = envProvider;  // 手动指定
    }
}
```

### 7.3 提示词工程

```java
// System Prompt 模板
String systemPrompt = """
    你是一个专业的旅游规划师。请严格按照以下规则生成行程：
    
    1. 使用 Markdown 格式
    2. 每天分为上午、下午、晚上三个时段
    3. 每个时段包含：景点名称、活动描述、预计停留时间、交通方式、费用预估
    4. 不要使用任何特殊符号（△▲▼▽◆◇ 等）
    5. 最后给出总体预算明细
    """;
```

### 7.4 JSON 输出 + 容错修复

```java
// 多策略修复
private String fixBrokenJson(String json) {
    // 策略1: 中文标点转英文
    json = json.replace("：", ":").replace("，", ",");
    // 策略2: 补齐大括号
    int opens = countChar(json, '{'), closes = countChar(json, '}');
    for (int i = 0; i < opens - closes; i++) json += "}";
    // 策略3: 字段名纠错
    json = json.replace("\"dayitle\"", "\"dayTitle\"");
    // 策略4: 从尾部截断尝试解析
    for (int i = json.length() - 1; i > 0; i--) {
        try { return objectMapper.readValue(json.substring(0, i) + "}", ...); }
        catch (Exception ignored) {}
    }
    return json;
}
```

### 7.5 @JsonProperty 解决命名映射

```java
public class ChatRequest {
    @JsonProperty("max_tokens")  // Java: maxTokens → JSON: max_tokens
    private Integer maxTokens;

    @JsonProperty("finish_reason")
    private String finishReason;
}
```

---

## 8. 异常处理与降级

### 8.1 全局异常处理器

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthUtils.AuthException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<?> handleAuth(AuthException e) { return Result.fail(e.getMessage()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getFieldErrors()
            .forEach(fe -> errors.put(fe.getField(), fe.getDefaultMessage()));
        return Result.fail("参数校验失败", errors);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleAll(Exception e) {
        log.error("未捕获异常", e);
        return Result.fail("服务器内部错误");
    }
}
```

### 8.2 三级降级策略

```java
// 模式：真实API → mock数据 → 静态默认值
public List<POISuggestionDTO> getSuggestions(String keyword) {
    try {
        String result = restTemplate.getForObject(baiduUrl, String.class);
        return parseResult(result);
    } catch (Exception e) {
        log.warn("百度API调用失败，使用mock数据");
        return mockSuggestions(keyword);
    }
}
```

### 8.3 Redis 不可用优雅降级

```java
try {
    Long count = redisTemplate.opsForValue().increment(key);
} catch (Exception e) {
    if (!redisWarned) {
        log.warn("Redis不可用，限流功能暂时关闭");
        redisWarned = true;
    }
    return true;  // 放行所有请求
}
```

---

## 9. 设计模式汇总

| 模式 | 应用位置 | 说明 |
|------|----------|------|
| **Builder** | `ChatRequest.Builder`, `ChatMessage.Builder` | 链式构建复杂对象 |
| **策略** | `AIProviderConfig` | 多 AI 供应商动态切换 |
| **单例** | `WebClient` 缓存池 | `ConcurrentHashMap.computeIfAbsent` |
| **工厂** | `Result.ok()`, `Result.fail()` | 静态工厂方法 |
| **门面** | `AIService` | 封装复杂的 AI 调用细节 |
| **模板方法** | `@PrePersist` / `@PreUpdate` | JPA 生命周期钩子 |
| **职责链** | `GlobalExceptionHandler` | 分层异常匹配 |
| **观察者** | SSE `onCompletion/onTimeout/onError` | 回调通知 |
| **代理** | `ImageProxyController` | 转发外部图片请求 |

---

## 10. Vue 3 Composition API

### 10.1 响应式基础

```javascript
import { ref, reactive, computed, watch } from 'vue'

const count = ref(0)                    // 基本类型
const user = reactive({ name: '' })     // 对象类型

const double = computed(() => count.value * 2)  // 计算属性

watch(() => props.modelValue, (newVal) => { ... })  // 监听
```

### 10.2 生命周期

```javascript
import { onMounted, onActivated, onDeactivated, onUnmounted } from 'vue'

onMounted(() => { /* DOM 挂载完成 */ })
onActivated(() => { /* keep-alive 激活 */ })
onDeactivated(() => { /* keep-alive 失活，清理 SSE/定时器 */ })
onUnmounted(() => { /* 组件销毁，清理事件监听 */ })
```

### 10.3 defineOptions（`<script setup>` 中设置组件名）

```javascript
defineOptions({ name: 'ChatView' })  // 供 keep-alive include 匹配
```

### 10.4 defineProps / defineEmits

```javascript
const props = defineProps({
    modelValue: { type: String, default: '' },
    placeholder: { type: String, default: '搜索' }
})

const emit = defineEmits(['update:modelValue', 'select'])
```

### 10.5 defineExpose（暴露方法给父组件）

```javascript
defineExpose({ snapTo, setState })
```

### 10.6 v-model 原理

```html
<!-- 写法1 -->
<SearchBar v-model="keyword" />

<!-- 等价于 -->
<SearchBar :modelValue="keyword" @update:modelValue="keyword = $event" />

<!-- 写法2：命名 v-model -->
<AIChatDialog v-model:visible="showDialog" />
<!-- 等价于 :visible="showDialog" @update:visible="showDialog = $event" -->
```

### 10.7 插槽

```html
<!-- 默认插槽 -->
<DragSheet><TripGeneratingState /></DragSheet>

<!-- 作用域插槽 -->
<router-view v-slot="{ Component }">
    <component :is="Component" />
</router-view>
```

### 10.8 nextTick

```javascript
import { nextTick } from 'vue'
await nextTick()  // 等待 DOM 更新完成
inputRef.value.blur()
```

---

## 11. Vue Router 路由设计

### 11.1 基础配置

```javascript
const router = createRouter({
    history: createWebHistory(),
    routes: [
        {
            path: '/',
            name: 'Home',
            component: () => import('../views/HomeView.vue'),  // 懒加载
            meta: { transition: 'fade', tab: 0 }
        }
    ]
})
```

### 11.2 路由懒加载 + 失败兜底

```javascript
component: () => import('../views/ChatView.vue')
    .catch(() => import('../views/HomeView.vue'))  // 加载失败降级
```

### 11.3 路由守卫

```javascript
router.beforeEach((to, from, next) => {
    const token = getToken()
    if (!token && !WHITELIST.includes(to.path)) {
        next({ path: '/login', query: { redirect: to.fullPath } })
    } else if (token && to.path === '/login') {
        next('/')  // 已登录不反复进登录页
    } else {
        next()
    }
})
```

### 11.4 滚动行为

```javascript
scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
        return { ...savedPosition, behavior: 'instant' }
    }
    return { top: 0 }
}
```

### 11.5 滚动位置记忆

```javascript
window.history.scrollRestoration = 'manual'  // 禁用浏览器原生恢复
const scrollPositions = {}

router.beforeEach((to, from) => {
    scrollPositions[from.path] = window.scrollY  // 离开时保存
})

// 配合 scrollBehavior 使用
if (to.meta.tab !== undefined) {
    return { top: scrollPositions[to.path] || 0 }
}
```

### 11.6 keep-alive + transition 组合

```html
<router-view v-slot="{ Component }">
    <transition :name="transitionName">
        <keep-alive :include="CACHED_VIEWS" :max="5">
            <component :is="Component" :key="route.path" />
        </keep-alive>
    </transition>
</router-view>
```

### 11.7 路由过渡动画

```css
.slide-left-enter-active, .slide-left-leave-active { transition: all 0.25s ease; }
.slide-left-enter-from { transform: translateX(100%); opacity: 0; }
.slide-left-leave-to { transform: translateX(-30%); opacity: 0; }
```

### 11.8 旧路由重定向

```javascript
{ path: '/chat', redirect: '/messages' },
{ path: '/planning', redirect: '/trip-map' },
{ path: '/:pathMatch(.*)*', redirect: '/' }  // 404 兜底
```

---

## 12. Vite 工程化

### 12.1 路径别名

```javascript
resolve: {
    alias: { '@': path.resolve(__dirname, 'src') }
}
```

### 12.2 代理配置（SSE 专用）

```javascript
server: {
    proxy: {
        '/api': {
            target: 'http://localhost:3200',
            changeOrigin: true,
            timeout: 1800000,  // 30分钟 SSE 长连接
            configure: (proxy) => {
                proxy.on('proxyReq', (proxyReq) => {
                    proxyReq.setHeader('Connection', 'keep-alive')
                    proxyReq.setHeader('Cache-Control', 'no-cache')
                })
            }
        }
    }
}
```

### 12.3 Vant 组件按需导入

```javascript
import Components from 'unplugin-vue-components/vite'
import { VantResolver } from '@vant/auto-import-resolver'

Components({ resolvers: [VantResolver()] })
```

### 12.4 postcss-pxtorem

```javascript
// postcss.config.js
module.exports = {
    plugins: {
        'postcss-pxtorem': {
            rootValue: 37.5,    // 375px 设计稿下 1rem=37.5px
            propList: ['*'],
            minPixelValue: 1,
            exclude: (file) => file.replace(/\\/g, '/').includes('node_modules')
        }
    }
}
```

### 12.5 环境变量

```bash
# .env.example
VITE_BAIDU_MAP_AK=your_key_here
# 只有 VITE_ 前缀的变量暴露给客户端代码
```

---

## 13. CSS 高级技巧

### 13.1 Design Token 系统

```css
:root {
    --primary: #8B5CF6;
    --primary-light: #A78BFA;
    --primary-gradient: linear-gradient(135deg, #8B5CF6, #C084FC);
    --radius-sm: 10px;
    --radius-md: 16px;
    --radius-pill: 28px;
    --shadow-purple: 0 4px 16px rgba(139, 92, 246, 0.3);
}
```

### 13.2 Glassmorphism 玻璃拟态

```css
.card-glass {
    background: rgba(255, 255, 255, 0.7);
    backdrop-filter: blur(16px);
    border: 1px solid rgba(255, 255, 255, 0.8);
}
```

### 13.3 关键帧动画（精选）

```css
/* 云朵漂移 */
@keyframes cloudDrift {
    0% { transform: translateX(100%); }
    100% { transform: translateX(-100%); }
}

/* 弹性弹跳 */
@keyframes elasticBounce {
    0%, 100% { transform: scale(1); }
    30% { transform: scale(1.15, 0.85); }
    50% { transform: scale(0.9, 1.1); }
}

/* 渐变流动 */
@keyframes gradientFlow {
    0% { background-position: 0% 50%; }
    100% { background-position: 200% 50%; }
}

/* 分层入场 */
@keyframes entranceUp {
    from { opacity: 0; transform: translateY(30px); }
    to { opacity: 1; transform: translateY(0); }
}
```

### 13.4 GPU 加速

```css
.gpu-accelerated {
    will-change: transform, opacity;
    transform: translateZ(0);
}
```

### 13.5 CSS Containment

```css
.card {
    contain: layout style;  /* 限制重排/重绘范围 */
}
```

### 13.6 三合一滚动条隐藏

```css
.hide-scrollbar {
    scrollbar-width: none;           /* Firefox */
    -ms-overflow-style: none;        /* IE/Edge */
}
.hide-scrollbar::-webkit-scrollbar { display: none; }  /* Chrome/Safari */
```

### 13.7 纯 CSS 图片骨架屏

```css
img {
    background: url("data:image/svg+xml,...") center/cover no-repeat;
}
img[src]:not([src=""]) {
    background: none;
    animation: fadeIn 0.3s ease;
}
```

### 13.8 Shimmer 加载动画

```css
@keyframes imgShimmer {
    0% { background-position: -200% 0; }
    100% { background-position: 200% 0; }
}
.skeleton-shimmer {
    background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
    background-size: 200% 100%;
    animation: imgShimmer 1.5s infinite;
}
```

### 13.9 安全区域适配

```css
.safe-bottom {
    padding-bottom: env(safe-area-inset-bottom, 16px);
}
```

### 13.10 `:focus-within` 联动

```css
.input-group:focus-within .input-icon { color: var(--primary); }
.input-group:focus-within .input-field { border-color: var(--primary); }
```

---

## 14. SSE 前端流式处理

### 14.1 fetch + ReadableStream 手动解析

```javascript
const response = await fetch(url, { headers, body, signal: controller.signal })
const reader = response.body.getReader()
const decoder = new TextDecoder()
let buffer = ''

while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() || ''  // 保留不完整的最后一行

    for (const line of lines) {
        if (line.startsWith('data: ')) {
            const data = line.slice(6).trim()
            if (data === '[DONE]' || data === '') continue
            onChunk(JSON.parse(data))
        }
    }
}
```

### 14.2 AbortController 取消

```javascript
const controller = new AbortController()
fetch(url, { signal: controller.signal })

// 取消
controller.abort()
reader?.cancel()
```

### 14.3 多事件类型分发

```javascript
// 服务端发送格式：event: hotel-update\ndata: {...}\n\n
if (line.startsWith('event: ')) {
    currentEvent = line.slice(7).trim()
} else if (line.startsWith('data: ')) {
    switch (currentEvent) {
        case 'progress-update': callbacks.onProgress?.(data); break
        case 'day-update': callbacks.onDay?.(data); break
        case 'generate-finish': callbacks.onFinish?.(data); break
        case 'stream-error': callbacks.onError?.(data); break
    }
}
```

### 14.4 自动重连

```javascript
async function startSSE(taskId, callbacks, retryCount = 0) {
    try {
        const response = await fetch(`${base}/api/travel/trip/progress/${taskId}`)
        // ... 读取流
    } catch (e) {
        if (retryCount < 3) {
            setTimeout(() => startSSE(taskId, callbacks, retryCount + 1), 2000 * (retryCount + 1))
        }
    }
}
```

---

## 15. 移动端适配

### 15.1 rem 自适应 JS 方案

```javascript
const MAX_WIDTH = 500
function setRootFontSize() {
    const width = Math.min(window.innerWidth, MAX_WIDTH)
    document.documentElement.style.fontSize = width / 10 + 'px'
}
window.addEventListener('resize', setRootFontSize)
setRootFontSize()
```

### 15.2 visualViewport 软键盘适配

```javascript
window.visualViewport.addEventListener('resize', () => {
    const viewport = window.visualViewport
    const keyboardOpen = window.innerHeight - viewport.height > 80
    if (keyboardOpen) {
        chatContainer.style.height = `${viewport.height - TAB_BAR_H}px`
    }
})
```

### 15.3 防止 iOS 自动缩放

```css
input, textarea { font-size: 16px; }
```

### 15.4 Touch Events 拖拽

```javascript
element.addEventListener('touchmove', (e) => {
    e.preventDefault()  // 需要 passive: false
}, { passive: false })

element.addEventListener('touchstart', handler, { passive: true })  // 不阻止默认
```

### 15.5 三段式吸附

```javascript
const SNAP_POINTS = [0.2, 0.65, 0.95]  // min, mid, max

function nearestSnap(percent) {
    return SNAP_POINTS.reduce((best, p) =>
        Math.abs(p - percent) < Math.abs(best - percent) ? p : best
    )
}
```

### 15.6 `100dvh` 动态视口

```css
.page { min-height: 100dvh; }  /* 移动端键盘弹起时自动缩小 */
```

### 15.7 `inputmode` 数字键盘

```html
<input inputmode="decimal" />   <!-- 带小数点 -->
<input inputmode="numeric" />   <!-- 纯数字 -->
```

---

## 16. Web API

### 16.1 SpeechRecognition 语音输入

```javascript
const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition
const recognition = new SpeechRecognition()
recognition.lang = 'zh-CN'
recognition.continuous = false
recognition.interimResults = false

recognition.onresult = (event) => {
    const transcript = event.results[0][0].transcript
    const isFinal = event.results[0].isFinal
}
recognition.onerror = (event) => { /* 'no-speech' | 'audio-capture' | 'not-allowed' | 'network' */ }
recognition.onend = () => { /* 自动结束 */ }

recognition.start()
recognition.abort()  // 强制停止
```

### 16.2 Clipboard API

```javascript
await navigator.clipboard.writeText(text)
```

### 16.3 剪贴板写入

```javascript
navigator.clipboard.writeText(text).then(() => showToast('已复制'))
```

### 16.4 `requestAnimationFrame`

```javascript
requestAnimationFrame(() => {
    element.style.transform = `translateY(${offset}px)`
})
```

### 16.5 动态 Script 加载

```javascript
function loadScript(url) {
    return new Promise((resolve, reject) => {
        const script = document.createElement('script')
        script.src = url
        script.onload = resolve
        script.onerror = reject
        document.head.appendChild(script)
    })
}
```

### 16.6 `URLSearchParams`

```javascript
const params = new URLSearchParams({ city, keyword, page })
fetch(`/api/search?${params}`)
```

---

## 17. 前端安全

### 17.1 XSS 过滤

```javascript
function sanitizeHtml(str) {
    const map = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#x27;', '/': '&#x2F;', '`': '&#x60;', '=': '&#x3D;' }
    return str.replace(/[&<>"'/`=]/g, (ch) => map[ch])
}

function filterXss(content) {
    return content
        .replace(/<script[\s\S]*?<\/script>/gi, '')
        .replace(/<iframe[\s\S]*?<\/iframe>/gi, '')
        .replace(/javascript:/gi, '')
        .replace(/on\w+\s*=\s*"[^"]*"/gi, '')
}
```

### 17.2 图片代理白名单

```javascript
const ALLOWED_DOMAINS = ['api.map.baidu.com', 'map.baidu.com', 'picsum.photos']

function getProxyImageUrl(url) {
    try {
        const host = new URL(url).hostname
        if (!ALLOWED_DOMAINS.some(d => host.endsWith(d))) {
            return DEFAULT_PLACEHOLDER
        }
        return `/api/proxy/image?url=${encodeURIComponent(url)}`
    } catch { return DEFAULT_PLACEHOLDER }
}
```

### 17.3 输入校验

```javascript
function validateInput(input, maxLength = 500) {
    if (!input || input.length > maxLength) return false
    const DANGEROUS = /<script|javascript:|on\w+=|<iframe|<embed|<object|<svg/i
    return !DANGEROUS.test(input)
}
```

---

## 18. 状态管理与存储

### 18.1 无 Pinia 的 reactive 单例

```javascript
// stores/trip.js
import { reactive } from 'vue'

const state = reactive({
    phase: 'form',
    progress: 0,
    planData: null,
    stepList: []
})

export function useTripStore() {
    function updateProgress(stepName, progress) {
        state.currentStep = stepName
        state.progress = progress
    }
    function resetState() { Object.assign(state, getDefaultState()) }
    return { state, updateProgress, resetState }
}
```

### 18.2 多账户 localStorage 隔离

```javascript
// 键命名规范
const KEY = `account:${username}`

function getAccountData(username, key) {
    const all = JSON.parse(localStorage.getItem(`account:${username}`) || '{}')
    return key ? all[key] : all
}

function setAccountData(username, key, value) {
    const all = getAccountData(username)
    all[key] = value
    localStorage.setItem(`account:${username}`, JSON.stringify(all))
}
```

### 18.3 旧数据迁移

```javascript
function migrateIfNeeded(username) {
    const oldKey = 'travel_chat_sessions'
    const newKey = `account:${username}`
    const oldData = localStorage.getItem(oldKey)
    if (oldData && !localStorage.getItem(`_migrated_${username}`)) {
        const all = JSON.parse(localStorage.getItem(newKey) || '{}')
        all.chatSessions = JSON.parse(oldData)
        localStorage.setItem(newKey, JSON.stringify(all))
        localStorage.setItem(`_migrated_${username}`, '1')
    }
}
```

### 18.4 ID 生成

```javascript
function generateId(prefix) {
    return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
}
```

---

## 19. 组件设计模式

### 19.1 v-model 双向绑定组件

```javascript
// SearchBar.vue
const props = defineProps({ modelValue: String, placeholder: { type: String, default: '搜索' } })
const emit = defineEmits(['update:modelValue', 'select'])

// 内部变更时
emit('update:modelValue', newValue)
```

### 19.2 计算属性代理 v-model

```javascript
const visible = computed({
    get: () => props.modelValue,
    set: (val) => emit('update:modelValue', val)
})
```

### 19.3 defineExpose 暴露方法

```javascript
// DragSheet.vue
function snapTo(percent) { /* ... */ }
function setState(open) { /* ... */ }
defineExpose({ snapTo, setState })

// 父组件
const sheetRef = ref(null)
sheetRef.value.snapTo(0.65)
```

### 19.4 click-outside 关闭

```javascript
function handleClickOutside(e) {
    if (panelRef.value && !panelRef.value.contains(e.target)) {
        show.value = false
    }
}
onMounted(() => document.addEventListener('click', handleClickOutside))
onUnmounted(() => document.removeEventListener('click', handleClickOutside))
```

### 19.5 防抖搜索

```javascript
let timer = null
watch(() => props.modelValue, (val) => {
    clearTimeout(timer)
    timer = setTimeout(() => fetchSuggestions(val), 300)
})
```

---

## 20. 性能优化

### 20.1 keep-alive 缓存

```html
<keep-alive :include="['HomeView', 'ChatView']" :max="5">
    <component :is="Component" />
</keep-alive>
```

### 20.2 路由防抖

```javascript
let lastClick = 0
function handleTabClick(idx) {
    const now = Date.now()
    if (now - lastClick < 300) return  // 300ms 防抖
    lastClick = now
    router.push(TABS[idx].path)
}
```

### 20.3 SSE 发送节流

```javascript
let lastSendTime = 0
function throttledSend(data) {
    const now = Date.now()
    if (now - lastSendTime >= 500) {
        emitter.send(SseEmitter.event().data(data))
        lastSendTime = now
    }
}
```

### 20.4 并行请求容错

```javascript
const results = await Promise.allSettled([
    fetch('/api/city/hot'),
    fetch('/api/travel/plan/saved'),
    fetch('/api/notes/my')
])
// 部分失败不影响其他结果展示
results.forEach((r, i) => {
    if (r.status === 'fulfilled') handleData(r.value, i)
})
```

### 20.5 乐观更新

```javascript
// 先更新 UI
item.liked = true
likeCount.value++

try {
    await api.like(item.id)
} catch {
    // 失败回滚
    item.liked = false
    likeCount.value--
}
```

### 20.6 图片懒加载

```html
<img loading="lazy" decoding="async" @error="e => e.target.style.opacity = '0'" />
```

---

## 21. 地图集成

### 21.1 百度地图动态加载

```javascript
function loadBaiduMap() {
    if (window.BMapGL) return
    if (window._baiduMapLoading) return  // 防止重复加载
    window._baiduMapLoading = true

    const script = document.createElement('script')
    script.src = '/api/map/script'  // AK 在后端，不暴露前端
    document.head.appendChild(script)

    const checkReady = setInterval(() => {
        if (window.BMapGL) {
            clearInterval(checkReady)
            initMap()
        }
    }, 100)

    setTimeout(() => clearInterval(checkReady), 3000)  // 超时
}
```

### 21.2 Leaflet 降级

```javascript
if (window._baiduMapUnavailable) {
    const script = document.createElement('script')
    script.src = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'
    document.head.appendChild(script)
    script.onload = () => {
        map = L.map('map').setView([39.9, 116.4], 12)
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png').addTo(map)
    }
}
```

### 21.3 地图实例单例

```javascript
let mapInstance = null  // 模块级变量，切换页面不销毁

function initMap() {
    if (mapInstance) return mapInstance
    mapInstance = new BMapGL.Map('map-container')
    return mapInstance
}
```

---

## 22. Vant 4 使用技巧

### 22.1 命令式 API 的 CSS 问题

```javascript
// main.js 中必须显式导入
import 'vant/lib/index.css'
// 因为 unplugin-vue-components 只处理模板中使用的组件
// 无法为 Toast/Dialog 这种函数式 API 自动注入 CSS
```

### 22.2 常用命令式 API

```javascript
import { showToast, showLoadingToast, closeToast, showDialog, showSuccessToast } from 'vant'

showToast({ message: '操作成功', position: 'middle', duration: 2000 })
const toast = showLoadingToast({ message: '加载中...', forbidClick: true, loadingType: 'spinner' })
closeToast()
showDialog({ title: '提示', message: '确认删除？' }).then(() => { /* 确认 */ })
```

### 22.3 样式覆盖

```css
/* 覆盖 Vant 默认 z-index */
.van-toast { z-index: 10000 !important; }

/* 穿透 scoped 修改 Vant 内部样式 */
:deep(.van-field__control) {
    font-size: 15px;
    color: var(--text-primary);
}
```

### 22.4 postcss-pxtorem 兼容

```javascript
// Vant 组件样式不应被 pxtorem 二次转换
exclude: (file) => file.replace(/\\/g, '/').includes('node_modules')
```

---

## 附录：关键文件索引

### 后端核心文件

| 文件 | 行数 | 核心知识点 |
|------|------|-----------|
| `AIService.java` | 1224 | 多供应商、SSE、JSON 修复、并行图片、提示词工程 |
| `TravelController.java` | 868 | SseEmitter、Flux、ConcurrentHashMap、异步线程 |
| `BaiduMapService.java` | 765 | RestTemplate、三级降级、内存缓存+过期 |
| `GlobalExceptionHandler.java` | ~80 | 分层异常匹配、ClientAbort 处理 |
| `WebClientConfig.java` | ~80 | Reactor Netty 连接池、多供应商 WebClient |
| `AIProviderConfig.java` | ~100 | @ConfigurationProperties、自动检测 |
| `RateLimitInterceptor.java` | ~70 | Redis 滑动窗口、IP 获取链、优雅降级 |
| `SecurityHeaderFilter.java` | ~50 | HTTP 安全响应头 |

### 前端核心文件

| 文件 | 行数 | 核心知识点 |
|------|------|-----------|
| `HomeView.vue` | 2566 | 全量 Composition API、乐观更新、瀑布流、骨架屏 |
| `LoginView.vue` | 1360 | 表单校验、Ken Burns 动画、多账号初始化 |
| `CommunityView.vue` | 1115 | reactive 键值存储、scroll.passive |
| `AIChatDialog.vue` | 1056 | v-model 组件、markdown-it、SpeechRecognition |
| `TripMapView.vue` | 767 | 地图动态加载、Leaflet 降级、DragSheet 集成 |
| `tripNew.js` | 260 | AbortController、多事件分发、自动重连 |
| `userAccountStorage.js` | 214 | 多账号隔离、旧数据迁移 |
| `App.vue` | 269 | keep-alive、过渡动画、Tab 防抖、GPU 加速 |
| `style.css` | 447 | Design Token、13 套动画、纯 CSS 骨架屏 |
| `DragSheet.vue` | 236 | Touch Events、三段吸附、滚动隔离 |
| `router/index.js` | 282 | 懒加载、滚动记忆、路由守卫、白名单 |
