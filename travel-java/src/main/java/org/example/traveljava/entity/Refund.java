package org.example.traveljava.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 【新功能】退款单。
 * 用户对已支付订单发起退款 → 管理员审核：
 *  approve → 调用退款渠道（Mock）→ 状态 refunded + 订单取消；
 *  reject  → 状态 rejected。
 * 同一订单同时只允许存在一笔 pending 退款（服务层去重）。
 */
@Entity
@Table(name = "refunds", indexes = {
        @Index(name = "idx_refunds_user_id", columnList = "user_id"),
        @Index(name = "idx_refunds_status", columnList = "status"),
        @Index(name = "idx_refunds_order_id", columnList = "order_id")
})
public class Refund {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_REFUNDED = "refunded";
    public static final String STATUS_REJECTED = "rejected";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联订单 id */
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** 申请用户 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 退款金额（单位：分，与订单 price 一致） */
    @Column(nullable = false)
    private Long amount;

    /** 退款原因 */
    @Column(length = 200)
    private String reason;

    /** 状态：pending / refunded / rejected */
    @Column(nullable = false, length = 20)
    private String status = STATUS_PENDING;

    /** 退款渠道返回的退款单号（Mock 渠道生成） */
    @Column(name = "refund_no", length = 64)
    private String refundNo;

    /** 处理人（管理员） */
    @Column(name = "handled_by")
    private Long handledBy;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = STATUS_PENDING;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Refund() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRefundNo() { return refundNo; }
    public void setRefundNo(String refundNo) { this.refundNo = refundNo; }
    public Long getHandledBy() { return handledBy; }
    public void setHandledBy(Long handledBy) { this.handledBy = handledBy; }
    public LocalDateTime getHandledAt() { return handledAt; }
    public void setHandledAt(LocalDateTime handledAt) { this.handledAt = handledAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
