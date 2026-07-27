package org.example.traveljava.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 酒店数据传输对象
 * 用于前端展示酒店信息，包含单间价格和多晚总价的计算结果
 */
public class HotelDTO {

    /** 酒店ID */
    private Long id;

    /** 酒店名称 */
    private String name;

    /** 所在城市 */
    private String city;

    /** 所在区域/商圈 */
    private String district;

    /** 详细地址 */
    private String address;

    /** 纬度坐标 */
    private Double latitude;

    /** 经度坐标 */
    private Double longitude;

    /** 每晚价格（人民币） */
    private BigDecimal pricePerNight;

    /** 酒店评分（满分5.0） */
    private Double rating;

    /** 酒店封面图片URL */
    private String imageUrl;

    /** 酒店设施列表 */
    private List<String> amenities;

    /** 多晚总价（pricePerNight * 入住天数） */
    private BigDecimal totalPrice;

    // ==================== 无参构造 ====================
    public HotelDTO() {
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

    public List<String> getAmenities() {
        return amenities;
    }

    public void setAmenities(List<String> amenities) {
        this.amenities = amenities;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }
}
