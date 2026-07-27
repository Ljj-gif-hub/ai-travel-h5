# 前后端联调流程 + 本地启动部署 + 性能优化方案

## 一、本地启动部署步骤

### 1.1 后端启动

```bash
# 1. 进入后端项目目录
cd travel-java

# 2. 确保 application.yml 中 AI API Key 已配置
#    编辑 src/main/resources/application.yml
#    设置环境变量或在 yml 中直接配置:
#    - CUSTOM_AI_KEY=sk-xxx  (AI 大模型 API Key)
#    - BAIDU_MAP_AK=xxx      (百度地图 AK，服务端类型)

# 3. 启动后端（默认端口 3200）
mvn spring-boot:run

# 4. 验证后端启动
curl http://localhost:3200/api/travel/health

# 期望返回:
# { "code": 0, "data": { "status": "ok", "provider": "custom", ... } }
```

### 1.2 前端启动

```bash
# 1. 进入前端项目目录
cd trval-h5

# 2. 安装依赖（首次）
npm install

# 3. 启动开发服务器（默认端口 5173）
npm run dev

# 4. 浏览器访问
# http://localhost:5173
```

### 1.3 百度地图 SDK 配置

百度地图 GL 版通过后端 `/api/map/script` 代理加载，无需前端配置 AK。

**后端配置：**
1. 前往 https://lbsyun.baidu.com/apiconsole/key 创建应用
2. 应用类型选择「服务端」，获取 AK
3. 在 `application.yml` 中配置: `baidu.map.ak: YOUR_AK`

**地图代理原理：**
`MapScriptController` 代理百度地图 JS SDK 请求，拼接 AK 参数后转发，前端无需暴露 AK。

---

## 二、前后端联调流程

### 2.1 核心联调链路

```
前端 TripMapView
  ↓ POST /api/travel/trip/generate  (获取 taskId)
  ↓ GET  /api/travel/trip/progress/{taskId}  (SSE 订阅进度)
  ↓       ← SSE: progress-update (0%→100%)
  ↓       ← SSE: hotel-found (酒店列表)
  ↓       ← SSE: day-update (逐天行程)
  ↓       ← SSE: cost-calc (费用明细)
  ↓       ← SSE: generate-finish (完成)
  ↓ GET  /api/map/landmarks?city=北京  (地图地标)
  ↓ GET  /api/map/metro-stations?city=北京  (地铁站点)
  ↓ GET  /api/hotel/search?city=北京  (酒店筛选)
  ↓ POST /api/cost/breakdown  (费用统计)
  ↓ POST /api/voice/transcribe  (语音转文字)
```

### 2.2 SSE 联调要点

```javascript
// 1. 前端使用 EventSource 或 fetch + ReadableStream
// 2. 后端 SseEmitter 发送 JSON 事件
// 3. 事件格式: data: {"eventType":"progress-update","progress":41,...}\n\n

// 联调检查清单:
// ✅ 后端 SSE Content-Type 必须是 text/event-stream
// ✅ 后端设置 X-Accel-Buffering: no (禁用 nginx 缓冲)
// ✅ 前端 fetch 不设置 Content-Type（GET 请求无需）
// ✅ 心跳机制: 每 15s 发送 : heartbeat 注释行
// ✅ 超时设置: 后端 600s，前端无超时
```

### 2.3 地图标记联调

```bash
# 测试地标接口
curl "http://localhost:3200/api/map/landmarks?city=北京"
# 期望返回: { code: 0, data: [{ name: "天安门广场", latitude: 39.909, ... }] }

# 测试地铁站接口
curl "http://localhost:3200/api/map/metro-stations?city=上海"
# 期望返回: { code: 0, data: [{ name: "人民广场站", type: "metro", ... }] }
```

---

## 三、性能优化方案

### 3.1 地图性能优化

**防抖处理：**
```javascript
// 地图缩放/拖拽防抖 — 减少标记重绘频率
import { debounce } from '../utils/debounce'

const onMapMove = debounce(() => {
  // 地图移动时延迟更新可见标记
  updateVisibleMarkers()
}, 300)
```

