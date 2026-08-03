"""
Agent 工具集 — 旅游规划 Agent 可以调用的所有外部工具

包含：
- search_attractions_info: Tavily 联网搜索景点/美食实时信息
- search_hotels_info: Tavily 联网搜索酒店价格区间
- get_commute_info: 高德地图 API 计算两点间距离和通勤时间
- calculate_budget: 本地预算核算与超标检测
"""
from __future__ import annotations

import json
import os
import re
from typing import Optional, List, Dict, Any

import httpx
from langchain_core.tools import tool


# ==================== 配置 ====================

def _get_amap_key() -> str:
    return os.getenv("AMAP_WEB_KEY", "")


def _get_tavily_key() -> str:
    return os.getenv("TAVILY_API_KEY", "")


# ==================== 工具 1：景点/美食搜索 ====================

@tool
def search_attractions_info(query: str) -> str:
    """
    联网搜索景点、美食、活动的实时信息。

    适用场景：
    - 搜索某城市的必去景点、热门打卡地
    - 查询景点的开放时间、门票价格、最佳游玩季节
    - 搜索当地特色美食、推荐餐厅
    - 查询当地近期的活动、节庆、展览

    :param query: 搜索关键词，如 "成都必去景点推荐 2025"、"成都大熊猫基地门票价格开放时间"
    :return: 搜索结果摘要（JSON格式，含标题、内容、来源URL）
    """
    api_key = _get_tavily_key()
    if not api_key:
        return json.dumps({
            "error": "Tavily API Key 未配置",
            "fallback": True,
            "message": f"无法实时搜索「{query}」，请使用已知信息规划。",
        }, ensure_ascii=False)

    try:
        # 直接通过 HTTP 调用 Tavily Search API
        with httpx.Client(timeout=15.0) as client:
            resp = client.post(
                "https://api.tavily.com/search",
                json={
                    "api_key": api_key,
                    "query": query,
                    "search_depth": "advanced",
                    "max_results": 5,
                    "include_answer": True,
                },
            )
            resp.raise_for_status()
            data = resp.json()

        # 整理搜索结果
        results = []
        if data.get("answer"):
            results.append({"type": "summary", "content": data["answer"]})

        for r in data.get("results", [])[:5]:
            results.append({
                "type": "result",
                "title": r.get("title", ""),
                "content": r.get("content", ""),
                "url": r.get("url", ""),
                "score": r.get("score", 0),
            })

        return json.dumps({
            "query": query,
            "results": results,
            "total_found": len(results),
        }, ensure_ascii=False)

    except Exception as e:
        return json.dumps({
            "error": f"搜索失败: {str(e)}",
            "fallback": True,
            "query": query,
        }, ensure_ascii=False)


# ==================== 工具 2：酒店搜索 ====================

@tool
def search_hotels_info(query: str) -> str:
    """
    联网搜索目的地酒店区域分布、价格区间和推荐。

    适用场景：
    - 搜索某城市不同区域的酒店价格区间
    - 搜索适合特定人群的酒店（情侣/亲子/商务）
    - 搜索特定档次的酒店推荐

    :param query: 搜索关键词，如 "成都春熙路附近舒适型酒店价格 2025"、"成都亲子酒店推荐"
    :return: 搜索结果摘要
    """
    api_key = _get_tavily_key()
    if not api_key:
        return json.dumps({
            "error": "Tavily API Key 未配置",
            "fallback": True,
            "hotels": _get_fallback_hotels(query),
        }, ensure_ascii=False)

    try:
        with httpx.Client(timeout=15.0) as client:
            resp = client.post(
                "https://api.tavily.com/search",
                json={
                    "api_key": api_key,
                    "query": query,
                    "search_depth": "advanced",
                    "max_results": 5,
                    "include_answer": True,
                },
            )
            resp.raise_for_status()
            data = resp.json()

        results = []
        if data.get("answer"):
            results.append({"type": "summary", "content": data["answer"]})

        for r in data.get("results", [])[:5]:
            results.append({
                "type": "result",
                "title": r.get("title", ""),
                "content": r.get("content", ""),
                "url": r.get("url", ""),
            })

        return json.dumps({
            "query": query,
            "results": results,
        }, ensure_ascii=False)

    except Exception as e:
        return json.dumps({
            "error": f"搜索失败: {str(e)}",
            "fallback": True,
            "hotels": _get_fallback_hotels(query),
        }, ensure_ascii=False)


