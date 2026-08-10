#!/usr/bin/env bash
# ================================================================
# 每日 MySQL 自动备份脚本（服务器端）
# 功能：导出 travel_plans 数据库 → gzip 压缩 → 保留最近 7 天
# 用法：bash /root/mysql_backup.sh
# 定时：crontab 每天凌晨 3:17 执行
# ================================================================
set -euo pipefail

BACKUP_DIR=/opt/backups/mysql
KEEP_DAYS=7
mkdir -p "$BACKUP_DIR"

# 从容器环境变量读取 root 密码（避免明文出现在 ps 命令行）
MYSQL_PWD=$(docker exec travel-mysql printenv MYSQL_ROOT_PASSWORD)

TS=$(date +%Y%m%d_%H%M%S)
OUT="$BACKUP_DIR/travel_plans_${TS}.sql.gz"

# 导出并压缩（--single-transaction 保证备份期间数据一致）
docker exec -e MYSQL_PWD="$MYSQL_PWD" travel-mysql \
  mysqldump -uroot --single-transaction --routines travel_plans | gzip > "$OUT"

# 只保留最近 N 天
find "$BACKUP_DIR" -name 'travel_plans_*.sql.gz' -mtime +"$KEEP_DAYS" -delete

echo "备份完成: $OUT ($(du -h "$OUT" | cut -f1))"
echo "保留策略: 最近 ${KEEP_DAYS} 天"
ls -lh "$BACKUP_DIR"
