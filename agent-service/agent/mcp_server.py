"""
MCP 服务端独立入口 — 把 Agent 工具暴露为 MCP 服务，供其他 MCP 客户端调用。

用法：
  python -m agent.mcp_server
  # 或指定传输/端口：
  #   MCP_SERVER_TRANSPORT=sse python -m agent.mcp_server
  #   MCP_SERVER_PORT=3202 python -m agent.mcp_server

默认 streamable-http：POST http://localhost:3202/mcp
SSE 传输则连接 http://localhost:3202/sse
"""
from __future__ import annotations

import logging
import os

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")

from .mcp_bridge import create_mcp_server  # noqa: E402


def main() -> None:
    server = create_mcp_server()
    transport = os.getenv("MCP_SERVER_TRANSPORT", "streamable-http").strip().lower()
    logger = logging.getLogger("agent.mcp_server")
    logger.info("MCP 服务端启动中: transport=%s port=%s",
                transport, os.getenv("MCP_SERVER_PORT", "3202"))
    server.run(transport=transport)


if __name__ == "__main__":
    main()
