package org.example.traveljava.dto;

import java.util.List;

/**
 * 周边游 —— 热门路线卡片数据（精选种子，非实时）。
 * 封面只存城市/景点名，由前端本地图库解析成 URL —— 见 {@link SurroundCityDTO} 说明。
 */
public class RouteCardDTO {

    private String id;
    private String name;            // 如 "越南7日自驾游"
    private String km;              // 如 "2058公里"
    private Integer days;           // 天数
    private Integer cityCount;      // 途经城市数（"6城"）
    private List<String> cities;    // 途经城市名
    private List<String> covers;    // 封面候选（城市/景点名，前端解析成图轮播）
    private String tagline;         // 一句话卖点

    public RouteCardDTO() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKm() {
        return km;
    }

    public void setKm(String km) {
        this.km = km;
    }

    public Integer getDays() {
        return days;
    }

    public void setDays(Integer days) {
        this.days = days;
    }

    public Integer getCityCount() {
        return cityCount;
    }

    public void setCityCount(Integer cityCount) {
        this.cityCount = cityCount;
    }

    public List<String> getCities() {
        return cities;
    }

    public void setCities(List<String> cities) {
        this.cities = cities;
    }

    public List<String> getCovers() {
        return covers;
    }

    public void setCovers(List<String> covers) {
        this.covers = covers;
    }

    public String getTagline() {
        return tagline;
    }

    public void setTagline(String tagline) {
        this.tagline = tagline;
    }
}
