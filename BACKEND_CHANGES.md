# 后端变更记录 (travel-java)

> Spring Boot 3.2.5 / Java 17 旅游规划后端应用

---

## 一、项目概览

基于 Spring Boot 构建的 AI 智能旅游助手后端，提供 REST API、AI 行程生成（SSE 流式）、地图集成、用户社交、酒店预订等功能。

### 技术栈

- **框架**: Spring Boot 3.2.5 + Spring Data JPA
- **数据库**: H2 File Mode（持久化到 `./data/` 目录）
- **认证**: JWT (jjwt 0.12.5) + BCrypt 密码加密
- **AI 集成**: Spring WebFlux（WebClient）+ SSE 流式推送
- **缓存**: Redis（限流）+ Caffeine（本地缓存）
- **地图**: 百度地图 API v2

### 分层架构

```
Controller (20个 REST 控制器)
    ↓
Service (17个业务服务)
    ↓
Repository (16个 JPA 数据仓库)
    ↓
Entity (16个 JPA 实体)
```

---

## 二、构建配置

### [.gitignore](travel-java/.gitignore)

忽略上传视频资源，防止大文件提交到仓库：

```gitignore
uploads/*.mp4
uploads/*.avi
uploads/*.mov
uploads/*.flv
```

### [pom.xml](travel-java/pom.xml)

Maven 项目配置，核心依赖：

| 依赖 | 用途 |
|------|------|
| `spring-boot-starter-web` | REST 控制器 |
| `spring-boot-starter-webflux` | WebClient + Flux SSE 流式 |
| `spring-boot-starter-validation` | 参数校验 |
| `spring-boot-starter-data-jpa` | ORM 持久层 |
| H2 Database | 文件模式数据库 |
| `jjwt-api/impl/jackson` 0.12.5 | JWT 令牌 |
| `spring-security-crypto` | BCrypt 密码哈希 |
| `spring-boot-starter-data-redis` | Redis 限流 |
| `caffeine` | 本地缓存 |

---

## 三、配置文件

### [application.yml](travel-java/src/main/resources/application.yml)（~113 行）

核心配置项：

- **服务端**: 端口 3200，UTF-8 编码，2GB 表单限制
- **文件上传**: 最大 1GB 单文件，2GB 请求
- **数据库**: H2 文件模式 `./data/travel_plans`，MySQL 兼容模式，JPA `ddl-auto=update`
- **Redis**: 可配置连接，用于限流
- **CORS**: 允许 `localhost:5173`、`localhost:5177`
- **AI 多供应商**: 支持 DeepSeek / OpenAI / Claude / Gemini / Custom，启动时自动检测第一个有效 API Key 的供应商
- **JWT**: 可配置密钥和过期时间（默认 24h）
- **百度地图**: 可配置 AK

### [application.yml.example](travel-java/src/main/resources/application.yml.example)

简化示例配置，附环境变量说明。

---

## 四、配置类（10 个）

### [AIProviderConfig.java](travel-java/src/main/java/org/example/traveljava/config/AIProviderConfig.java)

多 AI 供应商配置管理：
- `@ConfigurationProperties(prefix = "ai")` 绑定配置
- 支持 DeepSeek、OpenAI、Claude、Gemini、Custom 五类供应商
- 启动时自动扫描并按优先级激活第一个可用供应商
- 提供 `getActiveConfig()` / `getActiveBaseUrl()` / `getActiveApiKey()` / `getActiveModel()` 便捷方法

### [WebClientConfig.java](travel-java/src/main/java/org/example/traveljava/config/WebClientConfig.java)

WebClient 实例管理：
- Reactor Netty 连接池（100 最大连接、60s 获取超时、300s 响应超时）
- 按供应商构建独立 WebClient，支持非 Bearer 认证头（如 Claude 的 `x-api-key`）
- `AIWebClientManager` 支持运行时切换供应商

### [WebConfig.java](travel-java/src/main/java/org/example/traveljava/config/WebConfig.java)

- CORS 过滤器（允许凭证跨域）
- 静态资源映射 `/uploads/**` → 文件系统 `uploads/`