def _get_fallback_hotels(query: str) -> List[Dict]:
    """没配 Tavily API Key 时的兜底数据"""
    city_map = {
        "北京": [
            {"name": "王府井希尔顿酒店", "district": "东城区", "price_per_night": 1280, "rating": 4.7, "highlights": "步行5分钟到王府井步行街"},
            {"name": "前门建国饭店", "district": "西城区", "price_per_night": 580, "rating": 4.3, "highlights": "紧邻天安门广场"},
            {"name": "如家精选酒店(北京三里屯店)", "district": "朝阳区", "price_per_night": 380, "rating": 4.1, "highlights": "三里屯核心地段，性价比高"},
        ],
        "上海": [
            {"name": "外滩华尔道夫酒店", "district": "黄浦区", "price_per_night": 2600, "rating": 4.9, "highlights": "外滩一线江景"},
            {"name": "静安瑞吉酒店", "district": "静安区", "price_per_night": 1680, "rating": 4.7, "highlights": "静安寺商圈核心"},
            {"name": "全季酒店(上海南京东路店)", "district": "黄浦区", "price_per_night": 480, "rating": 4.2, "highlights": "南京路步行街旁"},
        ],
        "成都": [
            {"name": "成都博舍酒店", "district": "锦江区", "price_per_night": 2200, "rating": 4.8, "highlights": "太古里内，设计感极强"},
            {"name": "成都群光君悦酒店", "district": "锦江区", "price_per_night": 980, "rating": 4.6, "highlights": "春熙路核心区域"},
            {"name": "成都宜必思酒店(宽窄巷子店)", "district": "青羊区", "price_per_night": 320, "rating": 4.0, "highlights": "步行可达宽窄巷子，高性价比"},
        ],
        "杭州": [
            {"name": "杭州西子湖四季酒店", "district": "西湖区", "price_per_night": 3500, "rating": 4.9, "highlights": "西湖畔私家园林"},
            {"name": "杭州温德姆至尊豪廷", "district": "上城区", "price_per_night": 880, "rating": 4.5, "highlights": "湖滨商圈，交通便利"},
            {"name": "汉庭酒店(杭州西湖湖滨店)", "district": "上城区", "price_per_night": 280, "rating": 3.9, "highlights": "步行至西湖10分钟"},
        ],
        "大理": [
            {"name": "大理洱海天域英迪格", "district": "大理市", "price_per_night": 980, "rating": 4.6, "highlights": "洱海一线海景"},
            {"name": "大理古城既下山酒店", "district": "大理古城", "price_per_night": 580, "rating": 4.5, "highlights": "古城内设计型酒店"},
            {"name": "大理双廊海景客栈", "district": "双廊镇", "price_per_night": 280, "rating": 4.2, "highlights": "性价比海景客栈"},
        ],
        "三亚": [
            {"name": "三亚亚特兰蒂斯酒店", "district": "海棠湾", "price_per_night": 2800, "rating": 4.8, "highlights": "含水世界畅玩"},
            {"name": "三亚亚龙湾万豪", "district": "亚龙湾", "price_per_night": 1200, "rating": 4.6, "highlights": "亚龙湾一线海景"},
            {"name": "三亚大东海如家精选", "district": "大东海", "price_per_night": 350, "rating": 4.0, "highlights": "步行到大东海沙滩"},
        ],
        "西安": [
            {"name": "西安索菲特传奇酒店", "district": "新城区", "price_per_night": 1500, "rating": 4.8, "highlights": "人民大厦历史建筑"},
            {"name": "西安钟楼饭店", "district": "碑林区", "price_per_night": 680, "rating": 4.4, "highlights": "钟楼旁，回民街步行可达"},
            {"name": "汉庭酒店(西安钟楼店)", "district": "碑林区", "price_per_night": 260, "rating": 4.0, "highlights": "市中心经济之选"},
        ],
        "重庆": [
            {"name": "重庆来福士洲际酒店", "district": "渝中区", "price_per_night": 1200, "rating": 4.7, "highlights": "朝天门地标建筑"},
            {"name": "重庆解放碑威斯汀", "district": "渝中区", "price_per_night": 780, "rating": 4.5, "highlights": "解放碑核心区域"},
            {"name": "7天酒店(重庆洪崖洞店)", "district": "渝中区", "price_per_night": 220, "rating": 3.8, "highlights": "洪崖洞步行10分钟"},
        ],
    }

    # 匹配城市
    for city, hotels in city_map.items():
        if city in query:
            return hotels

    # 默认兜底
    return [
        {"name": "市中心豪华酒店", "district": "市中心", "price_per_night": 800, "rating": 4.5, "highlights": "交通便利"},
        {"name": "商务精品酒店", "district": "商业区", "price_per_night": 450, "rating": 4.2, "highlights": "性价比高"},
        {"name": "经济连锁酒店", "district": "市中心", "price_per_night": 250, "rating": 3.8, "highlights": "干净实惠"},
    ]


# ==================== 工具 3：通勤计算 ====================

