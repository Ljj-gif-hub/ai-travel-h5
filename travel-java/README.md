# AI 智能旅游助手 - 后端

> 基于 Spring Boot 3.2.5 / Java 17 的 AI 旅游助手后端服务，提供多供应商 AI 行程生成（SSE 流式）、百度地图集成、用户社交、酒店预订等核心功能。

## 📱 项目简介

AI 智能旅游助手后端服务是整个应用的核心引擎，负责：

- **多供应商 AI 集成**：支持 DeepSeek / OpenAI / Claude / Gemini / Custom 五类 LLM，启动时自动检测可用供应商
- **SSE 流式行程生成**：7 阶段进度推送 + 逐天行程生成，支持任务取消
- **百度地图 API**：POI 搜索、热门目的地、城市景点、周边搜索
- **用户社交系统**：游记发布/点赞/评论、关注/粉丝、收藏、反馈
- **电商功能**：优惠券管理、订单系统（机票/酒店/门票）
- **安全防护**：JWT 认证、Redis 限流、XSS 过滤、安全响应头、路径穿越防护

## 🛠️ 技术栈清单

| 分类 | 技术 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 3.2.5 |
| 语言 | Java | 17 |
| 数据库 | H2 File Mode（持久化） | — |
| ORM | Spring Data JPA | — |
| 响应式 | Spring WebFlux（WebClient + Flux） | — |
| 认证 | jjwt 0.12.5 + BCrypt | — |
| 限流 | Redis | — |
| 本地缓存 | Caffeine | — |
| AI 服务 | DeepSeek / OpenAI / Claude / Gemini / 自定义 | — |
| 地图服务 | 百度地图开放 API v2 | — |
| 构建工具 | Maven | — |

## 🎬 功能演示

### AI 行程规划演示

https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-server/main/docs/ai-planner-demo.mp4

### 百度地图数据获取演示

> 📍 后端通过百度地图开放 API 获取 POI 数据、景点信息、热门目的地等地理数据，支持地点联想搜索、周边景点查询、城市地标/地铁站数据获取。


## 🚀 环境启动步骤

### 前置条件

- JDK >= 17
- Maven >= 3.8.0
- （可选）Redis 服务（用于限流，不可用时自动降级）

### 安装依赖

```bash
cd travel-java
mvn clean install
```

### 配置环境变量

```bash
# 复制配置模板
cp src/main/resources/application.yml.example src/main/resources/application.yml

# 编辑 application.yml，填写以下必填项：
# - ai.{provider}.api-key: 至少配置一个 AI 供应商的 API Key
# - spring.jwt.secret: JWT 密钥
# - baidu.map.ak: 百度地图 AK（服务端类型）
```

### AI 供应商配置

支持以下供应商，在 `application.yml` 中配置任意一个即可（启动时自动检测）：

| 供应商 | 配置前缀 | 说明 |
|--------|----------|------|
| DeepSeek | `ai.deepseek` | 默认推荐，性价比高 |
| OpenAI | `ai.openai` | GPT 系列模型 |
| Claude | `ai.claude` | Anthropic Claude |
| Gemini | `ai.gemini` | Google Gemini |
| Custom | `ai.custom` | 任意 OpenAI 兼容代理 |

### 启动开发服务器

```bash
mvn spring-boot:run
```

服务启动后访问 http://localhost:3200。

### 打包部署

```bash
mvn clean package
java -jar target/travel-java-1.0.0.jar
```

### 健康检查

```bash
curl http://localhost:3200/api/travel/health
```

## 📁 项目结构

