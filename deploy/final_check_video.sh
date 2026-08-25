#!/bin/bash
# 2026-08-25 视频修复最终检查
set -euo pipefail
cd /opt/bundle/travel-java

echo '=== 1. 容器状态 ==='
docker compose ps --format '{{.Name}}  {{.Status}}' | head -10

echo ''
echo '=== 2. 前端 dist 是否含 filterXss video 白名单改动 ==='
grep -rl "video" /opt/bundle/trval-h5/dist/assets/*.js 2>/dev/null | head -3
if grep -rq "video" /opt/bundle/trval-h5/dist/assets/*.js 2>/dev/null; then
  echo "dist 已更新（搜索到 video 相关 chunk）"
else
  echo "注意：dist 中未直接搜到 video 字符串（可能被压缩改名）"
fi

echo ''
echo '=== 3. app 最近错误日志（过滤业务噪音）==='
docker compose logs app --since 10m 2>&1 | grep -iE 'ERROR|Exception|Caused by' | grep -viE 'rate.?limit|429|moderation' | tail -5 || echo "（近 10 分钟无 ERROR）"

echo ''
echo '=== 4. 健康检查 ==='
curl -sf http://localhost/actuator/health || echo "健康检查失败"

echo ''
echo '=== 5. 首页可访问 + 新 dist 时间戳 ==='
curl -s -o /dev/null -w "index.html HTTP %{http_code}\n" http://localhost/
ls -la --time-style=+%H:%M /opt/bundle/trval-h5/dist/index.html
