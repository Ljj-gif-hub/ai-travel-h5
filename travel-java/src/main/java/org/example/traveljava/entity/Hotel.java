package org.example.traveljava.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 酒店实体类
 * 存储各城市的酒店基础信息，供行程规划中的住宿推荐使用
 */
@Entity
@Table(name = "hotels")
public class Hotel {

    /** 酒店主键ID，自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 酒店名称 */
    @Column(nullable = false, length = 200)
    private String name;

    /** 所在城市 */
    @Column(nullable = false, length = 50)
    private String city;

    /** 所在区域/商圈 */
    @Column(length = 100)
    private String district;

    /** 详细地址 */
    @Column(length = 500)
    private String address;

    /** 纬度坐标 */
    @Column
    private Double latitude;

    /** 经度坐标 */
    @Column
    private Double longitude;

    /** 每晚价格（人民币） */
    @Column(name = "price_per_night", precision = 10, scale = 2)
    private BigDecimal pricePerNight;

    /** 酒店评分（满分5.0） */
    @Column
    private Double rating;

    /** 酒店封面图片URL */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** 酒店设施列表，以JSON数组字符串形式存储，如：["WiFi","停车场","健身房"] */
    @Column(columnDefinition = "CLOB")
    private String amenities;

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
    public Hotel() {
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

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
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

    public BigDecimal getPricePerNight() {
        return pricePerNight;
    }

    public void setPricePerNight(BigDecimal pricePerNight) {
        this.pricePerNight = pricePerNight;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getAmenities() {
        return amenities;
    }

    public void setAmenities(String amenities) {
        this.amenities = amenities;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
