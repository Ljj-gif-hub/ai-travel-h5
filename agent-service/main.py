"""
旅行规划 Agent 服务 — FastAPI 入口

提供端点：
  POST /api/agent/plan          — 同步生成完整行程（返回 JSON）
  POST /api/agent/plan/stream   — SSE 流式生成（实时推送 Agent 思考过程）
  POST /api/agent/plan/stream-sse — 原始 SSE 流式端点（网关透传 JSON）
  POST /api/agent/plan/export   — 行程导出 iCalendar（.ics）
  POST /api/agent/feedback      — 用户反馈（评分 + 意见，注入后续规划）
  GET  /api/agent/usage         — API 用量监控（需鉴权）
  GET  /api/agent/health        — 健康检查
  GET  /metrics                 — Prometheus 指标（顶层路径，免鉴权）

启动方式：
  cd agent-service
  pip install -r requirements.txt
  cp .env.example .env   # 编辑填写 API Key
  python main.py         # 启动在 http://localhost:3201
"""
from __future__ import annotations

import asyncio
import json
import logging
import os
import re
import sys
import time
import traceback
import uuid
from datetime import datetime
from pathlib import Path

# 确保项目根目录在 sys.path 中
sys.path.insert(0, str(Path(__file__).parent))

import uvicorn
from contextlib import asynccontextmanager
from dotenv import load_dotenv

# 必须先加载 .env，再导入 agent 模块（permissions/mcp 在导入时读取环境变量）
load_dotenv()

from fastapi import FastAPI, Request  # noqa: E402
from fastapi.exceptions import RequestValidationError  # noqa: E402
from fastapi.middleware.cors import CORSMiddleware  # noqa: E402
from fastapi.responses import JSONResponse, Response, StreamingResponse  # noqa: E402
from prometheus_client import generate_latest  # noqa: E402
from pydantic import ValidationError  # noqa: E402
from sse_starlette.sse import EventSourceResponse  # noqa: E402

from agent.planner import TravelAgentPlanner  # noqa: E402
from agent.schemas import (  # noqa: E402
    TravelRequest,
    AgentEvent,
    RawPlanRequest,
    PlanExportRequest,
    FeedbackRequest,
)
from agent.permissions import permission_manager  # noqa: E402
from agent.tools import ALL_TOOLS  # noqa: E402
from agent.mcp_bridge import McpToolLoader, start_mcp_server  # noqa: E402
from agent.auth import agent_auth_middleware  # noqa: E402
from agent.metrics import HTTP_REQUESTS, HTTP_DURATION  # noqa: E402
from agent.usage import usage_tracker, _current_request  # noqa: E402
from agent.result_cache import plan_cache  # noqa: E402
from agent.exporters import build_ical  # noqa: E402

# ==================== 初始化 ====================

# MCP 客户端加载器（从环境变量读取配置，惰性连接）
_mcp_loader = McpToolLoader()


def _verified_user_id(request: Request):
    """从鉴权中间件拿到已验证的 user_id（无则为 None）。"""
    state = getattr(request, "state", None)
    if state is None:
        return None
    return getattr(state, "verified_user_id", None)

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("travel-agent")


@asynccontextmanager
async def lifespan(_: FastAPI):
    """应用启动钩子：按配置自动拉起 MCP 服务端（后台线程，失败不阻塞）。"""
    if os.getenv("MCP_SERVER_ENABLED", "").lower() == "true":
        start_mcp_server()
    yield


app = FastAPI(
    title="Travel Agent Service",
    description="AI 旅游规划 Agent — 自主搜索、规划、校验、优化的智能体服务",
    version="1.1.0",
    lifespan=lifespan,
)

# CORS（允许前端直连 Agent 服务）
# 注意：allow_credentials=True 时不能用 ["*"]，必须指定具体 origin
app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:5173",
        "http://localhost:5174",
        "http://localhost:5175",
        "http://localhost:5176",
        "http://localhost:3200",
    ],
    allow_credentials=False,
    # S7：从 ["*"] 收窄为明确列表，减少跨域攻击面
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["Content-Type", "Authorization", "X-Agent-Key", "X-User-Id", "X-User-Sig", "X-Request-Id"],
)

