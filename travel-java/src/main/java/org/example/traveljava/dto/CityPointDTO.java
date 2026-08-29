package org.example.traveljava.dto;

/**
 * 城市坐标点 —— 周边游的"出发城市"选项（name + 坐标）。
 */
public class CityPointDTO {

    private String name;
    private Double lat;
    private Double lng;

    public CityPointDTO() {
    }

    public CityPointDTO(String name, Double lat, Double lng) {
        this.name = name;
        this.lat = lat;
        this.lng = lng;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
}
