#!/bin/bash
# 2026-08-24 部署启动：setsid 防 ssh 断连杀掉，日志落 deploy_20260824.log
cd /opt/bundle || exit 1
rm -f deploy_20260824.log
setsid nohup bash deploy.sh > deploy_20260824.log 2>&1 &
echo "started deploy pid: $!"
sleep 2
echo "--- 日志文件 ---"
ls -la /opt/bundle/deploy_20260824.log
echo "--- 进程 ---"
ps aux | grep '[d]eploy.sh' | head
