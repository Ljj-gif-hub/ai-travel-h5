package org.example.traveljava.mq;

import org.example.traveljava.config.AppMetrics;
import org.example.traveljava.config.MqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * RabbitMQ 发布器：app.mq.enabled=true 时生效。
 *
 * 优雅降级：RabbitMQ 不可用时 convertAndSend 抛异常，捕获后仅记录 WARN，
 * 不影响业务主流程（等价于同步降级，只是没有投递）。
 */
@Component
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true")
public class RabbitEventPublisher implements TravelEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final MqProperties props;
    private final AppMetrics appMetrics;

    public RabbitEventPublisher(RabbitTemplate rabbitTemplate, MqProperties props, AppMetrics appMetrics) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
        this.appMetrics = appMetrics;
    }

    /** MQ-1 修复：投递失败重试次数（含首次共 3 次），降低瞬时抖动丢事件概率 */
    private static final int MAX_ATTEMPTS = 3;
    /** MQ-1 修复：重试间隔，短暂等待避免对 RabbitMQ 造成压力 */
    private static final long RETRY_SLEEP_MS = 300L;

    @Override
    public void publish(TravelEvent event) {
        String routingKey = routeKeyFor(event.eventType());
        int attempts = 0;
        while (true) {
            try {
                rabbitTemplate.convertAndSend(props.getExchange(), routingKey, event);
                appMetrics.eventPublished(event.eventType());
                log.info("[MQ] 事件已投递: type={} routingKey={} eventId={}", event.eventType(), routingKey, event.eventId());
                return;
            } catch (Exception e) {
                attempts++;
                if (attempts < MAX_ATTEMPTS) {
                    log.warn("[MQ] 投递失败（第{}次，重试）: type={} eventId={} err={}",
                            attempts, event.eventType(), event.eventId(), e.getMessage());
                    try {
                        Thread.sleep(RETRY_SLEEP_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                } else {
                    log.error("[MQ] RabbitMQ 不可用，事件降级记录（已重试 {} 次）: type={} eventId={} err={}",
                            MAX_ATTEMPTS - 1, event.eventType(), event.eventId(), e.getMessage());
                    appMetrics.eventDropped(event.eventType());
                    return;
                }
            }
        }
    }

    private String routeKeyFor(String eventType) {
        return switch (eventType) {
            case "ORDER_PAID" -> props.getRoutingOrders();
            default -> "travel.event." + eventType.toLowerCase();
        };
    }
}
