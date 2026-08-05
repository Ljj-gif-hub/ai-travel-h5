package org.example.traveljava.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.traveljava.config.AppMetrics;
import org.example.traveljava.entity.AsyncAudit;
import org.example.traveljava.repository.AsyncAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 订单支付成功异步消费者。
 *
 * 仅在 app.mq.enabled=true 时注册。收到 ORDER_PAID 事件后：
 *  1. 将事件异步写入 async_audit 审计表（独立消费线程，不阻塞业务主流程）；
 *  2. 记录指标 travel_mq_event_processed_total；
 *  3. 预留扩展点：后续可在此接入站内信 / 邮件 / 短信通知。
 *
 * 消费者抛异常时 RabbitMQ 会自动重投（requeue），保证不丢事件。
 */
@Component
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true")
public class OrderPaidEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidEventConsumer.class);

    private final AsyncAuditRepository auditRepository;
    private final ObjectMapper objectMapper;
    private final AppMetrics appMetrics;

    public OrderPaidEventConsumer(AsyncAuditRepository auditRepository, ObjectMapper objectMapper, AppMetrics appMetrics) {
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
        this.appMetrics = appMetrics;
    }

    @RabbitListener(queues = "${app.mq.queue-orders}")
    public void onOrderPaid(TravelEvent event) {
        log.info("[MQ-CONSUMER] 收到订单支付成功事件: orderNo={} userId={}",
                event.payload().get("orderNo"), event.payload().get("userId"));

        // 异步落库（审计/对账用），失败不影响消息确认 —— 由业务层兜底
        try {
            AsyncAudit audit = new AsyncAudit();
            audit.setEventId(event.eventId());
            audit.setEventType(event.eventType());
            audit.setPayload(objectMapper.writeValueAsString(event.payload()));
            audit.setCreatedAt(LocalDateTime.now());
            auditRepository.save(audit);
            appMetrics.eventProcessed(event.eventType());
        } catch (Exception e) {
            log.error("[MQ-CONSUMER] 审计落库失败: eventId={} err={}", event.eventId(), e.getMessage(), e);
        }

        // TODO: 扩展点 — 发送站内信 / 邮件 / 短信通知
    }
}
