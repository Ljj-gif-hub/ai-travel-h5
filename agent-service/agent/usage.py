"""
API 用量监控 — 进程内环形缓冲 + 内存聚合 + 成本归因

- 每请求记录：{request_id, user_id, endpoint, llm_calls, tokens, tool_calls, duration}
  （deque maxlen=500，进程内环形缓冲）
- 内存聚合：按 user_id 与按 provider 累计 token 用量
- 关联方式：contextvars —— 观测中间件在请求开始时把当前记录写入
  _current_request；llm_router / tools 在调用时通过上下文读写同一条记录，
  无需逐层传参。SSE 流式响应期间记录对象仍在生成器上下文中可见，
  中间件完成计时后其后的 LLM/工具用量会继续累加到同一对象（deque 存引用）。
- tokens 优先从 LLM 响应 usage 取，取不到按字符数/4 估算
"""
from __future__ import annotations

import logging
import threading
import time as _time
from collections import defaultdict, deque
from contextvars import ContextVar
from typing import Any, Dict, List, Optional

logger = logging.getLogger("travel-agent.usage")

# 环形缓冲容量上限
_BUFFER_MAX = 500
# 最近返回条数（/api/agent/usage 接口）
_RECENT_MAX = 20

# 当前请求的用量记录（观测中间件写入；LLM/工具调用方读取累加）
_current_request: "ContextVar[Optional[dict]]" = ContextVar(
    "travel_agent_usage_current", default=None
)


def extract_tokens(resp: Any) -> int:
    """从 LLM 响应中提取 token 用量；取不到按字符/4 估算。"""
    # 1) langchain 风格 usage_metadata（AIMessage 常见字段）
    meta = getattr(resp, "usage_metadata", None)
    if isinstance(meta, dict):
        try:
            inp = int(meta.get("input_tokens") or 0)
            out = int(meta.get("output_tokens") or 0)
        except (TypeError, ValueError):
            inp = out = 0
        if inp or out:
            return inp + out
    # 2) response_metadata 内的 usage / token_usage
    rm = getattr(resp, "response_metadata", None)
    if isinstance(rm, dict):
        usage = rm.get("usage") or rm.get("token_usage") or {}
        if isinstance(usage, dict):
            try:
                total = int(usage.get("total_tokens") or 0)
                if not total:
                    total = int(usage.get("prompt_tokens") or 0) + int(usage.get("completion_tokens") or 0)
            except (TypeError, ValueError):
                total = 0
            if total:
                return total
    # 3) 兜底估算：内容字符数 / 4
    content = getattr(resp, "content", "") or ""
    if isinstance(content, list):
        parts = []
        for item in content:
            if isinstance(item, dict):
                parts.append(str(item.get("text") or item.get("content") or ""))
            elif isinstance(item, str):
                parts.append(item)
        content = "".join(parts)
    return max(len(str(content)) // 4, 1)


class UsageTracker:
    """进程内用量追踪：环形缓冲 + 按 user_id / provider 聚合。"""

    def __init__(self, maxlen: int = _BUFFER_MAX):
        self._lock = threading.Lock()
        self._buffer: "deque[dict]" = deque(maxlen=maxlen)
        self._by_user: Dict[str, int] = defaultdict(int)   # user_id -> 累计 tokens
        self._by_provider: Dict[str, int] = defaultdict(int)  # provider -> 累计 tokens
        self._total_requests = 0
        self._total_tokens = 0

    # ---------- 请求生命周期 ----------

    def start_request(self, request_id: str, endpoint: str, user_id: Optional[str]) -> dict:
        """请求开始时建立用量记录，并绑定到当前上下文。"""
        record = {
            "request_id": request_id,
            "user_id": user_id or "",
            "endpoint": endpoint,
            "llm_calls": 0,
            "tokens": 0,
            "tool_calls": {},  # 工具名 -> 调用次数
            "duration": 0.0,
            "started_at": _time.time(),
        }
        with self._lock:
            self._total_requests += 1
        return record

    def finish_request(self, record: dict, duration: float) -> None:
        """请求结束时：写入耗时、入环形缓冲、更新聚合。

        幂等设计（SSE 流式响应期间记录可能被继续累加，生成器收尾可再调一次刷新聚合）：
        token 聚合只累计差额，缓冲只入一次。
        """
        with self._lock:
            record["duration"] = round(duration, 3)
            # 聚合差额：避免同一记录重复计数
            prev_agg = record.get("_agg_tokens", 0)
            delta = record["tokens"] - prev_agg
            if delta:
                self._total_tokens += delta
                if record["user_id"]:
                    self._by_user[record["user_id"]] += delta
                record["_agg_tokens"] = record["tokens"]
            # 缓冲只入一次（记录对象后续被 SSE 生成器继续修改，deque 存引用即可见）
            if not record.get("_in_buffer"):
                self._buffer.append(record)
                record["_in_buffer"] = True

    # ---------- LLM / 工具打点（由 llm_router / tools 调用） ----------

    def record_llm(self, provider: str, model: str, tokens: int) -> None:
        """一次 LLM 调用：请求级记录 + provider 聚合。"""
        tokens = max(int(tokens or 0), 0)
        with self._lock:
            self._by_provider[provider] += tokens
        record = _current_request.get()
        if record is not None:
            record["llm_calls"] += 1
            record["tokens"] += tokens

    def record_tool(self, tool_name: str) -> None:
        """一次工具调用：请求级记录（按工具名计数）。"""
        record = _current_request.get()
        if record is not None:
            record["tool_calls"][tool_name] = record["tool_calls"].get(tool_name, 0) + 1

    # ---------- 查询 ----------

    def summary(self) -> dict:
        """用量汇总：总请求数、总 tokens、按 provider 分布（供 /api/agent/usage）。"""
        with self._lock:
            return {
                "total_requests": self._total_requests,
                "total_tokens": self._total_tokens,
                "by_provider": dict(self._by_provider),
            }

    def recent(self, n: int = _RECENT_MAX) -> List[dict]:
        """最近 n 条请求记录（最新在前）。"""
        with self._lock:
            items = list(self._buffer)
        # 只回传外部字段，避免内部聚合标记泄漏
        public = []
        for r in items[-n:][::-1]:
            public.append({
                "request_id": r["request_id"],
                "user_id": r["user_id"],
                "endpoint": r["endpoint"],
                "llm_calls": r["llm_calls"],
                "tokens": r["tokens"],
                "tool_calls": dict(r.get("tool_calls") or {}),
                "duration": r["duration"],
            })
        return public


# 全局单例 — 观测中间件与 llm_router / tools 共享
usage_tracker = UsageTracker()
