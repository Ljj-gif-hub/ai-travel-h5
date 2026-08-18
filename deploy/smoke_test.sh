#!/bin/bash
# 部署后冒烟测试（在服务器 /opt/bundle/travel-java 下执行）
# 用法：ssh_tool.py --upload deploy/smoke_test.sh --remote /opt/bundle/smoke_test.sh
#       ssh_tool.py --run 'bash /opt/bundle/smoke_test.sh'
set -u
cd /opt/bundle/travel-java

echo "== 0) nginx force-recreate（dist 已重建，强制重建容器）=="
docker compose up -d --force-recreate nginx 2>&1 | tail -2
sleep 3

echo
echo "== 1) 后端健康（经 nginx）=="
curl -s --max-time 15 http://localhost/actuator/health; echo

echo
echo "== 2) 管理端登录（验证 refreshToken 双令牌下发）=="
PW=$(grep '^ADMIN_PASSWORD=' .env | cut -d= -f2-)
RESP=$(curl -s --max-time 15 -X POST http://localhost:3200/api/auth/login \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin\",\"password\":\"$PW\"}")
echo "$RESP" | head -c 200; echo
echo "$RESP" | grep -q '"refreshToken":"' && echo "  -> ✅ refreshToken 已下发" || echo "  -> ❌ 未下发 refreshToken"
echo "$RESP" | grep -q '"token":"' && echo "  -> ✅ access token 已下发" || echo "  -> ❌ 未下发 token"
TOKEN=$(echo "$RESP" | grep -o '"token":"[^"]*"' | head -1 | sed 's/"token":"//;s/"$//')

echo
echo "== 3) 新功能公开端点 =="
echo "-- GET /api/template/market（模板市场）:"
curl -s --max-time 15 http://localhost:3200/api/template/market | head -c 400; echo
echo "-- GET /api/weather/北京:"
curl -s --max-time 15 'http://localhost:3200/api/weather/%E5%8C%97%E4%BA%AC' | head -c 400; echo

echo
echo "== 4) agent 健康（/api/agent/health 免鉴权代理）=="
curl -s --max-time 15 http://localhost:3200/api/agent/health | head -c 200; echo

echo
echo "== 5) 鉴权旧功能（GET /api/user/profile）=="
curl -s --max-time 15 -H "Authorization: Bearer $TOKEN" http://localhost:3200/api/user/profile | head -c 300; echo

echo
echo "== 6) 容器状态 =="
docker compose ps --format 'table {{.Name}}\t{{.Status}}'
