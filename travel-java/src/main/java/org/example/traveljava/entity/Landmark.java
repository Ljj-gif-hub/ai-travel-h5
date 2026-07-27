package org.example.traveljava.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 地标实体类
 * 存储各城市的主要景点、交通枢纽和标志性建筑信息，用于地图展示和行程规划
 */
@Entity
@Table(name = "landmarks")
public class Landmark {

    /** 地标主键ID，自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 地标名称 */
    @Column(nullable = false, length = 200)
    private String name;

    /** 所在城市 */
    @Column(nullable = false, length = 50)
    private String city;

    /**
     * 地标类型
     * "attraction" - 景点
     * "metro"     - 地铁站
     * "landmark"  - 标志性建筑
     */
    @Column(nullable = false, length = 20)
    private String type;

    /** 纬度坐标 */
    @Column
    private Double latitude;

    /** 经度坐标 */
    @Column
    private Double longitude;

    /** 地标简介描述 */
    @Column(columnDefinition = "CLOB")
    private String description;

    /** 地图图标URL */
    @Column(name = "icon_url", length = 500)
    private String iconUrl;

    /** 记录创建时间 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 实体持久化前自动设置创建时间
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // ==================== 无参构造 ====================
    public Landmark() {
    }

    // ==================== Getter / Setter ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
