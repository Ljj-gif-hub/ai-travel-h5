package org.example.traveljava.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 【新功能】游记收藏夹。
 * noteIds 以 JSON 数组字符串存储（LONGTEXT，如 [1,3,5]），
 * 支持创建/编辑/删除、添加/移除笔记（去重）、公开列表与详情摘要。
 */
@Entity
@Table(name = "note_collections", indexes = {
        @Index(name = "idx_collections_user_id", columnList = "user_id"),
        @Index(name = "idx_collections_public", columnList = "is_public, created_at")
})
public class NoteCollection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 收藏夹所有者 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 收藏夹名称 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 收藏夹描述 */
    @Column(length = 500)
    private String description;

    /** 封面（取收藏的第一篇笔记封面） */
    @Column(name = "cover_image", length = 500)
    private String coverImage;

    /** 收藏的笔记 id 列表（JSON 数组字符串，LONGTEXT） */
    @Column(name = "note_ids", columnDefinition = "LONGTEXT")
    private String noteIds = "[]";

    /** 是否公开（公开收藏夹出现在公共列表，可被其他用户浏览） */
    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (noteIds == null) noteIds = "[]";
        if (isPublic == null) isPublic = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public NoteCollection() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCoverImage() { return coverImage; }
    public void setCoverImage(String coverImage) { this.coverImage = coverImage; }
    public String getNoteIds() { return noteIds; }
    public void setNoteIds(String noteIds) { this.noteIds = noteIds; }
    public Boolean getIsPublic() { return isPublic; }
    public void setIsPublic(Boolean isPublic) { this.isPublic = isPublic; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
