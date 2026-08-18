"""
工具权限控制（Permission）

按策略决定 Agent 能否调用某个工具，防止 LLM 误用/滥用高风险操作：

  PERMISSION_MODE=open        （默认）所有工具可调用
  PERMISSION_MODE=blocklist   黑名单：TOOL_BLOCKLIST 中的工具被拒绝
  PERMISSION_MODE=allowlist   白名单：仅 TOOL_ALLOWLIST 中的工具可调用

对 LangChain 工具的包装方式：调用前检查，被拒绝时返回明确 JSON
（LLM 可感知并改走其他工具），而不是抛异常中断 Agent 循环。

注：当前工具均为只读/本地计算，暂无"写外部系统"的高风险工具；
接入 MCP 外部工具后，可用本模块对 MCP 工具同样做白/黑名单管控。
"""
from __future__ import annotations

import asyncio
import json
import logging
import os
from typing import Callable, Optional

logger = logging.getLogger("travel-agent.permission")


class PermissionDecision:
    """一次工具调用前的权限判定结果。"""

    __slots__ = ("allowed", "reason")

    def __init__(self, allowed: bool, reason: str = ""):
        self.allowed = allowed
        self.reason = reason


def _deny_payload(tool_name: str, reason: str) -> str:
    return json.dumps({
        "error": "PERMISSION_DENIED",
        "tool": tool_name,
        "message": f"工具「{tool_name}」无权限执行（{reason}），请改用其他可用工具。",
        "reason": reason,
    }, ensure_ascii=False)


class PermissionManager:
    """从环境变量加载策略，并对工具函数/工具对象做权限包装。"""

    def __init__(self):
        self.mode: str = (os.getenv("PERMISSION_MODE", "open") or "open").strip().lower()
        if self.mode not in ("open", "blocklist", "allowlist"):
            logger.warning("未知 PERMISSION_MODE=%s，回退为 open", self.mode)
            self.mode = "open"
        self.allowlist: set[str] = self._parse_list("TOOL_ALLOWLIST")
        self.blocklist: set[str] = self._parse_list("TOOL_BLOCKLIST")

    @staticmethod
    def _parse_list(env_name: str) -> set[str]:
        raw = os.getenv(env_name, "")
        return {p.strip() for p in raw.split(",") if p.strip()}

    def check(self, tool_name: str) -> PermissionDecision:
        """判定工具是否可调用。"""
        if self.mode == "allowlist":
            if tool_name in self.allowlist:
                return PermissionDecision(True)
            return PermissionDecision(False, "allowlist 模式：工具不在白名单")
        if self.mode == "blocklist":
            if tool_name in self.blocklist:
                return PermissionDecision(False, "blocklist 模式：工具在黑名单")
            return PermissionDecision(True)
        return PermissionDecision(True)

    def guard_func(self, tool_name: str, func: Callable) -> Callable:
        """包装同步工具函数：调用前检查权限。"""
        def wrapper(*args, **kwargs):
            decision = self.check(tool_name)
            if not decision.allowed:
                logger.warning("[permission] 拒绝调用工具 %s: %s", tool_name, decision.reason)
                return _deny_payload(tool_name, decision.reason)
            return func(*args, **kwargs)
        wrapper.__name__ = getattr(func, "__name__", tool_name)
        wrapper.__doc__ = getattr(func, "__doc__", None)
        wrapper.__wrapped__ = func
        return wrapper

    def guard(self, tool):
        """包装 LangChain 工具对象，返回带权限检查的新工具（保留同步/异步两个入口）。"""
        from langchain_core.tools import StructuredTool
        if not isinstance(tool, StructuredTool):
            return tool
        # 已包装过则跳过，避免嵌套
        if getattr(tool.func, "__wrapped__", None) is not None:
            return tool

        name = tool.name
        func = getattr(tool, "func", None)
        coroutine = getattr(tool, "coroutine", None)

        async def a_wrapper(*args, **kwargs):
            decision = self.check(name)
            if not decision.allowed:
                logger.warning("[permission] 拒绝调用工具 %s: %s", name, decision.reason)
                return _deny_payload(name, decision.reason)
            if coroutine is not None:
                return await coroutine(*args, **kwargs)
            if func is not None:
                # 同步工具（如 httpx.Client 阻塞 HTTP 搜索）不能在协程里直接调用，
                # 否则阻塞事件循环，导致 asyncio.gather 的并行子调研退化为串行。
                # 丢线程池执行，保持并行性。
                return await asyncio.to_thread(func, *args, **kwargs)
            raise TypeError(f"工具 {name} 无可调用实现")

        # 纯异步工具（如 MCP 适配器工具）func 为 None，只保留异步入口
        wrapped_sync = self.guard_func(name, func) if func is not None else None
        return StructuredTool.from_function(
            func=wrapped_sync,
            coroutine=a_wrapper,
            name=name,
            description=tool.description,
            args_schema=tool.args_schema,
            return_direct=tool.return_direct,
        )

    def summary(self) -> dict:
        """策略概要（供健康检查/接口返回）。"""
        return {
            "mode": self.mode,
            "allowlist": sorted(self.allowlist),
            "blocklist": sorted(self.blocklist),
        }


# 全局单例 — 进程启动时从环境变量加载一次策略
permission_manager = PermissionManager()
