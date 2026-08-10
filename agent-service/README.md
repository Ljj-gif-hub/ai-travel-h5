# 🤖 AI Travel Agent

> 基于 LangChain + FastAPI 的旅游规划智能体。ReAct 推理 + Tavily 实时搜索 + 高德地图 + 自动预算校验 + 记忆层（长期用户偏好 + 短期会话上下文 + 调研缓存）+ **MCP 协议**（客户端/服务端）+ **工具权限控制**。

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

### 🔌 MCP（Model Context Protocol）

Agent 同时是 **MCP 客户端** 与 **MCP 服务端**（`mcp` / `langchain-mcp-adapters` 为可选依赖，未安装优雅降级）：

**作为客户端**：接入外部 MCP 服务器，把其工具动态加载为 LangChain 工具，供 ReAct 循环调用（并套用权限包装）。
```bash
MCP_ENABLED=true
MCP_CLIENT_SERVERS=demo=http://localhost:9000/mcp,weather=http://localhost:9001/mcp   # 名字=URL，逗号分隔
```

**作为服务端**：把自身工具暴露为 MCP 服务，供其他客户端调用。
```bash
MCP_SERVER_ENABLED=true           # 随服务自启
# 或独立运行：python -m agent.mcp_server
MCP_SERVER_TRANSPORT=streamable-http   # streamable-http | sse
MCP_SERVER_PORT=3202              # streamable-http 端点 /mcp，sse 端点 /sse
```

暴露的 MCP 工具：`search_attractions` / `search_hotels` / `get_commute` / `calculate_budget` / `retrieve_guide`（知识库 RAG）。

### 🔐 Permission 工具权限控制

按策略决定 Agent 能否调用某个工具，防止 LLM 误用高风险操作。核心工具在注册时自动套上权限包装，MCP 外部工具同样受控。

| 配置 | 说明 |
|------|------|
| `PERMISSION_MODE=open` | （默认）所有工具可调用 |
| `PERMISSION_MODE=blocklist` | 黑名单：`TOOL_BLOCKLIST` 中的工具被拒绝 |
| `PERMISSION_MODE=allowlist` | 白名单：仅 `TOOL_ALLOWLIST` 中的工具可调用 |

- 被拒绝的调用返回 `{"error":"PERMISSION_DENIED", ...}` 给 LLM（可感知并改走其他工具），不中断 Agent 循环
- 查看策略：`GET /api/agent/permissions`
- 例：`PERMISSION_MODE=blocklist` + `TOOL_BLOCKLIST=calculate_budget` → 预算核算工具被禁用

### 🔑 Agent 服务鉴权（安全）

Agent 服务不再裸奔：所有 `/api/agent/*` 请求必须通过以下之一（`agent/auth.py` 中间件）：

| 方式 | 说明 |
|------|------|
| `X-Agent-Key` | 与 `AGENT_API_KEY` 一致（后端透传时附加的共享密钥） |
| `Authorization: Bearer <JWT>` | 与后端 `JWT_SECRET` 同密钥签发；校验通过后把 `userId` 与请求绑定，防伪造 `user_id` 越权读写记忆 |

- 前端 → Spring Boot（JWT 鉴权 + 附加共享密钥）→ Python Agent，**禁止直连**（vite/nginx 直连绕过已移除）
- 未配置 `AGENT_API_KEY` 且无有效 JWT 时请求返回 401
- 本地开发示例：后端与 Agent 均设 `AGENT_API_KEY=xxx`；如需 JWT 绑定设 `AGENT_JWT_SECRET=JWT_SECRET`

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
| `MCP_ENABLED` / `MCP_CLIENT_SERVERS` | ❌ | MCP 客户端：接入外部 MCP 服务器工具 |
| `MCP_SERVER_ENABLED` / `MCP_SERVER_PORT` | ❌ | MCP 服务端：暴露自身工具 |
| `PERMISSION_MODE` / `TOOL_ALLOWLIST` / `TOOL_BLOCKLIST` | ❌ | 工具权限控制（open/blocklist/allowlist） |

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

### 工具权限策略

```bash
GET /api/agent/permissions
# → { "policy": { "mode": "open", "allowlist": [...], "blocklist": [...] },
#     "tools": { "core": [...], "mcp_client": { "enabled": false, ... } } }
```

## 📁 项目结构

```
agent-service/
├── main.py              # FastAPI 入口（同步/SSE/原始SSE + 健康检查）
├── requirements.txt      # Python 依赖
├── .env.example          # 环境变量模板
├── agent/
│   ├── planner.py        # 5 阶段 Agent 编排器核心
│   ├── tools.py          # 4 个 LangChain Tool（套权限包装）
│   ├── memory.py         # 🆕 记忆层（长期偏好 + 会话上下文 + 调研缓存）
│   ├── schemas.py        # Pydantic 数据模型
│   ├── prompts.py        # System Prompt 模板
│   ├── permissions.py    # 🆕 工具权限控制（open/blocklist/allowlist + 包装器）
│   ├── mcp_bridge.py     # 🆕 MCP 桥接（客户端加载外部工具 + 服务端构建）
│   └── mcp_server.py     # 🆕 MCP 服务端独立入口（python -m agent.mcp_server）
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
