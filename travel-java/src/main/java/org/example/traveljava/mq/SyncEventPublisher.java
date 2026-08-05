package org.example.traveljava.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 默认同步降级发布器：未启用 RabbitMQ（app.mq.enabled=false）时生效，
 * 事件不投递 MQ，仅记录 INFO 日志（便于后续排查 / 离线联调）。
 */
@Component
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "false", matchIfMissing = true)
public class SyncEventPublisher implements TravelEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SyncEventPublisher.class);

    @Override
    public void publish(TravelEvent event) {
        log.info("[MQ-DISABLED] 事件同步降级记录: type={} eventId={} payload={}",
                event.eventType(), event.eventId(), event.payload());
    }
}
