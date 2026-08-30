# -*- coding: utf-8 -*-
"""从线上服务器读取有效的高德 Web 服务 Key，写回本地两个 .env（不输出到 stdout/日志）。
用法: python fetch_amap_key_local.py
覆盖: travel-java/.env 与 agent-service/.env 的 AMAP_WEB_KEY
"""
import os
import sys
import paramiko

HOST = "8.148.223.54"
USER = "root"
KEY = os.path.join(os.path.expanduser("~"), ".ssh", "id_ed25519")

ENV_FILES = [
    r"H:\ai项目\project\ai-travel-project\travel-java\.env",
    r"H:\ai项目\project\ai-travel-project\agent-service\.env",
]


def get_remote_key():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(hostname=HOST, port=22, username=USER,
              pkey=paramiko.Ed25519Key.from_private_key_file(KEY),
              look_for_keys=False, allow_agent=False, timeout=25)
    # 遍历 app 与 agent-service 容器，取第一个非空 AMAP_WEB_KEY
    cmd = (
        "for c in $(docker ps --format '{{.Names}}' | grep -E 'app|agent'); do "
        "v=$(docker exec \"$c\" printenv AMAP_WEB_KEY 2>/dev/null); "
        "if [ -n \"$v\" ] && [ ${#v} -ge 16 ]; then echo \"$v\"; exit 0; fi; done; exit 1"
    )
    _, out, err = c.exec_command(cmd, timeout=30)
    value = out.read().decode("utf-8", "replace").strip()
    code = out.channel.recv_exit_status()
    c.close()
    if code != 0 or not value:
        raise RuntimeError("线上未取到有效 AMAP_WEB_KEY")
    return value


def update_env(path, key, value):
    with open(path, "r", encoding="utf-8") as f:
        lines = f.read().splitlines()
    out, found = [], False
    for line in lines:
        if line.startswith("AMAP_WEB_KEY=") or line.startswith("AMAP_WEB_KEY ="):
            out.append(f"AMAP_WEB_KEY={value}")
            found = True
        else:
            out.append(line)
    if not found:
        out.append(f"AMAP_WEB_KEY={value}")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(out) + "\n")
    return found


if __name__ == "__main__":
    key = get_remote_key()
    # 本地校验：确认真是有效 Web key（只打印判定，不打印 key）
    import urllib.request
    url = ("https://restapi.amap.com/v3/place/text?keywords=%E6%95%85%E5%AE%AB"
           f"&key={key}&offset=1&page=1&extensions=all&city=%E5%8C%97%E4%BA%AC")
    with urllib.request.urlopen(url, timeout=10) as r:
        import json
        j = json.loads(r.read().decode("utf-8"))
    if str(j.get("status")) != "1":
        sys.exit(f"AMap 校验失败 status={j.get('status')} info={j.get('info')}")
    for p in ENV_FILES:
        update_env(p, "AMAP_WEB_KEY", key)
        print(f"updated: {os.path.basename(os.path.dirname(p))}/.env (len={len(key)})")
    print("AMap 校验通过 status=1")
