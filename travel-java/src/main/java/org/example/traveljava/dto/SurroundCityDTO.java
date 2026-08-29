package org.example.traveljava.dto;

import java.util.List;

/**
 * 周边游 —— 从出发城市可到达的目的城市卡片数据（精选种子，非实时）。
 * 图片：只存城市名 / 热门景点名，由前端本地图库（city-images.json / attraction-images.json）解析成 URL。
 */
public class SurroundCityDTO {

    private String name;
    private String province;
    private Double lat;
    private Double lng;
    private Integer heat;           // 热度值（0-100，示意）
    private Integer transitMin;     // 通勤时长（分钟，数值，用于时长筛选）
    private String transitLabel;    // 展示文案，如 "高铁14分钟 起"
    private String price;           // 展示文案，如 "¥64 起"
    private Integer priceValue;     // 价位（元，示意）
    private List<String> hotSpots;  // 热门景点名（前端解析成图 + 旅行热点文案）
    private List<String> tags;      // 城市标签，如 ["都市","购物"]
    private String description;     // 旅行热点一句话推荐

    public SurroundCityDTO() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLng() {
        return lng;
    }

    public void setLng(Double lng) {
        this.lng = lng;
    }

    public Integer getHeat() {
        return heat;
    }

    public void setHeat(Integer heat) {
        this.heat = heat;
    }

    public Integer getTransitMin() {
        return transitMin;
    }

    public void setTransitMin(Integer transitMin) {
        this.transitMin = transitMin;
    }

    public String getTransitLabel() {
        return transitLabel;
    }

    public void setTransitLabel(String transitLabel) {
        this.transitLabel = transitLabel;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public Integer getPriceValue() {
        return priceValue;
    }

    public void setPriceValue(Integer priceValue) {
        this.priceValue = priceValue;
    }

    public List<String> getHotSpots() {
        return hotSpots;
    }

    public void setHotSpots(List<String> hotSpots) {
        this.hotSpots = hotSpots;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