### [SecurityConfig.java](travel-java/src/main/java/org/example/traveljava/config/SecurityConfig.java)

- 注册 `RateLimitInterceptor` 到所有 `/api/**` 路径

### [SecurityHeaderFilter.java](travel-java/src/main/java/org/example/traveljava/config/SecurityHeaderFilter.java)

响应安全头过滤器：
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `X-XSS-Protection: 1; mode=block`
- `Strict-Transport-Security`
- `Referrer-Policy: no-referrer`
- `Content-Security-Policy`
- 移除 `Server` / `X-Powered-By` 头

### [GlobalExceptionHandler.java](travel-java/src/main/java/org/example/traveljava/config/GlobalExceptionHandler.java)

`@RestControllerAdvice` 全局异常处理：
- `AuthException` → 401
- `MaxUploadSizeExceededException` → 400
- `ClientAbortException` / `IOException` → 静默日志（客户端断开）
- `IllegalArgumentException` → 400
- `MethodArgumentNotValidException` → 400 + 字段级错误
- `NoHandlerFoundException` → 404
- 通用异常 → 500，Content-Type 感知

### [RedisConfig.java](travel-java/src/main/java/org/example/traveljava/config/RedisConfig.java)

RedisTemplate 配置（String 键序列化 + GenericJackson2JsonRedisSerializer 值序列化）。

### [RestTemplateConfig.java](travel-java/src/main/java/org/example/traveljava/config/RestTemplateConfig.java)

同步 HTTP 调用 Bean（百度地图服务使用）。

### [ThreadPoolConfig.java](travel-java/src/main/java/org/example/traveljava/config/ThreadPoolConfig.java)

`imageFetchExecutor` 守护线程池（核心 4、最大 8、队列 32），用于并行预加载景点图片。

---

## 五、工具类（3 个）

### [JwtUtil.java](travel-java/src/main/java/org/example/traveljava/util/JwtUtil.java)

JWT 创建和验证（HMAC-SHA 算法）：
- `generateToken(userId, username, role)`
- `extractUserId(token)` / `extractUsername(token)`
- `validateToken(token)`
- 自动补齐不足 32 字节的密钥

### [AuthUtils.java](travel-java/src/main/java/org/example/traveljava/util/AuthUtils.java)

认证工具：
- `requireUserId(authHeader, jwtUtil)` — 解析 Bearer Token，抛出 `AuthException`（由全局异常处理器捕获 → 401）

### [TextCleaner.java](travel-java/src/main/java/org/example/traveljava/util/TextCleaner.java)

AI 流式输出清洗：
- 移除控制字符
- 移除脏 Unicode 标记（三角/箭头/竖线符号）
- 压缩多余空格和换行

---

## 六、注解与拦截器

### [@RateLimit](travel-java/src/main/java/org/example/traveljava/annotation/RateLimit.java)

方法级限流注解：
- `max`：最大请求数（默认 100）
- `duration`：时间窗口秒数（默认 60）
- `key`：自定义键（默认请求 URI）

### [RateLimitInterceptor.java](travel-java/src/main/java/org/example/traveljava/interceptor/RateLimitInterceptor.java)

Redis 限流拦截器：
- 从代理头（`X-Forwarded-For` 等）提取客户端 IP
- Redis `INCR` + `EXPIRE` 实现滑动窗口
- Redis 不可用时优雅降级（允许所有请求）

---

## 七、VO / DTO

### VO（2 个）

| 类 | 说明 |
|----|------|
| [Result.java](travel-java/src/main/java/org/example/traveljava/vo/Result.java) | 统一响应体 `Result<T>`，code(0/-1)、message、data |
| [TravelRecommendVO.java](travel-java/src/main/java/org/example/traveljava/vo/TravelRecommendVO.java) | 旅行推荐请求：destination、budget、days、message |

### DTO（19 个）

