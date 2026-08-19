package org.example.traveljava.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 【新功能】行程分享（24 小时有效期）— 与 ShareRecord（永久短码分享）并存。
 * token 为 32 位 UUID 十六进制，分享创建后 24 小时有效，可主动撤销。
 */
@Entity
@Table(name = "trip_shares", indexes = {
        // L-ENT-2 修复：token 已有 @Column(unique=true) 生成 UNIQUE KEY，普通索引 idx_trip_shares_token 纯冗余，移除
        @Index(name = "idx_trip_shares_plan", columnList = "plan_id")
})
public class TripShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 分享 token（32 位 UUID 无横线） */
    @Column(nullable = false, unique = true, length = 64)
    private String token;

    /** 被分享的行程计划 id（SavedTravelPlan） */
    @Column(name = "plan_id", nullable = false)
    private Long planId;

    /** 分享创建人 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 过期时间（创建时间 + 24 小时） */
    @Column(name = "expire_at", nullable = false)
    private LocalDateTime expireAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (expireAt == null) {
            expireAt = createdAt.plusHours(24);
        }
    }

    public TripShare() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
