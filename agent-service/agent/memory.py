"""
记忆层 — 短期会话记忆 + 长期用户偏好

架构定位（Agent 闭环工作流中的「记忆层」）：
  接收请求 → 感知输入 → LLM理解意图 → 拆解任务 → 调用工具 → 【记忆存取】→ 结果输出 → 反馈迭代

- 短期记忆：session_id → 上次规划摘要，用于"延续上下文"（如同用户接着上次聊）
- 长期记忆：user_id → 用户偏好（酒店档位/预算/出行风格），用于个性化感知
- 持久化：写入 data/agent_memory.json（gitignored），进程重启不丢
"""
from __future__ import annotations

import json
import threading
import time as _time
from datetime import datetime
from pathlib import Path


class MemoryStore:
    """线程安全的 JSON 文件记忆存储"""

    def __init__(self, path: str | None = None):
        self._lock = threading.Lock()
        self.path: Path = Path(path) if path else Path(__file__).parent.parent / "data" / "agent_memory.json"
        self._data = self._load()
        # 感知层调研缓存（内存，不落盘）：同一目的地短时间复用，避免重复 ReAct
        self._research_cache: dict = {}

    # ---------- 底层 ----------
    def _load(self) -> dict:
        try:
            if self.path.exists():
                raw = json.loads(self.path.read_text(encoding="utf-8"))
                if isinstance(raw, dict):
                    return {"users": raw.get("users", {}), "sessions": raw.get("sessions", {})}
        except Exception:
            pass
        return {"users": {}, "sessions": {}}

    def _save(self) -> None:
        try:
            self.path.parent.mkdir(parents=True, exist_ok=True)
            self.path.write_text(
                json.dumps(self._data, ensure_ascii=False, indent=2),
                encoding="utf-8",
            )
        except Exception:
            pass  # 记忆写入失败不应阻断主流程

    # ---------- 长期记忆：用户偏好 ----------
    def get_user(self, user_id: str) -> dict:
        if not user_id:
            return {}
        return self._data["users"].get(str(user_id), {})

    def set_user(self, user_id: str, prefs: dict) -> None:
        if not user_id:
            return
        with self._lock:
            self._data["users"][str(user_id)] = prefs
            self._save()

    def build_user_context(self, user_id: str) -> str:
        """把用户长期偏好拼成一段可注入 prompt 的记忆上下文"""
        if not user_id:
            return ""
        prefs = self.get_user(user_id)
        if not prefs:
            return ""
        parts = []
        text = prefs.get("preference_text")
        if text:
            parts.append(f"历史偏好：{text}")
        if prefs.get("last_destination"):
            parts.append(f"上次规划过：{prefs['last_destination']}")
        return "【用户长期记忆】" + "；".join(parts) + "\n" if parts else ""

    # ---------- 短期记忆：会话上下文 ----------
    def get_session(self, session_id: str) -> dict:
        if not session_id:
            return {}
        return self._data["sessions"].get(str(session_id), {})

    def set_session(self, session_id: str, data: dict) -> None:
        if not session_id:
            return
        with self._lock:
            self._data["sessions"][str(session_id)] = data
            self._save()

    def build_session_context(self, session_id: str) -> str:
        """把短期会话记忆拼成一段可注入 prompt 的上下文"""
        if not session_id:
            return ""
        sess = self.get_session(session_id)
        if not sess or not sess.get("destination"):
            return ""
        return (
            f"【会话记忆】这是连续规划：用户上次规划了「{sess.get('destination')}」"
            f"{sess.get('days', '')}天，主题：{str(sess.get('overview', ''))[:60]}\n"
        )

    # ---------- 感知层调研缓存（内存 TTL） ----------
    def get_research(self, destination: str, days: int, ttl: int = 3600):
        """命中则返回缓存的调研结果，未命中/过期返回 None"""
        key = f"{destination}:{days}"
        hit = self._research_cache.get(key)
        if hit and (_time.time() - hit[0]) < ttl:
            return hit[1]
        return None

    def set_research(self, destination: str, days: int, research: dict) -> None:
        key = f"{destination}:{days}"
        with self._lock:
            self._research_cache[key] = (_time.time(), research)

    # ---------- 通用 ----------
    def touch(self, key: str) -> None:
        """记录最后活动时间（用于展示/调试）"""
        with self._lock:
            self._data.setdefault("meta", {})[key] = datetime.now().isoformat()
            self._save()


# 全局单例 — 各阶段共享同一个记忆存储
memory_store = MemoryStore()
