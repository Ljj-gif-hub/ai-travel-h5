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

    @Override
    public void publish(TravelEvent event) {
        String routingKey = routeKeyFor(event.eventType());
        try {
            rabbitTemplate.convertAndSend(props.getExchange(), routingKey, event);
            appMetrics.eventPublished(event.eventType());
            log.info("[MQ] 事件已投递: type={} routingKey={} eventId={}", event.eventType(), routingKey, event.eventId());
        } catch (Exception e) {
            log.warn("[MQ] RabbitMQ 不可用，事件降级记录: type={} eventId={} err={}",
                    event.eventType(), event.eventId(), e.getMessage());
            appMetrics.eventDropped(event.eventType());
        }
    }

    private String routeKeyFor(String eventType) {
        return switch (eventType) {
            case "ORDER_PAID" -> props.getRoutingOrders();
            default -> "travel.event." + eventType.toLowerCase();
        };
    }
}
