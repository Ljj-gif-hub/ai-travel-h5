#!/bin/bash
# V3 迁移（prod MySQL）：备份 → 执行 → 验证
# 幂等：V3.sql 的 CREATE TABLE IF NOT EXISTS 可重跑；ALTER 重复执行会报 1060，
#       用 --force 让 mysql 跳过报错继续（1060 错误被过滤展示），
#       最终以第 3 步验证结果为准。
set -u
cd /opt/bundle/travel-java

PW=$(grep '^MYSQL_ROOT_PASSWORD=' .env | cut -d= -f2-)
PW=${PW:-travel_root_2026}

echo "== 1) 备份（pre_v3_时间戳.sql.gz）=="
docker exec travel-mysql mysqldump -uroot -p"$PW" --single-transaction travel_plans 2>/dev/null \
  | gzip > /opt/backups/mysql/pre_v3_$(date +%Y%m%d_%H%M).sql.gz \
  && ls -lh /opt/backups/mysql/pre_v3_*.sql.gz | tail -1 \
  || { echo "[FAIL] 备份失败，中止迁移"; exit 1; }

echo
echo "== 2) 执行 V3.sql =="
docker exec -i travel-mysql mysql -uroot -p"$PW" --force travel_plans < V3.sql 2>&1 \
  | grep -v "^ERROR 1060" || true

echo
echo "== 3) 验证 =="
docker exec travel-mysql mysql -uroot -p"$PW" -N travel_plans -e "
SELECT CONCAT('[TABLE ] ', table_name) FROM information_schema.tables
  WHERE table_schema='travel_plans' AND table_name IN
  ('trip_shares','trip_templates','reports','refunds','invoices','note_collections');
SELECT CONCAT('[COLUMN] ', table_name, '.', column_name) FROM information_schema.columns
  WHERE table_schema='travel_plans'
  AND ((table_name='users' AND column_name='points')
    OR (table_name IN ('notes','posts','comments') AND column_name='hidden'));
"
