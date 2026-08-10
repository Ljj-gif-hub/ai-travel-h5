#!/usr/bin/env bash
echo "========== 1. 健康检查链路 =========="
echo "--- 经 nginx(公网): /actuator/health ---"
curl -s -m 8 -i http://8.148.223.54/actuator/health | head -3
echo ""
echo "--- 经 nginx: /api/agent/health ---"
curl -s -m 8 -o /dev/null -w 'HTTP %{http_code}\n' http://8.148.223.54/api/agent/health
echo "--- 直连 app: /actuator/health ---"
curl -s -m 8 -o /dev/null -w 'HTTP %{http_code}\n' http://localhost:3200/actuator/health

echo ""
echo "========== 2. 端口绑定(= 安全目标) =========="
echo "--- 监听地址 ---"
ss -tlnp 2>/dev/null | grep -E ':(80|3200|3201) ' | awk '{print $4, $6}'

echo ""
echo "========== 3. 公网访问阻断验证 =========="
echo "--- 公网访问 app:3200 (应失败/超时) ---"
curl -s -m 6 -o /dev/null -w 'HTTP %{http_code}\n' http://8.148.223.54:3200/actuator/health || echo "连接被拒/超时 ✓ (符合预期)"
echo "--- 公网访问 agent:3201 (应失败/超时) ---"
curl -s -m 6 -o /dev/null -w 'HTTP %{http_code}\n' http://8.148.223.54:3201/health || echo "连接被拒/超时 ✓ (符合预期)"

echo ""
echo "========== 4. 前端页面 =========="
curl -s -m 8 -o /dev/null -w '首页 HTTP %{http_code}, 大小 %{size_download} bytes\n' http://8.148.223.54/

echo ""
echo "========== 5. 容器状态 =========="
docker ps --format '{{.Names}} | {{.Status}}'
