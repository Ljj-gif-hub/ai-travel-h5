"""
agent.tools 单元测试：
  - get_commute_info 公交距离三种输入（distance / walking_distance 回退 / 均缺失）
  - calculate_budget 基本校验与不回显输入

运行前提：pip install pytest（项目 requirements 未包含 pytest）
    python -m pytest tests -q
"""
import json

from agent import tools


class _FakeResp:
    def __init__(self, data):
        self._data = data

    def raise_for_status(self):
        pass

    def json(self):
        return self._data


class _FakeHttpClient:
    """替换 tools._HTTP_CLIENT：geocode 固定成功，路径规划返回构造的 route_data。"""

    def __init__(self, route_data):
        self.route_data = route_data

    def get(self, url, params=None):
        if "geocode" in url:
            return _FakeResp({"status": "1", "geocodes": [{"location": "104.06,30.57"}]})
        return _FakeResp({"status": "1", "route": self.route_data})


def _commute_result(route, monkeypatch):
    monkeypatch.setenv("AMAP_WEB_KEY", "test-key")
    monkeypatch.setattr(tools, "_HTTP_CLIENT", _FakeHttpClient(route), raising=False)
    out = tools.get_commute_info.invoke({
        "origin": "景点A", "destination": "景点B", "mode": "公交",
    })
    return json.loads(out)


def test_commute_transit_uses_distance(monkeypatch):
    out = _commute_result(
        {"transits": [{"distance": 8000, "duration": 1800, "walking_distance": 1200}]},
        monkeypatch,
    )
    assert out["distance_km"] == 8.0
    assert out["duration_min"] == 30


def test_commute_transit_falls_back_to_walking_distance(monkeypatch):
    # 缺 distance：回退 walking_distance，且不再硬编码 +5000
    out = _commute_result(
        {"transits": [{"duration": 1800, "walking_distance": 1200}]},
        monkeypatch,
    )
    assert out["distance_km"] == 1.2


def test_commute_transit_no_distance_returns_zero(monkeypatch):
    # distance 与 walking_distance 均缺失：不再 int(None) 崩溃，距离为 0
    out = _commute_result(
        {"transits": [{"duration": 1800}]},
        monkeypatch,
    )
    assert out["distance_km"] == 0.0


def test_calculate_budget_basic():
    out = json.loads(tools.calculate_budget.invoke({"items_json": json.dumps({
        "budget_total": 5000, "people": 2, "days": 3,
        "items": {"transport": 1500, "accommodation": 1800, "food": 900, "tickets": 600, "shopping": 500},
    })}))
    assert out["actual_total"] == 5300
    assert out["budget_total"] == 10000
    assert out["is_over_budget"] is False


def test_calculate_budget_over():
    out = json.loads(tools.calculate_budget.invoke({"items_json": json.dumps({
        "budget_total": 1000, "people": 1, "days": 3,
        "items": {"transport": 500, "accommodation": 800, "food": 300, "tickets": 0, "shopping": 0},
    })}))
    assert out["is_over_budget"] is True
    assert out["gap"] == 600
    assert out["suggestions"]


def test_calculate_budget_bad_json_does_not_echo_input():
    out = json.loads(tools.calculate_budget.invoke({"items_json": "SENSITIVE{not-json"}))
    assert "error" in out
    assert "input" not in out  # 不回显原始输入
