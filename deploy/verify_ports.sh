#!/usr/bin/env bash
echo "===== 1. 端口绑定现状 ====="
ss -tlnp 2>/dev/null | grep -E ':(80|3200|3201)\b' | awk '{print $4, $6}'

echo ""
echo "===== 2. nginx 公网入口（:80 → app）====="
curl -s -m 8 -o /dev/null -w "GET / -> HTTP %{http_code}\n" http://localhost/
curl -s -m 8 http://localhost/actuator/health | head -c 200
echo ""
echo "===== 3. 经 nginx → Spring → Agent 透传链路 ====="
curl -s -m 10 http://localhost/api/agent/health | head -c 220
echo ""
echo "===== 4. Spring 应用本身（内网直连 3200）健康 ====="
curl -s -m 8 http://localhost:3200/actuator/health | head -c 120
echo ""