# 鉴权中间件：保护所有 /api/agent/* 端点（共享密钥或 JWT）
@app.middleware("http")
async def _auth_middleware(request: Request, call_next):
    return await agent_auth_middleware(request, call_next)


# 观测中间件（注册在鉴权之后 → 运行在最外层，覆盖含 /metrics 在内的全部请求）
@app.middleware("http")
async def _observability_middleware(request: Request, call_next):
    """请求级观测：X-Request-Id 透传/生成 + UI 语言解析 + Prometheus 指标 + 用量归因。

    - X-Request-Id：请求头有则透传，无则 uuid4().hex[:16]，回写响应头
    - Accept-Language：解析为 ui_lang（zh/en 二值，默认 zh），供多语言规划使用
    - 指标：http_requests_total + http_request_duration_seconds
    - 用量：为当前请求建立 usage 记录（contextvar 绑定；SSE 流式期间
      LLM/工具调用继续累加到同一记录对象，deque 存引用，/usage 可见最新值）
    """
    request_id = (request.headers.get("X-Request-Id") or "").strip()[:64] or uuid.uuid4().hex[:16]
    request.state.request_id = request_id

    # UI 语言：取 Accept-Language 首选语言，仅 zh/en 二值
    accept_lang = (request.headers.get("Accept-Language") or "").strip().lower()
    first_tag = accept_lang.split(",")[0].strip() if accept_lang else ""
    request.state.ui_lang = "en" if first_tag.startswith("en") else "zh"

    record = usage_tracker.start_request(request_id, request.url.path, _verified_user_id(request))
    token = _current_request.set(record)

    start = time.monotonic()
    response = None
    try:
        response = await call_next(request)
        response.headers["X-Request-Id"] = request_id
        return response
    finally:
        duration = time.monotonic() - start
        # 指标记录失败不能影响响应
        try:
            status = str(getattr(response, "status_code", 500))
            HTTP_REQUESTS.labels(method=request.method, path=request.url.path, status=status).inc()
            HTTP_DURATION.labels(method=request.method, path=request.url.path).observe(duration)
        except Exception:  # noqa: BLE001
            logger.debug("HTTP 指标记录失败（忽略）", exc_info=True)
        # 用量收尾：SSE 流式响应此处只统计到「响应头就绪」，
        # 生成器收尾时会用真实流时长再刷新一次（finish_request 幂等）
        usage_tracker.finish_request(record, duration)
        _current_request.reset(token)


STREAM_HEADERS = {
    "Cache-Control": "no-cache, no-store, must-revalidate",
    "X-Accel-Buffering": "no",
    "Connection": "keep-alive",
}


# ==================== 错误处理 ====================

@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    """Pydantic 校验失败时，流式端点返回 SSE 错误事件（而非 422 JSON），避免前端卡死"""
    if request.url.path == "/api/agent/plan/stream":
        async def gen():
            err = json.dumps(
                AgentEvent(event_type="error", message="请求参数校验失败，请检查输入").model_dump(),
                ensure_ascii=False,
            )
            yield f"data: {err}\n\n"
        return StreamingResponse(gen(), media_type="text/event-stream", headers=STREAM_HEADERS)
    return JSONResponse(status_code=422, content={"detail": exc.errors()})


# ==================== 端点 ====================

