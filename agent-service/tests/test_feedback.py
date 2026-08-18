"""
agent.memory 用户反馈单元测试：
  - 存取与最近 N 条顺序（旧 → 新）
  - 每用户容量上限（_FEEDBACK_MAX，超出淘汰最旧）
  - 随记忆持久化（重载后仍在）
  - 按 user_id 隔离
"""
from agent.memory import MemoryStore, _FEEDBACK_MAX


def test_feedback_store_and_recent_order(tmp_path):
    store = MemoryStore(path=str(tmp_path / "m.json"))
    for i in range(5):
        store.add_feedback("u1", {"rating": 4, "comment": f"第{i}次"})
    recent = store.recent_feedback("u1", 3)
    assert [f["comment"] for f in recent] == ["第2次", "第3次", "第4次"]


def test_feedback_cap_per_user(tmp_path):
    store = MemoryStore(path=str(tmp_path / "m.json"))
    for i in range(_FEEDBACK_MAX + 5):
        store.add_feedback("u1", {"rating": 4, "comment": f"第{i}次"})
    bucket = store.recent_feedback("u1", 100)
    assert len(bucket) == _FEEDBACK_MAX
    assert bucket[0]["comment"] == "第5次"  # 最旧的被淘汰
    assert bucket[-1]["comment"] == f"第{_FEEDBACK_MAX + 4}次"


def test_feedback_persists_across_reload(tmp_path):
    path = tmp_path / "m.json"
    store = MemoryStore(path=str(path))
    store.add_feedback("u1", {"rating": 5, "comment": "很棒"})
    reloaded = MemoryStore(path=str(path))
    assert reloaded.recent_feedback("u1", 3) == [{"rating": 5, "comment": "很棒"}]


def test_feedback_isolated_by_user(tmp_path):
    store = MemoryStore(path=str(tmp_path / "m.json"))
    store.add_feedback("u1", {"rating": 1, "comment": "差"})
    assert store.recent_feedback("u2", 3) == []
    assert store.recent_feedback("", 3) == []
