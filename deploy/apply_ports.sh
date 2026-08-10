#!/usr/bin/env bash
# 端口收紧：把 app(3200) / agent(3201) 绑定到 127.0.0.1，仅 nginx:80 对外
set -euo pipefail
cd /opt/bundle/travel-java

# 1) 备份
cp docker-compose.yml docker-compose.yml.bak
echo "已备份: docker-compose.yml.bak"

# 2) 幂等替换端口绑定（只处理当前仍为 0.0.0.0 的行）
sed -i 's/"3201:3201"/"127.0.0.1:3201:3201"/g' docker-compose.yml
sed -i 's/"3200:3200"/"127.0.0.1:3200:3200"/g' docker-compose.yml

# 3) 显示变更确认
echo "===== 修改后的 ports 段 ====="
grep -nE '"[0-9]+:[0-9]+"|127\.0\.0\.1:[0-9]+:[0-9]+' docker-compose.yml
