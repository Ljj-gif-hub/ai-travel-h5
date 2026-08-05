"""
知识库层 — 旅游攻略 RAG 检索

架构定位（Agent 闭环工作流中的「知识库层」）：
  接收请求 → 感知输入 → 【本地攻略知识检索】→ 注入规划 Prompt → 结果输出

- 内置语料：agent/knowledge/<city>.md（可版本控制），启动时加载并按 `## 小节` 分块
- 向量化：纯 Python 字符级 bigram + TF-IDF + 加权匹配（零新依赖，
  适配 venv 无 numpy/jieba、DeepSeek 无 embedding 端点的现状）
- 预留接口：KnowledgeProvider 抽象，未来外部语料库/语义检索实现同一接口即可接入
  （env KNOWLEDGE_SOURCE=builtin|remote + KNOWLEDGE_REMOTE_URL）

设计模仿 memory.py：类 + 模块级单例 knowledge_store，build_* 产出可注入 prompt 的字符串。
"""
from __future__ import annotations

import math
import os
import re
import threading
from pathlib import Path
from typing import Dict, List, Optional

# 内置攻略语料目录（放在 agent/ 下以支持版本控制；data/ 被 gitignore 不适合放语料）
KNOWLEDGE_DIR = Path(__file__).parent / "knowledge"


def _bigrams(text: str) -> Dict[str, int]:
    """字符级 bigram 词频统计 — 零依赖的中文"分词"（对检索场景足够）"""
    text = re.sub(r"[\s　]+", "", (text or "").lower())
    freq: Dict[str, int] = {}
    for i in range(len(text) - 1):
        g = text[i:i + 2]
        freq[g] = freq.get(g, 0) + 1
    return freq


class Chunk:
    """知识块：一篇攻略文档按 `## 小节` 切出的一块"""

    def __init__(self, title: str, content: str, city: str, source: str):
        self.title = title
        self.content = content
        self.city = city
        self.source = source

    def to_dict(self) -> dict:
        return {"title": self.title, "content": self.content, "city": self.city, "source": self.source}


class KnowledgeProvider:
    """知识库检索抽象接口 — planner 只依赖此接口，不感知具体数据源"""

    source_name = "知识库"

    def retrieve(self, destination: str, query: str = "", top_k: int = 3) -> List[Chunk]:
        raise NotImplementedError

    def build_context(self, destination: str, query: str = "", top_k: int = 3) -> str:
        """产出可注入规划 prompt 的字符串（对称 memory.build_user_context）"""
        chunks = self.retrieve(destination, query, top_k)
        if not chunks:
            return ""
        lines = [f"## 本地攻略参考（来源：{self.source_name}）"]
        for c in chunks:
            snippet = c.content.strip().replace("\n", " ")
            lines.append(f"- 【{c.city}·{c.title}】{snippet[:260]}")
        return "\n".join(lines) + "\n"


class BuiltinGuideProvider(KnowledgeProvider):
    """内置攻略语料 + 字符级 bigram TF-IDF 检索"""

    source_name = "内置旅游攻略知识库"

    def __init__(self, knowledge_dir: Path = KNOWLEDGE_DIR):
        self._lock = threading.Lock()
        self._chunks: List[Chunk] = []
        self._index: List[Dict[str, int]] = []      # 每块词频
        self._idf: Dict[str, float] = {}            # 全库逆文档频率
        self._load(knowledge_dir)

    # ---------- 加载与建索引 ----------

    def _load(self, knowledge_dir: Path) -> None:
        if not knowledge_dir.exists():
            return
        for md in sorted(knowledge_dir.glob("*.md")):
            city = md.stem
            title: Optional[str] = None
            parts: List[str] = []
            for line in md.read_text(encoding="utf-8").splitlines():
                stripped = line.strip()
                if stripped.startswith("## "):
                    if title is not None and parts:
                        self._chunks.append(Chunk(title, "\n".join(parts), city, md.name))
                    title = stripped[3:].strip()
                    parts = []
                elif stripped:
                    parts.append(stripped)
            if title is not None and parts:
                self._chunks.append(Chunk(title, "\n".join(parts), city, md.name))
        self._build_index()

    def _build_index(self) -> None:
        doc_freq: Dict[str, int] = {}
        for chunk in self._chunks:
            tf = _bigrams(chunk.title + " " + chunk.content)
            self._index.append(tf)
            for g in tf:
                doc_freq[g] = doc_freq.get(g, 0) + 1
        n = max(len(self._chunks), 1)
        # 平滑 IDF：避免未登录词 w=0
        self._idf = {g: math.log(n / (1 + f)) + 1 for g, f in doc_freq.items()}

    # ---------- 检索 ----------

    def _score(self, q_bigrams: Dict[str, int], tf: Dict[str, int]) -> float:
        """查询词加权 TF（query 词 idf 加权 × 文档 1+log(tf)），用于排序足够"""
        score = 0.0
        for g in q_bigrams:
            if g in tf:
                score += self._idf.get(g, 1.0) * (1 + math.log(tf[g]))
        return score

    def _dest_match(self, city: str, destination: str) -> bool:
        if not destination:
            return False
        return city == destination or city in destination or destination in city

    def retrieve(self, destination: str, query: str = "", top_k: int = 3) -> List[Chunk]:
        with self._lock:
            dest = (destination or "").strip()
            q_bigrams = _bigrams(query or "")
            scored = []
            for i, chunk in enumerate(self._chunks):
                s = self._score(q_bigrams, self._index[i])
                if self._dest_match(chunk.city, dest):
                    # 目的地匹配加权 + 保底（空 query 时也能命中目标城市攻略）
                    s = s * 2.0 + 1.0
                if s > 0:
                    scored.append((s, chunk))
            scored.sort(key=lambda x: -x[0])
            return [c for _, c in scored[:top_k]]


class RemoteCorpusProvider(KnowledgeProvider):
    """预留：对接未来外部语料库/语义检索服务（HTTP）。
    未配置 KNOWLEDGE_REMOTE_URL 时静默降级返回空，不阻断主流程。"""

    source_name = "外部语料库"

    def __init__(self, base_url: str = ""):
        self.base_url = base_url or os.getenv("KNOWLEDGE_REMOTE_URL", "")

    def retrieve(self, destination: str, query: str = "", top_k: int = 3) -> List[Chunk]:
        if not self.base_url:
            return []
        # TODO: 调用外部语料库检索接口（HTTP），返回 Chunk 列表
        return []

    def build_context(self, destination: str, query: str = "", top_k: int = 3) -> str:
        return ""


# ==================== 工厂 + 全局单例 ====================

def create_knowledge_provider() -> KnowledgeProvider:
    """按 env 选择知识库实现：builtin（默认）| remote"""
    if os.getenv("KNOWLEDGE_SOURCE", "builtin").strip().lower() == "remote":
        return RemoteCorpusProvider()
    return BuiltinGuideProvider()


# 全局单例 — planner 各阶段共享同一个知识库检索器
knowledge_store = create_knowledge_provider()
