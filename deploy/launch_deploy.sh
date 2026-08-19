#!/usr/bin/env bash
# 启动部署（setsid 防 ssh 断连杀掉），日志落 deploy_20260819_v3.log
cd /opt/bundle || exit 1
rm -f deploy_20260819_v3.log
setsid nohup bash deploy.sh > deploy_20260819_v3.log 2>&1 &
echo "started deploy pid: $!"
sleep 2
echo "--- 日志文件 ---"
ls -la /opt/bundle/deploy_20260819_v3.log
echo "--- 进程 ---"
ps aux | grep '[d]eploy.sh' | head
