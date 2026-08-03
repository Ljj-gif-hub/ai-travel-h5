# 🤖 AI Travel Agent

> 基于 LangChain + FastAPI 的旅游规划智能体。ReAct 推理 + Tavily 实时搜索 + 高德地图 + 自动预算校验。

## 🎯 核心能力

这是一个真正的 AI Agent，不是普通的 LLM 聊天应用。它能**自主调用工具、实时搜索信息、校验结果并自动修正**，形成完整的「思考→行动→观察→优化」闭环。

| | 传统 LLM 应用 | 本 Agent |
|---|---|---|
| 信息源 | 训练数据（可能过时） | Tavily 实时搜索 + LLM |
| 预算 | 不校验 | 自动核算 + 4 策略迭代调整 |
| 路线 | 不检查 | 同区域排序，避免折返 |
| 规划方式 | 一次 Prompt → 一次输出 | ReAct 循环 |
| 过程可见 | 黑盒 | SSE 流式推送 5 阶段进度 |

## 🏗️ 架构

```
用户请求 → FastAPI → Agent 编排器
                        │
          ┌─────────────┼─────────────┐
          ▼             ▼             ▼
    search_attractions  get_commute   calculate_budget
    (Tavily API)        (高德 API)    (本地引擎)
          │             │             │
          └─────────────┼─────────────┘
                        ▼
              LLM 汇总 → 结构化 JSON 行程
                        │
                        ▼
              SSE 流式推送给前端
```

### 5 阶段流水线

| 阶段 | 说明 | 耗时 |
|------|------|------|
| 🔍 RESEARCH | 并行搜索景点/美食/酒店实时信息 | ~3s |
| 📋 PLAN | LLM 基于搜索结果生成结构化 JSON | ~20s |
| 🔍 VERIFY | 核算总预算 + 检查路线 | ~1s |
| 🔧 ADJUST | 超标自动降酒店/删次要景点/优化餐饮 | ~5s |
| ✨ FINALIZE | 输出完整方案（每日卡片+预算+酒店+贴士） | ~1s |

### 内置工具

| 工具 | 实现 | 说明 |
|------|------|------|
| `search_attractions_info` | Tavily Search API | 联网搜索景点/美食实时信息 |
| `search_hotels_info` | Tavily Search API | 搜索酒店区域分布和价格 |
| `get_commute_info` | 高德地图 API | 计算两地通勤距离和时间 |
| `calculate_budget` | 本地引擎 | 核算总花费 + 超标策略建议 |

### Demo 模式

未配置 LLM API Key 时自动启用，内置 8 城真实数据（成都/北京/上海/杭州/大理/三亚/西安/重庆），零依赖即可完整体验 5 阶段流程。

## 🚀 快速启动

### 前置条件
- Python >= 3.10
- pip

### 1. 安装

```bash
git clone https://github.com/Ljj-gif-hub/ai-travel-agent.git
cd ai-travel-agent
pip install -r requirements.txt
```

### 2. 配置

```bash
cp .env.example .env
# 编辑 .env 填写 API Key
```

| 变量 | 必填 | 说明 |
|------|------|------|
| `LLM_API_KEY` | ✅ | DeepSeek / OpenAI 等 API Key |
| `LLM_BASE_URL` | ❌ | 默认 `https://api.deepseek.com` |
| `LLM_MODEL` | ❌ | 默认 `deepseek-v4-flash` |
| `TAVILY_API_KEY` | ❌ | 实时搜索，未配则用内置数据 |
| `AMAP_WEB_KEY` | ❌ | 高德地图通勤计算，未配则估算 |

### 3. 启动

```bash
python main.py
# → http://localhost:3201
# 健康检查: http://localhost:3201/api/agent/health
```

## 📡 API

### 健康检查

```bash
GET /api/agent/health
```

### 同步生成行程

```bash
POST /api/agent/plan
Content-Type: application/json

{
  "destination": "成都",
  "days": 3,
  "budget": 5000,
  "people": 2,
  "companion": "情侣",
  "styles": ["美食", "人文"],
  "hotel_level": "舒适型",
  "pace": "适中"
}
```

### SSE 流式生成

```bash
POST /api/agent/plan/stream
Content-Type: application/json

# 同上参数，返回 text/event-stream
# 事件类型: phase_start / phase_end / warning / complete / error
```

## 📁 项目结构

```
agent-service/
├── main.py              # FastAPI 入口，3 个端点
├── requirements.txt      # Python 依赖
├── .env.example          # 环境变量模板
├── agent/
│   ├── planner.py        # 5 阶段 Agent 编排器核心
│   ├── tools.py          # 4 个 LangChain Tool
│   ├── schemas.py        # Pydantic 数据模型
│   └── prompts.py        # System Prompt 模板
└── README.md
```

## 🛠️ 技术栈

| 层 | 技术 |
|----|------|
| Web 框架 | FastAPI + Uvicorn |
| Agent 框架 | LangChain + LangGraph |
| LLM | DeepSeek / OpenAI / 通义千问（OpenAI 兼容协议） |
| 搜索 | Tavily Search API |
| 地图 | 高德地图 Web API |
| 流式 | FastAPI StreamingResponse (SSE) |
| 校验 | Pydantic v2 |

## 🔗 集成到现有项目

本服务作为独立微服务运行，前端通过 Vite 代理或直连调用：

```js
// Vite 代理配置
proxy: {
  '/api/agent': { target: 'http://localhost:3201', changeOrigin: true }
}

// 前端调用
import { agentPlanStream } from './api/agent'
agentPlanStream(params, {
  onProgress: (e) => console.log(e),
  onComplete: (e) => console.log(e.data),
})
```

## 📄 许可证

MIT License
