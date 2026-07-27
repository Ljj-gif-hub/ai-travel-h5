package org.example.traveljava.dto;

/**
 * 地图标记数据传输对象
 * 用于前端地图组件渲染各类地标标记点（景点、地铁站、标志性建筑等）
 */
public class MapMarkerDTO {

    /** 地标ID */
    private Long id;

    /** 地标名称 */
    private String name;

    /** 所在城市 */
    private String city;

    /**
     * 地标类型
     * "attraction" - 景点
     * "metro"     - 地铁站
     * "landmark"  - 标志性建筑
     */
    private String type;

    /** 纬度坐标 */
    private Double latitude;

    /** 经度坐标 */
    private Double longitude;

    /** 地标描述信息 */
    private String description;

    /** 地图标记图标URL */
    private String iconUrl;

    // ==================== 无参构造 ====================
    public MapMarkerDTO() {
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
}
