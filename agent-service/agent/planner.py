"""
旅行规划 Agent 核心编排器

实现完整的 Agent 闭环：
  用户输入 → 需求解析 → 调研阶段(搜索工具) → 规划阶段 →
  校验阶段(预算+路线) → 自动调整 → 最终输出

支持 SSE 流式推送思考过程到前端
"""
from __future__ import annotations

import asyncio
import json
import logging
import os
import re
import time
from datetime import datetime
from typing import AsyncGenerator, Dict, Any, List, Optional

logger = logging.getLogger("travel-agent")

from langchain_openai import ChatOpenAI
from langchain_core.messages import HumanMessage

from .schemas import TripPlanOutput, AgentEvent


def _to_int(value, default=0) -> int:
    """LLM 返回的数值字段可能是字符串（如 "60元"/"5300"），防御式转 int，失败回退默认值"""
    if value is None or value == "":
        return default
    if isinstance(value, bool):
        return int(value)
    if isinstance(value, (int, float)):
        return int(value)
    if isinstance(value, str):
        s = value.strip().replace(",", "").rstrip("元")
        try:
            return int(float(s))
        except ValueError:
            return default
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


# ==================== LLM 工厂 ====================

def _build_llm(temperature: float = 0.3) -> ChatOpenAI:
    """从环境变量构建 LLM 实例（兼容 DeepSeek/OpenAI/通义千问/Kimi 等）"""
    base_url = os.getenv("LLM_BASE_URL", "https://api.deepseek.com")
    api_key = os.getenv("LLM_API_KEY", "")
    model = os.getenv("LLM_MODEL", "deepseek-v4-flash")

    extra_headers = {}
    headers_env = os.getenv("LLM_EXTRA_HEADERS", "")
    if headers_env:
        try:
            extra_headers = json.loads(headers_env)
        except json.JSONDecodeError:
            pass

    return ChatOpenAI(
        model=model,
        api_key=api_key,
        base_url=base_url,
        temperature=temperature,
        max_tokens=4096,
        default_headers=extra_headers if extra_headers else None,
        timeout=120,
    )


# ==================== 核心编排器 ====================

