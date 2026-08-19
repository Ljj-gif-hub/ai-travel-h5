"""
Agent 服务鉴权 — 中间件

安全边界：agent-service 不能裸奔。所有 /api/agent/* 请求必须通过以下之一：
  1) X-Agent-Key 等于 AGENT_API_KEY（后端透传时附带的共享密钥）；或
  2) Authorization: Bearer <JWT> 有效（与后端 JWT_SECRET 同密钥签发，HS256/384/512）。

共享密钥通道下的用户绑定（防 user_id 伪造越权）：
  共享密钥本身只能证明「请求来自受信任网关」，不能证明「请求属于某个用户」。
  因此网关（Spring Boot）在透传时必须用共享密钥对 userId 做 HMAC-SHA256 签名：
      X-User-Id:  <userId>
      X-User-Sig: <hex(hmac_sha256(AGENT_API_KEY, userId))>
  中间件校验签名通过后，才把 userId 写入 request.state.verified_user_id；
  签名缺失/不匹配时 verified_user_id 为 None，端点会强制覆盖请求体中的 user_id，
  使伪造的 user_id 失效（记忆层回退为无用户绑定，不影响规划主流程）。

配置：
  AGENT_API_KEY      共享密钥（必填，未设置则仅允许 JWT 鉴权；两者都未配置则拒绝所有请求）
  AGENT_JWT_SECRET   JWT 密钥（可选；未设置时回退读 JWT_SECRET，仍未设置则 JWT 校验不可用）
  AGENT_JWT_AUDIENCE 可选：JWT audience 白名单（设置了才校验）
  AGENT_JWT_ISSUER   可选：JWT issuer 白名单（设置了才校验）

健康检查豁免：/api/agent/health 不要求鉴权，供负载均衡/容器探活使用。
"""
from __future__ import annotations

import hashlib
import hmac
import logging
import os
from typing import Optional

from fastapi import Request
from fastapi.responses import JSONResponse

logger = logging.getLogger("travel-agent.auth")

_AUTH_REALM = "/api/agent"
# 免鉴权路径（仅限无敏感信息的路由）
_PUBLIC_PATHS = frozenset({"/api/agent/health"})

# 网关 HMAC 用户绑定头（见模块 docstring）
_USER_ID_HEADER = "X-User-Id"
_USER_SIG_HEADER = "X-User-Sig"


def _agent_key() -> str:
    return os.getenv("AGENT_API_KEY", "")


def _jwt_secret() -> str:
    return os.getenv("AGENT_JWT_SECRET", "") or os.getenv("JWT_SECRET", "")


def _hmac_sign(key: str, user_id: str) -> str:
    """与 Spring Boot 端约定的用户签名：hex(hmac_sha256(key, userId))。"""
    return hmac.new(key.encode("utf-8"), user_id.encode("utf-8"), hashlib.sha256).hexdigest()


def _verify_user_sig(key: str, user_id: str, sig: str) -> bool:
    """校验网关 HMAC 签名，防时序攻击。"""
    if not key or not user_id or not sig:
        return False
    return hmac.compare_digest(_hmac_sign(key, user_id), sig.strip().lower())


def _decode_jwt_user_id(token: str) -> Optional[str]:
    """校验 JWT 并返回 userId（字符串），无效返回 None。"""
    secret = _jwt_secret()
    if not secret:
        return None
    try:
        import jwt as pyjwt
        # L-PY-3 修复：强制要求 exp 声明，缺失 exp 的 token 一律拒绝（否则无 exp 即永不过期）。
        # travel-java 签发端恒定带 exp（JwtUtil.createToken 每次设置 expiration），强制校验安全兼容。
        kwargs: dict = {
            "algorithms": ["HS256", "HS384", "HS512"],
            "options": {"require": ["exp"]},
        }
        # audience/issuer 白名单：仅当配置了对应环境变量时才校验（向后兼容存量签发方）
        audience = os.getenv("AGENT_JWT_AUDIENCE", "").strip()
        issuer = os.getenv("AGENT_JWT_ISSUER", "").strip()
        if audience:
            kwargs["audience"] = audience
        if issuer:
            kwargs["issuer"] = issuer
        payload = pyjwt.decode(token, secret, **kwargs)
        uid = payload.get("userId")
        if uid is None:
            return None
        return str(uid)
    except Exception as e:  # noqa: BLE001
        logger.debug("[auth] JWT 校验失败: %s", e)
        return None


def _bearer_token(auth_header: Optional[str]) -> Optional[str]:
    """严格提取 Bearer token：非 "Bearer " 前缀一律拒绝（不接受裸 token）。"""
    auth_header = (auth_header or "").strip()
    if not auth_header.startswith("Bearer "):
        return None
    token = auth_header[7:].strip()
    return token or None


async def agent_auth_middleware(request: Request, call_next):
    """FastAPI HTTP 中间件：保护 /api/agent/* 端点。"""
    if not request.url.path.startswith(_AUTH_REALM):
        return await call_next(request)

    # 请求上下文默认值（端点可直接读取）
    request.state.auth_method = "none"
    request.state.verified_user_id = None

    # 健康检查豁免（负载均衡/容器探活无凭据调用）
    if request.url.path in _PUBLIC_PATHS:
        request.state.auth_method = "public"
        return await call_next(request)

    # 通道 1：共享密钥（网关透传；HMAC 签名通过才绑定用户身份）
    key = _agent_key()
    provided = request.headers.get("X-Agent-Key", "")
    # PY-4 修复：非 ASCII 的 X-Agent-Key 传给 compare_digest 会抛 TypeError → 500
    # 先编码为字节比较，编码失败则直接不匹配
    if key and provided:
        try:
            if hmac.compare_digest(key.encode("utf-8"), provided.encode("utf-8")):
                request.state.auth_method = "key"
                sig_user_id = request.headers.get(_USER_ID_HEADER, "")
                sig = request.headers.get(_USER_SIG_HEADER, "")
                if _verify_user_sig(key, sig_user_id, sig):
                    request.state.verified_user_id = sig_user_id
                else:
                    logger.warning("[auth] 共享密钥通道缺少有效用户签名，user_id 不绑定（来源 %s）",
                                   request.client.host if request.client else "?")
        except (UnicodeEncodeError, TypeError):
            logger.warning("[auth] X-Agent-Key 编码异常，拒绝认证（来源 %s）",
                           request.client.host if request.client else "?")
        return await call_next(request)

    # 通道 2：JWT（前端直连场景；userId 取自服务端签发的 token）
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
