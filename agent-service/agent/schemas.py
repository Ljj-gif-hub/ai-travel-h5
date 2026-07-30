"""
Pydantic 数据模型 — 定义 Agent 输入/输出的结构化 Schema
"""
from __future__ import annotations

from typing import List, Optional, Any
from pydantic import BaseModel, Field


# ==================== 用户输入 ====================

class TravelRequest(BaseModel):
    """用户旅行规划请求"""
    destination: str = Field(..., description="目的地城市", examples=["成都"])
    origin: str = Field(default="深圳", description="出发地", examples=["深圳"])
    days: int = Field(default=3, ge=1, le=14, description="出行天数")
    people: int = Field(default=2, ge=1, le=20, description="出行人数")
    budget: int = Field(default=5000, description="人均预算（元）", examples=[5000])
    companion: str = Field(default="独行", description="同行人群", examples=["情侣", "亲子", "朋友", "独行"])
    styles: List[str] = Field(default_factory=list, description="旅行偏好标签", examples=[["美食", "人文", "自然风光"]])
    hotel_level: str = Field(default="舒适型", description="酒店档次", examples=["经济型", "舒适型", "豪华型"])
    pace: str = Field(default="适中", description="行程节奏", examples=["轻松", "适中", "紧凑"])
    months: List[int] = Field(default_factory=list, description="出行月份（1-12）", examples=[[4, 5]])

    def build_preference_text(self) -> str:
        """构建人类可读的偏好描述"""
        parts = []
        if self.companion and self.companion != "独行":
            parts.append(f"{self.companion}出行")
        if self.styles:
            parts.append(f"偏好{'、'.join(self.styles[:4])}")
        if self.hotel_level:
            parts.append(f"酒店档次{self.hotel_level}")
        if self.pace:
            parts.append(f"节奏{self.pace}")
        parts.append(f"人均预算{self.budget}元")
        return "，".join(parts)


# ==================== 行程数据结构 ====================

class TimeSlot(BaseModel):
    """每日时段安排"""
    time_of_day: str = Field(..., description="时段：上午/下午/晚上")
    time: str = Field(..., description="具体时间", examples=["09:00"])
    attraction: str = Field(..., description="景点或活动名称")
    activity: str = Field(..., description="活动描述")
    duration: str = Field(..., description="预计时长", examples=["2小时"])
    cost: int = Field(default=0, description="预估费用（元）")
    transport: str = Field(default="步行", description="到达方式")
    tips: str = Field(default="", description="实用贴士")
    image_url: str = Field(default="", description="景点图片URL")
    lat: Optional[float] = Field(default=None, description="纬度")
    lng: Optional[float] = Field(default=None, description="经度")


class DayPlan(BaseModel):
    """单日行程"""
    day: int = Field(..., description="第几天")
    day_title: str = Field(..., description="当日主题", examples=["第1天：熊猫基地+宽窄巷子"])
    date_hint: str = Field(default="", description="日期提示")
    time_slots: List[TimeSlot] = Field(default_factory=list, description="当日时段安排")
    daily_budget: int = Field(default=0, description="当日预算（元）")
    meals: List[str] = Field(default_factory=list, description="推荐餐厅")


class BudgetDetail(BaseModel):
    """预算明细"""
    transport: int = Field(default=0, description="交通费")
    accommodation: int = Field(default=0, description="住宿费")
    food: int = Field(default=0, description="餐饮费")
    tickets: int = Field(default=0, description="门票费")
    shopping: int = Field(default=0, description="购物/其他")
    total: int = Field(default=0, description="总计")


class HotelOption(BaseModel):
    """酒店选项"""
    name: str = Field(..., description="酒店名称")
    district: str = Field(default="", description="所在区域")
    price_per_night: int = Field(..., description="每晚价格（元）")
    total_price: int = Field(default=0, description="总价")
    rating: float = Field(default=4.0, description="评分")
    highlights: str = Field(default="", description="亮点")
    lat: Optional[float] = Field(default=None)
    lng: Optional[float] = Field(default=None)


class TransportInfo(BaseModel):
    """往返交通信息"""
    depart_type: str = Field(default="flight", description="去程方式")
    depart_title: str = Field(default="", description="去程标题")
    depart_detail: str = Field(default="", description="去程详情")
    depart_price: int = Field(default=0)
    return_type: str = Field(default="flight")
    return_title: str = Field(default="")
    return_detail: str = Field(default="")
    return_price: int = Field(default=0)


class CommuteInfo(BaseModel):
    """城市内部通勤信息"""
    from_spot: str = Field(..., description="起点")
    to_spot: str = Field(..., description="终点")
    distance_km: float = Field(default=0, description="距离（公里）")
    duration_min: int = Field(default=0, description="耗时（分钟）")
    mode: str = Field(default="驾车", description="出行方式")


# ==================== 最终输出 ====================

class TripPlanOutput(BaseModel):
    """Agent 最终输出的完整旅行方案"""
    destination: str = Field(..., description="目的地")
    days: int = Field(..., description="天数")
    total_budget: int = Field(..., description="总预算")
    people: int = Field(default=1, description="人数")
    overview: str = Field(default="", description="行程总览（100-200字）")
    day_plans: List[DayPlan] = Field(default_factory=list, description="每日行程")
    budget_detail: BudgetDetail = Field(default_factory=BudgetDetail, description="预算明细")
    hotels: List[HotelOption] = Field(default_factory=list, description="推荐酒店")
    transport: Optional[TransportInfo] = Field(default=None, description="往返交通")
    tips: List[str] = Field(default_factory=list, description="旅行贴士")
    research_notes: List[str] = Field(default_factory=list, description="调研备注（信息来源）")
    commute_map: List[CommuteInfo] = Field(default_factory=list, description="景点间通勤信息")


# ==================== SSE 事件模型 ====================

class AgentEvent(BaseModel):
    """Agent 实时事件 — 通过 SSE 推送给前端"""
    event_type: str = Field(..., description="事件类型")
    phase: Optional[str] = Field(default=None, description="当前阶段")
    message: Optional[str] = Field(default=None, description="人类可读消息")
    data: Optional[Any] = Field(default=None, description="事件负载数据")

    # 事件类型说明：
    # thinking     — Agent 正在思考（展示思考过程）
    # tool_start   — 开始调用工具
    # tool_end     — 工具调用完成
    # phase_start  — 进入新阶段
    # phase_end    — 阶段完成
    # warning      — 警告（如预算超标）
    # adjustment   — 正在调整方案
    # plan_update  — 方案更新
    # complete     — 全部完成，携带完整 TripPlanOutput
    # error        — 出错
