#!/bin/bash
echo '=== 健康 ==='
curl -s -m 8 http://localhost/actuator/health; echo
echo ''
echo '=== RabbitMQ 队列（应含 travel.orders 与 travel.orders.dlq）==='
docker exec travel-rabbitmq rabbitmqctl list_queues name messages 2>/dev/null
echo ''
echo '=== app 日志 MQ 声明/监听 ==='
docker logs --tail 120 travel-java-app-1 2>&1 | grep -iE 'Declaring queue|SimpleMessageListenerContainer|406|PRECONDITION|Broker not available|channel error' | tail -10
echo ''
echo '=== app 日志 ERROR（最近）==='
docker logs --tail 120 travel-java-app-1 2>&1 | grep -iE '"level":"ERROR"' | tail -6
