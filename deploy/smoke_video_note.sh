#!/bin/bash
# 2026-08-25: 视频游记修复冒烟——API 创建带 <video> 的笔记，读回验证标签存活
# 关键断言：create 后 content 仍含 <video ... src=...>（此前被 sanitizeHtml 剥光）
set -euo pipefail
cd /opt/bundle/travel-java

echo '=== 1. 登录获取 token ==='
ADMIN_PW=$(grep '^ADMIN_PASSWORD=' .env | cut -d= -f2-)
TOKEN=$(curl -s -m 15 -X POST http://localhost/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin\",\"password\":\"$ADMIN_PW\"}" | grep -oE '"token":"[^"]+"' | head -1 | sed 's/"token":"//;s/"//')
echo "token: $([ -n "$TOKEN" ] && echo OK || echo '获取失败')"
[ -n "$TOKEN" ] || exit 1

echo ''
echo '=== 2. 创建带 <video> 标签的游记 ==='
VIDEO_HTML='<p>冒烟测试-视频标签存活</p><video src=\"/uploads/smoke-test.mp4\" controls style=\"width:100%;\"></video>'
NOTE_ID=$(curl -s -m 15 -X POST http://localhost/api/notes \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"title\":\"视频修复冒烟\",\"content\":\"$VIDEO_HTML\"}" | grep -oE '"id":[0-9]+' | head -1 | sed 's/"id"://')
echo "note id: ${NOTE_ID:-获取失败}"
[ -n "$NOTE_ID" ] || exit 1

echo ''
echo '=== 3. 读回该笔记 content，断言 <video> 标签存活 ==='
BACK=$(curl -s -m 15 http://localhost/api/notes/$NOTE_ID -H "Authorization: Bearer $TOKEN")
echo "$BACK" | head -c 400; echo
if echo "$BACK" | grep -q '<video'; then
  echo "✅ PASS: <video> 标签存活（前端 hasVideo 将识别为视频 → 路由 video-detail）"
else
  echo "❌ FAIL: <video> 标签被剥（修复未生效）"
fi
echo "$BACK" | grep -q '/uploads/smoke-test.mp4' && echo "✅ src 保留" || echo "❌ src 丢失"

echo ''
echo '=== 4. 列表接口确认（社区数据源）==='
LIST=$(curl -s -m 15 "http://localhost/api/notes?page=1&size=5" -H "Authorization: Bearer $TOKEN")
echo "$LIST" | grep -o '"content":"[^"]*video[^"]*"' | head -2 | sed 's/^/  /' || echo "  （未在列表前5条找到，可能需翻页）"

echo ''
echo '=== 5. 清理测试数据 ==='
DEL=$(curl -s -m 15 -X DELETE http://localhost/api/notes/$NOTE_ID -H "Authorization: Bearer $TOKEN")
echo "删除结果: $(echo "$DEL" | head -c 120)"
echo '完成'
