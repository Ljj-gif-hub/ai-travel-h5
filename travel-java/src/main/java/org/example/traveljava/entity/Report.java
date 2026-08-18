package org.example.traveljava.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 【新功能】内容举报记录。
 * 目标类型 targetType: note(游记) / post(动态) / comment(评论)。
 * 同一用户对同一目标只能举报一次（唯一约束去重）；
 * 同一目标累计举报 ≥5 次自动隐藏（hidden 字段）。
 */
@Entity
@Table(name = "reports", uniqueConstraints = {
        @UniqueConstraint(name = "uk_report_once", columnNames = {"reporter_id", "target_type", "target_id"})
}, indexes = {
        @Index(name = "idx_reports_target", columnList = "target_type,target_id"),
        @Index(name = "idx_reports_status", columnList = "status")
})
public class Report {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_HANDLED = "handled";
    public static final String STATUS_IGNORED = "ignored";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 举报人 */
    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    /** 目标类型：note / post / comment */
    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    /** 目标 id */
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /** 举报理由 */
    @Column(length = 200)
    private String reason;

    /** 状态：pending / handled / ignored */
    @Column(nullable = false, length = 20)
    private String status = STATUS_PENDING;

    /** 处理人（管理员） */
    @Column(name = "handled_by")
    private Long handledBy;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = STATUS_PENDING;
    }

    public Report() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getReporterId() { return reporterId; }
    public void setReporterId(Long reporterId) { this.reporterId = reporterId; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getHandledBy() { return handledBy; }
    public void setHandledBy(Long handledBy) { this.handledBy = handledBy; }
    public LocalDateTime getHandledAt() { return handledAt; }
    public void setHandledAt(LocalDateTime handledAt) { this.handledAt = handledAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
