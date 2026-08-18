"""
服务指标 — Prometheus 指标定义（HTTP / LLM / 工具）

指标：
  - http_requests_total(method, path, status)        HTTP 请求总数
  - http_request_duration_seconds(method, path)      请求耗时直方图
  - llm_calls_total(model, phase)                    LLM 调用次数
  - llm_tokens_total(model)                          LLM token 用量
  - tool_calls_total(tool)                           工具调用次数

抓取端点：GET /metrics（顶层路径，在 /api/agent 鉴权前缀之外，天然免鉴权）。
打点位置：main.py 观测中间件（HTTP）、llm_router.py（LLM）、tools.py（工具）。
"""
from __future__ import annotations

from prometheus_client import Counter, Histogram

# HTTP 指标（观测中间件在每次请求结束时打点）
HTTP_REQUESTS = Counter(
    "http_requests_total",
    "HTTP 请求总数",
    ["method", "path", "status"],
)
HTTP_DURATION = Histogram(
    "http_request_duration_seconds",
    "HTTP 请求耗时（秒）",
    ["method", "path"],
    buckets=(0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0, 30.0, 60.0, 120.0, 300.0),
)

# LLM 指标（llm_router 在每次成功调用后打点）
LLM_CALLS = Counter(
    "llm_calls_total",
    "LLM 调用次数",
    ["model", "phase"],
)
LLM_TOKENS = Counter(
    "llm_tokens_total",
    "LLM token 用量",
    ["model"],
)

# 工具指标（tools.py 工具包装层在每次调用时打点）
TOOL_CALLS = Counter(
    "tool_calls_total",
    "工具调用次数",
    ["tool"],
)
