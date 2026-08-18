"""
LLM 多模型路由 — primary 优先 + 自动降级 fallback + 冷却恢复

- 环境变量 LLM_FALLBACK_BASE_URL / LLM_FALLBACK_API_KEY / LLM_FALLBACK_MODEL
  全部设置才启用 fallback（由 planner._build_llm_router 判断）
- 捕获 httpx / openai 网络 / 429 / 5xx / 超时等瞬态异常后切换 fallback；
  非瞬态异常（如 400 参数错误）直接抛出，不做无意义降级
- 连续 3 次 primary 失败 → primary 冷却 60 秒（期间直接走 fallback）；
  primary 调用成功后立即恢复（清空失败计数与冷却）
- 每次成功调用打点：Prometheus llm_calls_total / llm_tokens_total + 请求级用量归因
- bind_tools 返回共享同一份冷却状态的新路由实例；create_react_agent 场景用
  「动态模型」选择函数（每次调用模型节点时重新 pick），见 planner._research_subagent
"""
from __future__ import annotations

import logging
import threading
import time as _time
from typing import Any, Optional

from langchain_core.runnables import Runnable

logger = logging.getLogger("travel-agent.llm-router")

# primary 冷却时长（秒）
_COOLDOWN_SECONDS = 60.0
# 触发冷却的连续失败次数
_FAIL_STREAK_LIMIT = 3


class _RouterState:
    """primary 的失败计数与冷却状态（bind_tools 派生实例共享）。"""

    __slots__ = ("lock", "fail_streak", "cooldown_until")

    def __init__(self):
        self.lock = threading.Lock()
        self.fail_streak = 0
        self.cooldown_until = 0.0


def _is_transient(exc: Exception) -> bool:
    """判断异常是否为可降级的瞬态故障（网络/429/5xx/超时）。"""
    # openai SDK 异常（langchain-openai 底层透传）
    try:
        import openai  # noqa: PLC0415
        if isinstance(exc, (openai.RateLimitError, openai.APITimeoutError, openai.APIConnectionError)):
            return True
        if isinstance(exc, openai.APIStatusError):
            code = getattr(exc, "status_code", 0)
            return code == 429 or code >= 500
    except ImportError:
        pass
    # httpx / 底层网络异常
    try:
        import httpx  # noqa: PLC0415
        if isinstance(exc, httpx.HTTPStatusError):
            code = exc.response.status_code
            return code == 429 or code >= 500
        if isinstance(exc, httpx.HTTPError):
            return True
    except ImportError:
        pass
    if isinstance(exc, (TimeoutError, ConnectionError, OSError)):
        return True
    # 兜底：异常文案含典型瞬态关键字
    text = str(exc).lower()
    return any(k in text for k in ("429", "rate limit", "timeout", "timed out", "connection", "5xx", "server error"))


