package org.example.traveljava.mq;

import java.util.Map;
import java.util.UUID;

/**
 * 业务事件载体。用于 RabbitMQ 异步处理（或同步降级）。
 */
public record TravelEvent(
        String eventId,
        String eventType,
        long occurredAt,
        Map<String, Object> payload
) {

    public static TravelEvent of(TravelEventType type, Map<String, Object> payload) {
        return new TravelEvent(
                UUID.randomUUID().toString(),
                type.name(),
                System.currentTimeMillis(),
                payload == null ? Map.of() : payload
        );
    }
}
