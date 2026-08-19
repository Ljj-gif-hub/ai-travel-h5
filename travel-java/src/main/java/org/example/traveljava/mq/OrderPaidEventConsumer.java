package org.example.traveljava.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.traveljava.config.AppMetrics;
import org.example.traveljava.entity.AsyncAudit;
import org.example.traveljava.repository.AsyncAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
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

        // MQ-1 修复①：eventId 幂等检查，防止 redelivery 重复插审计行
        if (event.eventId() != null && auditRepository.existsByEventId(event.eventId())) {
            log.info("[MQ-CONSUMER] 事件已处理（幂等跳过）: eventId={}", event.eventId());
            return;
        }

        // MQ-1 修复②：不再 catch 所有异常照常 ACK（会丢事件），
        // 让基础设施异常（DB 不可达等）传播出去触发 RabbitMQ 重投（requeue）
        // 例外：payload 序列化失败是永久性错误，重投也无法成功，捕获后记录并跳过（照常 ACK，不空转重投）
        AsyncAudit audit = new AsyncAudit();
        audit.setEventId(event.eventId());
        audit.setEventType(event.eventType());
        try {
            audit.setPayload(objectMapper.writeValueAsString(event.payload()));
        } catch (JsonProcessingException e) {
            log.error("[MQ-CONSUMER] 事件 payload 序列化失败，跳过该事件（不重投）: eventId={}", event.eventId(), e);
            return;
        }
        audit.setCreatedAt(LocalDateTime.now());
        try {
            auditRepository.save(audit);
        } catch (DataIntegrityViolationException e) {
            // MQ-1 修复③：并发 redelivery 下唯一约束兜底——另一线程已插入同 event_id 行，
            // 视为已处理（ACK 跳过），避免无限 requeue
            log.info("[MQ-CONSUMER] 事件已被并发处理（唯一约束兜底）: eventId={}", event.eventId());
            return;
        }
        appMetrics.eventProcessed(event.eventType());

        // TODO: 扩展点 — 发送站内信 / 邮件 / 短信通知
    }
}