@app.get("/api/agent/health")
async def health():
    """健康检查 — 返回各组件状态"""
    llm_ok = bool(os.getenv("LLM_API_KEY"))
    tavily_ok = bool(os.getenv("TAVILY_API_KEY"))
    amap_ok = bool(os.getenv("AMAP_WEB_KEY"))

    return {
        "status": "ok",
        "service": "travel-agent",
        "version": "1.1.0",
        "components": {
            "llm": "✅ 已配置" if llm_ok else "⚠️ 未配置（将使用降级方案）",
            "tavily_search": "✅ 已配置" if tavily_ok else "⚠️ 未配置（搜索将使用 LLM 知识库）",
            "amap": "✅ 已配置" if amap_ok else "⚠️ 未配置（通勤距离将估算）",
        },
        "mcp": {
            "client": _mcp_loader.summary(),
            "server": {
                "enabled": os.getenv("MCP_SERVER_ENABLED", "").lower() == "true",
                "running": getattr(start_mcp_server, "_started", False),
                "transport": os.getenv("MCP_SERVER_TRANSPORT", "streamable-http"),
                "port": int(os.getenv("MCP_SERVER_PORT", "3202")),
            },
        },
        "permission": permission_manager.summary(),
        "endpoints": {
            "sync": "POST /api/agent/plan",
            "stream": "POST /api/agent/plan/stream",
            "permissions": "GET /api/agent/permissions",
        },
    }


@app.get("/api/agent/permissions")
async def permissions():
    """工具权限策略 + 当前可用工具一览。"""
    return {
        "policy": permission_manager.summary(),
        "tools": {
            "core": [t.name for t in ALL_TOOLS],
            "mcp_client": _mcp_loader.summary(),
        },
    }


@app.get("/metrics")
async def metrics_endpoint():
    """Prometheus 指标抓取端点。

    注册在顶层（/api/agent 前缀之外），鉴权中间件天然不覆盖此路径；
    /api/agent 前缀下的所有请求指标也会被观测中间件统计在内。
    """
    return Response(content=generate_latest(), media_type="text/plain; version=0.0.4")


@app.get("/api/agent/usage")
async def usage_endpoint():
    """API 用量监控（进程内环形缓冲）：总量摘要 + 最近 20 条请求明细。

    位于 /api/agent/ 前缀下，自动受鉴权中间件保护。
    """
    return {
        "code": 0,
        "summary": usage_tracker.summary(),
        "recent": usage_tracker.recent(20),
    }


# 行程导出解析失败统一文案（400）
_EXPORT_FAIL_MSG = "行程数据解析失败，无法导出"


@app.post("/api/agent/plan/export")
async def export_plan(req: PlanExportRequest, request: Request):
    """行程导出 — 生成 iCalendar（.ics）文本。

    每天一个 VEVENT：SUMMARY=第N天-城市；DTSTART/DTEND 当地 09:00/21:00
    浮点时间；DESCRIPTION 该天标题 + 2-3 个景点。标准库实现，无第三方依赖。
    """
    if req.format != "ical":
        return JSONResponse(status_code=400, content={"code": -1, "message": _EXPORT_FAIL_MSG})
    ics = build_ical(req.plan)
    if ics is None:
        return JSONResponse(status_code=400, content={"code": -1, "message": _EXPORT_FAIL_MSG})
    return Response(
        content=ics,
        media_type="text/calendar; charset=utf-8",
        headers={"Content-Disposition": 'attachment; filename="travel-plan.ics"'},
    )


@app.post("/api/agent/feedback")
async def submit_feedback(req: FeedbackRequest, request: Request):
    """用户反馈闭环：记录评分 + 意见，按 verified user_id 归属存储。

    后续该用户规划时，planner 会把最近 3 条反馈注入规划 prompt。
    鉴权通过但未绑定用户身份（共享密钥通道缺 HMAC 签名）→ 401。
    """
    user_id = _verified_user_id(request)
    if not user_id:
        return JSONResponse(status_code=401, content={"code": -1, "message": "未绑定用户身份，无法记录反馈"})
    # 净化（S3 同策略）：剥离控制字符 + 长度截断，防存储型注入
    comment = re.sub(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]", "", str(req.comment or ""))[:500]
    destination = re.sub(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]", "", str(req.destination or "")).strip()[:50]
    # PY-5 修复：用 asyncio.to_thread 将同步写盘移出事件循环，不阻塞进行中的 SSE 规划流
    from agent.memory import memory_store
    import asyncio
    await asyncio.to_thread(memory_store.add_feedback, user_id, {
        "rating": req.rating,
        "comment": comment,
        "destination": destination,
        "created_at": datetime.now().isoformat(),
    })
    logger.info("收到用户反馈: user=%s rating=%d", user_id, req.rating)
    return {"code": 0, "message": "反馈已记录"}


