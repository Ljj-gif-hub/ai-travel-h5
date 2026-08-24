package org.example.traveljava.mq;

import org.example.traveljava.config.MqProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 声明配置：交换机 / 队列 / 绑定 + JSON 消息转换器 + 死信机制。
 * 仅在 app.mq.enabled=true 时生效。
 *
 * 【MQ-2 修复】死信机制 + 重试上限：
 *  - 主队列声明 x-dead-letter-exchange / x-dead-letter-routing-key，消费失败（拒绝且不 requeue）的消息进死信队列；
 *  - @RabbitListener 容器工厂默认不 requeue（defaultRequeueRejected=false）+ 带退避重试 3 次，
 *    此前默认 AUTO ack + 无限 requeue：DB 故障时异常消息被反复重投形成热循环。
 *  - 正常事件照常 ACK；瞬时故障最多重试 3 次；仍失败进死信队列人工处理，消息不丢。
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

    /** 【MQ-2 修复】死信交换机：主队列消费失败的消息转入死信队列 */
    @Bean
    public DirectExchange travelEventDlxExchange(MqProperties props) {
        return new DirectExchange(props.getExchange() + ".dlx", true, false);
    }

    @Bean
    public Queue orderPaidQueue(MqProperties props) {
        // 【MQ-2 修复】主队列绑定死信交换机：处理失败（重试耗尽被拒绝）的消息进死信队列，避免无限 requeue 热循环
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", props.getExchange() + ".dlx");
        args.put("x-dead-letter-routing-key", props.getRoutingOrders() + ".dlq");
        return new Queue(props.getQueueOrders(), true, false, false, args);
    }

    /** 【MQ-2 修复】死信队列：消费失败的事件落此处保留，等待人工介入/后续处理，不丢失 */
    @Bean
    public Queue orderPaidDlq(MqProperties props) {
        return new Queue(props.getQueueOrders() + ".dlq", true);
    }

    @Bean
    public Binding orderPaidBinding(Queue orderPaidQueue, DirectExchange travelEventExchange, MqProperties props) {
        return BindingBuilder.bind(orderPaidQueue).to(travelEventExchange).with(props.getRoutingOrders());
    }

    @Bean
    public Binding orderPaidDlqBinding(Queue orderPaidDlq, DirectExchange travelEventDlxExchange, MqProperties props) {
        return BindingBuilder.bind(orderPaidDlq).to(travelEventDlxExchange).with(props.getRoutingOrders() + ".dlq");
    }

    /**
     * 【MQ-2 修复】@RabbitListener 容器工厂：默认不 requeue 拒绝的消息 + 带退避重试 3 次。
     * 瞬时故障（如 DB 抖动）先退避重试，重试耗尽仍失败 → 拒绝进死信队列（消息不丢）。
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter mqMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(mqMessageConverter);
        factory.setDefaultRequeueRejected(false);
        RetryOperationsInterceptor retry = RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(2_000L, 2.0, 10_000L)
                .build();
        factory.setAdviceChain(retry);
        return factory;
    }
}
