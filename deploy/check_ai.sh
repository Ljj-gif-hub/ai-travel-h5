#!/usr/bin/env bash
# 检查线上 AI 功能链路：Spring -> Agent（X-Agent-Key 透传）
echo "===== 1. 容器状态 ====="
docker ps --format '{{.Names}} | {{.Status}}'

echo ""
echo "===== 2. 直接调 agent（无密钥，应 401）====="
curl -s -m 8 http://localhost:3201/api/agent/health | head -c 200

echo ""
echo "===== 3. 经 Spring 透传调 agent（应能拿到 health，验证 X-Agent-Key 转发）====="
curl -s -m 10 http://localhost:3200/api/agent/health | head -c 500

echo ""
echo "===== 4. app 容器内看 AGENT_API_KEY 是否注入 ====="
APP_ID=$(docker ps --format '{{.ID}} {{.Names}}' | grep -E 'travel-java.*app|app$' | awk '{print $1}' | head -1)
if [ -n "$APP_ID" ]; then
  docker exec "$APP_ID" env 2>/dev/null | grep -E '^(AGENT_API_KEY|AGENT_SERVICE_URL|DEEPSEEK_API_KEY|LLM_API_KEY)=' | sed -E 's/=(.{6}).*/=\1.../'
else
  echo "未找到 app 容器"
fi

echo ""
echo "===== 5. Spring 关键环境变量（脱敏）====="
docker exec "$APP_ID" env 2>/dev/null | grep -E '^(AGENT_API_KEY|DEEPSEEK_API_KEY|SPRING_PROFILES_ACTIVE)=' | sed -E 's/=(.{6}).*/=\1.../'

echo ""
echo "===== 6. app 日志中最近的 Agent 调用错误 ====="
docker logs --tail 200 "$APP_ID" 2>&1 | grep -iE 'agent|未授权|401|X-Agent' | tail -15
