"""
agent.llm_router 单元测试：
  - 无 fallback：直连 primary
  - 瞬态异常降级 fallback，primary 恢复后切回
  - 连续 3 次失败 → primary 冷却（期间不再尝试 primary）
  - 非瞬态异常不降级（直接上抛）
  - bind_tools 派生实例共享冷却状态

注：项目测试环境未装 pytest-asyncio，异步用例用 asyncio.run 直接驱动。
"""
import asyncio

from agent.llm_router import LLMRouter, _is_transient


class _FakeModel:
    def __init__(self, name, fail_times=0, exc=None):
        self.model_name = name
        self.fail_times = fail_times
        self.exc = exc
        self.calls = 0

    async def ainvoke(self, *args, **kwargs):
        self.calls += 1
        if self.calls <= self.fail_times:
            raise (self.exc or RuntimeError("boom"))
        return _FakeResp(self.model_name)

    def invoke(self, *args, **kwargs):
        self.calls += 1
        if self.calls <= self.fail_times:
            raise (self.exc or RuntimeError("boom"))
        return _FakeResp(self.model_name)


class _FakeResp:
    def __init__(self, text):
        self.content = text
        self.usage_metadata = {"input_tokens": 10, "output_tokens": 5}


def test_router_no_fallback_passthrough():
    async def _go():
        primary = _FakeModel("primary-m")
        router = LLMRouter(primary, fallback=None, phase="test")
        resp = await router.ainvoke(["hi"])
        assert resp.content == "primary-m"

    asyncio.run(_go())


def test_router_falls_back_on_transient_error_then_recovers():
    async def _go():
        primary = _FakeModel("primary-m", fail_times=1, exc=TimeoutError("timed out"))
        fallback = _FakeModel("fallback-m")
        router = LLMRouter(primary, fallback, phase="test")
        # 第一次：primary 失败 → 降级 fallback
        resp = await router.ainvoke(["hi"])
        assert resp.content == "fallback-m"
        # primary 单次失败不触发冷却，第二次 primary 成功 → 自动恢复
        resp2 = await router.ainvoke(["hi"])
        assert resp2.content == "primary-m"

    asyncio.run(_go())


def test_router_cooldown_after_three_failures():
    async def _go():
        primary = _FakeModel("primary-m", fail_times=999, exc=TimeoutError("timed out"))
        fallback = _FakeModel("fallback-m")
        router = LLMRouter(primary, fallback, phase="test")
        for _ in range(3):
            await router.ainvoke(["hi"])  # 每次都 primary 失败 → fallback 成功
        # 第 3 次失败后触发 60s 冷却：后续调用直接走 fallback，不再尝试 primary
        primary.calls = 0
        resp = await router.ainvoke(["hi"])
        assert resp.content == "fallback-m"
        assert primary.calls == 0
        assert fallback.calls == 4

    asyncio.run(_go())


def test_router_non_transient_error_raises():
    async def _go():
        primary = _FakeModel("primary-m", fail_times=999, exc=ValueError("bad param"))
        fallback = _FakeModel("fallback-m")
        router = LLMRouter(primary, fallback, phase="test")
        try:
            await router.ainvoke(["hi"])
        except ValueError:
            pass
        else:
            raise AssertionError("非瞬态异常应直接上抛")
        assert fallback.calls == 0  # 非瞬态异常不降级

    asyncio.run(_go())


def test_is_transient_classification():
    assert _is_transient(TimeoutError("timed out"))
    assert _is_transient(ConnectionError("connection reset"))
    assert not _is_transient(ValueError("bad param"))


def test_bind_tools_shares_state():
    class _Bindable(_FakeModel):
        def bind_tools(self, tools, **kwargs):
            return self

    primary = _Bindable("primary-m")
    fallback = _Bindable("fallback-m")
    router = LLMRouter(primary, fallback, phase="test")
    bound = router.bind_tools(["t1"])
    assert bound is not router
    assert bound._state is router._state  # 派生实例共享冷却状态
    assert bound.primary is primary and bound.fallback is fallback
