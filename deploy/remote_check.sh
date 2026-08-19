#!/usr/bin/env bash
# 远程部署前置检查
set +e
echo "=== [1] env files ==="
ls -la /opt/bundle/travel-java/.env /opt/bundle/agent-service/.env 2>&1
echo "=== [2] new nginx.conf cache headers ==="
grep -n "Cache-Control\|no-cache\|immutable" /opt/bundle/travel-java/deploy/nginx.conf | head -10
echo "=== [3] disk ==="
df -h / | tail -1
echo "=== [4] docker containers ==="
docker ps --format "{{.Names}}  {{.Status}}" | head -12
echo "=== [5] node/npm ==="
node -v; npm -v
echo "=== [6] new bundle agent/trval timestamps ==="
ls -ld /opt/bundle/travel-java /opt/bundle/agent-service /opt/bundle/trval-h5
