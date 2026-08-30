#!/usr/bin/env bash
echo "=== [1] app health (Started line) ==="
docker logs travel-java-app-1 2>&1 | grep -E 'Started TravelJavaApplication|ERROR|APPLICATION FAILED' | tail -8
echo ""
echo "=== [2] app status / loop-count ==="
docker inspect travel-java-app-1 --format 'RestartCount={{.RestartCount}} Status={{.State.Status}}' 2>/dev/null
echo ""
echo "=== [3] surround-tour (HTTP through nginx :80) ==="
curl -s -o /dev/null -w 'surround-tour HTTP %{http_code}\n' http://127.0.0.1/api/map/surround-tour
echo "=== [4] attraction-images (empty-key graceful) ==="
curl -s -w '\nattraction-images HTTP %{http_code}\n' 'http://127.0.0.1/api/map/attraction-images?city=%E5%8C%97%E4%BA%AC&names=%E6%95%85%E5%AE%AB' | head -c 300
echo ""
echo "=== [5] geocode (AMap real key on server) ==="
curl -s -w '\ngeocode HTTP %{http_code}\n' 'http://127.0.0.1/api/map/geocode?address=%E6%95%85%E5%AE%AB&city=%E5%8C%97%E4%BA%AC' | head -c 300