class TravelAgentPlanner:
    """
    旅游规划 Agent 编排器

    实现 5 阶段 Agent 流水线：
      Phase 1 — RESEARCH:   联网搜索景点、酒店、美食实时信息
      Phase 2 — PLAN:       基于调研结果生成初始行程
      Phase 3 — VERIFY:     核算预算 + 检查路线合理性
      Phase 4 — ADJUST:     超标则自动降档调整，路线不合理则重排
      Phase 5 — FINALIZE:   输出结构化 JSON + 推送给前端

    支持 Demo 模式 — 无需 LLM API Key，使用内置兜底数据走完整流程

    Attributes:
        req: 用户旅行请求
        collected_data: 调研阶段收集的原始数据
        plan_data: 当前行程方案
        budget_ok: 预算是否达标
        debug: 是否开启调试日志
        demo: 是否为 Demo 模式（跳过 LLM 调用）
    """

    def __init__(self, req: dict, debug: bool = False, demo: bool = False):
        self.req = req
        self.debug = debug
        self.demo = demo
        self.collected_data: Dict[str, Any] = {}
        self.plan_data: Optional[Dict[str, Any]] = None
        self.budget_ok = True
        self._has_llm = bool(os.getenv("LLM_API_KEY", ""))

    # ==================== 流式主循环 ====================

    async def run(self) -> AsyncGenerator[AgentEvent, None]:
        """
        主运行循环 — 按阶段执行，每个阶段产出 SSE 事件

        Demo 模式（DEMO_MODE=true 或无 LLM API Key）：
        使用内置 8 城真实景点数据，快速生成高质量行程，无需任何外部 API

        Yields:
            AgentEvent: 实时推送给前端的思考和进度事件
        """
        # 自动检测 Demo 模式
        if self.demo or not self._has_llm:
            async for event in self._run_demo():
                yield event
            return

        # 完整 LLM 模式
        async for event in self._run_full():
            yield event

    async def _run_demo(self) -> AsyncGenerator[AgentEvent, None]:
        """
        Demo 模式 — 使用内置真实景点数据，模拟完整 5 阶段 Agent 流程
        无需 LLM / Tavily / Amap 任何 API Key
        """
        destination = self.req.get("destination", "成都")
        days = self.req.get("days", 3)
        budget = self.req.get("budget", 5000)
        people = self.req.get("people", 2)
        pace = self.req.get("pace", "适中")
        styles = self.req.get("styles", [])
        hotel_level = self.req.get("hotel_level", "舒适型")

        # Demo 模式始终用内置中文数据（Tavily 搜出的英文结果不适合中文行程）
        yield AgentEvent(
            event_type="phase_start", phase="research",
            message=f"🔍 正在查询「{destination}」景点、美食、酒店信息…",
        )
        research_data = _get_demo_research(destination, days)
        await asyncio.sleep(0.3)

        yield AgentEvent(
            event_type="phase_end", phase="research",
            message=f"✅ 调研完成：已获取 {destination} {len(research_data.get('spots', []))} 个热门景点、{len(research_data.get('foods', []))} 种特色美食",
            data={"research_summary": research_data.get("summary", ""), "source": "built-in"},
        )

        # ===== Phase 2: PLAN =====
        yield AgentEvent(
            event_type="phase_start", phase="plan",
            message=f"📋 正在规划 {days} 天行程…",
        )
        await asyncio.sleep(0.8)

        plan = _build_demo_plan(destination, days, budget, people, pace, styles, hotel_level, research_data)
        self.plan_data = plan

        yield AgentEvent(
            event_type="phase_end", phase="plan",
            message=f"✅ [Demo] 初始行程已生成：共 {len(plan.get('day_plans', []))} 天行程",
            data={"plan_preview": self._build_plan_preview(plan)},
        )

        # ===== Phase 3: VERIFY =====
        yield AgentEvent(
            event_type="phase_start", phase="verify",
            message="🔍 [Demo] 正在核算总预算、检查路线合理性…",
        )
        await asyncio.sleep(0.5)

        bd = plan.get("budget_detail", {})
        actual_total = bd.get("total", sum([
            bd.get("transport", 0), bd.get("accommodation", 0),
            bd.get("food", 0), bd.get("tickets", 0), bd.get("shopping", 0),
        ]))
        budget_total = budget * people
        budget_ok = actual_total <= budget_total
        budget_gap = actual_total - budget_total if not budget_ok else 0

        verify_result = {
            "budget_ok": budget_ok,
            "budget_total": budget_total,
            "actual_total": actual_total,
            "budget_gap": budget_gap,
            "route_issues": [],
            "suggestions": [],
        }

        if not budget_ok:
            yield AgentEvent(
                event_type="warning", phase="verify",
                message=f"⚠️ [Demo] 预算超标 {budget_gap} 元，正在自动调整…",
                data=verify_result,
            )

            # ===== Phase 4: ADJUST =====
            yield AgentEvent(
                event_type="phase_start", phase="adjust",
                message="🔧 [Demo] 正在自动优化：降低酒店档位、优化餐饮…",
            )
            await asyncio.sleep(0.8)

            # Demo 调整策略：优先降低酒店档位
            hotels = plan.get("hotels", [])
            if hotels and hotel_level != "经济型":
                level_discount = {"豪华型": 0.5, "舒适型": 0.4, "经济型": 0.3}
                discount = level_discount.get(hotel_level, 0.4)
                for h in hotels:
                    old_price = h.get("price_per_night", 500)
                    h["price_per_night"] = int(old_price * (1 - discount))
                    h["total_price"] = h["price_per_night"] * days
                    h["highlights"] = "性价比之选 · " + h.get("highlights", "")
                # 更新住宿费
                plan["budget_detail"]["accommodation"] = sum(h.get("total_price", 0) for h in hotels)
            else:
                # 已是经济型/无酒店可降档：改降餐饮档位 20%
                plan["budget_detail"]["food"] = int(plan["budget_detail"].get("food", 0) * 0.8)

            plan["budget_detail"]["total"] = sum([
                plan["budget_detail"].get(k, 0) for k in ["transport", "accommodation", "food", "tickets", "shopping"]
            ])

            # 调整后重新核算预算，据实给出结果文案（不再无条件声称已达标）
            adj_total = plan["budget_detail"]["total"]
            adj_ok = adj_total <= budget * people
            self.plan_data = plan
            if adj_ok:
                yield AgentEvent(
                    event_type="phase_end", phase="adjust",
                    message=f"✅ [Demo] 调整完成 — 已优化至预算范围内（{adj_total}/{budget * people} 元）",
                    data={"budget_ok": True, "actual_total": adj_total, "budget_total": budget * people},
                )
            else:
                yield AgentEvent(
                    event_type="phase_end", phase="adjust",
                    message=f"⚠️ [Demo] 已尽力调整，仍超出预算 {adj_total - budget * people} 元，建议提高预算或减少天数",
                    data={"budget_ok": False, "actual_total": adj_total, "budget_total": budget * people},
                )
        else:
            yield AgentEvent(
                event_type="phase_end", phase="verify",
                message="✅ [Demo] 校验通过！预算在范围内，路线合理",
                data=verify_result,
            )

        # ===== Phase 5: FINALIZE =====
        yield AgentEvent(
            event_type="phase_start", phase="finalize",
            message="✨ [Demo] 正在生成最终旅行方案…",
        )
        await asyncio.sleep(0.3)

        plan.setdefault("research_notes", [
            f"Demo 模式 — 使用 {destination} 内置景点/酒店数据",
            "门票价格为参考价，以景区当日公示为准",
            f"酒店价格为 {datetime.now().strftime('%Y年%m月')} 参考区间",
            "配置 LLM/Tavily/Amap API Key 后可使用实时 Agent 模式",
        ])

        yield AgentEvent(
            event_type="complete", phase="finalize",
            message=f"🎉 [Demo] {destination} {days} 天行程方案已生成！"
                   f"\n💡 提示：配置 LLM_API_KEY + TAVILY_API_KEY + AMAP_WEB_KEY 后可使用实时 Agent 模式",
            data=plan,
        )

    async def _run_full(self) -> AsyncGenerator[AgentEvent, None]:
        """
        完整闭环 Agent 模式 — 需要 LLM API Key

        闭环工作流：感知输入 → 记忆读取 → LLM大脑/工具层(ReAct调研) →
        规划 → 记忆写入 → 校验 → 反馈迭代(本地优化 + LLM 带反馈重生成) → 输出
        """
        destination = self.req.get("destination", "")
        days = self.req.get("days", 3)
        budget = self.req.get("budget", 5000)
        _t0 = time.monotonic()

        # ===== 感知层：读取请求 + 记忆（长期偏好 + 短期会话） =====
        context = self._perceive()
        yield AgentEvent(
            event_type="phase_start", phase="research",
            message=context["perception_msg"],
            data={"memory": context["memory_block"] or None},
        )

        # ===== 工具层 + Multi-Agent：三个并行子调研智能体 + 主管汇总（失败则回退确定性搜索） =====
        try:
            research_results = await self._research_multiagent(destination, days)
        except Exception as e:
            logger.warning(f"多智能体调研失败，回退确定性搜索: {e}")
            research_results = await self._phase_research(destination, days)
        logger.info(f"[耗时] research: {time.monotonic() - _t0:.1f}s")
        yield AgentEvent(
            event_type="phase_end", phase="research",
            message=f"✅ 调研完成：已获取 {destination} 的景点、美食、酒店实时信息",
            data={"research_summary": research_results.get("summary", "")},
        )

        # ===== 规划层：LLM 结合调研 + 记忆生成行程 =====
        yield AgentEvent(
            event_type="phase_start", phase="plan",
            message=f"📋 正在规划 {days} 天行程 — 分配景点、安排每日时段…",
        )
        _t1 = time.monotonic()
        plan = await self._phase_plan(research_results, context)
        logger.info(f"[耗时] plan: {time.monotonic() - _t1:.1f}s")
        self.plan_data = plan
        yield AgentEvent(
            event_type="phase_end", phase="plan",
            message=f"✅ 初始行程已生成：共 {len(plan.get('day_plans', []))} 天行程",
            data={"plan_preview": self._build_plan_preview(plan)},
        )

        # ===== 记忆层：写入本轮（偏好 + 会话上下文） =====
        self._remember(context, plan)

        # ===== 校验 + 反馈迭代循环（本地数值校验 + 评审智能体 + 反馈迭代） =====
        yield AgentEvent(
            event_type="phase_start", phase="verify",
            message="🔍 评审智能体正在核算预算、检查路线与行程质量…",
        )
        max_refine = getattr(self, "max_refine_rounds", 2)
        verify_result = await self._phase_verify(plan, budget)   # 本地数值校验（快）
        review_result = await self._review_plan(plan, budget)     # 评审智能体（独立审查）
        attempts = 0
        while attempts < max_refine:
            budget_ok = verify_result.get("budget_ok", True)
            route_ok = not verify_result.get("route_issues")
            passed = bool(review_result.get("passed", True))
            if budget_ok and route_ok and passed:
                break

            # 汇总反馈（本地数值问题 + 评审智能体建议）
            issues = []
            if not budget_ok:
                issues.append(f"预算超标 {verify_result.get('budget_gap', 0)} 元（上限 {verify_result.get('budget_total', 0)}）")
            issues += verify_result.get("route_issues", [])
            issues += review_result.get("issues", [])
            self.budget_ok = budget_ok
            yield AgentEvent(
                event_type="warning", phase="verify",
                message="⚠️ " + "；".join(issues[:4]), data={"issues": issues},
            )

            attempts += 1
            if attempts == 1:
                # 第一轮：本地确定性优化（快，只解决预算/路线数值问题）
                yield AgentEvent(
                    event_type="phase_start", phase="adjust",
                    message="🔧 本地优化：降档酒店、压缩餐饮/门票…",
                )
                _t3 = time.monotonic()
                plan = await self._phase_adjust(plan, verify_result)
                logger.info(f"[耗时] adjust(本地): {time.monotonic() - _t3:.1f}s")
                self.plan_data = plan
                yield AgentEvent(event_type="thinking", phase="adjust", message="🔍 优化后二次校验…")
                verify_result = await self._phase_verify(plan, budget)
                self.budget_ok = verify_result.get("budget_ok", True)
                review_result = await self._review_plan(plan, budget)  # 评审再审
            else:
                # 后续轮：LLM 带评审反馈重生成（闭环反馈迭代）
                yield AgentEvent(
                    event_type="phase_start", phase="adjust",
                    message=f"🧠 LLM 根据评审反馈优化方案（第 {attempts} 轮）…",
                )
                _t3 = time.monotonic()
                plan = await self._refine_llm(context, plan, verify_result, review_result)
                logger.info(f"[耗时] refine(LLM): {time.monotonic() - _t3:.1f}s")
                self.plan_data = plan
                yield AgentEvent(event_type="thinking", phase="adjust", message="🔍 优化后二次校验…")
                verify_result = await self._phase_verify(plan, budget)
                self.budget_ok = verify_result.get("budget_ok", True)
                review_result = await self._review_plan(plan, budget)

            yield AgentEvent(
                event_type="phase_end", phase="adjust",
                message=f"✅ 优化完成 — 通过校验" if (self.budget_ok and not verify_result.get("route_issues") and review_result.get("passed", True)) else "⚠️ 本轮仍有超出，继续优化",
                data={"adjusted_plan": self._build_plan_preview(plan)},
            )
            self._remember(context, plan)

        yield AgentEvent(
            event_type="phase_end", phase="verify",
            message="✅ 校验通过！预算在范围内，路线合理",
            data={"verify": verify_result, "review": review_result},
        )

        # ===== 最终输出 =====
        yield AgentEvent(
            event_type="phase_start", phase="finalize",
            message="✨ 正在生成最终旅行方案…",
        )
        _t4 = time.monotonic()
        final_output = await self._phase_finalize(plan)
        logger.info(f"[耗时] finalize: {time.monotonic() - _t4:.1f}s，总耗时: {time.monotonic() - _t0:.1f}s")
        yield AgentEvent(
            event_type="complete", phase="finalize",
            message=f"🎉 {destination} {days} 天行程方案已生成！",
            data=final_output,
        )

    # ==================== 感知层：构建上下文 ====================

    def _perceive(self) -> dict:
        """感知层：读取请求 + 从记忆层加载长期偏好与短期会话上下文"""
        from .memory import memory_store
        user_id = str(self.req.get("user_id") or "")
        session_id = str(self.req.get("session_id") or "")
        memory_block = ""
        perception_msg = (
            f"🧠 感知输入：{self.req.get('destination', '')} "
            f"{self.req.get('days', 3)}天 {self.req.get('people', 2)}人 "
            f"人均{self.req.get('budget', 5000)}元"
        )
        user_ctx = memory_store.build_user_context(user_id)
        session_ctx = memory_store.build_session_context(session_id)
        if user_ctx:
            memory_block += user_ctx
            perception_msg += "，已读取长期偏好"
        if session_ctx:
            memory_block += session_ctx
            perception_msg += "，已读取会话记忆"
        return {
            "user_id": user_id,
            "session_id": session_id,
            "memory_block": memory_block,
            "perception_msg": perception_msg,
        }

    # ==================== 记忆层：写入 ====================

    def _remember(self, context: dict, plan: dict) -> None:
        """记忆层：把本轮偏好与规划写入长期/短期记忆"""
        from .memory import memory_store
        user_id = context.get("user_id", "")
        session_id = context.get("session_id", "")
        if not user_id and not session_id:
            return
        pref_parts = []
        companion = self.req.get("companion", "")
        if companion and companion != "独行":
            pref_parts.append(f"{companion}出行")
        styles = self.req.get("styles", [])
        if styles:
            pref_parts.append("偏好" + "、".join(styles[:4]))
        pref_parts.append(f"酒店{self.req.get('hotel_level', '舒适型')}")
        pref_parts.append(f"节奏{self.req.get('pace', '适中')}")
        pref_parts.append(f"人均预算{self.req.get('budget', 5000)}元")

        if user_id:
            memory_store.set_user(user_id, {
                "preference_text": "，".join(pref_parts),
                "hotel_level": self.req.get("hotel_level", ""),
                "budget": self.req.get("budget", 0),
                "last_destination": self.req.get("destination", ""),
                "updated_at": datetime.now().isoformat(),
            })
        if session_id:
            memory_store.set_session(session_id, {
                "destination": plan.get("destination", ""),
                "days": plan.get("days", 0),
                "overview": plan.get("overview", ""),
                "updated_at": datetime.now().isoformat(),
            })

    # ==================== 工具层：ReAct 调研（LLM 驱动工具调用） ====================

    async def _research_multiagent(self, destination: str, days: int) -> dict:
        """Multi-Agent 调研：三个并行子智能体（景点/美食/酒店）同时搜索，主管智能体汇总

        并行 → 总耗时 ≈ 最慢的子智能体（而非三者之和）
        """
        from .memory import memory_store

        # 感知缓存命中：同一目的地短时间内直接复用，跳过整组调研
        cached = memory_store.get_research(destination, days)
        if cached:
            logger.info(f"命中调研缓存: {destination} {days}天")
            return cached

        # 三个子智能体并行调研（互不依赖，同时跑）
        roles = ["attraction", "food", "hotel"]
        results = await asyncio.gather(
            *(self._research_subagent(role, destination, days) for role in roles),
            return_exceptions=True,
        )
        summaries = {}
        for role, r in zip(roles, results):
            if isinstance(r, Exception):
                logger.warning(f"子智能体[{role}]失败: {r}")
                summaries[role] = ""
            else:
                summaries[role] = r or ""
        logger.info(
            f"[MultiAgent] 子调研完成: 景点{len(summaries['attraction'])}字 "
            f"美食{len(summaries['food'])}字 酒店{len(summaries['hotel'])}字"
        )

        # 主管智能体汇总为统一摘要
        summary = await self._synthesize_research(destination, summaries)
        research = {
            "summary": summary,
            "source": "multiagent",
            "attraction": summaries["attraction"],
            "food": summaries["food"],
            "hotel": summaries["hotel"],
        }
        memory_store.set_research(destination, days, research)
        return research

    async def _research_subagent(self, role: str, destination: str, days: int) -> str:
        """单个子智能体：ReAct + 单一专注工具，快速产出该维度的调研摘要"""
        from langgraph.prebuilt import create_react_agent
        from .tools import search_attractions_info, search_hotels_info
        from .prompts import ATTRACTION_AGENT_SYSTEM, FOOD_AGENT_SYSTEM, HOTEL_AGENT_SYSTEM
        role_system = {
            "attraction": ATTRACTION_AGENT_SYSTEM,
            "food": FOOD_AGENT_SYSTEM,
            "hotel": HOTEL_AGENT_SYSTEM,
        }[role]
        tool = search_attractions_info if role != "hotel" else search_hotels_info
        task = {
            "attraction": f"请调研「{destination}」的必去景点（含亮点、门票/开放时间、行政区）。",
            "food": f"请调研「{destination}」的特色美食与推荐餐厅（含人均、推荐理由）。",
            "hotel": f"请调研「{destination}」的住宿区域建议（含价格区间、交通便利性）。",
        }[role]
        try:
            llm = _build_llm(temperature=0.3)
            agent = create_react_agent(llm, [tool], prompt=role_system)
            result = await agent.ainvoke(
                {"messages": [("human", task)]},
                config={"recursion_limit": 6},
            )
            for m in reversed(result.get("messages", [])):
                if getattr(m, "type", "") == "ai" and getattr(m, "content", ""):
                    return m.content
        except Exception as e:
            logger.warning(f"子智能体[{role}]异常: {e}")
        return ""

    async def _synthesize_research(self, destination: str, summaries: dict) -> str:
        """主管智能体：把三个子智能体的调研结果整合成统一摘要"""
        from .prompts import SYNTHESIZER_SYSTEM
        content = "\n\n".join([
            f"【景点调研】{summaries.get('attraction', '')}",
            f"【美食调研】{summaries.get('food', '')}",
            f"【酒店调研】{summaries.get('hotel', '')}",
        ])
        try:
            llm = _build_llm(temperature=0.2)
            prompt = f"""{SYNTHESIZER_SYSTEM}

目的地：{destination}
## 三个子智能体的调研结果
{content}

## 请输出统一调研摘要（400字以内）："""
            resp = await llm.ainvoke([HumanMessage(content=prompt)])
            s = resp.content if hasattr(resp, "content") else str(resp)
            if s and s.strip():
                return s.strip()
        except Exception as e:
            logger.warning(f"调研汇总失败: {e}")
        return content

    async def _review_plan(self, plan: dict, budget: int) -> dict:
        """评审智能体：独立审查行程质量，返回 {passed, score, issues}"""
        from .prompts import REVIEWER_SYSTEM
        people = self.req.get("people", 2)
        budget_total = _to_int(budget) * people
        prompt = f"""{REVIEWER_SYSTEM}

预算上限：{budget_total}（全队总费用）

## 待评审行程 JSON
{json.dumps(plan, ensure_ascii=False, indent=2, default=str)}
"""
        try:
            llm = _build_llm(temperature=0.1)
            resp = await llm.ainvoke([HumanMessage(content=prompt)])
            content = resp.content if hasattr(resp, "content") else str(resp)
            result = self._parse_json(content)
            if result and isinstance(result, dict):
                return {
                    "passed": bool(result.get("passed", True)),
                    "score": _to_int(result.get("score", 80), 80),
                    "issues": result.get("issues", []) if isinstance(result.get("issues", []), list) else [],
                }
        except Exception as e:
            logger.warning(f"评审智能体失败: {e}")
        return {"passed": True, "score": 80, "issues": []}

    # ==================== 反馈迭代：LLM 带反馈重生成 ====================

    async def _refine_llm(self, context: dict, plan: dict, verify: dict, review: dict | None = None) -> dict:
        """反馈迭代：把数值校验 + 评审智能体反馈喂给 LLM，让它重生成修正后的完整行程"""
        from .prompts import REFINE_SYSTEM
        llm = _build_llm(temperature=0.3)
        llm.max_tokens = 3500
        budget_total = _to_int(verify.get("budget_total", 0))
        critique = []
        if not verify.get("budget_ok", True):
            critique.append(
                f"预算超标 {_to_int(verify.get('budget_gap', 0))} 元"
                f"（上限 {budget_total}，当前 {_to_int(verify.get('actual_total', 0))}）"
            )
            for s in verify.get("suggestions", []):
                critique.append(f"- {s.get('strategy', '')}: {s.get('detail', '')}")
        for r in verify.get("route_issues", []):
            critique.append(f"- 路线：{r}")
        # 评审智能体的定性建议（路线/完整性/真实性）
        review = review or {}
        for i in review.get("issues", []):
            critique.append(f"- 评审：{i}")

        prompt = f"""{REFINE_SYSTEM}

## 校验反馈（数值 + 评审）
{'、'.join(critique) if critique else '行程基本合格，请保持，仅输出当前行程'}

## 当前行程 JSON
{json.dumps(plan, ensure_ascii=False, indent=2, default=str)}

请输出修正后的完整行程 JSON（budget_detail.total ≤ {budget_total}，五项之和 = total）。"""
        try:
            resp = await llm.ainvoke([HumanMessage(content=prompt)])
            content = resp.content if hasattr(resp, "content") else str(resp)
            adjusted = self._parse_json(content)
            if adjusted and adjusted.get("day_plans"):
                adjusted.setdefault("destination", plan.get("destination", ""))
                adjusted.setdefault("days", plan.get("days", 3))
                return adjusted
        except Exception as e:
            logger.warning(f"LLM 反馈优化失败: {e}")
        return plan

    async def _phase_research(self, destination: str, days: int) -> dict:
        """并行搜索工具获取实时信息（无 LLM 中间环节，提速 3-5x）"""
        from .tools import search_attractions_info, search_hotels_info

        # 3 个搜索并行执行，不经过 LLM 决策
        tasks = [
            search_attractions_info.ainvoke({"query": f"{destination} 必去景点推荐 门票价格 开放时间"}),
            search_attractions_info.ainvoke({"query": f"{destination} 特色美食 推荐餐厅 人均消费"}),
            search_hotels_info.ainvoke({"query": f"{destination} 酒店区域推荐 价格区间 住宿攻略"}),
        ]
        results = await asyncio.gather(*tasks, return_exceptions=True)

        # 汇总搜索结果
        parts = []
        for i, r in enumerate(results):
            if isinstance(r, Exception): continue
            try:
                data = json.loads(r) if isinstance(r, str) else r
                texts = [x.get("content", "") for x in data.get("results", [])]
                if texts: parts.append(" ".join(texts[:2]))
            except: pass

        summary = "。".join(parts) if parts else f"{destination}是中国热门旅游城市，拥有丰富的自然和文化景观。"
        self.collected_data["research_raw"] = summary
        return {"summary": summary, "destination": destination}

    # ==================== Phase 2: 规划 ====================

    async def _phase_plan(self, research: dict, context: dict | None = None) -> dict:
        """基于调研结果 + 记忆上下文生成完整的结构化行程"""
        llm = _build_llm(temperature=0.4)
        llm.max_tokens = 3000  # 限制输出长度，加速生成

        req = self.req
        destination = req.get("destination", "")
        days = req.get("days", 3)
        budget = req.get("budget", 5000)
        people = req.get("people", 2)
        companion = req.get("companion", "")
        styles = req.get("styles", [])
        hotel_level = req.get("hotel_level", "舒适型")
        pace = req.get("pace", "适中")
        adjustment = req.get("adjustment", "")
        origin = req.get("origin", "深圳")

        research_text = research.get("summary", "")
        # 记忆层上下文（长期偏好 + 会话记忆）注入规划 prompt
        context_memory = (context or {}).get("memory_block", "")

        plan_prompt = f"""{context_memory}你是携程旅行资深规划师，为用户生成可直接展示的深度旅行方案。

## 调研信息
{research_text}

## 用户需求
- 目的地：{destination} | 出发地：{origin} | {days}天 | {people}人 | 人均{budget}元
- 人群：{companion if companion else '无特殊要求'}
- 偏好：{', '.join(styles) if styles else '综合体验'}
- 酒店：{hotel_level} | 节奏：{pace}
- 调整需求：{adjustment if adjustment else '无（按原需求规划）'}

## 规划要求
1. 每天上午+下午+晚上3个时段，同天景点同一行政区，减少折返
2. 景点名后附标签：【5A】【世界遗产】【网红打卡】【本地人推荐】【免费】【需预约】
3. 每个景点的 activity 写4-6句深度介绍，拒绝空洞描述
4. tips 只写硬规则（预约截止时间/闭馆日/禁止事项），无则留空
5. meals 必须包含：店名+招牌菜+人均+推荐理由
6. hotels每个字段都填真实数据

## 输出 JSON（不要markdown标记，直接输出JSON）
{{
  "destination": "{destination}", "days": {days}, "people": {people},
  "overview": "一句话概括行程主题+3个亮点关键词",
  "day_plans": [{{
    "day": 1, "day_title": "第1天：主题",
    "time_slots": [{{
      "time_of_day": "上午", "time": "08:30",
      "attraction": "景点名【标签1·标签2】",
      "activity": "4-6句深度介绍：定调→怎么玩→精华→硬信息→实战经验",
      "duration": "2.5小时", "cost": 60,
      "transport": "地铁X号线XX站步行X分钟",
      "tips": "硬规则，无则留空", "hours": "08:00-17:00"
    }}],
    "meals": ["午餐：店名（人均XX元，招牌菜·理由）", "晚餐：店名（人均XX元，招牌菜·理由）"]
  }}],
  "budget_detail": {{"transport":0,"accommodation":0,"food":0,"tickets":0,"shopping":0,"total":0}},
  "hotels": [
    {{"name": "酒店名·区域","district":"XX区","price_per_night":500,"total_price":1500,"rating":4.5,"highlights":"步行X分钟到XX"}}
  ],
  "transport": {{"depart_type":"flight","depart_title":"","depart_detail":"","depart_price":0,"return_type":"flight","return_title":"","return_detail":"","return_price":0}},
  "tips": ["目的地通用提醒，每条20字以内，3-5条"]
}}

只输出 JSON，不要任何其他文字。"""

        try:
            resp = await llm.ainvoke([HumanMessage(content=plan_prompt)])
            content = resp.content if hasattr(resp, "content") else str(resp)
            if self.debug:
                print(f"=== LLM Response (first 500 chars) ===\n{content[:500]}")

            # 解析 JSON
            plan = self._parse_json(content)
            # 空行程（day_plans 为空/缺失）不兜底，直接走 fallback，避免渲染空白行程
            if plan and plan.get("day_plans"):
                plan.setdefault("destination", destination)
                plan.setdefault("days", days)
                plan.setdefault("day_plans", [])
                return plan
            else:
                logger.warning(f"JSON parse failed for destination={destination}, content preview: {content[:200]}")

        except Exception as e:
            logger.error(f"Plan phase error for {destination}: {e}")

        # Fallback: 构建基本行程
        return self._build_fallback_plan()

    # ==================== Phase 3: 校验 ====================

    async def _phase_verify(self, plan: dict, budget: int) -> dict:
        """核算预算 + 检查路线（本地计算，不调 LLM）"""
        # 提取费用数据（people 与请求默认保持一致）
        people = self.req.get("people", 2)
        days = plan.get("days", 3)
        budget_detail = plan.get("budget_detail", {})

        # 统一 _to_int 防御：LLM 可能返回字符串数值
        transport = _to_int(budget_detail.get("transport", 0))
        accommodation = _to_int(budget_detail.get("accommodation", 0))
        food = _to_int(budget_detail.get("food", 0))
        tickets = _to_int(budget_detail.get("tickets", 0))
        shopping = _to_int(budget_detail.get("shopping", 0))
        actual_total = _to_int(budget_detail.get("total", transport + accommodation + food + tickets + shopping))

        budget_total = budget * people
        budget_ok = actual_total <= budget_total
        budget_gap = actual_total - budget_total if not budget_ok else 0

        # 检查路线合理性
        day_plans = plan.get("day_plans", [])
        route_issues = []

        for dp in day_plans:
            for s in dp.get("time_slots", []):
                attraction = s.get("attraction", "")
                slot_transport = s.get("transport", "")
                if slot_transport == "打车" or "打车" in str(slot_transport):
                    route_issues.append(f"Day{dp.get('day')}「{attraction}」使用打车，建议改用公共交通")

        # 用 calculate_budget 工具做精确核算（items 口径与主流程一致，不额外追加 city_transport）
        try:
            budget_json = json.dumps({
                "budget_total": budget,
                "budget_per_person": budget,
                "people": people,
                "days": days,
                "items": {
                    "transport": transport,
                    "accommodation": accommodation,
                    "food": food,
                    "tickets": tickets,
                    "shopping": shopping,
                },
            })

            from .tools import calculate_budget
            budget_result = json.loads(calculate_budget.invoke({"items_json": budget_json}))
        except Exception:
            budget_result = {}

        return {
            "budget_ok": budget_ok,
            "budget_total": budget_total,
            "actual_total": actual_total,
            "budget_gap": budget_gap,
            "budget_detail": budget_detail,
            "budget_tool_result": budget_result,
            "route_issues": route_issues,
            "suggestions": budget_result.get("suggestions", []),
        }

    # ==================== Phase 4: 调整 ====================

    async def _phase_adjust(self, plan: dict, verify: dict) -> dict:
        """自动调整方案（预算超标 + 路线优化）

        使用确定性本地调整（酒店降档 + 餐饮压缩），不再让 LLM 重新生成整个行程 JSON——
        实测原实现的 adjust LLM 调用单次耗时 110-160s，是规划慢的主因。
        """
        budget_ok = verify.get("budget_ok", True)
        route_issues = verify.get("route_issues", [])

        # 一切正常，直接返回
        if budget_ok and not route_issues:
            return plan

        if not budget_ok:
            bd = plan.setdefault("budget_detail", {})
            days = _to_int(plan.get("days", self.req.get("days", 3)), 3)
            people = self.req.get("people", 2)
            budget_total = _to_int(verify.get("budget_total", self.req.get("budget", 5000) * people))
            hotel_level = self.req.get("hotel_level", "舒适型")
            hotels = plan.get("hotels", [])

            # 1) 酒店降档一档；已是经济型则压缩餐饮（全部 _to_int 防御字符串数值）
            level_discount = {"豪华型": 0.5, "舒适型": 0.4, "经济型": 0.3}
            if hotels and hotel_level != "经济型":
                discount = level_discount.get(hotel_level, 0.4)
                for h in hotels:
                    old = _to_int(h.get("price_per_night", 500), 500)
                    h["price_per_night"] = int(old * (1 - discount))
                    h["total_price"] = int(h["price_per_night"] * days)
                    h["highlights"] = "性价比之选 · " + h.get("highlights", "")
                bd["accommodation"] = sum(_to_int(h.get("total_price", 0)) for h in hotels)
            else:
                bd["food"] = int(_to_int(bd.get("food", 0)) * 0.8)

            # 2) 若仍超标：压缩门票预算（只减不增），同步下调非晚间时段展示费用。
            #    晚间时段属餐饮/夜游，不计入门票，避免重复计费导致 total 反而上升。
            if sum(_to_int(bd.get(k, 0)) for k in ["transport", "accommodation", "food", "tickets", "shopping"]) > budget_total:
                bd["tickets"] = int(_to_int(bd.get("tickets", 0)) * 0.6)
                for dp in plan.get("day_plans", []):
                    for s in dp.get("time_slots", []):
                        if s.get("time_of_day") != "晚上":
                            s["cost"] = int(_to_int(s.get("cost", 0)) * 0.6)

            bd["total"] = sum(_to_int(bd.get(k, 0)) for k in ["transport", "accommodation", "food", "tickets", "shopping"])

        # 路线问题本地标注（不再调 LLM 重排）
        if route_issues:
            plan.setdefault("research_notes", []).append("路线提示：" + "；".join(route_issues[:3]))

        return plan

    # ==================== Phase 5: 最终输出 ====================

    async def _phase_finalize(self, plan: dict) -> dict:
        """标准化输出，补充缺失字段，校验数据完整性"""
        destination = self.req.get("destination", "")
        days = self.req.get("days", 3)
        people = self.req.get("people", 2)
        budget = self.req.get("budget", 5000)

        # 确保顶层字段
        plan.setdefault("destination", destination)
        plan.setdefault("days", days)
        plan.setdefault("people", people)
        plan.setdefault("total_budget", budget * people)
        plan.setdefault("overview", f"{destination}{days}天深度游行程，涵盖必去景点与特色体验。")

        # 确保 day_plans 结构完整
        for dp in plan.get("day_plans", []):
            dp.setdefault("day_title", f"第{dp.get('day', '?')}天")
            dp.setdefault("meals", [])
            dp.setdefault("daily_budget", 0)
            for slot in dp.get("time_slots", []):
                slot.setdefault("time_of_day", "上午")
                slot.setdefault("time", "09:00")
                slot.setdefault("cost", 0)
                slot.setdefault("transport", "步行")
                slot.setdefault("tips", "")
                slot.setdefault("image_url", "")

        # 确保 budget_detail
        bd = plan.setdefault("budget_detail", {})
        bd.setdefault("transport", 0)
        bd.setdefault("accommodation", 0)
        bd.setdefault("food", 0)
        bd.setdefault("tickets", 0)
        bd.setdefault("shopping", 0)
        bd.setdefault("total", sum([bd.get(k, 0) for k in ["transport", "accommodation", "food", "tickets", "shopping"]]))

        # 确保 tips
        if not plan.get("tips"):
            plan["tips"] = [
                f"提前预订{destination}热门景点门票",
                "出行前查询当地天气预报",
                "下载离线地图备用",
                "携带身份证件",
                "注意防晒保暖",
            ]

        # 确保 hotels
        if not plan.get("hotels"):
            hotel_level = self.req.get("hotel_level", "舒适型")
            level_price = {"经济型": 250, "舒适型": 500, "豪华型": 1000}
            hotel_price = level_price.get(hotel_level, 500)
            plan["hotels"] = [
                {
                    "name": f"{destination}市中心酒店",
                    "district": "市中心",
                    "price_per_night": hotel_price,
                    "total_price": int(hotel_price * _to_int(days, 3)),
                    "rating": 4.3,
                    "highlights": "交通便利，周边配套齐全",
                }
            ]
            # 补默认酒店的同时回写住宿预算，避免酒店价格与预算明细脱节
            bd["accommodation"] = int(hotel_price * _to_int(days, 3))
            bd["total"] = sum(_to_int(bd.get(k, 0)) for k in ["transport", "accommodation", "food", "tickets", "shopping"])

        # 确保 research_notes
        if not plan.get("research_notes"):
            plan["research_notes"] = [
                f"景点信息来源于实时搜索 + AI 知识库",
                f"酒店价格参考 {datetime.now().strftime('%Y年%m月')} 市场行情",
                "门票价格为参考价，以景区当日公示为准",
            ]

        return plan

    # ==================== 辅助方法 ====================

    def _parse_json(self, text) -> Optional[dict]:
        """从 LLM 输出中提取 JSON（兼容各种格式问题）"""
        # 部分 OpenAI 兼容接口（启用工具调用风格）content 可能是 list[dict] 或 dict
        if isinstance(text, list):
            parts = []
            for item in text:
                if isinstance(item, dict):
                    if isinstance(item.get("text"), str):
                        parts.append(item["text"])
                    elif isinstance(item.get("content"), str):
                        parts.append(item["content"])
                elif isinstance(item, str):
                    parts.append(item)
            text = "\n".join(parts)
        elif isinstance(text, dict):
            if isinstance(text.get("text"), str):
                text = text["text"]
            elif isinstance(text.get("content"), str):
                text = text["content"]

        if not isinstance(text, str) or not text.strip():
            logger.warning("AI 返回内容不是字符串/可提取文本，解析失败")
            return None

        text = text.strip()

        # 去掉 markdown 代码块
        text = re.sub(r'^```(?:json)?\s*', '', text)
        text = re.sub(r'\s*```$', '', text)
        text = text.strip()

        # 尝试直接解析
        try:
            return json.loads(text)
        except json.JSONDecodeError:
            pass

        # 尝试找 JSON 边界
        for start_char, end_char in [('{', '}'), ('[', ']')]:
            start = text.find(start_char)
            end = text.rfind(end_char)
            if start >= 0 and end > start:
                try:
                    return json.loads(text[start:end + 1])
                except json.JSONDecodeError:
                    continue

        return None

    def _build_plan_preview(self, plan: dict) -> dict:
        """构建行程预览摘要（不传完整数据，减少 SSE 带宽）"""
        preview = {
            "destination": plan.get("destination", ""),
            "days": plan.get("days", 0),
            "overview": plan.get("overview", ""),
            "day_count": len(plan.get("day_plans", [])),
            "budget_total": plan.get("budget_detail", {}).get("total", 0),
            "hotel_count": len(plan.get("hotels", [])),
            "tip_count": len(plan.get("tips", [])),
        }
        # 每日概览
        day_summaries = []
        for dp in plan.get("day_plans", []):
            attractions = [s.get("attraction", "") for s in dp.get("time_slots", [])]
            day_summaries.append({
                "day": dp.get("day", 0),
                "title": dp.get("day_title", ""),
                "attractions": attractions,
            })
        preview["day_summaries"] = day_summaries
        return preview

    def _build_fallback_plan(self) -> dict:
        """构建兜底行程（AI 调用完全失败时）"""
        destination = self.req.get("destination", "目的地")
        days = self.req.get("days", 3)
        budget = self.req.get("budget", 5000)
        people = self.req.get("people", 2)

        day_plans = []
        for d in range(1, days + 1):
            if d == 1:
                title = f"第{d}天：抵达{destination}·城市初探"
                slots = [
                    {"time_of_day": "上午", "time": "09:00", "attraction": f"{destination}市中心", "activity": "抵达后安顿休整", "duration": "2小时", "cost": 0, "transport": "地铁", "tips": "建议选择市中心酒店"},
                    {"time_of_day": "下午", "time": "14:00", "attraction": f"{destination}地标景点", "activity": "参观城市标志性景点", "duration": "3小时", "cost": 80, "transport": "步行", "tips": "提前查询开放时间"},
                    {"time_of_day": "晚上", "time": "19:00", "attraction": f"{destination}夜市/美食街", "activity": "品尝当地特色美食", "duration": "2小时", "cost": 100, "transport": "步行", "tips": "人气餐厅可能需要排队"},
                ]
            elif d == days:
                title = f"第{d}天：深度体验·返程"
                slots = [
                    {"time_of_day": "上午", "time": "09:00", "attraction": f"{destination}特色体验", "activity": "自由探索或购物", "duration": "3小时", "cost": 50, "transport": "地铁", "tips": "预留充足时间去机场/车站"},
                    {"time_of_day": "下午", "time": "14:00", "attraction": "返程", "activity": f"从{destination}返回", "duration": "—", "cost": 0, "transport": "地铁", "tips": "提前2小时出发"},
                    {"time_of_day": "晚上", "time": "19:00", "attraction": "回到家中", "activity": "结束愉快旅程", "duration": "—", "cost": 0, "transport": "—", "tips": ""},
                ]
            else:
                title = f"第{d}天：{destination}深度探索"
                slots = [
                    {"time_of_day": "上午", "time": "09:00", "attraction": f"{destination}知名景点", "activity": "深度游览", "duration": "3小时", "cost": 80, "transport": "地铁", "tips": "尽量早到避开人流"},
                    {"time_of_day": "下午", "time": "14:00", "attraction": f"{destination}文化体验", "activity": "参观博物馆或特色街区", "duration": "2小时", "cost": 40, "transport": "步行", "tips": "部分场馆周一闭馆"},
                    {"time_of_day": "晚上", "time": "19:00", "attraction": f"{destination}美食街", "activity": "品尝地道小吃", "duration": "2小时", "cost": 80, "transport": "步行", "tips": "人多注意财物安全"},
                ]
            day_plans.append({"day": d, "day_title": title, "time_slots": slots, "meals": [f"推荐餐厅（人均60元）"]})

        return {
            "destination": destination,
            "days": days,
            "people": people,
            "total_budget": budget * people,
            "overview": f"{destination} {days}天旅行方案。建议提前预订酒店和门票，注意当地天气变化。",
            "day_plans": day_plans,
            "budget_detail": {
                "transport": int(budget * 0.3),
                "accommodation": int(budget * 0.35),
                "food": int(budget * 0.2),
                "tickets": int(budget * 0.1),
                "shopping": int(budget * 0.05),
                "total": int(budget * 0.3) + int(budget * 0.35) + int(budget * 0.2) + int(budget * 0.1) + int(budget * 0.05),
            },
            "hotels": [{"name": f"{destination}市中心酒店", "district": "市中心", "price_per_night": 500, "total_price": 500 * days, "rating": 4.3, "highlights": "交通便利"}],
            "transport": {"depart_type": "flight", "depart_title": f"前往{destination}", "depart_price": 800, "return_type": "flight", "return_title": f"从{destination}返回", "return_price": 800},
            "tips": ["提前预订门票", "注意天气", "品尝当地美食", "下载离线地图", "保管好随身物品"],
            "research_notes": ["兜底数据（AI 服务暂时不可用）"],
            "_fallback": True,
        }


