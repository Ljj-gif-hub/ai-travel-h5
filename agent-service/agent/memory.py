"""
记忆层 — 短期会话记忆 + 长期用户偏好

架构定位（Agent 闭环工作流中的「记忆层」）：
  接收请求 → 感知输入 → LLM理解意图 → 拆解任务 → 调用工具 → 【记忆存取】→ 结果输出 → 反馈迭代

- 短期记忆：session_id → 上次规划摘要，用于"延续上下文"（如同用户接着上次聊）
- 长期记忆：user_id → 用户偏好（酒店档位/预算/出行风格），用于个性化感知
- 持久化：写入 data/agent_memory.json（gitignored），进程重启不丢；原子写（临时文件 + os.replace）
"""
from __future__ import annotations

import json
import logging
import os
import threading
import time as _time
from collections import OrderedDict
from datetime import datetime
from pathlib import Path

logger = logging.getLogger("travel-agent.memory")

# 调研缓存容量上限（B8）：LRU，超出淘汰最旧条目，防内存无界增长
_RESEARCH_CACHE_MAX = 200
# 用户反馈容量上限（每用户保留最近 N 条，随记忆持久化）
_FEEDBACK_MAX = 20


class MemoryStore:
    """线程安全的 JSON 文件记忆存储"""

    def __init__(self, path: str | None = None):
        self._lock = threading.Lock()
        self.path: Path = Path(path) if path else Path(__file__).parent.parent / "data" / "agent_memory.json"
        self._data = self._load()
        self._last_saved = self._snapshot()
        # 感知层调研缓存（内存，不落盘）：同一目的地短时间复用，避免重复 ReAct
        self._research_cache: "OrderedDict[str, tuple]" = OrderedDict()

    # ---------- 底层 ----------
    def _snapshot(self) -> str:
        """当前数据的序列化快照（dirty 对比用）"""
        return json.dumps(self._data, ensure_ascii=False, indent=2, sort_keys=True)

    def _load(self) -> dict:
        try:
            if self.path.exists():
                raw = json.loads(self.path.read_text(encoding="utf-8"))
                if isinstance(raw, dict):
                    return {
                        "users": raw.get("users", {}),
                        "sessions": raw.get("sessions", {}),
                        # 用户反馈（老文件无此键时为 {}，向后兼容）
                        "feedback": raw.get("feedback", {}),
                    }
        except Exception as e:
            # 文件损坏/不可读时告警并回退空数据，不阻断记忆层初始化
            logger.warning("记忆文件读取失败（回退空数据）: %s: %s", self.path, e)
        return {"users": {}, "sessions": {}, "feedback": {}}

    def _save(self) -> None:
        """原子写盘（A2）：先写 .tmp 临时文件再 os.replace，避免中途崩溃产生半截 JSON。

        dirty 标记（P5）：数据无实际变化时跳过磁盘写入。调用方必须持有 self._lock。
        """
        try:
            new = self._snapshot()
            if new == self._last_saved:
                return  # 数据无变化，跳过写入
            self.path.parent.mkdir(parents=True, exist_ok=True)
            tmp_path = self.path.with_suffix(self.path.suffix + ".tmp")
            tmp_path.write_text(new, encoding="utf-8")
            os.replace(tmp_path, self.path)
            self._last_saved = new
        except Exception as e:
            logger.warning("记忆写入失败（不影响主流程）: %s", e)

    # ---------- 长期记忆：用户偏好 ----------
    def get_user(self, user_id: str) -> dict:
        if not user_id:
            return {}
        with self._lock:
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
        with self._lock:
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

    # ---------- 用户反馈（评分 + 意见，随记忆持久化） ----------
    def add_feedback(self, user_id: str, feedback: dict) -> None:
        """追加一条用户反馈：每用户最多保留最近 _FEEDBACK_MAX 条（超出淘汰最旧）。"""
        if not user_id:
            return
        with self._lock:
            bucket = self._data.setdefault("feedback", {}).setdefault(str(user_id), [])
            bucket.append(feedback)
            if len(bucket) > _FEEDBACK_MAX:
                del bucket[: len(bucket) - _FEEDBACK_MAX]
            self._save()

    def recent_feedback(self, user_id: str, n: int = 3) -> list:
        """取该用户最近 n 条反馈（旧 → 新顺序）。"""
        if not user_id:
            return []
        with self._lock:
            bucket = self._data.get("feedback", {}).get(str(user_id), [])
            return list(bucket[-n:])

    # ---------- 感知层调研缓存（内存 TTL + LRU 上限） ----------
    def get_research(self, destination: str, days: int, ttl: int = 3600):
        """命中则返回缓存的调研结果，未命中/过期返回 None"""
        key = f"{destination}:{days}"
        with self._lock:
            hit = self._research_cache.get(key)
        if hit and (_time.time() - hit[0]) < ttl:
            return hit[1]
        return None

    def set_research(self, destination: str, days: int, research: dict) -> None:
        key = f"{destination}:{days}"
        with self._lock:
            if key in self._research_cache:
                del self._research_cache[key]  # 重新写入视为最新使用，移到 LRU 尾部
            elif len(self._research_cache) >= _RESEARCH_CACHE_MAX:
                self._research_cache.popitem(last=False)  # 淘汰最旧条目
            self._research_cache[key] = (_time.time(), research)

    # ---------- 通用 ----------
    def touch(self, key: str) -> None:
        """记录最后活动时间（用于展示/调试）"""
        with self._lock:
            self._data.setdefault("meta", {})[key] = datetime.now().isoformat()
            self._save()


# 全局单例 — 各阶段共享同一个记忆存储
memory_store = MemoryStore()
