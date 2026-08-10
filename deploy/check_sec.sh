#!/usr/bin/env bash
echo "===== 1. .env 文件权限 ====="
ls -la /opt/bundle/travel-java/.env
echo ""
echo "===== 2. SSH 是否关闭密码登录（key-only）====="
grep -E '^(PasswordAuthentication|PubkeyAuthentication|PermitRootLogin)' /etc/ssh/sshd_config 2>/dev/null || echo "(未显式配置，看默认)"
echo ""
echo "===== 3. 防火墙/端口暴露面 ====="
ss -tlnp 2>/dev/null | grep -E ':(80|3200|3201|3306|6379|15672)\b' | awk '{print $4, $6}'
echo ""
echo "===== 4. docker 远程 API 是否暴露 ====="
ss -tlnp 2>/dev/null | grep -E ':2375\b' && echo "!! 2375 暴露" || echo "未暴露（好）"
echo ""
echo "===== 5. 公网可达端口探测（本机视角）====="
for p in 80 3200 3201 3306 6379; do
  timeout 2 bash -c "</dev/tcp/127.0.0.1/$p" 2>/dev/null && echo "127.0.0.1:$p 开放" || echo "127.0.0.1:$p 关闭"
done
echo ""
echo "===== 6. agent 端口是否需要密钥（公网直连验证）====="
curl -s -m 5 http://127.0.0.1:3201/api/agent/health | head -c 120
echo ""
