#!/bin/bash
# 2026-08-24: 修复 ①MQ 队列 406（删除旧声明→app 重声明带 DLX） ②nginx 未加载新安全头（force-recreate）
set -euo pipefail
cd /opt/bundle/travel-java

echo "=== 1. 删除前检查 travel.orders 积压 ==="
docker exec travel-rabbitmq rabbitmqctl list_queues name messages 2>/dev/null
sleep 1

echo ""
echo "=== 2. 删除旧声明队列（无 DLX 参数，无法原地升级） ==="
docker exec travel-rabbitmq rabbitmqctl delete_queue travel.orders 2>&1

echo ""
echo "=== 3. 重启 app 触发带 DLX 的重新声明 ==="
docker compose restart app 2>&1 | tail -2
sleep 20

echo ""
echo "=== 4. 验证队列（应出现 travel.orders + travel.orders.dlq） ==="
docker exec travel-rabbitmq rabbitmqctl list_queues name 2>/dev/null

echo ""
echo "=== 5. app 日志：MQ 声明应无 406 ==="
docker logs --tail 40 travel-java-app-1 2>&1 | grep -iE '406|PRECONDITION|Broker not available' | head -5 || echo '无 406 错误 ✓'

echo ""
echo "=== 6. force-recreate nginx 加载新安全头 ==="
docker compose up -d --no-deps --force-recreate nginx 2>&1 | tail -3
sleep 3

echo ""
echo "=== 7. 验证安全响应头 ==="
curl -s -m 8 -D - -o /dev/null http://8.148.223.54/ | grep -iE 'x-content-type|x-frame-options|referrer-policy' || echo '!! 安全头仍未生效'
