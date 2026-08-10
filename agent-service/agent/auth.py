"""
Agent 服务鉴权 — 中间件

安全边界：agent-service 不能裸奔。所有 /api/agent/* 请求必须通过以下之一：
  1) X-Agent-Key 等于 AGENT_API_KEY（后端透传时附带的共享密钥）；或
  2) Authorization: Bearer <JWT> 有效（与后端 JWT_SECRET 同密钥签发，HS256/384/512）。

若请求携带有效 JWT，则提取 userId 存入 request.state.verified_user_id，
各端点用它覆盖请求体中的 user_id，防止伪造 user_id 越权读写他人长期记忆。

配置：
  AGENT_API_KEY   共享密钥（必填，未设置则仅允许 JWT 鉴权；两者都未配置则拒绝所有请求）
  AGENT_JWT_SECRET JWT 密钥（可选；未设置时回退读 JWT_SECRET，仍未设置则 JWT 校验不可用）
"""
from __future__ import annotations

import logging
import os
from typing import Optional

from fastapi import Request
from fastapi.responses import JSONResponse

logger = logging.getLogger("travel-agent.auth")

_AUTH_REALM = "/api/agent"


def _agent_key() -> str:
    return os.getenv("AGENT_API_KEY", "")


def _jwt_secret() -> str:
    return os.getenv("AGENT_JWT_SECRET", "") or os.getenv("JWT_SECRET", "")


def _decode_jwt_user_id(token: str) -> Optional[str]:
    """校验 JWT 并返回 userId（字符串），无效返回 None。"""
    secret = _jwt_secret()
    if not secret:
        return None
    try:
        import jwt as pyjwt
        payload = pyjwt.decode(token, secret, algorithms=["HS256", "HS384", "HS512"])
        uid = payload.get("userId")
        if uid is None:
            return None
        return str(uid)
    except Exception as e:  # noqa: BLE001
        logger.debug("[auth] JWT 校验失败: %s", e)
        return None


def _bearer_token(auth_header: str) -> str:
    auth_header = (auth_header or "").strip()
    if auth_header.startswith("Bearer "):
        return auth_header[7:].strip()
    return auth_header


async def agent_auth_middleware(request: Request, call_next):
    """FastAPI HTTP 中间件：保护 /api/agent/* 端点。"""
    if not request.url.path.startswith(_AUTH_REALM):
        return await call_next(request)

    # 请求上下文默认值（端点可直接读取）
    request.state.auth_method = "none"
    request.state.verified_user_id = None

    # 通道 1：共享密钥
    key = _agent_key()
    provided = request.headers.get("X-Agent-Key", "")
    if key and provided and provided == key:
        request.state.auth_method = "key"
        return await call_next(request)

    # 通道 2：JWT
    token = _bearer_token(request.headers.get("Authorization", ""))
    if token:
        uid = _decode_jwt_user_id(token)
        if uid is not None:
            request.state.auth_method = "jwt"
            request.state.verified_user_id = uid
            return await call_next(request)

    client = request.client.host if request.client else "?"
    logger.warning("[auth] 拒绝未授权访问 %s（来源 %s）", request.url.path, client)
    return JSONResponse(status_code=401, content={"code": -1, "message": "未授权访问 Agent 服务"})
