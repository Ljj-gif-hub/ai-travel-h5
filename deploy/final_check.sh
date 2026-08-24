#!/bin/bash
echo '========== 1. 全部容器 =========='
docker ps --format '{{.Names}} | {{.Status}}'

echo ''
echo '========== 2. 公共健康 =========='
curl -s -m 8 http://8.148.223.54/actuator/health; echo

echo ''
echo '========== 3. 安全头（根 / 上传 / 静态资源）=========='
echo '--- 根 ---'
curl -s -m 8 -D - -o /dev/null http://8.148.223.54/ | grep -iE 'x-content-type|x-frame-options|referrer-policy' || echo '(缺)'
echo '--- /uploads/ ---'
curl -s -m 8 -D - -o /dev/null http://8.148.223.54/uploads/ | grep -iE 'x-content-type|x-frame-options|referrer-policy' || echo '(缺)'
echo '--- 一个真实资产 ---'
JS=$(curl -s -m 8 http://8.148.223.54/ | grep -oE 'assets/index-[A-Za-z0-9_-]+\.js' | head -1)
curl -s -m 8 -D - -o /dev/null "http://8.148.223.54/$JS" | grep -iE 'x-content-type|cache-control' || echo '(缺)'

echo ''
echo '========== 4. Redis 密码 + RabbitMQ 队列 =========='
docker exec travel-redis redis-cli -a "$(grep '^REDIS_PASSWORD=' /opt/bundle/travel-java/.env | cut -d= -f2-)" ping 2>/dev/null | tail -1
docker exec travel-rabbitmq rabbitmqctl list_queues name 2>/dev/null

echo ''
echo '========== 5. app 错误日志（近 100 行，排除 chat/stream 已知 500）=========='
docker logs --tail 100 travel-java-app-1 2>&1 | grep -iE '"level":"ERROR"' | grep -v 'chat/stream' | tail -5 || echo '无 ERROR ✓'
