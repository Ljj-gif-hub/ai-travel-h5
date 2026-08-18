package org.example.traveljava.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 【新功能】行程模板 — 模板市场中的公开行程模板。
 * 用户可将模板实例化为自己的 SavedTravelPlan；downloads 记录被实例化次数。
 */
@Entity
@Table(name = "trip_templates", indexes = {
        @Index(name = "idx_trip_templates_status", columnList = "status")
})
public class TripTemplate {

    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_DELETED = "deleted";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 模板名称 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 目的地 */
    @Column(nullable = false, length = 50)
    private String destination;

    /** 天数 */
    private Integer days;

    /** 预算（元） */
    private Long budget;

    /** 人数 */
    private Integer people;

    /** 封面图 URL */
    @Column(name = "cover_image", columnDefinition = "TEXT")
    private String coverImage;

    /** 标签（逗号分隔，如 "亲子,海滨,美食"） */
    @Column(length = 200)
    private String tags;

    /** 简介 */
    @Column(length = 500)
    private String description;

    /** 被实例化（下载）次数 */
    @Column(nullable = false)
    private Integer downloads = 0;

    /** 模板行程内容 JSON */
    @Column(name = "plan_json", nullable = false, columnDefinition = "LONGTEXT")
    private String planJson;

    /** 状态：published / deleted（软删除，保留审计引用） */
    @Column(nullable = false, length = 20)
    private String status = STATUS_PUBLISHED;

    /** 创建人（管理员创建，保留审计） */
    @Column(name = "creator_id")
    private Long creatorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = createdAt;
        if (downloads == null) downloads = 0;
        if (status == null) status = STATUS_PUBLISHED;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public TripTemplate() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public Integer getDays() { return days; }
    public void setDays(Integer days) { this.days = days; }
    public Long getBudget() { return budget; }
    public void setBudget(Long budget) { this.budget = budget; }
    public Integer getPeople() { return people; }
    public void setPeople(Integer people) { this.people = people; }
    public String getCoverImage() { return coverImage; }
    public void setCoverImage(String coverImage) { this.coverImage = coverImage; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getDownloads() { return downloads; }
    public void setDownloads(Integer downloads) { this.downloads = downloads; }
    public String getPlanJson() { return planJson; }
    public void setPlanJson(String planJson) { this.planJson = planJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