@tool
def get_commute_info(origin: str, destination: str, mode: str = "驾车", city: str = "") -> str:
    """
    计算两点之间的通勤距离和预估时间。用于检查景点之间的交通是否合理。

    适用场景：
    - 计算景点 A 到景点 B 的距离
    - 计算酒店到第一个景点的通勤时间
    - 比较打车、公交、步行等不同方式

    :param origin: 起点，可以是景点名、酒店名或地址。如 "成都大熊猫繁育研究基地"
    :param destination: 终点。如 "宽窄巷子"
    :param mode: 出行方式：驾车（默认）/ 公交 / 步行 / 骑行
    :param city: 所在城市（用于补全地址），如 "成都"
    :return: JSON 格式的距离和耗时信息
    """
    amap_key = _get_amap_key()

    # 出行方式映射到高德 API 参数
    mode_map = {
        "驾车": "driving",
        "公交": "transit",
        "步行": "walking",
        "骑行": "bicycling",
    }
    amap_mode = mode_map.get(mode, "driving")

    if not amap_key:
        # 没配置高德 Key → 用城市网格近似估算
        return _estimate_commute(origin, destination, mode, city)

    try:
        # 先地理编码获取两个点的坐标
        coords = []
        for addr in [origin, destination]:
            # 如果地址不包含城市名，补全
            if city and city not in addr:
                addr = f"{city}{addr}"

            with httpx.Client(timeout=10.0) as client:
                geo_resp = client.get(
                    "https://restapi.amap.com/v3/geocode/geo",
                    params={
                        "key": amap_key,
                        "address": addr,
                        "city": city,
                    },
                )
                geo_resp.raise_for_status()
                geo_data = geo_resp.json()

            if geo_data.get("status") == "1" and geo_data.get("geocodes"):
                location = geo_data["geocodes"][0]["location"]
                coords.append(location)
            else:
                return json.dumps({
                    "error": f"无法定位「{addr}」，请确认名称是否正确",
                    "origin": origin,
                    "destination": destination,
                }, ensure_ascii=False)

        # 路径规划
        origin_coord = coords[0]
        dest_coord = coords[1]

        url_map = {
            "driving": "https://restapi.amap.com/v3/direction/driving",
            "transit": "https://restapi.amap.com/v3/direction/transit/integrated",
            "walking": "https://restapi.amap.com/v3/direction/walking",
            "bicycling": "https://restapi.amap.com/v4/direction/bicycling",
        }

        with httpx.Client(timeout=10.0) as client:
            route_resp = client.get(
                url_map.get(amap_mode, url_map["driving"]),
                params={
                    "key": amap_key,
                    "origin": origin_coord,
                    "destination": dest_coord,
                    "extensions": "base",
                },
            )
            route_resp.raise_for_status()
            route_data = route_resp.json()

        if route_data.get("status") == "1":
            route = route_data.get("route", {})
            if amap_mode == "driving" and route.get("paths"):
                path = route["paths"][0]
                distance = int(path.get("distance", 0))
                duration = int(path.get("duration", 0))
            elif amap_mode == "transit" and route.get("transits"):
                transit = route["transits"][0]
                distance = int(transit.get("distance", 0)) if transit.get("distance") else int(transit.get("walking_distance", 0)) + 5000
                duration = int(transit.get("duration", 0))
            elif amap_mode == "walking" and route.get("paths"):
                path = route["paths"][0]
                distance = int(path.get("distance", 0))
                duration = int(path.get("duration", 0))
            else:
                distance, duration = 0, 0

            return json.dumps({
                "origin": origin,
                "destination": destination,
                "mode": mode,
                "distance_km": round(distance / 1000, 1),
                "duration_min": round(duration / 60),
                "source": "高德地图实时数据",
            }, ensure_ascii=False)
        else:
            return json.dumps({
                "error": f"路径规划失败: {route_data.get('info', '未知')}",
                "origin": origin,
                "destination": destination,
            }, ensure_ascii=False)

    except Exception as e:
        return json.dumps({
            "error": f"通勤计算失败: {str(e)}",
            "origin": origin,
            "destination": destination,
        }, ensure_ascii=False)


def _estimate_commute(origin: str, destination: str, mode: str, city: str) -> str:
    """
    无高德 Key 时的通勤估算（基于典型城市区域距离）
    仅作演示用途，实际部署请配置高德 API Key
    """
    # 粗略估算：同城市内景点间距离
    estimates = {
        "步行": (1.5, 20),
        "骑行": (3.0, 15),
        "公交": (8.0, 40),
        "驾车": (10.0, 25),
    }
    dist, dur = estimates.get(mode, (8.0, 30))

    return json.dumps({
        "origin": origin,
        "destination": destination,
        "mode": mode,
        "distance_km": dist,
        "duration_min": dur,
        "source": "估算值（请配置高德地图 API Key 获取精确数据）",
        "estimated": True,
    }, ensure_ascii=False)


