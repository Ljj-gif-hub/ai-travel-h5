# AI 智能旅游助手

> 基于 Vue 3 + Spring Boot 3.2 的全栈 AI 旅游规划应用，支持多供应商 LLM、SSE 流式行程生成、社区游记、短视频播放、百度地图集成。

## 📁 项目结构

```
├── trval-h5/          # 前端 — Vue 3 + Vite 8 + Vant 4 移动端 H5
├── travel-java/       # 后端 — Spring Boot 3.2 + Java 17 + JWT + Redis
├── scripts/           # 数据采集脚本
└── README.md          # 本文件
```

## 🔗 子项目文档

- 📱 [前端详细文档](trval-h5/README.md) — 技术栈、功能列表、最近更新、项目结构、环境启动
- 🖥️ [后端详细文档](travel-java/README.md) — API 列表、安全机制、AI 供应商配置、部署指南

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

## 📝 最近更新（v2.2.0 — 2026-07-27）

### 前端
- GPU 性能优化：移除 will-change、blur 降至 8px、禁用无限动画
- 全局品牌渐变背景 + 装饰光圈
- 抖音风格评论区（@用户名 + 回复分组 + 展开折叠）
- 短视频播放器（进度条 + 全屏按钮 + 横竖屏切换）
- 社区关注功能（关注 Tab 筛选 + 状态全局同步）
- SearchBar 穿透修复 + PostView 图片上传重做
- 本地 SVG 头像生成器（零外部依赖）

### 后端
- 评论回复系统（parentId 二级嵌套）
- 关注/粉丝数据隔离（JWT 提取 userId）

## 📄 许可证

MIT License
