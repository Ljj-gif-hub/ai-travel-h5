#!/bin/bash
echo '--- PING ---'
docker exec travel-redis redis-cli ping
echo '--- EVAL INCR+EXPIRE ---'
docker exec travel-redis redis-cli EVAL 'local c=redis.call("INCR",KEYS[1]); if c==1 then redis.call("EXPIRE",KEYS[1],ARGV[1]) end return c' 1 rate_limit:test:1 60
echo '--- EVAL return 1 ---'
docker exec travel-redis redis-cli EVAL 'return 1' 0
echo '--- VERSION ---'
docker exec travel-redis redis-cli INFO server 2>/dev/null | grep redis_version
echo '--- AUTH REQUIRED? ---'
docker exec travel-redis redis-cli -a "" ping 2>&1 | head -2
echo '--- RENAME-CMD ---'
docker exec travel-redis redis-cli CONFIG GET rename-command 2>/dev/null
