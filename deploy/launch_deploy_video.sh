#!/bin/bash
# 2026-08-25 视频修复批次部署：setsid 防 ssh 断连杀掉，日志落 deploy_20260825.log
cd /opt/bundle || exit 1
rm -f deploy_20260825.log

# 部署门检查：服务器 .env 是否带必要变量（fail-closed，缺则中止）
echo "--- .env 必要变量检查 ---"
grep -q '^MYSQL_ROOT_PASSWORD=' travel-java/.env && echo "MYSQL_ROOT_PASSWORD OK" || { echo "缺少 MYSQL_ROOT_PASSWORD"; exit 1; }
grep -q '^REDIS_PASSWORD=' travel-java/.env && echo "REDIS_PASSWORD OK" || { echo "缺少 REDIS_PASSWORD"; exit 1; }
grep -q '^RABBITMQ_USER=' travel-java/.env && grep -q '^RABBITMQ_PASSWORD=' travel-java/.env && echo "RABBITMQ_USER/PASS OK" || { echo "缺少 RABBITMQ 凭证"; exit 1; }
grep -q '^AGENT_API_KEY=' travel-java/.env && echo "AGENT_API_KEY OK" || { echo "缺少 AGENT_API_KEY"; exit 1; }

echo "--- 前端 .env 残留检查（应为不存在）---"
if [ -f trval-h5/.env ]; then echo "警告: trval-h5/.env 存在"; ls -la trval-h5/.env; else echo "trval-h5/.env 不存在（OK）"; fi

setsid nohup bash deploy.sh > deploy_20260825.log 2>&1 &
echo "started deploy pid: $!"
sleep 2
echo "--- 日志文件 ---"
ls -la /opt/bundle/deploy_20260825.log
echo "--- 进程 ---"
ps aux | grep '[d]eploy.sh' | head
