"""
agent.memory 单元测试：
  - 原子写（临时文件 + os.replace）后数据一致、无 .tmp 残留
  - dirty 标记：数据未实际变化时不重写磁盘
  - 调研缓存 LRU 容量上限

运行前提：pip install pytest（项目 requirements 未包含 pytest）
    python -m pytest tests -q
"""
import json

from agent.memory import MemoryStore, _RESEARCH_CACHE_MAX


def test_atomic_write_and_reload(tmp_path):
    path = tmp_path / "agent_memory.json"
    store = MemoryStore(path=str(path))
    store.set_user("u1", {"preference_text": "喜欢美食"})
    store.set_session("s1", {"destination": "成都", "days": 3})

    # 原子写：无 .tmp 残留，落盘文件为完整合法 JSON
    assert not (tmp_path / "agent_memory.json.tmp").exists()
    assert path.exists()
    data = json.loads(path.read_text(encoding="utf-8"))
    assert data["users"]["u1"]["preference_text"] == "喜欢美食"

    # 重新加载后数据一致
    reloaded = MemoryStore(path=str(path))
    assert reloaded.get_user("u1") == {"preference_text": "喜欢美食"}
    assert reloaded.get_session("s1")["destination"] == "成都"


def test_dirty_skip_rewrite(tmp_path):
    path = tmp_path / "agent_memory.json"
    store = MemoryStore(path=str(path))
    store.set_user("u1", {"preference_text": "A"})
    mtime1 = path.stat().st_mtime_ns

    # 相同数据再次写入：dirty 检测应跳过磁盘写（mtime 不变）
    store.set_user("u1", {"preference_text": "A"})
    assert path.stat().st_mtime_ns == mtime1

    # 数据实际变化时才落盘
    store.set_user("u1", {"preference_text": "B"})
    assert path.stat().st_mtime_ns != mtime1
    assert MemoryStore(path=str(path)).get_user("u1")["preference_text"] == "B"


def test_research_cache_lru_cap(tmp_path):
    store = MemoryStore(path=str(tmp_path / "m.json"))
    for i in range(_RESEARCH_CACHE_MAX + 10):
        store.set_research(f"city{i}", 3, {"summary": str(i)})
    assert len(store._research_cache) <= _RESEARCH_CACHE_MAX
    # 最早写入的条目已被淘汰，最新的仍在
    assert store.get_research("city0", 3) is None
    assert store.get_research(f"city{_RESEARCH_CACHE_MAX + 9}", 3) is not None


def test_load_corrupt_file_falls_back(tmp_path):
    path = tmp_path / "agent_memory.json"
    path.write_text("{not json", encoding="utf-8")
    store = MemoryStore(path=str(path))
    assert store.get_user("u1") == {}
    assert store.get_session("s1") == {}
