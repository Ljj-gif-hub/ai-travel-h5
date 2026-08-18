"""Gunicorn 配置 — 可选的多 worker 部署入口（A1，默认保持单进程语义）。

用法一（python main.py 自动切换）：
    在 .env 中设置 GUNICORN_WORKERS=4，然后 python main.py
用法二（命令行）：
    GUNICORN_WORKERS=4 gunicorn -c gunicorn.conf.py main:app

⚠️ 注意：多 worker 前必须先把进程内记忆层迁出。当前 agent/memory.py 的
MemoryStore 是进程内单例 + data/agent_memory.json 单进程独占写，多 worker
并发写同一 JSON 文件会互相覆盖/丢数据。默认 workers=1 与现有单进程行为一致。
"""
import os

bind = f"0.0.0.0:{os.getenv('AGENT_PORT', '3201')}"
workers = int(os.getenv("GUNICORN_WORKERS", "1"))
worker_class = "uvicorn.workers.UvicornWorker"
# 规划接口单次耗时 30-90s+，超时需放宽
timeout = 300
graceful_timeout = 30
