package org.example.traveljava.mq;

/**
 * 业务事件发布器。
 *
 * 两种实现，通过 {@code app.mq.enabled} 切换：
 *  - RabbitEventPublisher（enabled=true）  → 投递到 RabbitMQ 异步处理
 *  - SyncEventPublisher（默认）            → 本地同步降级，仅记录日志，不影响主流程
 */
public interface TravelEventPublisher {

    /**
     * 发布一条业务事件。
     * 实现应保证调用方不受失败影响（MQ 异常内部捕获并降级记录）。
     */
    void publish(TravelEvent event);
}