| 类 | 说明 |
|----|------|
| `ChatMessage` | OpenAI 兼容消息格式 |
| `ChatRequest` | OpenAI 兼容请求（model、messages、stream、temperature） |
| `ChatResponse` | OpenAI 兼容响应 |
| `TravelPlanDTO` | 结构化行程计划（天计划 → 时间段 → 景点/活动/费用/交通） |
| `TripPlannerRequest` | AI 行程规划表单输入（含 `buildPreferencePrompt()`） |
| `TripGenerateRequest` | 简化生成请求 |
| `DailyTripDTO` | SSE 单日行程片段 |
| `BudgetDTO` | SSE 预算片段 |
| `TripTipsDTO` | SSE 贴士片段 |
| `GenerateProgressDTO` | SSE 进度事件（7 阶段进度） |
| `GenerateStep` | 枚举：7 个生成阶段及对应百分比 |
| `TaskCancelledException` | 任务取消异常 |
| `AttractionDTO` | 景点信息 |
| `CityDTO` | 城市数据（省份分组、洲际分组等嵌套结构） |
| `CostBreakdownDTO` | 费用明细 |
| `HotDestinationDTO` | 热门目的地 |
| `HotelDTO` | 酒店信息 |
| `MapMarkerDTO` | 地图标记 |
| `POIDetailDTO` / `POISuggestionDTO` | POI 详情/建议 |
| `SavedPlanRequest` | 保存计划请求 |
| `SceneImageDTO` | 场景图片 |

---

## 八、实体（16 个 JPA 实体）

| 实体 | 表名 | 核心字段 |
|------|------|----------|
| **User** | `users` | username, password(加密), nickname, avatar, bio, phone, email, role, level, points, followingCount, followersCount, notesCount, citiesVisited, totalDays, totalSpent |
| **TripPlan** | `trip_plans` | userId, destination, days, people, budget明细, planJson(CLOB), hotelIds, status |
| **SavedTravelPlan** | `saved_travel_plans` | userId, destination, days, budget, people, planJson, source |
| **Hotel** | `hotels` | name, city, district, address, lat/lng, pricePerNight, rating, imageUrl, amenities |
| **Landmark** | `landmarks` | name, city, type, lat/lng, description, iconUrl |
| **CityMaterial** | `city_material` | cityCode(6位GB码), cityName, thumbImg, bannerImg, tags, imgSource, materialLevel |
| **CityImage** | `city_images` | cityName(unique), imageUrl, source |
| **AttractionImage** | `attraction_images` | attractionName(unique), imageUrl, source |
| **Post** | `posts` | userId, content, images, likes, comments |
| **Comment** | `comments` | noteId, userId, content, image, video |
| **Note** | `notes` | userId, title, content, cover, tags, views, likes, comments, status |
| **NoteLike** | `note_likes` | noteId, userId (联合唯一约束) |
| **Order** | `orders` | userId, orderNo(unique), type, status, price + 类型特定字段 |
| **Favorite** | `favorites` | userId, targetId, targetType, targetName, targetCover (联合唯一约束) |
| **Follow** | `follows` | followerId, followingId (联合唯一约束) |
| **Feedback** | `feedbacks` | userId, type, content, images, contact, status, reply |
| **Coupon** | `coupons` | userId, value, minAmount, title, validUntil, category, status |

---

## 九、数据仓库（16 个 JPA Repository）

全部继承 `JpaRepository`，含领域特定查询方法：

| Repository | 核心查询方法 |
|------------|-------------|
| `UserRepository` | `findByUsername`, `existsByUsername`, `existsByPhone` |
| `TripPlanRepository` | `findByUserIdOrderByCreatedAtDesc` |
| `SavedTravelPlanRepository` | `findByUserIdOrderByCreatedAtDesc` |
| `HotelRepository` | `findByCityAndDistrict...`, `findByPricePerNightBetween` |
| `LandmarkRepository` | `findByCityAndType` |
| `CityImageRepository` | `findByCityName` |
| `CityMaterialRepository` | `findByCityCode`, `findByCityName` |
| `AttractionImageRepository` | `findByAttractionName` |
| `NoteRepository` | `findByUserIdOrderByCreatedAtDesc` |
| `NoteLikeRepository` | `findByNoteIdAndUserId`, `countByNoteId` |
| `CommentRepository` | `findByNoteIdOrderByCreatedAtDesc` |
| `PostRepository` | `findByUserIdOrderByCreatedAtDesc` |
| `OrderRepository` | `findByUserIdAndTypeOrderByCreatedAtDesc` |
| `FavoriteRepository` | `findByUserIdAndTargetTypeOrderByCreatedAtDesc` |
| `FollowRepository` | `findByFollowerIdAndFollowingId` |
| `FeedbackRepository` / `CouponRepository` | 标准 CRUD + 过滤查询 |