```
travel-java/
├── src/main/java/org/example/traveljava/
│   ├── TravelJavaApplication.java     # 启动类
│   ├── annotation/                    # 自定义注解
│   │   └── RateLimit.java             # 限流注解
│   ├── config/                        # 配置类（10个）
│   │   ├── AIProviderConfig.java      # 多供应商 AI 配置（自动检测）
│   │   ├── WebClientConfig.java       # WebClient 连接池管理
│   │   ├── WebConfig.java             # CORS + 静态资源映射
│   │   ├── SecurityConfig.java        # 拦截器注册
│   │   ├── SecurityHeaderFilter.java  # 安全响应头
│   │   ├── GlobalExceptionHandler.java # 全局异常处理
│   │   ├── RedisConfig.java           # Redis 配置
│   │   ├── RestTemplateConfig.java    # RestTemplate
│   │   └── ThreadPoolConfig.java      # 线程池（图片预加载）
│   ├── controller/                    # REST API 控制器（20个）
│   │   ├── TravelController.java      # 行程规划 API（核心 SSE 流式）
│   │   ├── TripAIController.java      # AI 行程规划
│   │   ├── AuthController.java        # 用户认证（限流）
│   │   ├── UserController.java        # 用户管理
│   │   ├── CityController.java        # 城市数据
│   │   ├── MapController.java         # 百度地图 API
│   │   ├── MapScriptController.java   # 地图脚本代理
│   │   ├── HotelController.java       # 酒店搜索
│   │   ├── CostController.java        # 费用计算
│   │   ├── NoteController.java        # 游记 CRUD + 点赞
│   │   ├── PostController.java        # 社区帖子
│   │   ├── CommentController.java     # 评论管理
│   │   ├── OrderController.java       # 订单管理
│   │   ├── FavoriteController.java    # 收藏管理
│   │   ├── FollowController.java      # 关注/粉丝
│   │   ├── CouponController.java      # 优惠券
│   │   ├── FeedbackController.java    # 用户反馈
│   │   ├── FileUploadController.java  # 文件上传
│   │   ├── ImageProxyController.java  # 图片代理
│   │   ├── SceneImageController.java  # 场景图片
│   │   ├── SavedTravelPlanController.java # 行程保存
│   │   └── VoiceController.java       # 语音转文字
│   ├── service/                       # 业务逻辑层（17个）
│   │   ├── AIService.java             # 核心 AI 服务（1224行）
│   │   ├── BaiduMapService.java       # 百度地图服务（765行）
│   │   ├── UserService.java           # 用户服务
│   │   ├── CityService.java           # 城市服务
│   │   ├── CityMaterialService.java   # 城市素材管理
│   │   ├── SceneImageService.java     # 多源图片查找
│   │   ├── HotelService.java          # 酒店服务
│   │   ├── CostCalculationService.java # 费用估算
│   │   ├── NoteService.java           # 游记服务
│   │   ├── PostService.java           # 帖子服务
│   │   ├── CommentService.java        # 评论服务
│   │   ├── OrderService.java          # 订单服务
│   │   ├── FavoriteService.java       # 收藏服务
│   │   ├── FollowService.java         # 关注服务
│   │   ├── CouponService.java         # 优惠券服务
│   │   ├── FeedbackService.java       # 反馈服务
│   │   └── VoiceToTextService.java    # 语音转文字
│   ├── repository/                    # 数据访问层（16个 JPA Repository）
│   ├── entity/                        # 数据库实体（16个）
│   ├── dto/                           # 数据传输对象（19个）
│   ├── vo/                            # 视图对象（2个）
│   │   ├── Result.java                # 统一响应封装
│   │   └── TravelRecommendVO.java     # 推荐请求
│   ├── interceptor/                   # 拦截器
│   │   └── RateLimitInterceptor.java  # Redis 滑动窗口限流
│   └── util/                          # 工具类
│       ├── JwtUtil.java               # JWT 令牌
│       ├── AuthUtils.java             # 认证工具
│       └── TextCleaner.java           # AI 输出清洗
├── src/main/resources/
│   ├── application.yml                # 应用配置（113行）
│   ├── application.yml.example        # 配置模板
│   └── db/migration/
│       └── V2__trip_map_init.sql      # 数据库初始化（酒店+地标种子数据）
├── uploads/                           # 上传文件目录（视频已 gitignore）
├── pom.xml                            # Maven 配置
└── .gitignore                         # 忽略配置（含视频文件过滤）
```

## 🔌 API 接口列表

### 核心 - 行程规划

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/travel/plan` | POST | 非流式生成行程规划 |
| `/api/travel/plan/stream` | POST | 流式生成行程规划 (SSE) |
| `/api/travel/plan/structured` | POST | 生成结构化行程 (JSON) |
| `/api/travel/planner/stream` | POST | AI 行程规划 Flux 流式 |
| `/api/travel/planner/progress` | POST | 7 阶段进度 SSE |
| `/api/travel/planner/stream-detail` | POST | 逐天生成 SSE |
| `/api/travel/planner/stop` | POST | 停止生成 |
| `/api/travel/trip/generate/stream` | POST | 单端点 SSE 行程生成 |
| `/api/travel/trip/generate` | POST | 创建生成任务 |
| `/api/travel/trip/progress/{taskId}` | GET | 订阅任务进度 SSE |
| `/api/travel/trip/stop/{taskId}` | POST | 取消任务 |

### 核心 - AI 对话

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/travel/chat` | POST | 非流式 AI 对话 |
| `/api/travel/chat/stream` | POST | 流式 AI 对话 (SSE) |
| `/api/travel/recommend` | POST | 旅游推荐 |

