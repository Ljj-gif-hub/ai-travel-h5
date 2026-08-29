# AI 智能旅游助手

> 基于 Vue 3 + Spring Boot 3.2 的全栈 AI 旅游规划应用。iOS 风格磨砂玻璃主题，悬浮椭圆胶囊 Tab 栏，Apple Photos 风格透明模糊效果，支持多供应商 LLM、SSE 流式行程生成、社区游记、短视频播放。

## 📁 项目结构

```
├── trval-h5/          # 前端 — Vue 3 + Vite 8 + Vant 4 移动端 H5
├── travel-java/       # 后端 — Spring Boot 3.2 + Java 17 + JWT + Redis
├── agent-service/     # 🆕 Agent 微服务 — Python FastAPI + LangChain 智能体
└── README.md          # 本文件
```

## 🎬 功能演示

📥 [**下载完整演示视频 (MP4, 7.8MB)**](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/full-demo.mp4)

> 下载后观看完整功能演示：山水 Banner · AI 智能对话 · 行程规划 · 携程风格图片网格 · 视频内联播放 · 评论区 · 收藏/订单 · 登录注册

## 🔗 子项目文档

- 📱 [前端详细文档](trval-h5/README.md) — 技术栈、功能列表、UI 设计系统、项目结构
- 🖥️ [后端详细文档](travel-java/README.md) — API 列表、安全机制、AI 供应商配置、部署指南
- 🤖 [Agent 微服务文档](agent-service/README.md) — 5 阶段流水线、记忆层、工具、API

## 🚀 快速启动

### 前端

```bash
cd trval-h5
npm install
npm run dev        # → http://localhost:5173
```

### 后端

```bash
cd travel-java
mvn spring-boot:run   # → http://localhost:3200
```

### 前置条件

- **前端**：Node.js >= 18，npm >= 9
- **后端**：JDK >= 17，Maven >= 3.8，Redis（可选，限流用）

## 📸 界面截图

| 首页 | AI 行程规划 |
|------|------------|
| ![首页](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e9%a6%96%e9%a1%b5.jpg) | ![AI规划](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/ai%e8%a7%84%e5%88%92%e5%a5%bd%e7%9a%84%e8%a1%8c%e7%a8%8b1.jpg) |

| 社区动态 | 视频详情 |
|---------|---------|
| ![社区](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e7%a4%be%e5%8c%ba.jpg) | ![视频详情](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e8%a7%86%e9%a2%91%e8%af%a6%e6%83%85.jpg) |

| 图片详情 & 评论 | 写游记 |
|---------------|-------|
| ![图片评论](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e5%9b%be%e7%89%87%e8%af%a6%e6%83%85%e8%af%84%e8%ae%ba.jpg) | ![写游记](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e5%86%99%e6%b8%b8%e8%ae%b0.jpg) |

| AI 智能对话 | 热门目的地 |
|------------|-----------|
| ![AI对话](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/ai%e5%af%b9%e8%af%9d.jpg) | ![热门目的地](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e7%83%ad%e9%97%a8%e7%9b%ae%e7%9a%84%e5%9c%b0.jpg) |

| 热门景点详情 | 选择出行偏好 |
|------------|------------|
| ![景点详情](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e7%83%ad%e9%97%a8%e6%99%af%e7%82%b9%e8%af%a6%e6%83%85.jpg) | ![出行偏好](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e9%80%89%e6%8b%a9%e5%87%ba%e8%a1%8c%e5%81%8f%e5%a5%bd.jpg) |

| 选择起点 | 规划日期 |
|---------|---------|
| ![选择起点](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e9%80%89%e6%8b%a9%e8%b5%b7%e7%82%b9.jpg) | ![日期选择](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e8%a7%84%e5%88%92%e6%97%a5%e6%9c%9f%e9%80%89%e6%8b%a9.jpg) |

| 行程管理 | 个人中心 |
|---------|---------|
| ![行程](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e8%a1%8c%e7%a8%8b.jpg) | ![我的](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e6%88%91%e7%9a%84.jpg) |

| 我的收藏 | 订单中心 |
|---------|---------|
| ![收藏](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e6%88%91%e7%9a%84%e6%94%b6%e8%97%8f.jpg) | ![订单](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e8%ae%a2%e5%8d%95%e4%b8%ad%e5%bf%83.jpg) |

| 更多产品服务 | AI 规划（旧版） |
|------------|---------------|
| ![更多产品](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e6%9b%b4%e5%a4%9a%e4%ba%a7%e5%93%81.jpg) | ![规划](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e8%a7%84%e5%88%92.jpg) |

