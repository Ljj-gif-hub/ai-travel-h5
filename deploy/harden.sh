#!/usr/bin/env bash
# 服务器安全加固：收紧敏感文件权限（先扫描报告，再修复）
set -uo pipefail

echo "========== [1/3] 扫描 /opt 下敏感文件权限 =========="
find /opt -type f \( -name ".env" -o -name "*.env" -o -name ".env.production" -o -name "id_*" -o -name "*.pem" -o -name "*.key" \) 2>/dev/null | while read -r f; do
  echo "$(stat -c '%a %U:%G %n' "$f")"
done

echo ""
echo "========== [2/3] 修复：敏感文件统一收紧到 600（仅 root）=========="
find /opt -type f \( -name ".env" -o -name ".env.production" -o -name "id_*" -o -name "*.pem" -o -name "*.key" \) 2>/dev/null | while read -r f; do
  chmod 600 "$f" 2>/dev/null && echo "已收紧: $f -> $(stat -c '%a' "$f")"
done

echo ""
echo "========== [3/3] 复检 =========="
find /opt -type f \( -name ".env" -o -name ".env.production" -o -name "id_*" -o -name "*.pem" -o -name "*.key" \) 2>/dev/null | while read -r f; do
  echo "$(stat -c '%a %U:%G %n' "$f")"
done
