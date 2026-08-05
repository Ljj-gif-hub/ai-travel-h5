package org.example.traveljava.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 应用自定义监控指标（Micrometer）。
 *
 * 全部指标可在 GET /actuator/prometheus 查看：
 *  - travel_ai_calls_total{provider=...}    AI 调用次数（按供应商）
 *  - travel_plan_generated_total            行程规划生成次数
 *  - travel_chat_stream_total               SSE 流式对话次数
 */
@Component
public class AppMetrics {

    private final MeterRegistry registry;
    private final Counter planGenerated;
    private final Counter chatStream;
    private final Counter eventPublished;
    private final Counter eventDropped;

    public AppMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.planGenerated = Counter.builder("travel_plan_generated")
                .description("AI 行程规划生成次数")
                .register(registry);
        this.chatStream = Counter.builder("travel_chat_stream")
                .description("SSE 流式对话/生成次数")
                .register(registry);
        this.eventPublished = Counter.builder("travel_mq_event_published")
                .description("MQ 事件投递成功次数")
                .register(registry);
        this.eventDropped = Counter.builder("travel_mq_event_dropped")
                .description("MQ 事件降级丢弃次数（RabbitMQ 不可用）")
                .register(registry);
    }

    /** 记录一次 AI 调用，provider 标签随实际供应商变化 */
    public void aiCall(String provider) {
        Counter.builder("travel_ai_calls")
                .description("AI 模型调用次数")
                .tag("provider", provider == null ? "unknown" : provider)
                .register(registry)
                .increment();
    }

    public void planGenerated() {
        planGenerated.increment();
    }

    public void chatStream() {
        chatStream.increment();
    }

    /** 推荐接口被请求（展示用） */
    public void recommendServed() {
        registry.counter("travel_recommend_served", "description", "推荐接口请求次数").increment();
    }

    /** MQ 事件投递成功 */
    public void eventPublished(String eventType) {
        Counter.builder("travel_mq_event_published")
                .description("MQ 事件投递成功次数")
                .tag("type", eventType == null ? "unknown" : eventType)
                .register(registry)
                .increment();
    }

    /** MQ 事件被消费者成功处理 */
    public void eventProcessed(String eventType) {
        Counter.builder("travel_mq_event_processed")
                .description("MQ 事件消费成功次数")
                .tag("type", eventType == null ? "unknown" : eventType)
                .register(registry)
                .increment();
    }

    /** MQ 事件降级丢弃（RabbitMQ 不可用） */
    public void eventDropped(String eventType) {
        Counter.builder("travel_mq_event_dropped")
                .description("MQ 事件降级丢弃次数（RabbitMQ 不可用）")
                .tag("type", eventType == null ? "unknown" : eventType)
                .register(registry)
                .increment();
    }
}
