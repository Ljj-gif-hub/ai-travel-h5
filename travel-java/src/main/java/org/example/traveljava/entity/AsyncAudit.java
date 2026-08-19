package org.example.traveljava.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 异步审计日志（消息队列消费者写入）。
 * 演示 MQ 异步处理：事件消费在独立线程完成，不阻塞主流程。
 */
@Entity
@Table(name = "async_audit", indexes = {
        @Index(name = "idx_async_audit_type", columnList = "event_type"),
        @Index(name = "idx_async_audit_created", columnList = "created_at")
}, uniqueConstraints = {
        // MQ-1 修复：event_id 唯一约束兜底幂等——并发 redelivery 重复插行时由 DB 拒绝（配合消费者 catch）
        @UniqueConstraint(name = "uk_async_audit_event_id", columnNames = "event_id")
})
public class AsyncAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    /** 事件 payload 的 JSON 序列化（含 orderNo / userId 等） */
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** L-ENT-1 修复：created_at 列 NOT NULL，漏调 setCreatedAt 即插库失败——统一 @PrePersist 兜底（与全项目其他实体一致） */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
