#!/bin/bash
echo '=== 找 chat/stream 相关异常（近 200 行）==='
docker logs --tail 200 travel-java-app-1 2>&1 | grep -B2 -A8 'chat/stream' | tail -40
echo ''
echo '=== 通用异常扫描（近 200 行 ERROR）==='
docker logs --tail 200 travel-java-app-1 2>&1 | grep -iE '"level":"(ERROR|WARN)"' | tail -10