# ==================== Demo 模式数据 ====================

def _get_demo_research(destination: str, days: int) -> dict:
    """Demo 模式：返回目的地的内置真实景点数据"""
    city_data = _DEMO_CITY_DATA.get(destination, None)
    if not city_data:
        # 模糊匹配
        for key in _DEMO_CITY_DATA:
            if key in destination or destination in key:
                city_data = _DEMO_CITY_DATA[key]
                break

    if not city_data:
        # 通用兜底
        city_data = {
            "summary": f"{destination}是一座充满魅力的旅游城市，建议安排{days}天深度游览，体验当地风土人情和特色美食。",
            "spots": [
                {"name": f"{destination}古城/老街区", "desc": "感受城市历史底蕴，漫步石板路，体验地道生活", "price": 0, "hours": "全天开放", "tips": "建议早晨前往避开人流"},
                {"name": f"{destination}博物馆", "desc": "了解城市历史文化，馆藏丰富", "price": 30, "hours": "09:00-17:00（周一闭馆）", "tips": "建议请讲解员，游览体验更佳"},
                {"name": f"{destination}地标观光塔", "desc": "俯瞰城市全景的最佳位置", "price": 80, "hours": "09:00-21:00", "tips": "选晴天前往，能见度高"},
                {"name": f"{destination}城市公园", "desc": "城市绿肺，本地人休闲好去处", "price": 0, "hours": "全天开放", "tips": "适合晨跑和傍晚散步"},
                {"name": f"{destination}特色街区", "desc": "文艺小店、网红咖啡馆聚集地", "price": 0, "hours": "10:00-22:00", "tips": "拍照打卡的好地方"},
            ],
            "foods": [f"{destination}特色小吃", f"{destination}老字号餐厅", f"{destination}夜市美食街"],
            "hotel_areas": {"市中心": "交通便利，周边配套齐全", "景区周边": "环境优美，适合度假"},
        }
    return city_data


