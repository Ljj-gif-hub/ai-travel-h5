#!/usr/bin/env bash
echo "=== deploy.sh still running? ==="
ps aux | grep '[d]eploy.sh' | head -2
echo "=== docker ps (travel) ==="
docker ps --format '{{.Names}}  {{.Status}}' | grep -E 'travel-|app'
echo "=== app container started at ==="
docker inspect travel-java-app-1 --format '{{.State.StartedAt}}' 2>/dev/null
echo "=== last log lines ==="
tail -6 /opt/bundle/deploy_20260830.log
