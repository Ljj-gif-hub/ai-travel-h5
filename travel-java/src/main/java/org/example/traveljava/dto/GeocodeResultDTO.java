package org.example.traveljava.dto;

/**
 * 地理编码结果 —— 统一携带坐标系，前端据此做 CRS 归一化。
 *
 * 背景：高德 web-key 返回 GCJ-02，Nominatim 返回 WGS-84；两者混在一张图上会整体偏移。
 * 前端 spot-geocoder 拿到 crs 后统一转换到当前地图瓦片坐标系（高德=gcj02 / OSM=wgs84）。
 */
public class GeocodeResultDTO {

    private double lat;
    private double lng;
    /** 坐标系：gcj02 / wgs84 / bd09 */
    private String crs;
    /** 命中的名称/地址（仅诊断用） */
    private String name;

    public GeocodeResultDTO() {
    }

    public GeocodeResultDTO(double lat, double lng, String crs, String name) {
        this.lat = lat;
        this.lng = lng;
        this.crs = crs;
        this.name = name;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLng() {
        return lng;
    }

    public void setLng(double lng) {
        this.lng = lng;
    }

    public String getCrs() {
        return crs;
    }

    public void setCrs(String crs) {
        this.crs = crs;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
