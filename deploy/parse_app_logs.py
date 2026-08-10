#!/usr/bin/env python3
"""解析 travel-java 应用的 JSON 日志，输出 ERROR/Caused by 等关键行"""
import sys, json

path = sys.argv[1] if len(sys.argv) > 1 else "-"
n = int(sys.argv[2]) if len(sys.argv) > 2 else 40

lines = []
for line in sys.stdin if path == "-" else open(path, encoding="utf-8", errors="replace"):
    line = line.strip()
    if not line or "app-1  |" not in line:
        continue
    try:
        rec = json.loads(line.split("|", 1)[1].strip())
    except Exception:
        continue
    lvl = rec.get("level", "?")
    logger = rec.get("loggerName", "")
    msg = rec.get("message", "")
    thr = rec.get("throwable") or {}
    lines.append((lvl, logger, msg, thr.get("message", "")))

# 优先展示 ERROR / WARN / 以及启动结果
important = [x for x in lines if x[0] in ("ERROR", "WARN", "FATAL")]
print(f"共 {len(lines)} 条日志，其中 {len(important)} 条 ERROR/WARN/FATAL：")
for lvl, logger, msg, thr in important[-n:]:
    m = msg[:300]
    print(f"[{lvl}] {logger}: {m}")
    if thr:
        print(f"    ⚠️ 异常: {thr[:500]}")
    print("-" * 60)
