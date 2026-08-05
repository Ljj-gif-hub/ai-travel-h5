"""
旅行规划 Agent 服务 — FastAPI 入口

提供两个核心端点：
  POST /api/agent/plan         — 同步生成完整行程（返回 JSON）
  POST /api/agent/plan/stream  — SSE 流式生成（实时推送 Agent 思考过程）
  GET  /api/agent/health       — 健康检查

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
import sys
import traceback
from pathlib import Path

# 确保项目根目录在 sys.path 中
sys.path.insert(0, str(Path(__file__).parent))

import uvicorn
from dotenv import load_dotenv
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, StreamingResponse
from sse_starlette.sse import EventSourceResponse

from agent.planner import TravelAgentPlanner
from agent.schemas import TravelRequest, AgentEvent

# ==================== 初始化 ====================

load_dotenv()

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("travel-agent")

app = FastAPI(
    title="Travel Agent Service",
    description="AI 旅游规划 Agent — 自主搜索、规划、校验、优化的智能体服务",
    version="1.0.0",
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
    allow_methods=["*"],
    allow_headers=["*"],
)

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
        "version": "1.0.0",
        "components": {
            "llm": "✅ 已配置" if llm_ok else "⚠️ 未配置（将使用降级方案）",
            "tavily_search": "✅ 已配置" if tavily_ok else "⚠️ 未配置（搜索将使用 LLM 知识库）",
            "amap": "✅ 已配置" if amap_ok else "⚠️ 未配置（通勤距离将估算）",
        },
        "endpoints": {
            "sync": "POST /api/agent/plan",
            "stream": "POST /api/agent/plan/stream",
        },
    }


@app.post("/api/agent/plan")
async def generate_plan_sync(req: TravelRequest):
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
        planner = TravelAgentPlanner(
            req.model_dump(),
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
async def generate_plan_stream(req: TravelRequest):
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

    async def event_generator():
        # 立即发送连接确认，确保浏览器收到流式响应头
        yield f"data: {json.dumps(AgentEvent(event_type='connected', message='Agent 已连接').model_dump(), ensure_ascii=False)}\n\n"
        try:
            planner = TravelAgentPlanner(
                req.model_dump(),
                debug=os.getenv("DEBUG", "").lower() == "true",
                demo=demo_mode,
            )
            async for event in planner.run():
                yield f"data: {json.dumps(event.model_dump(), ensure_ascii=False)}\n\n"
        except Exception as e:
            logger.error(f"流式规划异常: {e}", exc_info=True)
            # 对外只返回通用文案，完整堆栈留在服务端日志
            err = json.dumps(AgentEvent(event_type="error", message="服务异常，请稍后重试").model_dump(), ensure_ascii=False)
            yield f"data: {err}\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers=STREAM_HEADERS,
    )


@app.post("/api/agent/plan/stream-sse")
async def generate_plan_stream_raw(request: Request):
    """
    原始 SSE 流式端点 — 直接从 Request body 解析 JSON，无需 Pydantic 校验

    兼容 Spring Boot 透传过来的任意 JSON 格式
    每行输出格式：data: <json>\n\n
    """
    try:
        body = await request.json()
    except Exception:
        return EventSourceResponse(_error_stream("请求体必须是合法 JSON"))

    if not isinstance(body, dict):
        return EventSourceResponse(_error_stream("请求体必须是 JSON 对象"))

    destination = body.get("destination", "")
    if not destination:
        return EventSourceResponse(_error_stream("请提供目的地"))

    # days 校验：非法/越界时回退到 3，避免字符串/负数/超大值进入生成器抛异常
    try:
        days = int(body.get("days", 3))
    except (TypeError, ValueError):
        days = 3
    if not (1 <= days <= 14):
        days = 3
    body["days"] = days

    demo_mode = os.getenv("DEMO_MODE", "").lower() == "true" or not os.getenv("LLM_API_KEY")
    logger.info(f"{'🔶 Demo' if demo_mode else ''}原始SSE规划: {destination} {days}天")

    async def raw_generator():
        yield f"data: {json.dumps(AgentEvent(event_type='connected', message='Agent 已连接').model_dump(), ensure_ascii=False)}\n\n"
        try:
            planner = TravelAgentPlanner(body, demo=demo_mode)
            async for event in planner.run():
                event_json = json.dumps(event.model_dump(), ensure_ascii=False)
                yield f"data: {event_json}\n\n"
        except Exception as e:
            logger.error(f"SSE异常: {e}", exc_info=True)
            # 对外只返回通用文案，完整堆栈留在服务端日志
            error_json = json.dumps(AgentEvent(event_type="error", message="服务异常，请稍后重试").model_dump(), ensure_ascii=False)
            yield f"data: {error_json}\n\n"

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

if __name__ == "__main__":
    port = int(os.getenv("AGENT_PORT", "3201"))
    logger.info(f"🚀 Travel Agent Service 启动在 http://localhost:{port}")
    logger.info(f"📋 健康检查: http://localhost:{port}/api/agent/health")
    uvicorn.run(app, host="0.0.0.0", port=port, log_level="info")
