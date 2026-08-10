#!/usr/bin/env bash
echo "===== 容器状态 ====="
docker ps --format '{{.Names}} | {{.Status}}'
echo ""
echo "===== app 最近日志 ====="
APP_ID=$(docker ps --format '{{.ID}} {{.Names}}' | grep 'travel-java-app' | awk '{print $1}' | head -1)
echo "app 容器 ID: $APP_ID"
docker logs --tail 25 "$APP_ID" 2>&1 | grep -vE '^\{"sequenceNumber"' | tail -25
echo ""
echo "===== 重试 Spring 健康（内网直连 3200）====="
sleep 5
curl -s -m 8 http://localhost:3200/actuator/health | head -c 200
echo ""