### 行程 AI

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/trip/ai/generateTrip` | POST | 生成行程 |
| `/api/trip/ai/optimizeRoute` | POST | 优化路线 |
| `/api/trip/ai/chat` | POST | 行程对话 |
| `/api/trip/ai/chat/stream` | POST | 行程对话流式 |
| `/api/trip/ai/saveToPlan` | POST | 保存为计划 |

### 用户认证

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/auth/register` | POST | 用户注册（5次/分钟限流） |
| `/api/auth/login` | POST | 用户登录（10次/分钟限流） |
| `/api/user/profile` | GET/PUT | 获取/更新个人信息 |
| `/api/user/logout` | POST | 退出登录 |

### 地图数据

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/map/suggestion` | GET | 地点联想搜索 |
| `/api/map/detail` | GET | POI 详情查询 |
| `/api/map/hot-destinations` | GET | 热门目的地列表 |
| `/api/map/city-attractions` | GET | 城市景点列表 |
| `/api/map/nearby-attractions` | GET | 周边景点搜索 |
| `/api/map/landmarks` | GET | 城市地标 |
| `/api/map/metro-stations` | GET | 城市地铁站 |

### 社交功能

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/notes/*` | CRUD | 游记发布、查看、点赞 |
| `/api/notes/{noteId}/comments` | GET/POST | 游记评论 |
| `/api/posts` | GET/POST | 社区帖子 |
| `/api/user/follow/{id}` | POST | 关注用户 |
| `/api/user/unfollow/{id}` | POST | 取关用户 |
| `/api/user/following` | GET | 关注列表 |
| `/api/user/followers` | GET | 粉丝列表 |

### 电商

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/orders` | CRUD | 订单管理 |
| `/api/favorites` | CRUD | 收藏管理 |
| `/api/coupons` | GET | 优惠券列表 |
| `/api/hotel/search` | GET | 酒店搜索 |
| `/api/cost/breakdown` | POST | 费用明细 |

### 其他

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/upload` | POST | 文件上传（最大1GB） |
| `/api/files/{filename}` | GET | 文件访问 |
| `/api/proxy/image` | GET | 图片代理（域名白名单） |
| `/api/scene/image` | GET | 场景图片搜索 |
| `/api/voice/transcribe` | POST | 语音转文字 |
| `/api/city/*` | GET | 城市数据（国内/海外/搜索） |
| `/api/feedback` | GET/POST | 用户反馈 |
| `/api/travel/plan/save` | POST | 保存行程规划 |
| `/api/travel/plan/saved` | GET | 获取已保存行程 |

## 🔒 安全机制

- **JWT 认证**：`AuthUtils.requireUserId()` 解析 Bearer Token，`GlobalExceptionHandler` 统一返回 401
- **Redis 限流**：`@RateLimit` 注解 + `RateLimitInterceptor` 滑动窗口限流，Redis 不可用时优雅降级
- **安全响应头**：`SecurityHeaderFilter` 注入 CSP、XSS-Protection、HSTS、Referrer-Policy 等
- **XSS 防护**：输入参数校验（`@Valid` + Jakarta Bean Validation）
- **路径穿越防护**：文件访问端点过滤 `..` 路径
- **图片代理白名单**：仅允许 `api.map.baidu.com`、`picsum.photos`、`trae-api-cn.mchost.guru` 三个域名

## 🗺️ 后续迭代规划

### 短期目标

- [x] 多 AI 供应商支持（DeepSeek/OpenAI/Claude/Gemini/Custom）
- [x] Redis 限流（`@RateLimit` 注解 + 优雅降级）
- [x] 安全响应头过滤器
- [ ] 添加接口文档（Swagger/OpenAPI）
- [ ] 完善日志系统和监控指标

### 中期目标

- [ ] 接入 RAG 知识库整合旅游攻略
- [ ] 行程分享功能
- [ ] 支持 MySQL/PostgreSQL 多数据库
- [ ] 消息队列异步处理

### 长期目标

- [ ] 分布式部署和负载均衡
- [ ] 智能推荐算法
- [ ] 接入机票、酒店第三方预订 API

## 📄 许可证

MIT License

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！