@app.post("/api/agent/plan")
async def generate_plan_sync(req: TravelRequest, request: Request):
    """
    同步生成完整旅行方案 — 等待全部完成后返回 JSON

    适合：后端调用、保存到数据库、批量生成
    耗时：20-60 秒（取决于 Agent 工具调用次数）
    """
    demo_mode = os.getenv("DEMO_MODE", "").lower() == "true" or not os.getenv("LLM_API_KEY")
    if demo_mode:
        logger.info(f"🔶 Demo 模式规划: {req.destination} {req.days}天（无需外部API）")
    else:
        logger.info(f"同步规划: {req.destination} {req.days}天")

    try:
        data = req.model_dump()
        # 以服务端验证的 user_id 为准（None 表示未绑定），防伪造越权
        data["user_id"] = _verified_user_id(request)
        data["ui_lang"] = getattr(request.state, "ui_lang", "zh")

        # 结果缓存：Demo 模式 / 无绑定用户不缓存；命中则跳过 LLM 直接返回
        cache_key = None
        cached_plan = None
        if not demo_mode and data.get("user_id"):
            cache_key = plan_cache.key(data)
            cached_plan = plan_cache.get(cache_key)
        if cached_plan is not None:
            payload = dict(cached_plan)
            payload["cached"] = True
            logger.info(f"命中结果缓存: {req.destination} {req.days}天")
            events = [AgentEvent(
                event_type="complete", phase="finalize",
                message=f"🎉 {req.destination} {req.days} 天行程方案已生成！（缓存命中）",
                data=payload,
            ).model_dump()]
            return {
                "code": 0,
                "message": "success",
                "data": payload,
                "cached": True,
                "agent_events": events,
            }

        planner = TravelAgentPlanner(
            data,
            debug=os.getenv("DEBUG", "").lower() == "true",
            demo=demo_mode,
        )
        events = []
        final_result = None

        async for event in planner.run():
            events.append(event.model_dump())
            if event.event_type == "complete":
                final_result = event.data

        if final_result:
            if cache_key:
                plan_cache.set(cache_key, final_result)
            return {
                "code": 0,
                "message": "success",
                "data": final_result,
                "agent_events": events,  # 附上完整思考过程
            }
        else:
            return JSONResponse(
                status_code=500,
                content={"code": -1, "message": "Agent 未能生成有效方案", "events": events},
            )
    except Exception as e:
        tb = traceback.format_exc()
        logger.error(f"同步规划失败: {e}\n{tb}")
        # 对外只返回通用文案，避免泄露内部异常/路径
        return JSONResponse(
            status_code=500,
            content={"code": -1, "message": "规划服务暂时不可用，请稍后重试"},
        )


