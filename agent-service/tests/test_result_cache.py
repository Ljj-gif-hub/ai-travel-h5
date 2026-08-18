"""
agent.result_cache 单元测试：
  - 命中 / 未命中
  - TTL 过期（负 TTL 确定性验证，不依赖 sleep）
  - key 归一化（styles 顺序无关）+ 不同 user_id / ui_lang 隔离
  - LRU 容量上限
"""
from agent.result_cache import PlanResultCache, _CACHE_MAX


def _req(**overrides) -> dict:
    req = {
        "destination": "成都",
        "days": 3,
        "budget": 5000,
        "styles": ["美食", "人文"],
        "companion": "情侣",
        "user_id": "u1",
    }
    req.update(overrides)
    return req


def test_cache_hit_and_miss():
    cache = PlanResultCache()
    key = cache.key(_req())
    assert cache.get(key) is None  # 未命中
    cache.set(key, {"destination": "成都", "day_plans": []})
    assert cache.get(key) == {"destination": "成都", "day_plans": []}  # 命中


def test_cache_key_stable_across_styles_order():
    cache = PlanResultCache()
    a = cache.key(_req(styles=["美食", "人文"]))
    b = cache.key(_req(styles=["人文", "美食"]))
    assert a == b
    assert len(a) == 16  # sha256 前 16 位 hex


def test_cache_key_isolated_by_user_and_lang():
    cache = PlanResultCache()
    assert cache.key(_req(user_id="u1")) != cache.key(_req(user_id="u2"))
    # ui_lang 参与 key：不同界面语言不混用缓存
    assert cache.key(_req(ui_lang="zh")) != cache.key(_req(ui_lang="en"))


def test_cache_ttl_expiry():
    # ttl=-1：写入即过期（确定性验证 TTL 分支，无需 sleep）
    cache = PlanResultCache(ttl=-1)
    key = cache.key(_req())
    cache.set(key, {"x": 1})
    assert cache.get(key) is None


def test_cache_lru_cap():
    cache = PlanResultCache(max_size=10)
    for i in range(_CACHE_MAX + 10):
        cache.set(f"key{i}", {"i": i})
    assert len(cache._store) <= 10
    assert cache.get("key0") is None  # 最早写入的已被淘汰
    assert cache.get(f"key{_CACHE_MAX + 9}") == {"i": _CACHE_MAX + 9}  # 最新保留