| 视频评论区 | 登录 / 注册 |
|----------|-----------|
| ![视频评论](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e8%a7%86%e9%a2%91%e8%af%a6%e6%83%85%e7%95%8c%e9%9d%a2%e8%af%84%e8%ae%ba%e5%8c%ba.jpg) | ![登录](https://raw.githubusercontent.com/Ljj-gif-hub/ai-travel-h5/main/trval-h5/public/showcase/screenshots/%e7%99%bb%e5%bd%95.jpg) |

## 🛠️ 技术栈概览

| 层 | 技术 |
|----|------|
| 前端框架 | Vue 3.5（Composition API） |
| 构建工具 | Vite 8 |
| UI 组件库 | Vant 4.10 |
| 后端框架 | Spring Boot 3.2.5 |
| 数据库 | H2 File Mode（持久化） |
| AI 服务 | DeepSeek / OpenAI / Claude / Gemini / Custom |
| 地图 | 百度地图 WebGL / Leaflet |
| 认证 | JWT (jjwt 0.12.5) + BCrypt |
| 限流 | Redis 滑动窗口 |

## 🆕 最近更新（v4.5 — 2026-08-30）

### 🗺️ agent-map 行程增强：周边游 + 坐标修复 + 景点多图
- **周边游**：后端 `/api/map/surround-tour`（出发城市→周边目的地推荐，深圳/北京种子数据），前端周边推荐卡片
- **坐标修复**：AMap 地理编码改 InputTips（传 `city` 消歧），返回 `crs` 坐标系；前端 CRS 归一 + 城市 bbox；`cityOkay` 100km 门拒绝同名异地
- **景点多图**：`/api/map/attraction-images` 批量返回每景点最多 3 张 AMap 实拍图，行程卡片槽位 1→3，本地静态图兜底
- **Tab 无缝**：行程区相邻胶囊模块改侧向圆角，消除共享边界白隙

## 🆕 最近更新（v4.4 — 2026-08-06）

### 🔒 安全加固（第 1 步）
- **支付安全**：`mock-pay` 生产关闭（`payment.mock-pay-enabled=false`）、真实渠道回调 fail-closed 拒签、订单号随机不可枚举
- **Actuator**：生产只暴露 health/info/metrics/prometheus，移除 `env`/`loggers`
- **默认管理员口令**：未显式配置 `ADMIN_PASSWORD` 拒绝启动
- **Agent 鉴权**：agent-service 增加共享密钥/JWT 校验；移除 vite/nginx 直连绕过，统一走 Spring 透传；`user_id` 服务端绑定防伪造
- **CORS**：移除 SSE 硬编码 `*`，统一走白名单
- **异常收敛**：AI/代理/控制器不再把 `e.getMessage()` 透传前端
- **AK 日志**：百度 AK 与响应体日志脱敏/降级
- **机票防篡改**：下单金额改为服务端重新报价

### 🐛 界面 bug 修复（第 2 步）
- 修复 3 个高严重 bug：评论回复展开崩溃、首页死路由、机票日期选择失效
- 修复 6 个中严重 bug：`/planning` 丢 query、日历地图跳转、目的地头图占位、AI 对话中断持久化、语音未接线、视频播放图标重复

### 🔧 功能闭环（第 3 步）
- docker-compose 编排 agent-service（`--scale` 支持）；JWT 登出黑名单生效；优惠券使用闭环（校验 pending + 抵扣 + 取消释放）；清理记忆乱码；远程知识库未配置明确报错；规划偏好字段完整透传

### ⚡ 性能（第 4 步）
- 动态接口分页；推荐接口缓存行程热度；Agent 同步代理加超时

## 🆕 最近更新（v4.3 — 2026-08-05）

### 🔧 工程化基础设施补全（后端）
- **Swagger/OpenAPI**：springdoc 接入，`/swagger-ui.html` + `/v3/api-docs`，全部 Controller 按业务分组标注
- **日志系统**：`logback-spring.xml`（控制台 + 按天滚动文件 + 错误独立归档；prod profile 切 JSON 结构化输出）
- **监控指标**：Actuator + Prometheus，`/actuator/prometheus` 暴露 JVM/HTTP/自定义业务指标（AI 调用、行程生成、推荐、MQ 事件）

### 🛢️ 多数据库 + 消息队列
- **MySQL/PostgreSQL**：驱动就绪 + `application-mysql.yml` / `application-postgres.yml` profile，环境变量一键切换
- **RabbitMQ 异步处理**：`app.mq.enabled=true` 启用；订单支付成功事件 → 异步审计落库；默认关闭走同步降级，无 MQ 也能运行

### 🎯 智能推荐 + 机票预订
- **推荐引擎**：内容推荐 + 用户协同过滤 + 热门兜底三段式混合，`/api/recommend/items` + `/destinations`，登录个性化/未登录热门
- **机票预订**：`/api/flight/search` + `/api/flight/book` + `FlightProvider`（Mock 确定性数据 / Real 第三方骨架），前端新增「机票预订」页（`/flight-booking`）
- **预订对接层**：`booking.*` 配置，酒店/机票均按支付层同款「Mock/Real 双实现」模式

### 🚀 分布式部署
- `docker-compose.yml` 编排 MySQL/Redis/RabbitMQ/应用（`--scale app=2` 水平扩容）
- `deploy/nginx.conf` 负载均衡 + SSE 转发 + 前端静态托管示例
- 首页「机票预订」入口已接入新页面

### 🌐 多语言国际化全量补齐
- 语言包按 24 个功能模块拆分（`src/locales/{zh,en}/*.js`），`zh-CN.js`/`en-US.js` 聚合
- **全量抽取**：全部视图/组件静态文案改为 `t()`，中英 key 一一对应（929 个 key）
- 通用词库 `common.js`（返回/取消/保存/加载/失败等）跨模块复用；动态数据（城市名、用户内容、数值）保持不翻译

## 🆕 最近更新（v4.2 — 2026-08-05）

### 🏨 酒店预订对接（可配置对接层）
- 后端 `POST /api/hotel/book`：校验酒店/日期 → 报价 → 创建 hotel 订单（pending）
- 前端「酒店预订」页（`/hotel-booking`）：城市切换 + 酒店列表 + 入住/晚数/间数
- 配置 `hotel.mock-full` 模拟满房校验；后续可扩展真实房态对接

### 💳 第三方支付对接层（Mock / Real 双实现）
- `PaymentProvider` 接口 + `MockPaymentProvider`（默认）/ `RealPaymentProvider`（支付宝/微信骨架）
- `POST /api/payment/create`、`POST /api/payment/notify`（幂等回调）、`GET /api/payment/mock-pay`
- Order 新增 `payChannel / payTradeNo / paidAt` 三字段；填商户密钥改 `payment.provider` 即切真实渠道

### 🧭 Agent RAG 知识库（agent-service）
- 内置 6 城旅游攻略语料（`agent/knowledge/*.md`）+ 纯 Python 字符级 bigram TF-IDF 检索（零新依赖）
- 规划 Prompt 注入「本地攻略参考」，`research_notes` 追加来源
- `KnowledgeProvider` 接口 + `KNOWLEDGE_SOURCE=builtin|remote`，预留外部语料库接入

### 📅 行程日历视图 + 🔗 行程分享闭环
- 日历视图（`/trip-calendar`）：按天日程卡 + 日期条，入口在行程地图顶栏
- 分享闭环：后端 8 位短链 + 公开只读快照 → 前端 `navigator.share` / 复制链接 + Canvas 海报 + 免登录落地页（`/share/:token`）

### 🌙 深色模式
- 全局 token 体系 + `[data-theme]` 覆盖 + 跟随系统/手动切换（`Profile` 设置）
- Vant 组件 `van-config-provider theme="dark"` 接管；主路径视图（4 Tab + 行程 + 聊天）已适配

### 🌐 多语言国际化 + 📡 PWA 离线 + 🗺️ 离线地图
- i18n：vue-i18n + 中/英文案 + 切换入口（核心界面已抽取，Vant 文案联动）
- PWA：manifest `start_url/scope` 修正为相对路径 + 离线兜底页 `offline.html` + navigateFallback
- 离线地图：workbox OSM 瓦片 CacheFirst 缓存 + Leaflet 本地打包 + 地图页「离线」开关强制 Leaflet

### 🔧 其他
- 行程坐标补齐：保存前自动地理编码（marker→缓存→高德 geocode），随 planJson 持久化

## 🆕 最近更新（v4.1 — 2026-08-05）

### 🤖 Agent 记忆层（agent-service）
- **长期用户偏好**（`user_id`）+ **短期会话上下文**（`session_id`）→ 个性化感知 + 连续规划
- **调研缓存**（内存 TTL）避免重复搜索；记忆持久化到 `data/agent_memory.json`
- **行程调整**：`adjustment` 参数局部增量重规划（如「放慢节奏」）

### 🗺️ Agent 地图页（AgentMapView）
- 高德地图 + 可拖拽抽屉联动：抽屉收放时地图**无极缩放**（easeOutCubic 缓动 + rAF 60fps）
- 收起时显示**行程/住宿/天数摘要卡**（携程同款），点击或上滑展开
- 景点标记**去重叠**布局 + 点击卡片定位、放大、高亮
- 生成/调整后**一键保存**到「我的行程」

### ⚙️ 其他
- 后端：Agent 微服务**透传代理**（JWT 鉴权 + 限流 + 优雅降级）、地图 `script`/`geocode` 端点
- 性能：游记分页、动态与酒店查询优化
- 安全：鉴权/管理员角色/上传校验/CORS/限流/密钥清理

## 📝 最近更新（v3.0 — 2026-07-29）

### 前端重大更新
- **iOS 磨砂玻璃主题**：全站 backdrop-filter blur + saturate，模拟 iOS 系统玻璃透光反射
- **悬浮椭圆胶囊 Tab 栏**：4 Tab 椭圆悬浮设计，选中态滑动指示器平移动画，非 Tab 页自动隐藏动画
- **Apple Photos 风格透明模糊**：底部 Tab 栏透明磨砂 + 内容滚动穿透效果
- **山水大图 Banner**：首页/社区/我的三大 Banner 全宽高清自然风光背景图 + 渐变遮罩
- **所有抽屉/弹窗浮层化**：底部弹窗四周留白 + 圆角，不贴边，独立悬浮卡片
- **统一滑动指示器**：Tab 切换（登录页、收藏页、订单页）全部使用白色滑动块 + 磨砂玻璃底
- **图片网格智能布局**：1-5+ 张图片不同排版（携程风格），图片点击内联预览
- **视频内联播放**：社区卡片内直接播放视频，metadata 预加载第一帧
- **GPU 性能优化**：will-change + backface-visibility + 精确属性过渡

### 后端
- 评论回复系统（parentId 二级嵌套）
- 关注/粉丝数据隔离（JWT 提取 userId）

## 🤖 AI Agent 智能规划（v4.0 重大升级）

> 从「LLM 一次性生成」升级为「Agent 自主规划 + 工具调用 + 实时校验 + 自动优化」的智能体闭环。

### 🆚 新旧对比

| | 旧版 AI 规划 | 🆕 Agent 智能规划 |
|---|---|---|
| **信息源** | LLM 训练数据（可能过时） | Tavily 实时搜索 + LLM 推理 |
| **预算** | ❌ 不校验 | ✅ 自动核算 + 超标 4 策略逐级调整 |
| **路线** | ❌ 不检查 | ✅ 同天景点同区域，避免折返 |
| **规划方式** | 一次 Prompt → 一次输出 | ReAct 循环：思考→调工具→分析→再决策 |
| **过程可见** | 黑盒等待 | SSE 流式推送 5 阶段实时进度 |
| **二次修改** | 重新生成全部 | 局部增量调整 |
| **目的地** | 受限 | 全球任意城市 |

### 🏗️ Agent 架构

```
用户输入「成都3天·情侣·美食·5000元」
        │
        ▼
┌─────────────────────────────────────────┐
│         Agent 5 阶段流水线                │
│                                          │
│  Phase 1 🔍 RESEARCH                     │
│    Tavily 并行搜索景点/美食/酒店实时信息   │
│                                          │
│  Phase 2 📋 PLAN                         │
│    LLM 基于搜索结果生成结构化 JSON 行程   │
│    · 每个景点 4-6 句深度介绍             │
│    · 自动标签【5A】【网红打卡】【需预约】 │
│                                          │
│  Phase 3 🔍 VERIFY                       │
│    核算总预算 + 检查路线合理性            │
│                                          │
│  Phase 4 🔧 ADJUST                       │
│    超标自动调整：降酒店→删次要景点→优化餐饮│
│                                          │
│  Phase 5 ✨ FINALIZE                      │
│    输出完整方案：每日卡片/预算/酒店/贴士   │
└─────────────────────────────────────────┘
        │
        ▼
  前端渲染：高德地图 + DragSheet 抽屉 + 景点卡片
```

### 🛠️ Agent 技术栈

| 层 | 技术 |
|----|------|
| Agent 框架 | Python FastAPI + LangChain |
| LLM | DeepSeek / OpenAI / 通义千问 等 OpenAI 兼容接口 |
| 搜索工具 | Tavily Search API（实时景点/酒店/美食） |
| 地图工具 | 高德地图 API（通勤距离/时间） |
| 预算工具 | 本地核算引擎（4 策略自动降级） |
| 记忆层 | JSON 文件持久化（用户偏好 + 会话上下文 + 调研缓存） |
| 编排模式 | ReAct Agent + 多阶段流水线 |
| 流式推送 | SSE (Server-Sent Events) 实时进度 |

### 🚀 启动 Agent 服务

```bash
# 1. 配置 API Key
cd agent-service
cp .env.example .env
# 编辑 .env，填写 LLM_API_KEY（必填）、TAVILY_API_KEY（可选）、AMAP_WEB_KEY（可选）

# 2. 安装依赖
pip install -r requirements.txt

# 3. 启动
python main.py         # → http://localhost:3201
```

### 🎯 体验入口

启动后进入行程 Tab → 点击「🤖 AI Agent 智能规划」→ 填写目的地/天数/偏好 → 查看 Agent 5 阶段实时规划过程 → 获得完整行程方案。

> 💡 Demo 模式：未配置 LLM API Key 时自动启用内置 8 城数据，无需任何外部 API 即可体验完整流程。

## 📄 许可证

MIT License
