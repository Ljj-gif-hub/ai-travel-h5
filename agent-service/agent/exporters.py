"""
行程导出 — 标准库生成 iCalendar（.ics），无第三方依赖

规则：
  - 每天一个 VEVENT：SUMMARY=第N天-城市
  - DTSTART / DTEND 用当地 09:00 / 21:00 浮点时间（20260818T090000 样式，不带时区）
  - DESCRIPTION 该天标题 + 2-3 个景点
  - 出发日默认取当天（行程无固定日期），每天顺延一天
  - plan 解析失败（非 dict / 无 day_plans）返回 None，由端点统一回 400
"""
from __future__ import annotations

import logging
from datetime import datetime, timedelta
from typing import Optional

logger = logging.getLogger("travel-agent.export")

# 每天事件的起止时间（当地时间浮点时间）
_EVENT_START = "T090000"
_EVENT_END = "T210000"


def _ical_escape(text) -> str:
    """RFC 5545 文本转义：反斜杠、分号、逗号、换行。"""
    return (
        str(text)
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r", "")
        .replace("\n", "\\n")
    )


def build_ical(plan: dict) -> Optional[str]:
    """plan dict → iCalendar 文本；解析失败返回 None。"""
    if not isinstance(plan, dict):
        logger.warning("iCal 导出失败：plan 不是 dict")
        return None
    day_plans = plan.get("day_plans")
    if not isinstance(day_plans, list) or not day_plans:
        logger.warning("iCal 导出失败：plan 缺少 day_plans")
        return None

    destination = str(plan.get("destination") or "目的地")
    start_day = datetime.now().date()
    stamp = datetime.now().strftime("%Y%m%dT%H%M%S")

    lines = [
        "BEGIN:VCALENDAR",
        "VERSION:2.0",
        "PRODID:-//travel-agent//plan-export//ZH",
        "CALSCALE:GREGORIAN",
        "METHOD:PUBLISH",
    ]
    for idx, dp in enumerate(day_plans):
        if not isinstance(dp, dict):
            continue
        try:
            day = int(dp.get("day") or (idx + 1))
        except (TypeError, ValueError):
            day = idx + 1
        date = start_day + timedelta(days=max(day, 1) - 1)
        date_str = date.strftime("%Y%m%d")

        # DESCRIPTION：当天标题 + 2-3 个景点
        desc_parts = []
        if dp.get("day_title"):
            desc_parts.append(str(dp["day_title"]))
        slots = dp.get("time_slots") or []
        if isinstance(slots, list):
            for s in slots[:3]:
                if isinstance(s, dict) and s.get("attraction"):
                    desc_parts.append(str(s["attraction"]))
        description = "；".join(desc_parts[:4]) if desc_parts else "自由安排"

        lines += [
            "BEGIN:VEVENT",
            f"UID:travel-{date_str}-day{day}@{_ical_escape(destination)}",
            f"DTSTAMP:{stamp}",
            # 浮点日期时间（当地时间 09:00-21:00，不带时区后缀）
            f"DTSTART:{date_str}{_EVENT_START}",
            f"DTEND:{date_str}{_EVENT_END}",
            f"SUMMARY:{_ical_escape('第' + str(day) + '天-' + destination)}",
            f"DESCRIPTION:{_ical_escape(description)}",
            "END:VEVENT",
        ]
    lines.append("END:VCALENDAR")
    # iCalendar 规范要求 CRLF 行结束
    return "\r\n".join(lines) + "\r\n"