---

## 十、服务层（17 个 Service）

### [AIService.java](travel-java/src/main/java/org/example/traveljava/service/AIService.java)（核心，~1224 行）

所有 AI/LLM 交互的核心服务：

| 方法 | 说明 |
|------|------|
| `testConnection()` | 测试 AI API 连通性 |
| `generateTravelPlan(dest, budget, days)` | 非流式行程生成（带缓存） |
| `chat(messages)` | 非流式对话 |
| `streamChat(messages)` | 响应式 SSE 流式对话（WebClient Flux） |
| `streamTravelPlan(dest, budget, days)` | 流式行程生成（50 字符分段） |
| `generateStructuredTravelPlan(...)` | 结构化行程生成（JSON 输出 → TravelPlanDTO） |
| `streamPlannerTrip(req)` | AI 行程规划 Flux（心跳 15s、120s 超时） |
| `streamPlannerWithStages(req, consumer, taskId)` | 7 阶段进度推送 + 取消检测 |
| `streamGenerateDailyTrip(...)` | 逐天 AI 生成 → 预算 → 贴士，含兜底硬编码数据 |
| `callAIForJson(prompt)` / `callAIForJsonWithRetry()` | JSON 提取（最多 3 次重试、括号修复、常见字段名纠正） |
| `cancelTask(taskId)` / `removeTask(taskId)` | 线程安全的任务取消 |

### [BaiduMapService.java](travel-java/src/main/java/org/example/traveljava/service/BaiduMapService.java)（~765 行）

百度地图 API v2 集成：
- `getSuggestions(keyword)` — POI 自动补全（API 失败时兜底 mock 数据）
- `getPOIDetail(uid)` — POI 详情含照片
- `getHotDestinations()` — 热门城市列表（1 小时缓存）
- `getCityAttractions(city)` — 城市景点搜索
- `getNearbyAttractions(lat, lng)` — 周边搜索（5km）
- `getAttractionImageUrl(name, uid)` — 景点图片（百度 → 文字转图 → 本地缓存）

### 其他服务

| 服务 | 说明 |
|------|------|
| `UserService` | 注册（BCrypt）、登录（返回 JWT + 用户信息）、资料更新 |
| `CityService` | 国内/海外城市列表构建（省份/洲际分组 + Unsplash 图片） |
| `CityMaterialService` | 城市素材图片缓存管理 |
| `SceneImageService` | 多源图片查找（百度 → Bing → 文字转图默认图） |
| `HotelService` | 按城市/区域/价格范围搜索酒店 |
| `CostCalculationService` | 启发式费用计算 + AI 辅助估算 |
| `NoteService` | 游记 CRUD + 点赞切换 |
| `PostService` | 社区帖子 CRUD |
| `CommentService` | 游记评论 CRUD |
| `OrderService` | 订单 CRUD（含机票/酒店/门票类型特定字段） |
| `FavoriteService` | 收藏 CRUD（按类型筛选） |
| `FollowService` | 关注/取关（双向计数更新） |
| `CouponService` | 优惠券 CRUD + 使用 |
| `FeedbackService` | 反馈提交 |
| `SavedTravelPlanService` | AI 计划保存/读取/删除 |
| `VoiceToTextService` | 语音转文字（mock 实现） |

---

## 十一、控制器（20 个 REST Controller）