**标记聚合（点聚合）：**
```javascript
// 大量标记时使用 BMapGL.MarkerClusterer
// 百度地图 GL 版内置聚合能力
if (mapMarkers.length > 50) {
  const clusterer = new BMapGL.MarkerClusterer(mapInstance, {
    markers: mapMarkers,
    girdSize: 60,
    maxZoom: 15,
  })
}
```

**地图实例复用：**
```javascript
// 地图实例不销毁，缓存在内存中
// TripMapView onUnmounted 时不调用 mapInstance.destroy()
// 下次进入页面直接挂载已有实例
let mapInstance = null // 模块级单例
```

### 3.2 抽屉拖拽流畅度优化

**GPU 硬件加速：**
```css
.drag-sheet {
  will-change: transform;
  transform: translateZ(0);
  backface-visibility: hidden;
}
```

**requestAnimationFrame 节流：**
```javascript
let rafId = null
const onTouchMove = (e) => {
  if (rafId) return
  rafId = requestAnimationFrame(() => {
    // 在下一帧更新位置
    updateSheetPosition(e)
    rafId = null
  })
}
```

**passive 事件监听：**
```javascript
// 告诉浏览器不会 preventDefault，提升滚动性能
contentRef.value.addEventListener('touchstart', handler, { passive: true })
```

### 3.3 AI 接口异步处理

**后端异步架构：**
```java
// 行程生成使用线程池异步执行
@Async("taskExecutor")
public void generateTripAsync(TripPlannerRequest req, String taskId) {
    // 1. AI 调用（最耗时 ~30-60s）
    // 2. 酒店筛选（并行查询数据库）
    // 3. 费用计算（内存计算，毫秒级）
    // 4. 结果持久化
}
```

**前端竞态处理：**
```javascript
// 防止快速切换导致旧请求结果覆盖新请求
let requestId = 0
const startGeneration = async (params) => {
  const id = ++requestId
  const result = await tripNewApi.generateTrip(params)
  if (id !== requestId) return // 不是最新请求，丢弃
  // 处理结果...
}
```

### 3.4 大数据列表虚拟滚动

**使用 vant 内置 List 组件：**
```html
<van-list
  v-model:loading="loading"
  :finished="finished"
  finished-text="没有更多了"
  @load="onLoad"
>
  <div v-for="item in list" :key="item.id">
    <!-- 列表项 -->
  </div>
</van-list>
```

**按需渲染（v-if 替代 v-show）：**
```html
<!-- max 状态才渲染完整每日行程，减少 DOM 节点 -->
<div class="daily-itinerary" v-if="drawerState === 'max'">
  <!-- 行程卡片列表 -->
</div>
```

### 3.5 其他优化

| 优化点 | 方案 |
|---|---|
| 图片懒加载 | `loading="lazy"` + Intersection Observer |
| CSS 动画 | 仅使用 transform/opacity（不触发重排） |
| SSE 重连 | 指数退避重试（1s→2s→4s→8s→max 30s） |
| 酒店列表缓存 | ConcurrentHashMap + 1小时 TTL |
| 地标数据缓存 | 首次加载后内存缓存（地图页面不销毁） |
| 打包优化 | Vite code-splitting, 路由懒加载 |

---

## 四、原有项目需要删除/替换的旧代码

| 文件 | 操作 | 原因 |
|---|---|---|
| `src/api/plan.js` | 已删除（git status 显示 D） | 功能合并到 tripNew.js |
| `PlanningView.vue` | 保留但添加 `// @deprecated 请使用 TripMapView` 注释 | 新页面替代核心功能 |
| `AITripPlanner.vue` | 保留 | 不同交互模式，两者共存 |
| `AITripPlannerProgress.vue` | 保留 | 不同交互模式，两者共存 |
| `HomeView.vue` AI规划入口 | 修改 `router.push('/planning')` → `router.push('/trip-map')` | 跳转到新页面 |
