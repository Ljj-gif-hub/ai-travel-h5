#!/bin/bash
# 2026-08-24 部署门检查：fail-closed compose 前的 .env 变量 + 存量卷状态
echo "=== 1. /opt/bundle/travel-java/.env 变量名（值隐藏）==="
if [ -f /opt/bundle/travel-java/.env ]; then
  grep -oE '^[A-Z0-9_]+=' /opt/bundle/travel-java/.env | tr -d '=' | sort
else
  echo "!! .env 不存在: /opt/bundle/travel-java/.env"
fi

echo ""
echo "=== 2. 相关数据卷（判断 RabbitMQ 是否已初始化 guest/guest）==="
docker volume ls --format '{{.Name}}' | grep -E 'rabbit|redis|mysql|travel' || echo "(无匹配卷)"

echo ""
echo "=== 3. 当前容器状态 ==="
docker ps -a --format '{{.Names}}\t{{.Status}}'

echo ""
echo "=== 4. RabbitMQ 现有用户（若已初始化，新 RABBITMQ_DEFAULT_USER 不生效）==="
docker exec travel-rabbitmq rabbitmqctl list_users 2>&1 | head -10 || echo "(rabbitmq 未运行)"

echo ""
echo "=== 5. Redis 当前密码状态 ==="
docker exec travel-redis sh -c 'redis-cli ping 2>&1' | head -3 || echo "(redis 未运行)"

echo ""
echo "=== 6. 现有 compose 中 rabbitmq 凭据配置 ==="
grep -A3 'RABBITMQ_DEFAULT' /opt/bundle/travel-java/docker-compose.yml 2>/dev/null || echo "(compose 文件未找到或已变动)"
