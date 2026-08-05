package org.example.traveljava.mq;

import org.example.traveljava.config.MqProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 声明配置：交换机 / 队列 / 绑定 + JSON 消息转换器。
 * 仅在 app.mq.enabled=true 时生效。
 */
@Configuration
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true")
public class RabbitMqConfig {

    @Bean
    public MessageConverter mqMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public DirectExchange travelEventExchange(MqProperties props) {
        return new DirectExchange(props.getExchange(), true, false);
    }

    @Bean
    public Queue orderPaidQueue(MqProperties props) {
        return new Queue(props.getQueueOrders(), true);
    }

    @Bean
    public Binding orderPaidBinding(Queue orderPaidQueue, DirectExchange travelEventExchange, MqProperties props) {
        return BindingBuilder.bind(orderPaidQueue).to(travelEventExchange).with(props.getRoutingOrders());
    }
}
