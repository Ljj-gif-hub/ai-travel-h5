#!/bin/bash
# V4 迁移（prod MySQL）：async_audit.event_id 唯一约束（MQ-1 幂等兜底）
# 幂等：V4__mq_audit_event_id_uk.sql 内用 information_schema.statistics 守卫，重复执行不报错
set -u
cd /opt/bundle/travel-java

PW=$(grep '^MYSQL_ROOT_PASSWORD=' .env | cut -d= -f2-)
PW=${PW:-travel_root_2026}
SQL=src/main/resources/db/migration/V4__mq_audit_event_id_uk.sql

echo "== 1) 备份（pre_v4_时间戳.sql.gz）=="
docker exec travel-mysql mysqldump -uroot -p"$PW" --single-transaction travel_plans 2>/dev/null \
  | gzip > /opt/backups/mysql/pre_v4_$(date +%Y%m%d_%H%M).sql.gz \
  && ls -lh /opt/backups/mysql/pre_v4_*.sql.gz | tail -1 \
  || { echo "[FAIL] 备份失败，中止迁移"; exit 1; }

echo
echo "== 2) 执行 V4__mq_audit_event_id_uk.sql =="
docker exec -i travel-mysql mysql -uroot -p"$PW" travel_plans < "$SQL"
echo "exit=$?"

echo
echo "== 3) 验证唯一约束 =="
docker exec travel-mysql mysql -uroot -p"$PW" -N travel_plans -e "
SELECT CONCAT('[INDEX] ', index_name) FROM information_schema.statistics
  WHERE table_schema='travel_plans' AND table_name='async_audit' AND index_name='uk_async_audit_event_id'
  GROUP BY index_name;
SELECT CONCAT('[DUPLICATE event_id rows] ', COUNT(*)) FROM (
  SELECT event_id FROM async_audit GROUP BY event_id HAVING COUNT(*) > 1
) t;
"
