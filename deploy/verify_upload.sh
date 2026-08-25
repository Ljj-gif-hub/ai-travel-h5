#!/bin/bash
# 2026-08-25: 验证视频上传 200MB 上限（nginx force-recreate + 60MB 成功 + 210MB 拒绝）
set -euo pipefail
cd /opt/bundle/travel-java

echo '=== 1. force-recreate nginx 加载 250m ==='
docker compose up -d --no-deps --force-recreate nginx 2>&1 | tail -2
sleep 3
echo "nginx 配置: $(grep client_max_body_size deploy/nginx.conf | tr -d ' ')"

echo ''
echo '=== 2. 生成测试文件（60MB + 210MB）==='
dd if=/dev/zero of=/tmp/test60.mp4 bs=1M count=60 status=none
dd if=/dev/zero of=/tmp/test210.mp4 bs=1M count=210 status=none
ls -la /tmp/test60.mp4 /tmp/test210.mp4 | awk '{print $5, $9}'

echo ''
echo '=== 3. 登录获取 token ==='
ADMIN_PW=$(grep '^ADMIN_PASSWORD=' .env | cut -d= -f2-)
TOKEN=$(curl -s -m 15 -X POST http://localhost/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin\",\"password\":\"$ADMIN_PW\"}" | grep -oE '"token":"[^"]+"' | head -1 | sed 's/"token":"//;s/"//')
echo "token: $([ -n "$TOKEN" ] && echo OK || echo '获取失败')"

echo ''
echo '=== 4. 60MB 视频上传（应成功）==='
R1=$(curl -s -m 120 -X POST http://localhost/api/upload -H "Authorization: Bearer $TOKEN" -F "file=@/tmp/test60.mp4")
echo "$R1" | head -c 200; echo
URL1=$(echo "$R1" | grep -oE '/uploads/[A-Za-z0-9._-]+\.mp4' | head -1)

echo ''
echo '=== 5. 210MB 视频上传（应拒绝并提示 200MB 限制）==='
R2=$(curl -s -m 120 -X POST http://localhost/api/upload -H "Authorization: Bearer $TOKEN" -F "file=@/tmp/test210.mp4")
echo "$R2" | head -c 200; echo

echo ''
echo '=== 6. 清理测试产物 ==='
if [ -n "$URL1" ]; then rm -f "/opt/bundle/travel-java/uploads/$(basename "$URL1")" && echo "已删测试上传: $URL1"; fi
rm -f /tmp/test60.mp4 /tmp/test210.mp4
echo '临时文件已清理'
