"""
MCP（Model Context Protocol）桥接 — 客户端 + 服务端

1) 客户端：接入外部 MCP 服务器，把其工具动态加载为 LangChain 工具，
   供 Agent 的 ReAct 循环调用，并套用 Permission 权限包装。
   - MCP_ENABLED=true 启用
   - MCP_CLIENT_SERVERS=name1=http://host:port/mcp,name2=http://host:port/mcp
     逗号分隔，每项 "名字=URL"（或仅 URL，自动命名为 server1/server2...）

2) 服务端：把 Agent 自身工具（搜索/通勤/预算/知识库）暴露为 MCP 服务，
   供其他 MCP 客户端调用。
   - MCP_SERVER_ENABLED=true 随服务自启；或单独运行 python -m agent.mcp_server
   - 默认 streamable-http，端口 MCP_SERVER_PORT（默认 3202），端点 /mcp
   - 默认只监听 127.0.0.1（本机）；对外暴露需显式 MCP_SERVER_HOST=0.0.0.0

mcp / langchain-mcp-adapters 为可选依赖：未安装时优雅降级，不影响核心功能。
"""
from __future__ import annotations

import json
import logging
import os
import threading
from typing import List, Optional, Tuple

from .permissions import permission_manager

logger = logging.getLogger("travel-agent.mcp")

# MCP 服务端启动互斥锁（B6）：防并发调用 start_mcp_server 时 _started 标志竞态
_mcp_start_lock = threading.Lock()


def _run_mcp_server_thread(server, transport: str) -> None:
    """MCP 服务端线程入口（L-PY-4）。

    server.run() 是异步阻塞调用，端口冲突等失败发生在线程内部——
    旧实现 _started 在 t.start() 即置 True，run() 在线程内挂掉后线程静默死亡，
    /api/agent/health 永远误报 running。这里捕获异常并回写 _started，线程退出也一并清理。
    """
    try:
        server.run(transport=transport)
    except Exception as e:  # noqa: BLE001
        logger.error("MCP 服务端线程异常退出，健康检查将标记未运行: %s", e)
    finally:
        start_mcp_server._started = False


# ==================== 可用性探测 ====================

def _mcp_available() -> bool:
    try:
        import mcp  # noqa: F401
        import langchain_mcp_adapters  # noqa: F401
        return True
    except ImportError:
        return False


# ==================== 配置解析 ====================

def _parse_servers(env_val: Optional[str]) -> List[Tuple[str, str]]:
    """解析 MCP_CLIENT_SERVERS：'name=url' 或 'url'，逗号分隔。"""
    if not env_val:
        return []
    configs: List[Tuple[str, str]] = []
    for i, part in enumerate([p.strip() for p in env_val.split(",") if p.strip()]):
        if "=" in part:
            name, url = part.split("=", 1)
            configs.append((name.strip() or f"server{i + 1}", url.strip()))
        else:
            configs.append((f"server{i + 1}", part))
    return configs


# ==================== MCP 客户端 ====================

class McpToolLoader:
    """从外部 MCP 服务器加载工具为 LangChain 工具（结果缓存）。"""

    def __init__(self, enabled: Optional[bool] = None):
        self.enabled = enabled if enabled is not None else os.getenv("MCP_ENABLED", "").lower() == "true"
        self.server_configs: List[Tuple[str, str]] = _parse_servers(os.getenv("MCP_CLIENT_SERVERS"))
        self._tools: Optional[List] = None
        self.error: str = ""

    async def get_tools(self) -> List:
        """返回全部 MCP 工具（LangChain BaseTool，已套权限包装）。失败降级为空列表。"""
        if not self.enabled or not self.server_configs:
            return []
        if self._tools is not None:
            return self._tools
        if not _mcp_available():
            self.error = "已启用 MCP 客户端但未安装 mcp / langchain-mcp-adapters"
            logger.warning("[MCP] %s，跳过", self.error)
            self._tools = []
            return self._tools
        try:
            from langchain_mcp_adapters.client import MultiServerMCPClient

            connections = {name: {"transport": "streamable_http", "url": url}
                           for name, url in self.server_configs}
            client = MultiServerMCPClient(connections=connections)
            raw = await client.get_tools()
            guarded = []
            for t in raw:
                try:
                    guarded.append(permission_manager.guard(t))
                except Exception:
                    guarded.append(t)  # 权限包装失败则原样使用
            self._tools = guarded
            logger.info("[MCP] 客户端已加载 %d 个外部工具: %s",
                        len(guarded), [t.name for t in guarded])
            return guarded
        except Exception as e:
            self.error = str(e)
            logger.warning("[MCP] 客户端加载失败（降级为空）: %s", e)
            self._tools = []
            return self._tools

    def summary(self) -> dict:
        return {
            "enabled": self.enabled,
            "servers": [{"name": n, "url": u} for n, u in self.server_configs],
            "tool_count": len(self._tools) if self._tools is not None else 0,
            "error": self.error or None,
        }