# ==================== 工具 4：预算核算 ====================

@tool
def calculate_budget(items_json: str) -> str:
    """
    核算整个行程的总花费，检查是否超出预算。

    输入一个 JSON 字符串，包含分项费用和预算上限，返回核算结果和调整建议。

    :param items_json: JSON 字符串，格式为：
        {
          "budget_total": 5000,
          "budget_per_person": 5000,
          "people": 2,
          "days": 3,
          "items": {
            "transport": 1500,     // 往返交通
            "accommodation": 1800, // 住宿（全程）
            "food": 900,           // 餐饮
            "tickets": 600,        // 门票
            "shopping": 500,       // 购物/其他
            "city_transport": 300  // 城市内交通
          }
        }
    :return: 核算结果 JSON
    """
    try:
        data = json.loads(items_json)
    except json.JSONDecodeError:
        return json.dumps({
            "error": "无法解析费用数据，请确保输入是合法 JSON",
            "input": items_json[:200],
        }, ensure_ascii=False)

    budget_total = data.get("budget_total", 5000)
    people = data.get("people", 1)
    items = data.get("items", {})

    # 计算各项
    transport = items.get("transport", 0)
    accommodation = items.get("accommodation", 0)
    food = items.get("food", 0)
    tickets = items.get("tickets", 0)
    shopping = items.get("shopping", 0)
    city_transport = items.get("city_transport", 0)

    actual_total = transport + accommodation + food + tickets + shopping + city_transport

    over_budget = actual_total > budget_total * people
    gap = actual_total - budget_total * people

    # 按优先级生成调整建议
    suggestions = []
    remaining_gap = gap

    if over_budget:
        # 策略1：降住宿
        if accommodation > 0 and remaining_gap > 0:
            # 能省多少
            save = min(remaining_gap, int(accommodation * 0.4))
            remaining_gap -= save
            suggestions.append({
                "strategy": "降低住宿标准",
                "save_amount": save,
                "detail": f"将酒店从当前档位下调一档，每晚可节省约{save // data.get('days', 1)}元，共节省{save}元",
            })

        # 策略2：删可选门票
        if tickets > 0 and remaining_gap > 0:
            save = min(remaining_gap, int(tickets * 0.35))
            remaining_gap -= save
            suggestions.append({
                "strategy": "删减可选收费景点",
                "save_amount": save,
                "detail": f"保留核心景点，将1-2个次要收费景点替换为免费景点，节省{save}元",
            })

        # 策略3：优化餐饮
        if food > 0 and remaining_gap > 0:
            save = min(remaining_gap, int(food * 0.3))
            remaining_gap -= save
            suggestions.append({
                "strategy": "优化餐饮",
                "save_amount": save,
                "detail": f"用当地特色小吃和美食街替代高档餐厅，节省{save}元",
            })

        # 策略4：调整交通（无市内交通预算时跳过，避免生成虚假建议）
        if city_transport > 0 and remaining_gap > 0:
            save = min(remaining_gap, int(city_transport * 0.5))
            remaining_gap -= save
            suggestions.append({
                "strategy": "优化交通方式",
                "save_amount": save,
                "detail": f"用公共交通替代打车/包车，节省{save}元",
            })

    result = {
        "budget_total": budget_total * people,
        "budget_per_person": budget_total,
        "people": people,
        "actual_total": actual_total,
        "is_over_budget": over_budget,
        "gap": gap,
        "remaining_gap_after_suggestions": remaining_gap,
        "breakdown": {
            "transport": {"amount": transport, "percentage": round(transport / max(actual_total, 1) * 100, 1)},
            "accommodation": {"amount": accommodation, "percentage": round(accommodation / max(actual_total, 1) * 100, 1)},
            "food": {"amount": food, "percentage": round(food / max(actual_total, 1) * 100, 1)},
            "tickets": {"amount": tickets, "percentage": round(tickets / max(actual_total, 1) * 100, 1)},
            "shopping": {"amount": shopping, "percentage": round(shopping / max(actual_total, 1) * 100, 1)},
            "city_transport": {"amount": city_transport, "percentage": round(city_transport / max(actual_total, 1) * 100, 1)},
        },
        "suggestions": suggestions,
        "can_fix": remaining_gap <= 0,
    }

    return json.dumps(result, ensure_ascii=False)


# ==================== 工具注册表 ====================

ALL_TOOLS = [
    search_attractions_info,
    search_hotels_info,
    get_commute_info,
    calculate_budget,
]

TOOLS_BY_NAME = {t.name: t for t in ALL_TOOLS}
