package org.example.traveljava.service;

import org.example.traveljava.dto.AttractionDTO;
import org.example.traveljava.dto.HotDestinationDTO;
import org.example.traveljava.dto.POIDetailDTO;
import org.example.traveljava.dto.POISuggestionDTO;

import java.util.List;

/**
 * 地图服务抽象接口
 * 统一百度地图和高德地图的调用入口，由 MapConfig.provider 决定具体实现
 */
public interface MapService {

    /**
     * POI 搜索联想（输入提示）
     * @param keyword 搜索关键词
     * @return 联想结果列表
     */
    List<POISuggestionDTO> getSuggestions(String keyword);

    /**
     * 获取 POI 详情
     * @param uid 地点唯一标识（可选）
     * @param lat 纬度（uid 为空时使用）
     * @param lng 经度（uid 为空时使用）
     * @return POI 详情，失败返回 null
     */
    POIDetailDTO getPOIDetail(String uid, Double lat, Double lng);

    /**
     * 获取全国热门旅游城市列表（含缓存）
     * @return 热门目的地列表
     */
    List<HotDestinationDTO> getHotDestinations();

    /**
     * 获取城市内景点列表
     * @param cityName 城市名
     * @return 景点列表
     */
    List<AttractionDTO> getCityAttractions(String cityName);

    /**
     * 获取周边景点列表
     * @param lat 中心纬度
     * @param lng 中心经度
     * @param radius 搜索半径（米）
     * @return 周边景点列表
     */
    List<AttractionDTO> getNearbyAttractions(double lat, double lng, int radius);

    /**
     * 地理编码：地址 → 坐标
     * @param address 地址（城市名、地标名等）
     * @return [lat, lng]，失败返回 null
     */
    double[] geocode(String address);

    /**
     * @return 当前地图提供商标识，如 "baidu" / "amap"
     */
    String getProviderName();

    /**
     * @return 当前提供商返回的坐标体系，如 "bd09" / "gcj02"
     */
    String getCoordinateSystem();
}