# ==================== MCP 服务端 ====================

def create_mcp_server():
    """构建 FastMCP 服务端，暴露 Agent 现有工具 + 知识库检索。"""
    from mcp.server.fastmcp import FastMCP
    # 别名导入，避免与下方同名 MCP 包装函数互相遮蔽
    from .tools import (search_attractions_info as _sa_tool,
                        search_hotels_info as _sh_tool,
                        get_commute_info as _gc_tool,
                        calculate_budget as _cb_tool)
    from .knowledge import knowledge_store

    # 默认只监听本机回环（S8）：MCP 端口不应默认暴露公网；
    # 确需对外提供服务时显式设置 MCP_SERVER_HOST=0.0.0.0
    host = os.getenv("MCP_SERVER_HOST", "127.0.0.1")
    port = int(os.getenv("MCP_SERVER_PORT", "3202"))
    mcp = FastMCP(
        name="travel-agent",
        instructions="AI 旅游规划 Agent 的 MCP 服务：提供景点/美食/酒店联网搜索、"
                     "两地通勤计算、预算核算与旅游攻略知识库检索。",
        host=host,
        port=port,
    )

    @mcp.tool()
    def search_attractions(query: str) -> str:
        """联网搜索景点、美食、活动的实时信息（含门票、开放时间、推荐）"""
        return _sa_tool.invoke({"query": query})

    @mcp.tool()
    def search_hotels(query: str) -> str:
        """联网搜索目的地酒店区域、价格区间与推荐"""
        return _sh_tool.invoke({"query": query})

    @mcp.tool()
    def get_commute(origin: str, destination: str, mode: str = "驾车", city: str = "") -> str:
        """计算两点之间通勤距离与耗时（驾车/公交/步行/骑行）"""
        return _gc_tool.invoke(
            {"origin": origin, "destination": destination, "mode": mode, "city": city})

    @mcp.tool()
    def calculate_budget(items_json: str) -> str:
        """核算行程总花费并给出超标调整建议"""
        return _cb_tool.invoke({"items_json": items_json})

    @mcp.tool()
    def retrieve_guide(destination: str, query: str = "") -> str:
        """从内置旅游攻略知识库检索目的地相关信息（RAG）"""
        try:
            # L-PY-1 修复：复用全局单例知识库，避免每次检索重建 TF-IDF 索引
            chunks = knowledge_store.retrieve(destination, query)
            return json.dumps([c.to_dict() for c in chunks], ensure_ascii=False)
        except Exception as e:  # noqa: BLE001
            return json.dumps({"error": f"知识库检索失败: {e}"}, ensure_ascii=False)

    return mcp


def start_mcp_server() -> None:
    """在后台线程启动 MCP 服务端（幂等；失败仅告警，不阻塞主服务）。"""
    with _mcp_start_lock:  # B6：启动标志的检查与设置放在锁内，防竞态
        if getattr(start_mcp_server, "_started", False):
            return
        try:
            server = create_mcp_server()
            transport = os.getenv("MCP_SERVER_TRANSPORT", "streamable-http").strip().lower()
            # L-PY-4：线程目标用包装函数，run() 内部异步失败也能回写 _started
            t = threading.Thread(target=_run_mcp_server_thread, args=(server, transport),
                                 daemon=True, name="mcp-server")
            t.start()
            start_mcp_server._started = True
            logger.info("🚀 MCP 服务端已启动: transport=%s host=%s port=%s",
                        transport, os.getenv("MCP_SERVER_HOST", "127.0.0.1"),
                        os.getenv("MCP_SERVER_PORT", "3202"))
        except Exception as e:  # noqa: BLE001
            logger.warning("MCP 服务端启动失败（忽略）: %s", e)
