"""
JSON / 数值解析辅助 — 从 planner.py 拆分（A3，保持行为不变）

LLM 输出常夹带 markdown 代码块、前后缀说明文字，数值字段可能是
字符串（如 "60元"/"5300"），这里提供防御式解析工具。
"""
from __future__ import annotations

import json
import logging
import re
from typing import Optional

logger = logging.getLogger("travel-agent")


def to_int(value, default: int = 0) -> int:
    """LLM 返回的数值字段可能是字符串（如 "60元"/"5300"），防御式转 int，失败回退默认值"""
    if value is None or value == "":
        return default
    if isinstance(value, bool):
        return int(value)
    if isinstance(value, (int, float)):
        return int(value)
    if isinstance(value, str):
        # LLM 常输出 "约500元"、"60元/人"、"5300（含门票）" 等，用正则提取首个数字
        m = re.search(r'\d+(?:\.\d+)?', value)
        if m:
            try:
                return int(float(m.group(0)))
            except ValueError:
                return default
        return default
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def parse_json(text) -> Optional[dict]:
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

    # 尝试直接解析（只接受 dict；数组等其它结构对下游无意义，返回 None 触发 fallback）
    try:
        parsed = json.loads(text)
        return parsed if isinstance(parsed, dict) else None
    except json.JSONDecodeError:
        pass

    # 尝试找 JSON 边界
    for start_char, end_char in [('{', '}'), ('[', ']')]:
        start = text.find(start_char)
        end = text.rfind(end_char)
        if start >= 0 and end > start:
            try:
                parsed = json.loads(text[start:end + 1])
                return parsed if isinstance(parsed, dict) else None
            except json.JSONDecodeError:
                continue

    return None


# ==================== 行程版本 Diff ====================

def _summarize(value, limit: int = 100) -> str:
    """值摘要：字符串原样截断；其它类型 JSON 序列化后截断（超长加省略号）。"""
    if value is None:
        return "null"
    if isinstance(value, str):
        s = value
    else:
        try:
            s = json.dumps(value, ensure_ascii=False, default=str)
        except (TypeError, ValueError):
            s = str(value)
    return s[:limit] + "…" if len(s) > limit else s


def diff_plans(a: dict, b: dict) -> list:
    """行程版本对比 — 顶层字段级差异（新增/删除/修改的 key 与前后值摘要）。

    返回：[{"key": 字段名, "action": "added"|"removed"|"modified", "before": 摘要, "after": 摘要}]
    摘要截断 100 字符；非 dict 输入返回空列表。
    """
    if not isinstance(a, dict) or not isinstance(b, dict):
        return []
    diffs = []
    keys_a, keys_b = set(a.keys()), set(b.keys())
    # 删除的字段（b 中不存在）
    for k in sorted(keys_a - keys_b):
        diffs.append({"key": k, "action": "removed", "before": _summarize(a[k]), "after": ""})
    # 新增的字段（a 中不存在）
    for k in sorted(keys_b - keys_a):
        diffs.append({"key": k, "action": "added", "before": "", "after": _summarize(b[k])})
    # 双方都有但值不同 → modified
    for k in sorted(keys_a & keys_b):
        va, vb = a[k], b[k]
        if json.dumps(va, ensure_ascii=False, sort_keys=True, default=str) != \
                json.dumps(vb, ensure_ascii=False, sort_keys=True, default=str):
            diffs.append({"key": k, "action": "modified", "before": _summarize(va), "after": _summarize(vb)})
    return diffs