@app.post("/api/agent/plan/stream")
async def generate_plan_stream(req: TravelRequest, request: Request):
    """
    SSE 流式生成旅行方案 — 实时推送 Agent 的每一步思考和操作

    事件类型（event_type）：
      - phase_start   : 进入新阶段（research/plan/verify/adjust/finalize）
      - phase_end     : 阶段完成，data 中包含阶段结果
      - thinking      : Agent 正在思考
      - warning       : 警告（预算超标等）
      - adjustment    : 正在自动调整
      - plan_update   : 方案部分更新
      - complete      : 全部完成，data 为完整 TripPlanOutput
      - error         : 出错

    适合：前端页面展示 Agent 思考过程
    耗时：30-90 秒
    """
    demo_mode = os.getenv("DEMO_MODE", "").lower() == "true" or not os.getenv("LLM_API_KEY")
    if demo_mode:
        logger.info(f"🔶 Demo 模式流式规划: {req.destination} {req.days}天")
    else:
        logger.info(f"流式规划: {req.destination} {req.days}天")

    data = req.model_dump()
    # 以服务端验证的 user_id 为准（None 表示未绑定），防伪造越权
    data["user_id"] = _verified_user_id(request)
    data["ui_lang"] = getattr(request.state, "ui_lang", "zh")

    # 结果缓存：Demo 模式 / 无绑定用户不缓存
    cache_key = None
    cached_plan = None
    if not demo_mode and data.get("user_id"):
        cache_key = plan_cache.key(data)
        cached_plan = plan_cache.get(cache_key)

    async def event_generator():
        stream_start = time.monotonic()
        record = _current_request.get()
        # PY-3 修复：心跳与 planner 事件交错等待。原实现心跳任务只往队列塞数据，
        # 但生成器挂在 async for planner.run() 上不 yield，LLM 静默期心跳发不出去；
        # 改为后台 pump 拉取 planner 事件入队，主循环 wait_for 超时 15s 即发心跳。
        import asyncio as _aio
        event_queue: _aio.Queue = _aio.Queue()
        pump_task = None

        async def _pump_planner():
            # 结束放 None 哨兵；异常也进队列，主循环 re-raise 走统一错误处理
            try:
                async for ev in planner.run():
                    await event_queue.put(ev)
            except Exception as exc:  # noqa: BLE001
                await event_queue.put(exc)
                return
            await event_queue.put(None)

        try:
            # 立即发送连接确认，确保浏览器收到流式响应头
            yield f"data: {json.dumps(AgentEvent(event_type='connected', message='Agent 已连接').model_dump(), ensure_ascii=False)}\n\n"
            # 缓存命中：跳过 LLM 直接产出 complete 事件（附 cached: true 标记）
            if cached_plan is not None:
                logger.info(f"命中结果缓存: {req.destination} {req.days}天")
                payload = dict(cached_plan)
                payload["cached"] = True
                done = AgentEvent(
                    event_type="complete", phase="finalize",
                    message=f"🎉 {req.destination} {req.days} 天行程方案已生成！（缓存命中）",
                    data=payload,
                )
                yield f"data: {json.dumps(done.model_dump(), ensure_ascii=False)}\n\n"
                return
            planner = TravelAgentPlanner(
                data,
                debug=os.getenv("DEBUG", "").lower() == "true",
                demo=demo_mode,
            )
            pump_task = _aio.create_task(_pump_planner())
            count = 0
            final_result = None
            while True:
                try:
                    # 最多等 15s：超时说明 planner 处于静默期，先发心跳保持连接
                    event = await _aio.wait_for(event_queue.get(), timeout=15)
                except _aio.TimeoutError:
                    yield ": heartbeat\n\n"
                    continue
                if isinstance(event, Exception):
                    raise event
                if event is None:
                    break  # 规划流正常结束
                count += 1
                # B4：周期性探测客户端断连（每 2 个事件一次），断连立即停止 LLM 流程
                if count % 2 == 0 and await request.is_disconnected():
                    logger.info("客户端已断开连接，停止流式规划")
                    return
                if event.event_type == "complete":
                    final_result = event.data
                yield f"data: {json.dumps(event.model_dump(), ensure_ascii=False)}\n\n"
            # 规划正常完成后写入结果缓存（断连/异常不写，避免缓存半成品）
            if final_result is not None and cache_key:
                plan_cache.set(cache_key, final_result)
        except Exception as e:
            logger.error(f"流式规划异常: {e}", exc_info=True)
            # 对外只返回通用文案，完整堆栈留在服务端日志
            err = json.dumps(AgentEvent(event_type="error", message="服务异常，请稍后重试").model_dump(), ensure_ascii=False)
            yield f"data: {err}\n\n"
        finally:
            if pump_task is not None:
                pump_task.cancel()
            # 用量收尾：用真实流时长刷新请求记录（幂等）
            if record is not None:
                usage_tracker.finish_request(record, time.monotonic() - stream_start)
            logger.debug("流式规划流结束（完成/断连/异常）")

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers=STREAM_HEADERS,
    )


