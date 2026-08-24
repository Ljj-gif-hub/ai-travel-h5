#!/bin/bash
# 2026-08-24: 为 fail-closed compose 补全 .env 必填变量（值永不回显）
ENV_FILE=/opt/bundle/travel-java/.env
OLD_COMPOSE=/opt/bundle/travel-java/docker-compose.yml
touch "$ENV_FILE"

set_env() { # set_env KEY VALUE（已存在则跳过，不覆盖）
  local key="$1"; shift
  if grep -q "^${key}=" "$ENV_FILE"; then
    echo "  = $key 已存在，跳过"
  else
    printf '%s=%s\n' "$key" "$*" >> "$ENV_FILE"
    echo "  + $key 已补入（值隐藏）"
  fi
}

# 提取现有 MySQL root 密码：优先取运行中 app 容器的 DB_PASSWORD（权威：当前实际连得上），
# 其次旧 compose 的 MYSQL_ROOT_PASSWORD / DB_PASSWORD 硬编码值
MPW=$(docker inspect travel-java-app-1 --format '{{range .Config.Env}}{{println .}}{{end}}' 2>/dev/null | grep '^DB_PASSWORD=' | head -1 | cut -d= -f2-)
if [ -z "$MPW" ]; then
  MPW=$(grep -oE 'MYSQL_ROOT_PASSWORD:[^#]*' "$OLD_COMPOSE" 2>/dev/null | head -1 | sed -E 's/.*:[[:space:]]*//' | tr -d ' \t')
fi
if [ -z "$MPW" ]; then
  MPW=$(grep -oE 'DB_PASSWORD:[^#]*' "$OLD_COMPOSE" 2>/dev/null | head -1 | sed -E 's/.*:[[:space:]]*//' | tr -d ' \t')
fi
if [ -z "$MPW" ]; then
  echo "!! 无法提取 MySQL root 密码，终止（需人工介入）"; exit 1
fi
echo "MySQL root 密码: 已提取（长度 ${#MPW}）"

set_env MYSQL_ROOT_PASSWORD "$MPW"

# Redis：新随机强密码（数据为缓存/限流/刷新令牌，可重建，无迁移负担）
if command -v openssl >/dev/null 2>&1; then
  RDPW=$(openssl rand -hex 20)
else
  RDPW=$(head -c 20 /dev/urandom | od -An -tx1 | tr -d ' \n')
fi
set_env REDIS_PASSWORD "$RDPW"

# RabbitMQ：存量卷已初始化 guest，RABBITMQ_DEFAULT_USER 对已有卷不生效 → 必须沿用 guest/guest
# 才能免删卷平滑升级（rabbitmq 仅绑 127.0.0.1；换强凭据需删 rabbitmq-data 卷，列为后续可选）
set_env RABBITMQ_USER "guest"
set_env RABBITMQ_PASSWORD "guest"

echo ""
echo "=== 补入后 .env 变量清单（值打码）==="
grep -oE '^[A-Z0-9_]+=' "$ENV_FILE" | tr -d '=' | sort

echo ""
echo "=== 校验：MYSQL_ROOT_PASSWORD 与现有 MySQL 连通 ==="
docker exec travel-mysql mysqladmin -uroot -p"$MPW" ping 2>&1 | tail -1