class LLMRouter(Runnable):
    """包装 primary/fallback 两个 ChatOpenAI 实例，按状态路由 invoke / ainvoke。

    继承 Runnable：可直接作为 langgraph create_react_agent 的「动态模型」
    选择函数返回值（prompt runnable 可与其管道串联）。
    """

    def __init__(self, primary: Any, fallback: Optional[Any] = None, phase: str = ""):
        self.primary = primary
        self.fallback = fallback
        self.phase = phase  # 打点用阶段名（research/plan/review/refine…）
        self._state = _RouterState()

    # ---------- 模型选择 ----------

    def _pick(self) -> tuple:
        """选择当前应使用的模型：冷却期内直接走 fallback，否则 primary。"""
        with self._state.lock:
            if self.fallback is not None and _time.monotonic() < self._state.cooldown_until:
                return self.fallback, "fallback"
        return self.primary, "primary"

    def _on_failure(self) -> None:
        """primary 失败：累计失败次数，连续 3 次则冷却 60 秒。"""
        with self._state.lock:
            self._state.fail_streak += 1
            if self._state.fail_streak >= _FAIL_STREAK_LIMIT:
                self._state.cooldown_until = _time.monotonic() + _COOLDOWN_SECONDS
                self._state.fail_streak = 0
                logger.warning(
                    "[LLM路由] primary 连续 %d 次失败，冷却 %.0f 秒（期间走 fallback）",
                    _FAIL_STREAK_LIMIT, _COOLDOWN_SECONDS,
                )

    def _on_success(self) -> None:
        """primary 成功：立即恢复（清空失败计数与冷却）。"""
        with self._state.lock:
            self._state.fail_streak = 0
            self._state.cooldown_until = 0.0

    def _model_name(self, provider: str) -> str:
        model_obj = self.primary if provider == "primary" else self.fallback
        return getattr(model_obj, "model_name", None) or getattr(model_obj, "model", None) or provider

    # ---------- 指标 / 用量打点 ----------

    def _record_success(self, provider: str, resp: Any) -> None:
        """成功调用打点：Prometheus 计数 + 请求级用量归因。失败不影响业务。"""
        try:
            from .metrics import LLM_CALLS, LLM_TOKENS
            from .usage import extract_tokens, usage_tracker
            model = self._model_name(provider)
            LLM_CALLS.labels(model=model, phase=self.phase or "unknown").inc()
            tokens = extract_tokens(resp)
            LLM_TOKENS.labels(model=model).inc(tokens)
            usage_tracker.record_llm(provider=provider, model=model, tokens=tokens)
        except Exception as exc:  # noqa: BLE001
            logger.debug("[LLM路由] 指标记录失败（忽略）: %s", exc)

    # ---------- 调用分发 ----------

    def _dispatch_sync(self, call_name: str, *args, **kwargs):
        """同步分发：primary 优先，瞬态异常降级 fallback。"""
        if self.fallback is None:
            resp = getattr(self.primary, call_name)(*args, **kwargs)
            self._record_success("primary", resp)
            return resp
        model, provider = self._pick()
        if provider == "primary":
            try:
                resp = getattr(model, call_name)(*args, **kwargs)
            except Exception as exc:  # noqa: BLE001
                if not _is_transient(exc):
                    raise  # 非瞬态异常（如 400）不做降级，直接上抛
                logger.warning("[LLM路由] primary %s 调用失败（%s），降级 fallback", self.phase or "-", exc)
                self._on_failure()
                resp = getattr(self.fallback, call_name)(*args, **kwargs)
                self._record_success("fallback", resp)
                return resp
            self._on_success()
            self._record_success("primary", resp)
            return resp
        # 冷却期内直接走 fallback
        try:
            resp = getattr(self.fallback, call_name)(*args, **kwargs)
        except Exception as exc:  # noqa: BLE001
            logger.warning("[LLM路由] fallback %s 调用失败: %s", self.phase or "-", exc)
            raise
        self._record_success("fallback", resp)
        return resp

    async def _dispatch_async(self, call_name: str, *args, **kwargs):
        """异步分发（与 _dispatch_sync 同逻辑）。"""
        if self.fallback is None:
            resp = await getattr(self.primary, call_name)(*args, **kwargs)
            self._record_success("primary", resp)
            return resp
        model, provider = self._pick()
        if provider == "primary":
            try:
                resp = await getattr(model, call_name)(*args, **kwargs)
            except Exception as exc:  # noqa: BLE001
                if not _is_transient(exc):
                    raise
                logger.warning("[LLM路由] primary %s 调用失败（%s），降级 fallback", self.phase or "-", exc)
                self._on_failure()
                resp = await getattr(self.fallback, call_name)(*args, **kwargs)
                self._record_success("fallback", resp)
                return resp
            self._on_success()
            self._record_success("primary", resp)
            return resp
        try:
            resp = await getattr(self.fallback, call_name)(*args, **kwargs)
        except Exception as exc:  # noqa: BLE001
            logger.warning("[LLM路由] fallback %s 调用失败: %s", self.phase or "-", exc)
            raise
        self._record_success("fallback", resp)
        return resp

    # ---------- LangChain 兼容接口 ----------

    def invoke(self, input, config=None, **kwargs):
        """Runnable 接口：路由分发到主/备模型的 invoke。"""
        return self._dispatch_sync("invoke", input, config=config, **kwargs)

    async def ainvoke(self, input, config=None, **kwargs):
        """Runnable 接口：路由分发到主/备模型的 ainvoke。"""
        return await self._dispatch_async("ainvoke", input, config=config, **kwargs)

    def bind_tools(self, tools, **kwargs):
        """绑定工具后返回新路由实例（共享同一份冷却状态，供 create_react_agent 使用）。"""
        clone = LLMRouter.__new__(LLMRouter)
        clone.primary = self.primary.bind_tools(tools, **kwargs) if self.primary is not None else None
        clone.fallback = self.fallback.bind_tools(tools, **kwargs) if self.fallback is not None else None
        clone.phase = self.phase
        clone._state = self._state  # 共享失败计数 / 冷却状态
        return clone
