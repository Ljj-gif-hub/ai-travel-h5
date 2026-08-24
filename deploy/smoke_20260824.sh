#!/bin/bash
# 2026-08-24 部署冒烟：fail-closed 凭据 + 安全头 + MQ DLX + 配额路径 + 前端产物
ENV_FILE=/opt/bundle/travel-java/.env
BASE=http://8.148.223.54
echo '========== 1. 公共健康 + 容器 =========='
curl -s -m 8 $BASE/actuator/health; echo
docker ps --format '{{.Names}} | {{.Status}}' | grep -E 'app|nginx|agent|redis|rabbit|mysql'

echo ''
echo '========== 2. 安全响应头（本次新增） =========='
curl -s -m 8 -D - -o /dev/null $BASE/ | grep -iE 'x-content-type|x-frame-options|referrer-policy'
echo '--- 上传 location 的安全头 ---'
curl -s -m 8 -D - -o /dev/null $BASE/assets/ 2>/dev/null | grep -iE 'x-content-type|x-frame-options|referrer-policy' | head -3

echo ''
echo '========== 3. Redis requirepass 生效 =========='
RDPW=$(grep '^REDIS_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)
echo '无密码 ping（应报 NOAUTH）:'
docker exec travel-redis redis-cli ping 2>&1 | head -1
echo "带密码 ping（应 PONG）:"
docker exec travel-redis redis-cli -a "$RDPW" ping 2>&1 | tail -1

echo ''
echo '========== 4. app 容器新 env 注入 =========='
docker inspect travel-java-app-1 --format '{{range .Config.Env}}{{println .}}{{end}}' | grep -E '^(REDIS_PASSWORD|RABBITMQ_USERNAME|RABBITMQ_PASSWORD|DB_PASSWORD)=' | sed -E 's/(=.*)/=<已注入>/'

echo ''
echo '========== 5. RabbitMQ 队列（含新增 DLX/DLQ） =========='
docker exec travel-rabbitmq rabbitmqctl list_queues name 2>/dev/null | head -12

echo ''
echo '========== 6. app 启动日志错误扫描（重点 Redis/MQ 认证） =========='
docker logs --tail 60 travel-java-app-1 2>&1 | grep -iE 'ERROR|Exception|AUTH|access.*denied|Refused' | head -8 || echo '无错误日志 ✓'

echo ''
echo '========== 7. 登录 + 配额路径链路（值不回显） =========='
ADMIN_PW=$(grep '^ADMIN_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)
LOGIN=$(curl -s -m 15 -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin\",\"password\":\"$ADMIN_PW\"}")
TOKEN=$(echo "$LOGIN" | grep -oE '"token":"[^"]+"' | head -1 | sed 's/"token":"//;s/"//')
if [ -z "$TOKEN" ]; then
  echo "!! 登录失败（拿不到 token）。登录响应片段："
  echo "$LOGIN" | head -c 200; echo
else
  echo "✅ 登录成功（token 已获取，不显示）"
  for PATH_NAME in "chat/stream" "recommend"; do
    CODE=$(curl -s -m 10 -o /dev/null -w '%{http_code}' -X POST "$BASE/api/travel/$PATH_NAME" \
      -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}')
    echo "  POST /api/travel/$PATH_NAME → HTTP $CODE（非500即配额拦截器正常放行）"
  done
fi

echo ''
echo '========== 8. 前端新构建产物 =========='
curl -s -m 8 $BASE/ | grep -oE 'assets/index-[A-Za-z0-9_-]+\.js' | head -1
IDX_JS=$(curl -s -m 8 $BASE/ | grep -oE 'assets/index-[A-Za-z0-9_-]+\.js' | head -1)
curl -s -m 8 -o /dev/null -w "主 chunk: HTTP %{http_code}\n" "$BASE/$IDX_JS"
