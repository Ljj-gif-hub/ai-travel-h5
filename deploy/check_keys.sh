#!/usr/bin/env bash
echo "===== 服务器 .env 中各密钥的实际状态（值脱敏）====="
while IFS= read -r line; do
  case "$line" in
    LLM_API_KEY=*|TAVILY_API_KEY=*|AMAP_WEB_KEY=*|DEEPSEEK_API_KEY=*|BAIDU_MAP_AK=*|AGENT_API_KEY=*)
      key="${line%%=*}"
      val="${line#*=}"
      if [ -n "$val" ]; then
        echo "$key = <已设置 ${#val} 字符: ${val:0:6}...>"
      else
        echo "$key = <空>"
      fi
      ;;
  esac
done < /opt/bundle/travel-java/.env

echo ""
echo "===== docker-compose.yml 中 agent-service 完整段 ====="
sed -n '/^  agent-service:/,/^  app:/p' /opt/bundle/travel-java/docker-compose.yml

echo ""
echo "===== agent-service 容器实际 env ====="
AGENT_ID=$(docker ps --format '{{.ID}} {{.Names}}' | grep 'agent-service' | awk '{print $1}' | head -1)
docker exec "$AGENT_ID" env 2>/dev/null | grep -E '^(LLM_API_KEY|TAVILY_API_KEY|AMAP_WEB_KEY|AGENT_API_KEY|DEMO_MODE|MCP_)=' | sed -E 's/=(.{6}).*/=\1.../'
echo "(容器ID: $AGENT_ID)"