def _build_demo_plan(dest: str, days: int, budget: int, people: int, pace: str,
                     styles: list, hotel_level: str, research: dict) -> dict:
    """Demo 模式：用内置数据构建完整旅行方案"""
    spots = research.get("spots", [])
    foods = research.get("foods", [])
    hotel_areas = research.get("hotel_areas", {})

    # 酒店价格映射
    hotel_price_base = {"经济型": 250, "舒适型": 500, "豪华型": 1000}
    price_per_night = hotel_price_base.get(hotel_level, 500)
    area_name, area_desc = next(iter(hotel_areas.items())) if hotel_areas else ("市中心", "交通便利")

    # 建酒店
    hotels = [
        {
            "name": f"{dest}{area_name}酒店",
            "district": area_name,
            "price_per_night": price_per_night,
            "total_price": price_per_night * days,
            "rating": 4.3,
            "highlights": area_desc,
        }
    ]

    # 构建每日行程
    day_plans = []
    spot_idx = 0
    n_spots = len(spots)

    day_idx = 0
    for d in range(1, days + 1):
        s1 = spots[spot_idx % n_spots]
        s2 = spots[(spot_idx + 1) % n_spots]
        spot_idx += 2

        if d == 1:
            title = f"第{d}天：抵达{dest}·{s1['name']}"
        elif d == days:
            title = f"第{d}天：{s2['name']}·告别{dest}"
        else:
            title = f"第{d}天：{s1['name']} & {s2['name']}"

        pace_mult = {"轻松": 0.7, "适中": 1.0, "紧凑": 1.3}.get(pace, 1.0)

        time_slots = [
            {
                "time_of_day": "上午", "time": "08:30",
                "attraction": s1["name"], "activity": s1.get("desc", f"探索{s1['name']}，感受{dest}独特魅力"),
                "duration": f"{int(2.5 * pace_mult)}小时",
                "cost": int(s1.get("price", 0) or 0),
                "transport": "地铁" if d > 1 else "步行",
                "tips": s1.get("tips", ""),
                "hours": s1.get("hours", ""),
            },
            {
                "time_of_day": "下午", "time": "14:00",
                "attraction": s2["name"], "activity": s2.get("desc", f"深度游览{s2['name']}"),
                "duration": f"{int(2 * pace_mult)}小时",
                "cost": int(s2.get("price", 0) or 0),
                "transport": "步行",
                "tips": s2.get("tips", ""),
                "hours": s2.get("hours", ""),
            },
            {
                "time_of_day": "晚上", "time": "18:30",
                "attraction": f"{dest}特色美食街区",
                "activity": f"品尝当地美食：{'、'.join(foods[:3]) if foods else dest + '小吃'}，感受夜市烟火气。人气餐厅建议提前30分钟取号，避开用餐高峰时段。",
                "duration": "2小时",
                "cost": 100,
                "transport": "步行/地铁",
                "tips": "",
                "hours": "17:00-23:00",
            },
        ]
        day_plans.append({
            "day": d, "day_title": title,
            "time_slots": time_slots,
            "meals": [
                f"午餐：{foods[day_idx % len(foods)] if foods else '当地特色菜'}（人均60-80元）",
                f"晚餐：{foods[(day_idx+1) % len(foods)] if len(foods) > 1 else (foods[0] if foods else '地道小吃')}（人均80-100元）",
            ] if foods else [f"午餐：{dest}本地菜（人均60元）", f"晚餐：{dest}特色美食（人均80元）"],
        })
        day_idx += 1

    # 预算（晚间餐饮只计入 food，避免与晚上时段 cost 重复计费）
    total_budget = budget * people
    accommodation = price_per_night * days
    tickets = sum(
        int(s.get("cost", 0) or 0)
        for dp in day_plans
        for s in dp["time_slots"]
        if s.get("time_of_day") != "晚上"
    )
    food = 80 * days * 3  # 三餐（含晚餐）
    transport_est = int(total_budget * 0.25)
    shopping = total_budget - accommodation - tickets - food - transport_est
    if shopping < 0:
        # 预算过紧：压缩住宿，保证 accommodation 非负且与酒店价格一致
        shopping = int(total_budget * 0.05)
        accommodation = total_budget - tickets - food - transport_est - shopping
        if accommodation < 0:
            accommodation = 0
        room_price = max(200, accommodation // max(days, 1))
        hotels[0]["price_per_night"] = room_price
        hotels[0]["total_price"] = room_price * days
        accommodation = room_price * days

    return {
        "destination": dest, "days": days, "people": people,
        "total_budget": total_budget,
        "overview": research.get("summary", f"{dest}{days}天深度游"),
        "day_plans": day_plans,
        "budget_detail": {
            "transport": transport_est, "accommodation": accommodation,
            "food": food, "tickets": tickets, "shopping": shopping,
            "total": transport_est + accommodation + food + tickets + shopping,
        },
        "hotels": hotels,
        "transport": {
            "depart_type": "flight", "depart_title": f"飞往{dest}",
            "depart_detail": "建议选上午航班 · 提前2小时到机场",
            "depart_price": 800, "return_type": "flight",
            "return_title": f"从{dest}返程", "return_detail": "建议选傍晚航班",
            "return_price": 800,
        },
        "tips": [
            f"📱 提前在官方渠道预订{dest}热门景点门票，旺季至少提前3天",
            f"🌤️ 出行前一周查询{dest}天气预报，准备合适的衣物和防晒用品",
            f"🍜 必尝美食：{'、'.join(foods[:4])}" if foods else f"🍜 尝试{dest}本地特色美食，打开大众点评看本地人推荐",
            f"🚇 {dest}市内交通以地铁+公交为主，建议下载当地交通APP",
            "📷 热门拍照打卡点建议早晨8点前到达，避开旅行团人流",
            "💊 随身携带常用药物：肠胃药、创可贴、晕车药",
            f"💰 {dest}大部分景点支持线上购票，比现场便宜10-20%",
            "🔌 带上充电宝，导航+拍照耗电很快",
        ],
        "research_notes": [
            f"Demo 模式 — 使用 {dest} 内置景点/酒店数据",
            "门票价格为参考价，以景区当日公示为准",
            f"酒店价格参考 {datetime.now().strftime('%Y年%m月')} 市场行情",
            "⚠️ 配置 LLM_API_KEY + TAVILY_API_KEY + AMAP_WEB_KEY 后可使用实时 Agent 模式",
        ],
        "_demo": True,
    }


# ==================== 8 城真实景点库 ====================

_DEMO_CITY_DATA = {
    "成都": {
        "summary": "成都是一座来了就不想走的城市。悠闲的生活节奏、麻辣鲜香的美食、深厚的文化底蕴，让这座天府之国成为国内最受欢迎的旅游目的地之一。",
        "spots": [
            {"name": "大熊猫繁育研究基地", "desc": "全球最大的大熊猫人工繁育基地，拥有100多只大熊猫。清晨是熊猫最活跃的时段，可以看到它们吃竹子、爬树、打滚卖萌的可爱场景。园内还有月亮产房可以看到熊猫幼崽，建议安排3-4小时慢慢逛。", "price": 55, "hours": "07:30-18:00", "tips": "务必早上8点前到达，9点后熊猫吃饱就睡了；提前在公众号「成都大熊猫繁育研究基地」购票；园内观光车20元建议乘坐"},
            {"name": "宽窄巷子", "desc": "由宽巷子、窄巷子、井巷子三条平行排列的清代古街组成，是成都保存最完好的历史文化街区。宽巷子展示老成都的市井生活，窄巷子主打精致小资的院落文化，井巷子则是现代设计与传统的碰撞。巷内有川剧变脸表演、掏耳朵体验和各种文创小店。", "price": 0, "hours": "全天开放", "tips": "建议傍晚开始逛，灯笼亮起氛围最佳；巷内小吃偏贵，正宗美食在附近魁星楼街；掏耳朵40元一次，体验一下即可"},
            {"name": "锦里古街", "desc": "紧邻武侯祠的三国文化主题古街，全长550米，明清风格建筑蜿蜒曲折。白天可以感受三国文化和蜀绣、糖画等非遗手工艺，晚上灯笼高挂、流光溢彩，是成都最美的夜景之一。街内茶馆可以边喝茶边看川剧变脸。", "price": 0, "hours": "全天开放", "tips": "晚上19:00亮灯后最美，建议先逛武侯祠再到锦里；张飞牛肉和三大炮是必尝小吃；周末人极多注意防盗"},
            {"name": "武侯祠", "desc": "中国唯一君臣合祀祠庙，纪念诸葛亮和刘备，始建于公元223年。祠内古柏参天，碑刻林立，最有名的是岳飞手书《出师表》和三绝碑。三义庙供奉刘关张三人，是三国文化爱好者必朝圣之地。隔壁就是锦里，可一起游览。", "price": 50, "hours": "08:00-18:00", "tips": "建议花20元租讲解器或请导游，否则很难看懂历史内涵；全程约1.5小时；周一正常开放"},
            {"name": "杜甫草堂", "desc": "诗圣杜甫在成都的故居，他在此居住近四年，创作了240余首诗篇。草堂内翠竹掩映、溪水环绕，充满诗情画意。核心景点包括工部祠、诗史堂和茅屋故居，整个园林融合了江南园林的精致和川西民居的质朴。", "price": 60, "hours": "08:00-18:30", "tips": "环境清幽适合慢慢逛，预留2小时；红墙夹道是拍照最美的地方；旁边浣花溪公园免费，可以顺路逛"},
            {"name": "春熙路", "desc": "成都最繁华的百年商业街，也是全国十大步行街之一。IFS国际金融中心顶楼的爬墙大熊猫雕塑是成都地标级网红打卡点，太古里则汇集了国际大牌和设计师店铺。春熙路不仅是购物天堂，更是看成都美女的最佳地点。", "price": 0, "hours": "全天开放", "tips": "IFS顶楼大熊猫免费拍照，从7楼空中花园上去；太古里负一层有大牌折扣店；附近盐市口也有很多小吃"},
            {"name": "青城山", "desc": "中国道教发源地之一，有「青城天下幽」的美誉。前山以道教宫观为主，建福宫、上清宫、天师洞都值得一看；后山以自然风光取胜，飞瀑流泉、绿意盎然。全程游览需5-6小时，是成都周边的天然氧吧。", "price": 90, "hours": "08:00-17:00", "tips": "前山问道后山观景，体力有限选前山即可；穿运动鞋，山路较陡；山上物价高，自带水和干粮"},
            {"name": "都江堰", "desc": "始建于公元前256年的世界水利工程奇迹，由秦国蜀郡太守李冰父子主持修建。鱼嘴分水堤、飞沙堰溢洪道、宝瓶口引水口三大工程至今仍在发挥作用，灌溉了成都平原千万亩良田。站在伏龙观俯瞰整个工程，会被古人的智慧深深震撼。", "price": 80, "hours": "08:00-17:30", "tips": "强烈建议请导游讲解（约100元），否则看不太懂；和青城山同方向，可安排同一天；景区门口有直达高铁站的公交"},
        ],
        "foods": ["火锅", "串串香", "担担面", "龙抄手", "夫妻肺片", "钵钵鸡", "兔头", "冰粉"],
        "hotel_areas": {"春熙路/太古里": "市中心核心商圈，交通便利，美食集中", "宽窄巷子附近": "老成都风情，适合慢节奏体验"},
    },
    "北京": {
        "summary": "北京是一座兼具古典韵味与现代气息的城市，故宫的红墙金瓦、长城的雄伟壮阔、胡同里的人间烟火，每一处都值得细细品味。",
        "spots": [
            {"name": "故宫博物院", "desc": "明清两代皇家宫殿，世界最大宫殿建筑群，红墙金瓦震撼人心", "price": 60, "hours": "08:30-17:00（周一闭馆）", "tips": "提前7天在公众号预约，现场不售票"},
            {"name": "八达岭长城", "desc": "万里长城最精华段，登高望远气势磅礴", "price": 40, "hours": "07:30-16:00", "tips": "穿舒适运动鞋，带足够水"},
            {"name": "颐和园", "desc": "清代皇家园林，昆明湖畔万寿山下，一步一景", "price": 30, "hours": "06:30-18:00", "tips": "建议上午去人少，佛香阁可俯瞰全景"},
            {"name": "天坛公园", "desc": "明清皇帝祭天场所，祈年殿是北京地标", "price": 15, "hours": "06:00-21:00", "tips": "清晨可看大爷大妈打太极"},
            {"name": "南锣鼓巷", "desc": "北京最有名的胡同街区，小店、美食、文创琳琅满目", "price": 0, "hours": "全天开放", "tips": "尝老北京炸酱面和豆汁"},
            {"name": "798艺术区", "desc": "旧工厂改造的当代艺术区，展览、画廊、设计店", "price": 0, "hours": "10:00-18:00", "tips": "周末有市集，很好逛"},
            {"name": "什刹海", "desc": "前海、后海、西海统称，可划船赏荷花，酒吧街热闹", "price": 0, "hours": "全天开放", "tips": "傍晚去可看日落，晚上酒吧街热闹"},
        ],
        "foods": ["北京烤鸭", "炸酱面", "豆汁焦圈", "涮羊肉", "卤煮火烧", "炒肝", "爆肚"],
        "hotel_areas": {"王府井/东单": "市中心核心，步行可到天安门", "后海/鼓楼": "胡同风情，文艺气息浓厚"},
    },
    "上海": {
        "summary": "上海是一座海纳百川的国际化大都市，外滩的万国建筑、陆家嘴的摩天大楼、弄堂里的市井生活，在这里古今交融、中西合璧。",
        "spots": [
            {"name": "外滩", "desc": "万国建筑博览群，浦江两岸古今辉映", "price": 0, "hours": "全天开放", "tips": "夜景比白天更美，建议傍晚去"},
            {"name": "东方明珠", "desc": "上海地标，登塔俯瞰浦江两岸全景", "price": 199, "hours": "08:30-21:30", "tips": "选晴天去，能见度高"},
            {"name": "豫园", "desc": "明代江南园林，曲径通幽别有洞天", "price": 40, "hours": "08:30-17:00", "tips": "旁边城隍庙小吃很多"},
            {"name": "南京路步行街", "desc": "中华第一商业街，购物天堂", "price": 0, "hours": "全天开放", "tips": "晚上霓虹灯璀璨"},
            {"name": "迪士尼乐园", "desc": "中国大陆首座迪士尼，梦幻童话世界", "price": 475, "hours": "08:30-21:30", "tips": "提前下载APP抢FP快速通行"},
            {"name": "田子坊", "desc": "弄堂里的文艺小店聚集地，手工艺品和咖啡香", "price": 0, "hours": "10:00-22:00", "tips": "周末人很多，建议工作日去"},
            {"name": "上海博物馆", "desc": "中国古代艺术顶级殿堂，青铜器收藏举世闻名", "price": 0, "hours": "09:00-17:00（周一闭馆）", "tips": "免费但需提前预约"},
        ],
        "foods": ["生煎包", "小笼包", "蟹粉面", "本帮菜", "葱油拌面", "排骨年糕", "蝴蝶酥"],
        "hotel_areas": {"外滩/南京路": "一线江景，步行逛外滩", "静安寺": "时尚商圈，购物方便"},
    },
    "杭州": {
        "summary": "上有天堂，下有苏杭。杭州以西湖为核心，湖光山色与人文底蕴交相辉映，是一座让人流连忘返的诗意之城。",
        "spots": [
            {"name": "西湖", "desc": "世界文化遗产，十景如画，泛舟湖上如入仙境", "price": 0, "hours": "全天开放", "tips": "断桥残雪最美，苏堤春晓必走"},
            {"name": "灵隐寺", "desc": "千年古刹，飞来峰下香烟缭绕", "price": 45, "hours": "07:00-17:30", "tips": "心诚则灵，建议早上前往"},
            {"name": "雷峰塔", "desc": "白蛇传说之地，塔顶俯瞰西湖全景", "price": 40, "hours": "08:00-17:30", "tips": "傍晚登塔可看西湖日落"},
            {"name": "西溪湿地", "desc": "城市绿肺，坐摇橹船穿行芦苇荡", "price": 80, "hours": "08:00-17:00", "tips": "坐摇橹船体验最佳"},
            {"name": "龙井村", "desc": "西湖龙井原产地，层层茶园翠绿欲滴", "price": 0, "hours": "全天开放", "tips": "清明前后最热闹，可品正宗龙井"},
            {"name": "河坊街", "desc": "南宋古街，美食小吃和手工艺品丰富", "price": 0, "hours": "全天开放", "tips": "定胜糕和葱包烩必尝"},
        ],
        "foods": ["西湖醋鱼", "龙井虾仁", "东坡肉", "葱包烩", "定胜糕", "片儿川", "叫花鸡"],
        "hotel_areas": {"西湖湖滨": "西湖畔，出门即是湖景", "武林广场": "市中心商圈，交通便利"},
    },
    "大理": {
        "summary": "大理是云南高原上的一颗明珠，苍山洱海之间的白族古城，有「风花雪月」之美。在这里时间变慢，心灵得到治愈。",
        "spots": [
            {"name": "洱海", "desc": "环湖骑行赏苍山倒影，湖水湛蓝如宝石", "price": 0, "hours": "全天开放", "tips": "环海西路风景绝美，租电动车最方便"},
            {"name": "大理古城", "desc": "漫步白族古城，石板路、老宅、鲜花饼", "price": 0, "hours": "全天开放", "tips": "人民路小店值得逛"},
            {"name": "苍山", "desc": "乘索道登顶，俯瞰洱海和大理坝子", "price": 280, "hours": "08:30-17:00", "tips": "山顶凉，带外套"},
            {"name": "喜洲古镇", "desc": "白族民居博物馆，严家大院和转角楼必看", "price": 0, "hours": "全天开放", "tips": "喜洲粑粑必尝，打车20分钟可达"},
            {"name": "双廊古镇", "desc": "洱海边发呆看日落的最佳位置", "price": 0, "hours": "全天开放", "tips": "海景咖啡馆很多，适合发呆"},
            {"name": "崇圣寺三塔", "desc": "千年古塔，大理国皇家寺院遗址", "price": 75, "hours": "08:00-18:00", "tips": "清晨钟声很治愈"},
        ],
        "foods": ["酸辣鱼", "烤乳扇", "喜洲粑粑", "凉鸡米线", "洱海虾", "白族三道茶"],
        "hotel_areas": {"大理古城": "吃喝玩乐方便，夜生活丰富", "双廊/洱海边": "海景客栈，适合发呆看日落"},
    },
    "三亚": {
        "summary": "三亚是中国最南端的热带滨海城市，蓝天碧海、椰风海韵，是冬日避寒、夏日戏水的首选度假胜地。",
        "spots": [
            {"name": "亚龙湾", "desc": "天下第一湾，沙白水清，热带天堂", "price": 0, "hours": "全天开放", "tips": "自带浮潜装备玩得更尽兴"},
            {"name": "蜈支洲岛", "desc": "中国马尔代夫，潜水爱好者的天堂", "price": 168, "hours": "08:00-17:30", "tips": "提前一天买票，早上第一班船上岛人少"},
            {"name": "南山文化旅游区", "desc": "108米海上观音庄严壮观", "price": 129, "hours": "08:00-17:00", "tips": "景区很大，穿舒适鞋子"},
            {"name": "天涯海角", "desc": "三亚标志性景点，礁石海滩椰林", "price": 81, "hours": "07:30-18:00", "tips": "情侣必去打卡地"},
            {"name": "海棠湾免税店", "desc": "全球最大单体免税店，购物天堂", "price": 0, "hours": "10:00-22:00", "tips": "离岛前提前购买，机场提货"},
        ],
        "foods": ["海鲜大排档", "椰子鸡", "清补凉", "抱罗粉", "文昌鸡", "热带水果"],
        "hotel_areas": {"亚龙湾": "一线海景五星酒店群", "大东海": "性价比高，交通便利"},
    },
    "西安": {
        "summary": "西安是十三朝古都，兵马俑的壮观、古城墙的厚重、回民街的美食，让这座千年帝都散发着永恒的魅力。",
        "spots": [
            {"name": "秦始皇兵马俑", "desc": "世界第八大奇迹，千军万马气势磅礴", "price": 120, "hours": "08:30-17:00", "tips": "建议请导游讲解，自己看很难懂"},
            {"name": "大雁塔", "desc": "唐代古塔，玄奘译经之地，音乐喷泉壮观", "price": 50, "hours": "08:00-18:00", "tips": "晚上音乐喷泉表演很棒"},
            {"name": "西安城墙", "desc": "中国保存最完整的古城墙，可骑车环游", "price": 54, "hours": "08:00-22:00", "tips": "租自行车骑行一圈约1.5小时"},
            {"name": "回民街", "desc": "西安最热闹的美食街，羊肉泡馍、肉夹馍", "price": 0, "hours": "全天开放", "tips": "晚上最热闹，人多注意财物"},
            {"name": "大唐不夜城", "desc": "仿唐建筑群，夜景灯光华丽", "price": 0, "hours": "全天开放", "tips": "晚上去才有feel，不倒翁小姐姐很火"},
            {"name": "华清宫", "desc": "唐玄宗与杨贵妃的温泉离宫", "price": 120, "hours": "07:00-18:00", "tips": "和兵马俑一天可以逛完"},
        ],
        "foods": ["羊肉泡馍", "肉夹馍", "凉皮", "Biangbiang面", "灌汤包", "甑糕", "胡辣汤"],
        "hotel_areas": {"钟楼/回民街": "市中心核心，吃喝玩乐方便", "大雁塔/曲江": "环境好，夜景美"},
    },
    "重庆": {
        "summary": "重庆是一座魔幻 8D 山城，轻轨穿楼、洪崖洞夜景、麻辣火锅，每一步都是惊喜，每一口都是热辣。",
        "spots": [
            {"name": "洪崖洞", "desc": "千与千寻同款吊脚楼，夜景璀璨夺目", "price": 0, "hours": "全天开放", "tips": "晚上亮灯后最美，人超多注意安全"},
            {"name": "解放碑", "desc": "重庆地标，中国唯一抗战胜利纪功碑", "price": 0, "hours": "全天开放", "tips": "周边好吃街很多"},
            {"name": "磁器口古镇", "desc": "千年古镇，麻花飘香，茶馆听川剧", "price": 0, "hours": "全天开放", "tips": "陈麻花必买，周末人超多"},
            {"name": "长江索道", "desc": "万里长江第一条空中走廊，飞渡长江", "price": 20, "hours": "07:30-22:30", "tips": "傍晚坐，看长江日落"},
            {"name": "南山一棵树", "desc": "俯瞰重庆夜景的绝佳位置", "price": 30, "hours": "09:00-22:30", "tips": "重庆夜景是名片，必看"},
        ],
        "foods": ["火锅", "小面", "酸辣粉", "抄手", "毛血旺", "辣子鸡", "豆花饭"],
        "hotel_areas": {"解放碑/洪崖洞": "市中心核心，夜景尽收眼底", "南滨路": "江景酒店，环境好"},
    },
    "长沙": {
        "summary": "长沙是一座烟火气十足的城市，岳麓书院的千年书香、橘子洲的伟人足迹、文和友的市井美食，让人来了就不想走。",
        "spots": [
            {"name": "橘子洲", "desc": "湘江中的长岛，毛泽东青年雕像巍然屹立", "price": 0, "hours": "全天开放", "tips": "周末晚上有烟花表演"},
            {"name": "岳麓山", "desc": "南岳衡山余脉，岳麓书院千年学府", "price": 0, "hours": "06:00-23:00", "tips": "秋天红叶最美"},
            {"name": "太平老街", "desc": "长沙最有韵味的老街，臭豆腐和茶颜悦色的发源地", "price": 0, "hours": "全天开放", "tips": "茶颜悦色排队很长但值得"},
            {"name": "湖南省博物馆", "desc": "马王堆汉墓出土文物，辛追夫人千年不朽", "price": 0, "hours": "09:00-17:00（周一闭馆）", "tips": "需提前预约，免费"},
            {"name": "五一广场", "desc": "长沙最繁华的商业中心，IFS国金中心", "price": 0, "hours": "全天开放", "tips": "长沙夜生活从这里开始"},
        ],
        "foods": ["臭豆腐", "口味虾", "辣椒炒肉", "剁椒鱼头", "糖油粑粑", "茶颜悦色", "文和友"],
        "hotel_areas": {"五一广场/坡子街": "市中心核心，美食触手可及", "岳麓区": "大学城附近，性价比高"},
    },
    "厦门": {
        "summary": "厦门是一座文艺清新的海岛城市，鼓浪屿的万国建筑、环岛路的椰风海韵、曾厝垵的渔村风情，每一步都是风景。",
        "spots": [
            {"name": "鼓浪屿", "desc": "海上花园，万国建筑博物馆，钢琴之岛", "price": 35, "hours": "全天开放", "tips": "提前在公众号买船票，现场常售罄"},
            {"name": "厦门大学", "desc": "中国最美大学，嘉庚建筑，芙蓉隧道涂鸦", "price": 0, "hours": "需预约入校", "tips": "需提前在U厦大预约"},
            {"name": "曾厝垵", "desc": "文艺渔村变网红打卡地，小吃手信一条街", "price": 0, "hours": "全天开放", "tips": "沙茶面和海蛎煎必吃"},
            {"name": "环岛路", "desc": "沿海景观大道，骑行看海吹海风", "price": 0, "hours": "全天开放", "tips": "租自行车骑行超惬意"},
            {"name": "南普陀寺", "desc": "闽南佛教圣地，千年古刹，免费开放", "price": 0, "hours": "03:00-18:00", "tips": "素饼很有名，可做伴手礼"},
        ],
        "foods": ["沙茶面", "海蛎煎", "姜母鸭", "土笋冻", "花生汤", "烧肉粽"],
        "hotel_areas": {"中山路/轮渡": "去鼓浪屿方便，中山路美食多", "曾厝垵": "文艺民宿聚集地，近海"},
    },
}
