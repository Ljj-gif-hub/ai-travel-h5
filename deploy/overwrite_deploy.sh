#!/bin/bash
# 2026-08-24: 把新 bundle 原位覆盖到 /opt/bundle 现有部署目录（保留 uploads/ 运行数据）
set -euo pipefail
cd /opt/bundle

echo "=== 覆盖部署目录（uploads 不在包内，原位保留）==="
cp -a bundle/travel-java/. travel-java/
cp -a bundle/agent-service/. agent-service/
cp -a bundle/trval-h5/. trval-h5/
cp -a bundle/deploy.sh deploy.sh

echo "=== 确认新 compose 就位 ==="
echo "请设置标记数: $(grep -c '请设置' travel-java/docker-compose.yml)（应≥12）"

echo "=== compose config 预检（fail-closed 变量齐全性）==="
cd travel-java
docker compose config -q && echo "✅ COMPOSE_VALID"

echo "=== uploads 保留确认（应≥4）==="
ls /opt/bundle/travel-java/uploads | wc -l

echo "=== 源码包留档备份 ==="
mv /opt/bundle/bundle /opt/bundle/bundle_20260824_src
echo "完成：bundle 已改名 bundle_20260824_src（部署成功后可按需清理）"
