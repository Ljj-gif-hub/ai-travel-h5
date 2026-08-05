package org.example.traveljava.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 消息队列配置（app.mq.*）。
 *
 *  - enabled=false（默认）：不连接 RabbitMQ，事件走 {@code SyncEventPublisher} 同步降级（仅记录日志）。
 *  - enabled=true：启用 RabbitMQ 异步处理；RabbitMQ 不可用时启动不受影响，
 *    发布失败会捕获并回退为同步记录（优雅降级）。
 */
@ConfigurationProperties(prefix = "app.mq")
public class MqProperties {

    /** 是否启用 RabbitMQ 异步处理（默认关闭，避免本地无 MQ 时启动失败） */
    private boolean enabled = false;

    /** 交换机名称 */
    private String exchange = "travel.events";

    /** 业务事件队列（如订单支付成功） */
    private String queueOrders = "travel.orders";

    /** 业务事件路由键 */
    private String routingOrders = "order.paid";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getQueueOrders() {
        return queueOrders;
    }

    public void setQueueOrders(String queueOrders) {
        this.queueOrders = queueOrders;
    }

    public String getRoutingOrders() {
        return routingOrders;
    }

    public void setRoutingOrders(String routingOrders) {
        this.routingOrders = routingOrders;
    }
}