| 控制器 | 前缀 | 核心端点 |
|--------|------|----------|
| **TravelController** | `/api/travel` | `/health`, `/test-ai`, `/plan` (POST), `/plan/stream` (SSE), `/chat` (POST), `/chat/stream` (SSE), `/plan/structured`, `/image`, `/planner/stream` (SSE), `/planner/progress` (SSE 7阶段), `/planner/stream-detail` (SSE 逐天), `/planner/stop`, `/trip/generate/stream` (单端点SSE), `/trip/generate` (POST), `/trip/progress/{taskId}` (SSE), `/trip/stop/{taskId}` |
| **TripAIController** | `/api/trip/ai` | `/generateTrip`, `/optimizeRoute`, `/chat`, `/chat/stream` (SSE), `/generateRemark`, `/travelInspiration`, `/saveToPlan` |
| **AuthController** | `/api/auth` | `/register` (5次/分钟限流), `/login` (10次/分钟限流) |
| **UserController** | `/api/user` | `/profile` (GET/PUT), `/logout` |
| **CityController** | `/api/city` | `/domestic`, `/overseas`, `/search`, `/location`, `/hot`, `/image`, `/images/batch`, `/images/map`, `/materials/batch`, `/materials/clear`, `/attraction/images/map`, `/attraction/images/batch` |
| **MapController** | `/api/map` | `/suggestion`, `/detail`, `/hot-destinations`, `/city-attractions`, `/nearby-attractions`, `/landmarks`, `/metro-stations` |
| **MapScriptController** | `/api/map` | `/script` (代理百度地图 GL SDK，AK 未配置时返回 Leaflet 提示) |
| **HotelController** | `/api/hotel` | `/search` (GET), `/{id}` |
| **CostController** | `/api/cost` | `/breakdown` (POST), `/estimate` (GET) |
| **NoteController** | `/api/notes` | `/my`, `/{id}`, POST, PUT, DELETE, `/{id}/like`, `/count` |
| **PostController** | `/api/posts` | GET, POST, DELETE, `/{id}/like` |
| **CommentController** | — | `GET /api/notes/{noteId}/comments`, `POST`, `DELETE /api/comments/{id}` |
| **OrderController** | `/api/orders` | GET, POST, PUT `/{id}/status`, POST `/{id}/cancel`, `/count` |
| **FavoriteController** | `/api/favorites` | GET, POST, DELETE `/{id}`, `/count` |
| **FollowController** | `/api/user` | `/following`, `/followers`, POST `/follow/{id}`, POST `/unfollow/{id}`, `/following/count`, `/followers/count` |
| **CouponController** | `/api/coupons` | GET, `/count`, POST `/use/{id}` |
| **FeedbackController** | `/api/feedback` | GET, POST |
| **FileUploadController** | — | `POST /api/upload` (最大1GB), `GET /api/files/{filename}` (路径穿越防护) |
| **ImageProxyController** | `/api/proxy` | `GET /image?url=` (域名白名单代理) |
| **SceneImageController** | `/api/scene` | `GET /image?scenicName=&scenicDesc=` |
| **SavedTravelPlanController** | `/api/travel/plan` | `POST /save`, `GET /saved`, `GET /saved/{id}`, `DELETE /saved/{id}` |
| **VoiceController** | `/api/voice` | `POST /transcribe` (multipart 音频), `/health` |

---

## 十二、数据库迁移

### [V2__trip_map_init.sql](travel-java/src/main/resources/db/migration/V2__trip_map_init.sql)

Flyway 迁移脚本，创建 `hotels`、`landmarks`、`trip_plans` 表并插入种子数据：
- 北京酒店 10 家、上海酒店 8 家、巴黎酒店 6 家
- 北京地标 15 个、上海地标 12 个、巴黎地标 10 个

---

## 十三、关键架构决策

1. **AI 多供应商抽象**：`AIProviderConfig` 启动时自动检测可用供应商，无需硬编码
2. **SSE 流式推送**：所有 AI 实时响应使用 `SseEmitter` 和 `Flux<String>`
3. **任务取消机制**：`ConcurrentHashMap` cancel flags 跨控制器和服务共享
4. **H2 文件模式数据库**：零安装持久化
5. **Redis 限流**：Redis 不可用时优雅降级
6. **全局兜底/mock 数据**：AI、地图、图片均有兜底，无需外部 API Key 也能运行
7. **并行图片预加载**：结构化计划生成时使用 `imageFetchExecutor` 线程池
