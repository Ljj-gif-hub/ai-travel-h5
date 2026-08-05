# AI 智能旅游助手 - 后端

> 基于 Spring Boot 3.2 / Java 17 的 RESTful API 后端服务。多供应商 AI 行程生成（SSE 流式）、百度地图集成、用户社交系统、JWT 认证 + Redis 限流。

## 📱 项目简介

AI 智能旅游助手后端服务是整个应用的核心引擎，负责：

- **多供应商 AI 集成**：DeepSeek / OpenAI / Claude / Gemini / Custom 五类 LLM，启动时自动检测可用供应商
- **SSE 流式行程生成**：7 阶段进度推送 + 逐天行程生成，支持任务取消
- **Agent 微服务透传**：`/api/agent/**` 转发到 Python Agent（同步 + SSE 流式），JWT 鉴权 + 限流 + 优雅降级
- **地图 API**：百度地图 + 高德地图（POI 搜索、热门目的地、城市景点、周边搜索、地理编码、地图 SDK 代理）
- **用户社交系统**：游记发布/点赞/评论/回复（二级嵌套）、关注/粉丝（JWT 隔离）、收藏、反馈
- **电商功能**：优惠券管理、订单系统（机票/酒店/门票）
- **安全防护**：JWT 认证、Redis 滑动窗口限流、XSS 过滤、安全响应头、路径穿越防护、图片代理白名单

## 🛠️ 技术栈清单

| 分类 | 技术 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 3.2.5 |
| 语言 | Java | 17 |
| 数据库 | H2 File Mode（持久化） | — |
| ORM | Spring Data JPA | — |
| 响应式 | Spring WebFlux（WebClient + Flux） | — |
| 认证 | jjwt 0.12.5 + BCrypt | — |
| 限流 | Redis（不可用时自动降级） | — |
| 本地缓存 | Caffeine | — |
| AI 服务 | DeepSeek / OpenAI / Claude / Gemini / 自定义 | — |
| 地图服务 | 百度地图开放 API v2 | — |
| 构建工具 | Maven | — |

## 📝 最近更新（2026-07-29）

### 社交功能增强
- 评论系统支持嵌套回复（`parentId` 二级结构）
- `GET /api/comments/{commentId}/replies` — 获取评论的子回复列表
- 关注/取关 API 完善，支持关注列表、粉丝列表、关注/粉丝计数

### 数据隔离
- 所有用户相关接口通过 JWT `extractUserId()` 提取当前用户 ID
- 关注列表、粉丝列表、评论、收藏等数据严格按用户隔离

### 部署优化
- CORS 配置支持所有来源（`allowedOriginPatterns: "*"`）方便局域网访问
- 静态资源映射支持 uploads 目录
- Docker 支持（Render 部署）

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
# - jwt.secret: JWT 密钥（必填，>=32 字符，未设置时应用启动即失败）
# - amap.web-key: 高德 Web 服务 Key（地图功能需要，通过 AMAP_WEB_KEY 注入）
# - baidu.map.ak: 百度地图 AK（服务端类型，可选）

# 也可以通过环境变量注入（推荐，避免密钥入库）：
#   JWT_SECRET=xxx  AMAP_WEB_KEY=xxx  DEEPSEEK_API_KEY=xxx  ...
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

### 健康检查

```bash
curl http://localhost:3200/api/travel/health
```

### Docker 部署

```bash
docker build -t travel-java .
docker run -p 3200:3200 travel-java
```

## 📁 项目结构

```
travel-java/
├── src/main/java/org/example/traveljava/
│   ├── TravelJavaApplication.java     # 启动类
│   ├── config/                        # 配置类
│   │   ├── AIProviderConfig.java      # 多供应商 AI 配置（自动检测）
│   │   ├── SecurityConfig.java        # 拦截器注册 + CORS
│   │   ├── SecurityHeaderFilter.java  # 安全响应头
│   │   ├── WebConfig.java             # CORS + 静态资源映射
│   │   ├── WebClientConfig.java       # WebClient 连接池管理
│   │   ├── GlobalExceptionHandler.java # 全局异常处理
│   │   ├── RedisConfig.java           # Redis 配置
│   │   └── ThreadPoolConfig.java      # 线程池（图片预加载）
│   ├── controller/                    # REST API 控制器（23 个）
│   │   ├── TravelController.java      # 行程规划（核心 SSE 流式）
│   │   ├── TripAIController.java      # AI 行程规划
│   │   ├── AgentProxyController.java  # 🆕 Agent 微服务透传（同步 + SSE）
│   │   ├── AuthController.java        # 用户认证（限流）
│   │   ├── UserController.java        # 用户管理
│   │   ├── CommentController.java     # 评论管理 + 回复
│   │   ├── FollowController.java      # 关注/粉丝（数据隔离）
│   │   ├── NoteController.java        # 游记 CRUD + 点赞
│   │   ├── PostController.java        # 社区帖子
│   │   ├── FileUploadController.java  # 文件上传
│   │   ├── ImageProxyController.java  # 图片代理（白名单）
│   │   ├── MapScriptController.java   # 地图 JS SDK 代理（高德 v2.0 / 百度 GL）
│   │   └── ...                        # 地图/酒店/收藏/订单等
│   ├── service/                       # 业务逻辑层（17 个）
│   │   ├── AIService.java             # 核心 AI 服务
│   │   ├── BaiduMapService.java       # 百度地图服务
│   │   └── ...
│   ├── repository/                    # 数据访问层（16 个 JPA Repository）
│   ├── entity/                        # 数据库实体（16 个）
│   ├── dto/                           # 数据传输对象（19 个）
│   ├── interceptor/                   # 拦截器
│   │   └── RateLimitInterceptor.java  # Redis 滑动窗口限流
│   └── util/                          # 工具类
│       ├── JwtUtil.java               # JWT 令牌
│       └── TextCleaner.java           # AI 输出清洗
├── src/main/resources/
│   ├── application.yml                # 应用配置
│   ├── application.yml.example        # 配置模板
│   └── db/migration/
│       └── V2__trip_map_init.sql      # 数据库初始化
├── uploads/                           # 上传文件目录
├── Dockerfile                         # Docker 构建
├── pom.xml                            # Maven 配置
└── .gitignore                         # 忽略配置
```

