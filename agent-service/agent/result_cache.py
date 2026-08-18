"""
规划结果缓存 — 按请求参数 hash 缓存最终行程方案

设计：
  - key：destination/days/budget/styles/companion/user_id/ui_lang 归一化后
    JSON sort_keys 序列化 → sha256 取前 16 位
  - TTL 1 小时、容量上限 100 条（LRU，超出淘汰最旧）
  - Demo 模式 / user_id 为 None 时由调用方跳过缓存（避免跨用户混用）

用途：/plan 三个端点（sync / stream / stream-sse）在 LLM 规划前先查缓存，
命中则跳过全部 LLM 调用直接产出 complete 事件（附 cached: true）。
"""
from __future__ import annotations

import hashlib
import json
import logging
import threading
import time as _time
from collections import OrderedDict
from typing import Any, Dict, Optional

logger = logging.getLogger("travel-agent.cache")

# 容量上限（LRU 淘汰最旧）
_CACHE_MAX = 100
# 默认 TTL：1 小时
_CACHE_TTL = 3600


class PlanResultCache:
    """进程内线程安全的行程结果缓存（LRU + TTL）。"""

    def __init__(self, max_size: int = _CACHE_MAX, ttl: int = _CACHE_TTL):
        self._lock = threading.Lock()
        self._store: "OrderedDict[str, tuple]" = OrderedDict()
        self.max_size = max_size
        self.ttl = ttl

    @staticmethod
    def key(req: Dict[str, Any]) -> str:
        """按规划参数构建缓存 key（参数归一化，styles 排序保证顺序无关）。

        返回值恒为 16 位 hex 字符串。
        """
        styles = sorted([str(s) for s in (req.get("styles") or [])])
        params = {
            "destination": str(req.get("destination") or "").strip(),
            "days": str(req.get("days") or ""),
            "budget": str(req.get("budget") or ""),
            "styles": styles,
            "companion": str(req.get("companion") or ""),
            "user_id": str(req.get("user_id") or ""),
            # ui_lang 也参与 key：同一参数下不同界面语言的缓存不能混用
            "ui_lang": str(req.get("ui_lang") or "zh"),
        }
        payload = json.dumps(params, ensure_ascii=False, sort_keys=True)
        return hashlib.sha256(payload.encode("utf-8")).hexdigest()[:16]

    def get(self, key: str) -> Optional[Dict[str, Any]]:
        """命中且未过期返回缓存的行程，否则返回 None。"""
        with self._lock:
            hit = self._store.get(key)
        if hit is None:
            return None
        ts, plan = hit
        if (_time.time() - ts) >= self.ttl:
            # 过期即淘汰（顺带清理，避免过期条目一直占着容量）
            with self._lock:
                self._store.pop(key, None)
            return None
        return plan

    def set(self, key: str, plan: Dict[str, Any]) -> None:
        """写入缓存：命中则刷新为最新（移到 LRU 尾部），超出容量淘汰最旧。"""
        with self._lock:
            if key in self._store:
                del self._store[key]
            self._store[key] = (_time.time(), plan)
            while len(self._store) > self.max_size:
                self._store.popitem(last=False)

    def clear(self) -> None:
        """清空缓存（测试/排障用）。"""
        with self._lock:
            self._store.clear()


# 全局单例 — 三个规划端点共享
plan_cache = PlanResultCache()