@app.post("/api/agent/plan/stream-sse")
async def generate_plan_stream_raw(request: Request):
    """
    原始 SSE 流式端点 — 从 Request body 解析 JSON，经 RawPlanRequest Pydantic 校验（S2）

    兼容 Spring Boot 透传的 JSON 格式（destination/days/budget/styles/companion）
    每行输出格式：data: <json>\n\n
    """
    try:
        body = await request.json()
    except Exception:
        return EventSourceResponse(_error_stream("请求体必须是合法 JSON"))

    if not isinstance(body, dict):
        return EventSourceResponse(_error_stream("请求体必须是 JSON 对象"))

    # Pydantic 校验：字段级约束（destination 1-50 字 / days 1-14 / budget > 0），
    # 校验失败走 SSE 错误事件，避免非法值进入生成器抛异常
    try:
        req_data = RawPlanRequest.model_validate(body)
    except ValidationError:
        return EventSourceResponse(_error_stream("请求参数校验失败，请检查输入"))

    destination = req_data.destination
    days = req_data.days

    demo_mode = os.getenv("DEMO_MODE", "").lower() == "true" or not os.getenv("LLM_API_KEY")
    # 使用模型字段构建规划输入；user_id 以服务端验证的为准（None 表示未绑定），防伪造越权
    data = req_data.model_dump(exclude_none=True)
    data["user_id"] = _verified_user_id(request)
    data["ui_lang"] = getattr(request.state, "ui_lang", "zh")
    logger.info(f"{'🔶 Demo' if demo_mode else ''}原始SSE规划: {destination} {days}天")

    # 结果缓存：Demo 模式 / 无绑定用户不缓存
    cache_key = None
    cached_plan = None
    if not demo_mode and data.get("user_id"):
        cache_key = plan_cache.key(data)
        cached_plan = plan_cache.get(cache_key)

    async def raw_generator():
        stream_start = time.monotonic()
        record = _current_request.get()
        # PY-3 修复：心跳与 planner 事件交错等待（同 /plan/stream：后台 pump 拉事件入队，
        # 主循环 wait_for 超时 15s 即发心跳，LLM 静默期心跳不再发不出去）
        import asyncio as _aio
        event_queue: _aio.Queue = _aio.Queue()
        pump_task = None

        async def _pump_planner():
            # 结束放 None 哨兵；异常也进队列，主循环 re-raise 走统一错误处理
            try:
                async for ev in planner.run():
                    await event_queue.put(ev)
            except Exception as exc:  # noqa: BLE001
                await event_queue.put(exc)
                return
            await event_queue.put(None)

        try:
            yield f"data: {json.dumps(AgentEvent(event_type='connected', message='Agent 已连接').model_dump(), ensure_ascii=False)}\n\n"
            # 缓存命中：跳过 LLM 直接产出 complete 事件（附 cached: true 标记）
            if cached_plan is not None:
                logger.info(f"命中结果缓存: {destination} {days}天")
                payload = dict(cached_plan)
                payload["cached"] = True
                done = AgentEvent(
                    event_type="complete", phase="finalize",
                    message=f"🎉 {destination} {days} 天行程方案已生成！（缓存命中）",
                    data=payload,
                )
                yield f"data: {json.dumps(done.model_dump(), ensure_ascii=False)}\n\n"
                return
            planner = TravelAgentPlanner(data, demo=demo_mode)
            pump_task = _aio.create_task(_pump_planner())
            count = 0
            final_result = None
            while True:
                try:
                    # 最多等 15s：超时说明 planner 处于静默期，先发心跳保持连接
                    event = await _aio.wait_for(event_queue.get(), timeout=15)
                except _aio.TimeoutError:
                    yield ": heartbeat\n\n"
                    continue
                if isinstance(event, Exception):
                    raise event
                if event is None:
                    break  # 规划流正常结束
                count += 1
                # B4：周期性探测客户端断连（每 2 个事件一次），断连立即停止 LLM 流程
                if count % 2 == 0 and await request.is_disconnected():
                    logger.info("客户端已断开连接，停止流式规划")
                    return
                if event.event_type == "complete":
                    final_result = event.data
                event_json = json.dumps(event.model_dump(), ensure_ascii=False)
                yield f"data: {event_json}\n\n"
            # 规划正常完成后写入结果缓存（断连/异常不写，避免缓存半成品）
            if final_result is not None and cache_key:
                plan_cache.set(cache_key, final_result)
        except Exception as e:
            logger.error(f"SSE异常: {e}", exc_info=True)
            # 对外只返回通用文案，完整堆栈留在服务端日志
            error_json = json.dumps(AgentEvent(event_type="error", message="服务异常，请稍后重试").model_dump(), ensure_ascii=False)
            yield f"data: {error_json}\n\n"
        finally:
            if pump_task is not None:
                pump_task.cancel()
            # 用量收尾：用真实流时长刷新请求记录（幂等）
            if record is not None:
                usage_tracker.finish_request(record, time.monotonic() - stream_start)
            logger.debug("原始 SSE 规划流结束（完成/断连/异常）")

    return StreamingResponse(
        raw_generator(),
        media_type="text/event-stream",
        headers=STREAM_HEADERS,
    )


