# 🤖 AI Travel Agent

> 基于 LangChain + FastAPI 的旅游规划智能体。ReAct 推理 + Tavily 实时搜索 + 高德地图 + 自动预算校验 + 记忆层（长期用户偏好 + 短期会话上下文 + 调研缓存）。

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

### 🧠 记忆层

| 类型 | 标识 | 说明 |
|------|------|------|
| 长期用户偏好 | `user_id` | 酒店档位 / 预算 / 出行风格，按用户个性化感知 |
| 短期会话上下文 | `session_id` | 上次规划摘要，实现「延续上下文」连续规划 |
| 调研缓存 | destination + days | 同一目的地短时间复用搜索结果，避免重复 ReAct |

- 持久化：写入 `data/agent_memory.json`（gitignored），进程重启不丢
- 前端通过 JWT 解码出的 `user_id` + 本地会话号 `session_id` 传入
- 调研缓存为内存 TTL（默认 1 小时），不落盘
- 行程调整：`adjustment` 参数携带新的调整需求（如「放慢节奏」），Agent 局部增量重规划

### 🧭 知识库（RAG 旅游攻略检索）

| 项 | 说明 |
|------|------|
| 语料 | `agent/knowledge/<city>.md`（北京/上海/广州/成都/西安/杭州，可版本控制） |
| 检索 | 纯 Python 字符级 bigram + TF-IDF + 加权匹配（零新依赖） |
| 注入 | `_phase_plan` 在规划 Prompt 中注入「## 本地攻略参考」，`research_notes` 追加来源 |
| 预留 | `KnowledgeProvider` 接口 + `KNOWLEDGE_SOURCE=builtin\|remote`，未来外部语料库实现同接口即接入 |

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
# 事件类型: phase_start / phase_end / thinking / warning / adjustment / plan_update / complete / error
```

### 原始 SSE（Spring Boot 透传专用）

```bash
POST /api/agent/plan/stream-sse
Content-Type: application/json

# 直接从 Request body 解析 JSON，兼容任意 JSON 格式（无需 Pydantic 校验）
# 后端 AgentProxyController 即转发到此端点（前端 → Spring Boot 3200 → Python 3201）
```

## 📁 项目结构

```
agent-service/
├── main.py              # FastAPI 入口（同步/SSE/原始SSE + 健康检查）
├── requirements.txt      # Python 依赖
├── .env.example          # 环境变量模板
├── agent/
│   ├── planner.py        # 5 阶段 Agent 编排器核心
│   ├── tools.py          # 4 个 LangChain Tool
│   ├── memory.py         # 🆕 记忆层（长期偏好 + 会话上下文 + 调研缓存）
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
