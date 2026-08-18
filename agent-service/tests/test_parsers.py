"""
agent.parsers.diff_plans 单元测试：
  - 顶层字段 新增 / 删除 / 修改
  - 相同内容无差异、未变化字段不输出
  - 超长值摘要截断（100 + 省略号）
  - 非 dict 输入返回空列表
"""
from agent.parsers import diff_plans


def test_diff_plans_basic():
    a = {"destination": "成都", "days": 3, "budget_detail": {"total": 5000}}
    b = {"destination": "成都", "days": 4, "hotels": [{"name": "H"}]}
    diffs = diff_plans(a, b)
    by_key = {d["key"]: d for d in diffs}
    # 修改
    assert by_key["days"] == {"key": "days", "action": "modified", "before": "3", "after": "4"}
    # 删除 / 新增
    assert by_key["budget_detail"]["action"] == "removed"
    assert by_key["budget_detail"]["before"] == '{"total": 5000}'
    assert by_key["hotels"]["action"] == "added"
    # 未变化字段不输出
    assert "destination" not in by_key


def test_diff_plans_same_content_returns_empty():
    a = {"destination": "成都", "days": 3, "day_plans": [{"day": 1}]}
    assert diff_plans(a, dict(a)) == []


def test_diff_plans_truncates_long_values():
    a = {"overview": ""}
    b = {"overview": "长" * 300}
    diffs = diff_plans(a, b)
    assert diffs[0]["action"] == "modified"
    after = diffs[0]["after"]
    assert len(after) <= 101  # 100 字符 + 省略号
    assert after.endswith("…")


def test_diff_plans_non_dict_returns_empty():
    assert diff_plans("not-a-dict", {"a": 1}) == []
    assert diff_plans({"a": 1}, None) == []
    assert diff_plans(None, None) == []
