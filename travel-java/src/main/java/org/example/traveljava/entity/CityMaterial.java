package org.example.traveljava.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 城市素材表 — 携程同款三级素材池核心
 * 以国标行政区划编码为主键，一一绑定城市图片
 */
@Entity
@Table(name = "city_material", indexes = {
    @Index(name = "idx_cm_code", columnList = "cityCode", unique = true),
    @Index(name = "idx_cm_province", columnList = "provinceCode")
})
public class CityMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 国家行政区划6位编码（唯一主键），境外城市用 ISO 国家码+城市序号 */
    @Column(nullable = false, unique = true, length = 20)
    private String cityCode;

    /** 城市名称 */
    @Column(nullable = false, length = 100)
    private String cityName;

    /** 归属省份编码，关联省份表 */
    @Column(length = 20)
    private String provinceCode;

    /** 省份名称 */
    @Column(length = 100)
    private String provinceName;

    /** 城市卡片缩略图 CDN 地址（3:2 比例） */
    @Column(length = 1024)
    private String thumbImg;

    /** 行程详情页大图/横幅图 */
    @Column(length = 1024)
    private String bannerImg;

    /** 城市标签，逗号分隔 */
    @Column(length = 200)
    private String tags;

    /** 图片来源：commercial/ugc/merchant/photographer/map_api */
    @Column(length = 50)
    private String imgSource;

    /** 素材层级：1=省份底图 2=城市标准图 3=景点细分图 */
    @Column(nullable = false)
    private Integer materialLevel = 2;

    /** 热度/排序权重 */
    private Integer sortOrder = 0;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }
    @PreUpdate public void preUpdate() { updatedAt = LocalDateTime.now(); }

    public CityMaterial() {}

    public CityMaterial(String cityCode, String cityName, String provinceCode, String provinceName,
                        String thumbImg, String tags) {
        this.cityCode = cityCode; this.cityName = cityName;
        this.provinceCode = provinceCode; this.provinceName = provinceName;
        this.thumbImg = thumbImg; this.tags = tags;
        this.materialLevel = 2;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCityCode() { return cityCode; }
    public void setCityCode(String cityCode) { this.cityCode = cityCode; }
    public String getCityName() { return cityName; }
    public void setCityName(String cityName) { this.cityName = cityName; }
    public String getProvinceCode() { return provinceCode; }
    public void setProvinceCode(String provinceCode) { this.provinceCode = provinceCode; }
    public String getProvinceName() { return provinceName; }
    public void setProvinceName(String provinceName) { this.provinceName = provinceName; }
    public String getThumbImg() { return thumbImg; }
    public void setThumbImg(String thumbImg) { this.thumbImg = thumbImg; }
    public String getBannerImg() { return bannerImg; }
    public void setBannerImg(String bannerImg) { this.bannerImg = bannerImg; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getImgSource() { return imgSource; }
    public void setImgSource(String imgSource) { this.imgSource = imgSource; }
    public Integer getMaterialLevel() { return materialLevel; }
    public void setMaterialLevel(Integer materialLevel) { this.materialLevel = materialLevel; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
