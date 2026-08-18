"""
agent.auth 单元测试。

运行前提：pip install pytest（项目 requirements 未包含 pytest）
    python -m pytest tests -q
"""
import pytest

from agent import auth


# ---------- _bearer_token 严格性 ----------

def test_bearer_token_valid():
    assert auth._bearer_token("Bearer abc.def") == "abc.def"
    assert auth._bearer_token("  Bearer  tok ") == "tok"


def test_bearer_token_rejects_nonstandard():
    # 小写/混合大小写 scheme、其他 scheme、裸 token 一律拒绝
    assert auth._bearer_token("bearer tok") is None
    assert auth._bearer_token("BEARER tok") is None
    assert auth._bearer_token("Basic abc") is None
    assert auth._bearer_token("abc.def") is None


def test_bearer_token_rejects_empty():
    assert auth._bearer_token("") is None
    assert auth._bearer_token(None) is None
    assert auth._bearer_token("Bearer") is None
    assert auth._bearer_token("Bearer   ") is None


# ---------- _verify_user_sig 正确/错误签名 ----------

def test_verify_user_sig_correct():
    key = "test-agent-key"
    user_id = "user-123"
    good_sig = auth._hmac_sign(key, user_id)
    assert auth._verify_user_sig(key, user_id, good_sig)
    # 大小写不敏感（网关可能大写 hex）
    assert auth._verify_user_sig(key, user_id, good_sig.upper())


def test_verify_user_sig_wrong():
    key = "test-agent-key"
    user_id = "user-123"
    good_sig = auth._hmac_sign(key, user_id)
    assert not auth._verify_user_sig(key, user_id, "0" * 64)
    assert not auth._verify_user_sig(key, user_id, "")
    # 签名与 user_id 不匹配
    assert not auth._verify_user_sig(key, "user-456", good_sig)
    # 换 key 后签名失效
    assert not auth._verify_user_sig("another-key", user_id, good_sig)
    # 缺失 key/user_id/sig
    assert not auth._verify_user_sig("", user_id, good_sig)
    assert not auth._verify_user_sig(key, "", good_sig)
    assert not auth._verify_user_sig(key, user_id, "")


# ---------- _decode_jwt_user_id 无效 token 返回 None ----------

def test_decode_jwt_user_id_invalid(monkeypatch):
    monkeypatch.setenv("AGENT_JWT_SECRET", "jwt-test-secret")
    monkeypatch.delenv("JWT_SECRET", raising=False)
    assert auth._decode_jwt_user_id("not-a-jwt") is None
    assert auth._decode_jwt_user_id("") is None
    assert auth._decode_jwt_user_id("a.b.c") is None
    # 用错误密钥签发的 token 也应被拒绝
    jwt = pytest.importorskip("jwt")
    forged = jwt.encode({"userId": "u1"}, "wrong-secret", algorithm="HS256")
    assert auth._decode_jwt_user_id(forged) is None


def test_decode_jwt_user_id_no_secret(monkeypatch):
    monkeypatch.setenv("AGENT_JWT_SECRET", "")
    monkeypatch.setenv("JWT_SECRET", "")
    assert auth._decode_jwt_user_id("a.b.c") is None


def test_decode_jwt_user_id_valid(monkeypatch):
    jwt = pytest.importorskip("jwt")
    monkeypatch.setenv("AGENT_JWT_SECRET", "jwt-test-secret")
    monkeypatch.delenv("JWT_SECRET", raising=False)
    token = jwt.encode({"userId": 42}, "jwt-test-secret", algorithm="HS256")
    assert auth._decode_jwt_user_id(token) == "42"
