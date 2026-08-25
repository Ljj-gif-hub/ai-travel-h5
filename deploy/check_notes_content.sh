#!/bin/bash
# 2026-08-25: 排查社区视频不显示——全量查 notes 表
# 目标：找出所有含视频特征的记录（content/cover 带 video/mp4/webm/mov），并看创建时间
set -euo pipefail
cd /opt/bundle/travel-java
DB_PW=$(grep '^MYSQL_ROOT_PASSWORD=' .env | cut -d= -f2-)

echo '=== 全量 notes：id | title | created_at | cover | content 前 160 字符 ==='
docker compose exec -T mysql mysql -uroot -p"$DB_PW" -N -e "
SELECT id, LEFT(title,24), created_at, LEFT(COALESCE(cover,''),40),
  LEFT(REPLACE(REPLACE(COALESCE(content,''), CHAR(10), ' '), CHAR(13), ' '), 160)
FROM travel_plans.notes ORDER BY id DESC;"

echo ''
echo '=== 含视频特征（video 标签 / mp4|webm|mov 扩展名）的记录 ==='
docker compose exec -T mysql mysql -uroot -p"$DB_PW" -N -e "
SELECT id, LEFT(title,24),
  (content LIKE '%<video%' OR content LIKE '%.mp4%' OR content LIKE '%.webm%' OR content LIKE '%.mov%' OR cover LIKE '%.mp4%' OR cover LIKE '%.webm%' OR cover LIKE '%.mov%') AS has_video_marker
FROM travel_plans.notes ORDER BY id DESC;"

echo ''
echo '=== 总数 ==='
docker compose exec -T mysql mysql -uroot -p"$DB_PW" -N -e "SELECT COUNT(*) FROM travel_plans.notes;"