async def _error_stream(message: str):
    """产生错误事件流"""
    error = json.dumps(
        AgentEvent(event_type="error", message=message).model_dump(),
        ensure_ascii=False,
    )
    yield {"data": error}


# ==================== 启动 ====================
# 启动方式（A1）：
#   1) 默认单进程（uvicorn）：python main.py —— 进程内记忆层/调研缓存要求单进程独占
#   2) 多 worker（gunicorn）：设置环境变量 GUNICORN_WORKERS=N 后 python main.py
#      自动切换为 gunicorn；或 GUNICORN_WORKERS=4 gunicorn -c gunicorn.conf.py main:app
#      ⚠️ 多 worker 前必须先把进程内记忆层迁出（详见 gunicorn.conf.py 注释）
if __name__ == "__main__":
    port = int(os.getenv("AGENT_PORT", "3201"))
    gunicorn_workers = os.getenv("GUNICORN_WORKERS", "").strip()
    if gunicorn_workers:
        try:
            n_workers = max(int(gunicorn_workers), 1)
        except ValueError:
            logger.warning("GUNICORN_WORKERS 值非法: %r，回退默认单进程 uvicorn", gunicorn_workers)
            n_workers = 0
        if n_workers:
            from gunicorn.app.base import BaseApplication

            class _GunicornApp(BaseApplication):
                """以 ASGI app 方式跑 gunicorn（uvicorn worker）。"""

                def __init__(self, application, options=None):
                    self.application = application
                    self.options = options or {}
                    super().__init__()

                def load_config(self):
                    for key, value in self.options.items():
                        self.cfg.set(key, value)

                def load(self):
                    return self.application

            logger.info(f"🚀 Travel Agent Service 以 gunicorn 启动: http://localhost:{port} workers={n_workers}")
            _GunicornApp(
                app,
                {
                    "bind": f"0.0.0.0:{port}",
                    "workers": n_workers,
                    "worker_class": "uvicorn.workers.UvicornWorker",
                    "timeout": 300,
                    "graceful_timeout": 30,
                },
            ).run()
            raise SystemExit(0)

    logger.info(f"🚀 Travel Agent Service 启动在 http://localhost:{port}")
    logger.info(f"📋 健康检查: http://localhost:{port}/api/agent/health")
    uvicorn.run(app, host="0.0.0.0", port=port, log_level="info")