## 🔌 API 接口列表

### 核心 - 行程规划

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/travel/plan` | POST | 非流式生成行程规划 |
| `/api/travel/plan/stream` | POST | 流式生成行程规划 (SSE) |
| `/api/travel/planner/progress` | POST | 7 阶段进度 SSE |
| `/api/travel/planner/stream-detail` | POST | 逐天生成 SSE |
| `/api/travel/planner/stop` | POST | 停止生成 |
| `/api/travel/trip/generate/stream` | POST | 单端点 SSE 行程生成 |

### 核心 - AI 对话

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/travel/chat` | POST | 非流式 AI 对话 |
| `/api/travel/chat/stream` | POST | 流式 AI 对话 (SSE) |
| `/api/travel/recommend` | POST | 旅游推荐 |

### 用户认证

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/auth/register` | POST | 用户注册（限流） |
| `/api/auth/login` | POST | 用户登录（限流） |
| `/api/user/profile` | GET/PUT | 获取/更新个人信息 |

### 社交功能

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/notes/*` | CRUD | 游记发布、查看、点赞 |
| `/api/notes/{noteId}/comments` | GET/POST | 游记评论 |
| `/api/comments/{id}/replies` | GET | 评论子回复列表 |
| `/api/comments/{id}` | DELETE | 删除评论 |
| `/api/comments/{id}/like` | POST | 点赞评论 |
| `/api/posts` | GET/POST | 社区帖子 |
| `/api/user/follow/{id}` | POST | 关注用户 |
| `/api/user/unfollow/{id}` | POST | 取关用户 |
| `/api/user/following` | GET | 关注列表（按 JWT 隔离） |
| `/api/user/followers` | GET | 粉丝列表（按 JWT 隔离） |

### Agent 微服务透传

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/agent/health` | GET | Agent 服务健康检测（含代理状态） |
| `/api/agent/plan` | POST | 同步生成行程（透传 Python Agent，需登录） |
| `/api/agent/plan/stream` | POST | SSE 流式生成（透传，需登录） |

> 架构：前端 → Spring Boot `:3200`（AgentProxyController）→ Python Agent `:3201`

### 地图数据

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/map/suggestion` | GET | 地点联想搜索 |
| `/api/map/detail` | GET | POI 详情查询 |
| `/api/map/hot-destinations` | GET | 热门目的地列表 |
| `/api/map/city-attractions` | GET | 城市景点列表 |
| `/api/map/nearby-attractions` | GET | 周边景点搜索 |
| `/api/map/script` | GET | 地图 JS SDK 代理（高德 v2.0 / 百度 GL） |
| `/api/map/geocode` | GET | 地理编码（城市名 → 经纬度） |

### 其他

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/upload` | POST | 文件上传 |
| `/api/files/{filename}` | GET | 文件访问 |
| `/api/proxy/image` | GET | 图片代理（域名白名单） |
| `/api/feedback` | GET/POST | 用户反馈 |
| `/api/voice/transcribe` | POST | 语音转文字 |

## 🔒 安全机制

- **JWT 认证**：`AuthUtils.requireUserId()` 解析 Bearer Token
- **用户数据隔离**：所有用户相关接口通过 `jwtUtil.extractUserId(token)` 获取当前用户 ID
- **Redis 限流**：`@RateLimit` 注解 + 滑动窗口限流，Redis 不可用时优雅降级
- **安全响应头**：CSP、XSS-Protection、HSTS 等
- **XSS 防护**：输入参数校验（`@Valid` + Jakarta Bean Validation）
- **路径穿越防护**：文件访问端点过滤 `..` 路径
- **图片代理白名单**：仅允许指定域名
- **CORS**：`allowedOriginPatterns: "*"` 支持局域网访问

## 🗺️ 后续迭代规划

### 短期目标
- [x] 多 AI 供应商支持
- [x] Redis 限流 + 优雅降级
- [x] 安全响应头过滤器
- [x] 评论回复功能（二级嵌套）
- [x] 关注/粉丝数据隔离
- [ ] Swagger/OpenAPI 接口文档
- [ ] 完善日志系统和监控指标

### 中期目标
- [ ] RAG 知识库整合旅游攻略
- [ ] 行程分享功能
- [ ] MySQL/PostgreSQL 多数据库支持
- [ ] 消息队列异步处理

### 长期目标
- [ ] 分布式部署和负载均衡
- [ ] 智能推荐算法
- [ ] 接入机票、酒店第三方预订 API

## 📄 许可证

MIT License

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！
