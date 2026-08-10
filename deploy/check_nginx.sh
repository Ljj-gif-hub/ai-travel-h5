#!/usr/bin/env bash
echo "===== 部署的 nginx.conf（健康检查段 + 关键段）====="
cat /opt/bundle/travel-java/deploy/nginx.conf 2>/dev/null
echo ""
echo "===== nginx 容器挂载的 conf（实际生效）====="
NGX_ID=$(docker ps --format '{{.ID}} {{.Names}}' | grep 'travel-nginx' | awk '{print $1}' | head -1)
docker exec "$NGX_ID" cat /etc/nginx/conf.d/default.conf 2>/dev/null | grep -nA6 'actuator/health'
echo ""
echo "===== 精确测试 /actuator/health 直连 vs 经 nginx ====="
echo "--- 直连 3200 ---"
curl -s -m 8 -i http://localhost:3200/actuator/health | head -12
echo "--- 经 nginx 80 ---"
curl -s -m 8 -i http://localhost/actuator/health | head -12
