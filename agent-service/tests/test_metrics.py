"""
agent.metrics 单元测试：计数器随打点递增。

用独立 label 组合避免用例之间/与真实运行互相干扰。
"""
from agent.metrics import HTTP_REQUESTS, LLM_CALLS, LLM_TOKENS, TOOL_CALLS


def test_llm_calls_counter_increments():
    labels = {"model": "test-model", "phase": "test-phase"}
    before = LLM_CALLS.labels(**labels)._value.get()
    LLM_CALLS.labels(**labels).inc()
    LLM_CALLS.labels(**labels).inc(2)
    assert LLM_CALLS.labels(**labels)._value.get() == before + 3


def test_llm_tokens_counter_increments():
    labels = {"model": "test-model-token"}
    before = LLM_TOKENS.labels(**labels)._value.get()
    LLM_TOKENS.labels(**labels).inc(123)
    assert LLM_TOKENS.labels(**labels)._value.get() == before + 123


def test_tool_calls_counter_increments():
    labels = {"tool": "test-tool"}
    before = TOOL_CALLS.labels(**labels)._value.get()
    TOOL_CALLS.labels(**labels).inc()
    assert TOOL_CALLS.labels(**labels)._value.get() == before + 1


def test_http_requests_counter_increments():
    labels = {"method": "TEST", "path": "/test-path", "status": "200"}
    before = HTTP_REQUESTS.labels(**labels)._value.get()
    HTTP_REQUESTS.labels(**labels).inc()
    assert HTTP_REQUESTS.labels(**labels)._value.get() == before + 1
