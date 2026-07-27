package org.example.traveljava.dto;

/**
 * 地图配置信息传输对象
 * 返回给前端，用于判断使用哪个地图 SDK
 */
public class MapConfigDTO {

    /** 地图提供商：baidu / amap / leaflet */
    private String provider;

    /** 坐标体系：bd09 / gcj02 */
    private String coordinateSystem;

    /** JS SDK 加载 URL（可选，前端直接加载时使用） */
    private String jsSdkUrl;

    /** 是否可用（至少有一个 key 已配置） */
    private boolean available;

    public MapConfigDTO() {
    }

    public MapConfigDTO(String provider, String coordinateSystem, boolean available) {
        this.provider = provider;
        this.coordinateSystem = coordinateSystem;
        this.available = available;
    }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getCoordinateSystem() { return coordinateSystem; }
    public void setCoordinateSystem(String coordinateSystem) { this.coordinateSystem = coordinateSystem; }

    public String getJsSdkUrl() { return jsSdkUrl; }
    public void setJsSdkUrl(String jsSdkUrl) { this.jsSdkUrl = jsSdkUrl; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
}
