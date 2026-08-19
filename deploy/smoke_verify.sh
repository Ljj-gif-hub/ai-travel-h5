#!/usr/bin/env bash
# 部署后冒烟验证
echo '========== 1. 健康检查 =========='
curl -s -m 8 http://8.148.223.54/actuator/health; echo
curl -s -m 8 -o /dev/null -w 'agent health: HTTP %{http_code}\n' http://8.148.223.54/api/agent/health

echo ''
echo '========== 2. index.html 缓存头 + 新 chunk 引用 =========='
curl -s -m 8 -D - -o /tmp/idx.html http://8.148.223.54/ | grep -iE 'HTTP/|cache-control'
echo '--- index.html 引用的主 JS ---'
grep -oE 'assets/[A-Za-z0-9_-]+\.js' /tmp/idx.html | head -5

echo ''
echo '========== 3. 新 chunk 可访问 + immutable =========='
NEW=$(grep -oE 'assets/LazyImage-[A-Za-z0-9_-]+\.js' /tmp/idx.html | head -1)
echo "LazyImage chunk: $NEW"
curl -s -m 8 -o /dev/null -w 'LazyImage: HTTP %{http_code}\n' "http://8.148.223.54/$NEW"
curl -s -m 8 -D - -o /dev/null "http://8.148.223.54/$NEW" | grep -iE 'cache-control'

echo ''
echo '========== 4. 容器状态 =========='
docker ps --format '{{.Names}} | {{.Status}}' | grep -E 'app|nginx|agent'

echo ''
echo '========== 5. app 日志无 ERROR/Exception（最近30行） =========='
docker logs --tail 30 travel-java-app-1 2>&1 | grep -iE 'ERROR|Exception|Caused by' | tail -5 || echo '无错误日志 ✓'
