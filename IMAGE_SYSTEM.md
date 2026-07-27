# 图片系统架构文档

> **设计理念**：携程模式——后端 POI 图片 API 为主渠道，静态 JSON 兜底，CSS 渐变色最终占位。

---

## 一、图片加载链路

```
用户看到城市卡片
    │
    ▼
┌─────────────────────────────────────────────────┐
│ 1. 后端 API（主渠道）                              │
│    GET /api/city/image?name=北京                   │
│    GET /api/city/images/map   (批量)               │
│    GET /api/city/attraction/images/map             │
│                                                   │
│    CityController.getCityImage()                   │
│      ├── city_images 表（DB 缓存）                  │
│      ├── AmapService POI 搜索（高德地图 API）        │
│      ├── Bing 图片搜索                             │
│      └── Picsum 占位图                             │
└─────────────────────────────────────────────────┘
    │ 失败 / 未覆盖
    ▼
┌─────────────────────────────────────────────────┐
│ 2. 静态 JSON（兜底）                              │
│    /city-images.json      → 城市名 → 图片 URL      │
│    /attraction-images.json → 景点名 → 图片 URL     │
└─────────────────────────────────────────────────┘
    │ 失败 / 404
    ▼
┌─────────────────────────────────────────────────┐
│ 3. CSS 渐变色 + 城市首字（永不失败）                │
│    hsl(name.hash) 渐变背景 + 首汉字                │
└─────────────────────────────────────────────────┘
```

---

## 二、厂商接入指南

### 方案 A：接入图片 CDN（推荐）

如果有图片供应商（如携程、百度地图、高德、Unsplash 等）：

**1. 配置后端 API**

编辑 `travel-java/src/main/resources/application.yml`：

```yaml
# 高德地图 POI 图片搜索（国内城市覆盖率高）
amap:
  api-key: ${AMAP_API_KEY}

# Bing 图片搜索（国际城市）
bing:
  api-key: ${BING_API_KEY}
```

**2. 实现图片源接口**

在 `travel-java/src/main/java/org/example/traveljava/service/` 下，参考 `AmapService.java` 的模式新建你需要的图片源 Service。

接口规范：
```java
// 输入：城市名或景点名
// 输出：图片 URL 字符串
public String searchCityImage(String cityName);
```

**3. 注册到 CityController**

在 `CityController.getCityImage()` 的 fallback 链中添加新 Service：

```java
// 1. 查 DB 缓存
// 2. 查 Amap
// 3. 查你的新图片源   ← 在这里加
// 4. Picsum 兜底
```

**4. 刷新已有缓存**

```bash
POST /api/city/images/refresh
```

### 方案 B：填充静态 JSON（轻量）

如果暂时不接后端 API，可以直接把图片 URL 写入 JSON 文件：

**1. 准备图片**

将图片上传到 CDN 或放入 `trval-h5/public/images/landmarks/` 目录。

**2. 更新 JSON 文件**

编辑 `trval-h5/public/city-images.json`：
```json
{
  "北京": "https://your-cdn.com/beijing-tiananmen.jpg",
  "上海": "https://your-cdn.com/shanghai-oriental-pearl.jpg"
}
```

或使用本地路径：
```json
{
  "北京": "/images/landmarks/beijing.jpg",
  "上海": "/images/landmarks/shanghai.jpg"
}
```

**3. 批量下载工具**

项目提供了 Python 脚本用于从 Pexels/Unsplash 批量下载地标图片：

```bash
pip install requests Pillow
python scripts/download_landmarks.py --pexels-key YOUR_KEY
python scripts/download_attractions.py --pexels-key YOUR_KEY
```

### 方案 C：混合模式（生产推荐）

```
热门城市（Top 50）→  CDN 直链（JSON 直接配好）
    +
长尾城市（500+） →  后端 API 动态获取
    +
境外城市          →  Bing API / Unsplash API
```

---

## 三、关键文件清单

| 文件 | 用途 | 修改者 |
|------|------|--------|
| `travel-java/.../controller/CityController.java` | 城市图片 API（302 重定向） | 后端 |
| `travel-java/.../service/AmapService.java` | 高德 POI 图片搜索 | 后端 |
| `travel-java/.../service/CityMaterialService.java` | 城市基础数据初始化 | 后端 |
| `travel-java/.../entity/CityImage.java` | 城市图片 DB 实体 | 后端 |
| `travel-java/.../entity/AttractionImage.java` | 景点图片 DB 实体 | 后端 |
| `trval-h5/public/city-images.json` | 城市图片静态兜底（898 条） | 前端/运营 |
| `trval-h5/public/attraction-images.json` | 景点图片静态兜底（858 条） | 前端/运营 |
| `trval-h5/src/views/CitySelectView.vue` | 城市选择页图片渲染 | 前端 |
| `trval-h5/src/views/HomeView.vue` | 首页图片渲染 | 前端 |
| `trval-h5/src/views/TripsView.vue` | 行程页图片渲染 | 前端 |
| `scripts/download_landmarks.py` | 批量下载城市地标图（可选） | 运维 |
| `scripts/download_attractions.py` | 批量下载景点图片（可选） | 运维 |

---

## 四、前端图片加载逻辑（各页面统一）

```javascript
// 所有页面均遵循此优先级：
// 1. 后端 /api/city/image?name=xxx  （302 → 图片 URL）
// 2. /city-images.json              （静态 JSON 兜底）
// 3. CSS HSL 渐变色 + 首汉字          （永不失败的占位）

// 模板中的 img 标签均有 @error 处理：
<img :src="getImageUrl(name)" @error="e => e.target.style.display='none'" />
```

---

## 五、图片规格要求

| 属性 | 标准 |
|------|------|
| 比例 | 1:1 正方形（适配圆角卡片） |
| 最小分辨率 | 800×800 |
| 格式 | JPEG / WebP |
| 质量 | 85-90% |
| 内容 | 城市标志性地标实拍 |
| 禁止 | 水印、广告、文字贴纸、街景、商铺 |

---

## 六、目前状态

| 项目 | 状态 |
|------|------|
| 后端 API 接口 | ✅ 已就绪，等待配置图片源 |
| 高德 API Key | ⚠️ 未配置（在 application.yml 中填入即可） |
| 静态 JSON | ✅ 路径已预留，等待填充图片 |
| 前端渲染链路 | ✅ 三重 fallback 正常工作 |
| Python 下载工具 | ✅ 可用，需要 Pexels API Key |

---

## 七、接入后验证

```bash
# 1. 测试单城市图片
curl "http://localhost:8080/api/city/image?name=北京"
# 应返回 302 重定向到图片 URL

# 2. 测试批量映射
curl "http://localhost:8080/api/city/images/map"
# 应返回 {"北京": "https://...", "上海": "https://..."}

# 3. 前端验证
# 打开 http://localhost:5173 → 城市选择页 → 确认卡片图片正常展示
```
