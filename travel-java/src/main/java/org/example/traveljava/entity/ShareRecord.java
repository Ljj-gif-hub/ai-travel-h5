package org.example.traveljava.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 行程分享记录 — 8 位短码映射到已保存的行程计划
 * 分享走「短码 + 只读快照」，不复制行程数据副本（复用 SavedTravelPlan.planJson）
 */
@Entity
@Table(name = "share_records")
public class ShareRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 8 位 base62 短码（分享链接中的 token） */
    @Column(nullable = false, unique = true, length = 16)
    private String token;

    /** 被分享的行程计划 id（SavedTravelPlan） */
    @Column(name = "plan_id", nullable = false)
    private Long planId;

    /** 目的地（便于列表/展示） */
    @Column(length = 50)
    private String destination;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public ShareRecord() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
