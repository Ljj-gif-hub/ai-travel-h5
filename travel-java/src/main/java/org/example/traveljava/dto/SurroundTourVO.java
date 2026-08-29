package org.example.traveljava.dto;

import java.util.List;
import java.util.Map;

/**
 * 周边游完整数据 —— 一次返回全部，前端按出发城市切换（避免换城市 N+1）。
 * data：精选种子（非实时）。
 */
public class SurroundTourVO {

    private String defaultDeparture;                 // 默认出发城市，如 "深圳"
    private List<CityPointDTO> departureCities;      // 可选出发城市（含坐标，供地图中心）
    private Map<String, List<SurroundCityDTO>> surround; // key=出发城市 → 可到达城市列表
    private List<RouteCardDTO> routes;               // 热门路线

    public SurroundTourVO() {
    }

    public String getDefaultDeparture() {
        return defaultDeparture;
    }

    public void setDefaultDeparture(String defaultDeparture) {
        this.defaultDeparture = defaultDeparture;
    }

    public List<CityPointDTO> getDepartureCities() {
        return departureCities;
    }

    public void setDepartureCities(List<CityPointDTO> departureCities) {
        this.departureCities = departureCities;
    }

    public Map<String, List<SurroundCityDTO>> getSurround() {
        return surround;
    }

    public void setSurround(Map<String, List<SurroundCityDTO>> surround) {
        this.surround = surround;
    }

    public List<RouteCardDTO> getRoutes() {
        return routes;
    }

    public void setRoutes(List<RouteCardDTO> routes) {
        this.routes = routes;
    }
}
