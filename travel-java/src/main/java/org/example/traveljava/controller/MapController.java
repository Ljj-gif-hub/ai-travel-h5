package org.example.traveljava.controller;

import org.example.traveljava.annotation.RateLimit;
import org.example.traveljava.dto.AttractionDTO;
import org.example.traveljava.dto.HotDestinationDTO;
import org.example.traveljava.dto.MapMarkerDTO;
import org.example.traveljava.dto.POIDetailDTO;
import org.example.traveljava.dto.POISuggestionDTO;
import org.example.traveljava.service.MapService;
import org.example.traveljava.util.CoordinateUtil;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/map")
public class MapController {

    private static final Logger log = LoggerFactory.getLogger(MapController.class);

    private final MapService mapService;

    public MapController(MapService mapService) {
        this.mapService = mapService;
    }

    @GetMapping("/suggestion")
    @RateLimit(max = 60, duration = 60, key = "map_suggestion")
    public Result<List<POISuggestionDTO>> getSuggestions(@RequestParam(required = false) String keyword,
                                                         @RequestParam(required = false) String city) {
        log.info("获取地点联想请求: keyword={}, city={}", keyword, city);

        if (keyword == null || keyword.trim().isEmpty()) {
            return Result.fail("请输入搜索关键词");
        }

        if (keyword.length() > 100) {
            return Result.fail("关键词长度不能超过100个字符");
        }

        try {
            List<POISuggestionDTO> suggestions = mapService.getSuggestions(keyword, city);
            if (suggestions.isEmpty()) {
                return Result.ok("未找到相关地点", suggestions);
            }
            return Result.ok(suggestions);
        } catch (Exception e) {
            log.error("获取地点联想失败", e);
            return Result.fail("获取地点联想失败");
        }
    }

    @GetMapping("/detail")
    @RateLimit(max = 30, duration = 60, key = "map_detail")
    public Result<POIDetailDTO> getPOIDetail(
            @RequestParam(required = false) String uid,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        
        log.info("获取POI详情请求: uid={}, lat={}, lng={}", uid, lat, lng);
        
        if ((uid == null || uid.trim().isEmpty()) && (lat == null || lng == null)) {
            return Result.fail("请提供uid或经纬度参数");
        }

        if (uid != null && uid.length() > 100) {
            return Result.fail("uid长度不能超过100个字符");
        }

        try {
            POIDetailDTO detail = mapService.getPOIDetail(uid, lat, lng);
            if (detail == null) {
                return Result.fail("未找到该地点详情");
            }
            return Result.ok(detail);
        } catch (Exception e) {
            log.error("获取POI详情失败", e);
            return Result.fail("获取POI详情失败");
        }
    }

    @GetMapping("/hot-destinations")
    @RateLimit(max = 120, duration = 60, key = "map_hot_dest")
    public Result<List<HotDestinationDTO>> getHotDestinations() {
        log.info("获取热门目的地推荐请求");

        try {
            List<HotDestinationDTO> destinations = mapService.getHotDestinations();
            if (destinations.isEmpty()) {
                return Result.ok("暂无热门目的地数据", destinations);
            }
            return Result.ok(destinations);
        } catch (Exception e) {
            log.error("获取热门目的地失败", e);
            return Result.fail("获取热门目的地失败");
        }
    }

    @GetMapping("/city-attractions")
    @RateLimit(max = 30, duration = 60, key = "map_city_attr")
    public Result<List<AttractionDTO>> getCityAttractions(@RequestParam(required = false) String city) {
        log.info("获取城市景点请求: city={}", city);

        if (city == null || city.trim().isEmpty()) {
            return Result.fail("请提供城市名称参数");
        }

        if (city.length() > 50) {
            return Result.fail("城市名称长度不能超过50个字符");
        }

        try {
            List<AttractionDTO> attractions = mapService.getCityAttractions(city);
            if (attractions.isEmpty()) {
                return Result.ok("未找到该城市景点", attractions);
            }
            return Result.ok(attractions);
        } catch (Exception e) {
            log.error("获取城市景点失败", e);
            return Result.fail("获取城市景点失败");
        }
    }

    @GetMapping("/nearby-attractions")
    @RateLimit(max = 30, duration = 60, key = "map_nearby_attr")
    public Result<List<AttractionDTO>> getNearbyAttractions(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(defaultValue = "5000") int radius) {
        log.info("获取周边景点请求: lat={}, lng={}, radius={}", lat, lng, radius);

        if (lat == null || lng == null) {
            return Result.fail("请提供经纬度参数");
        }

        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            return Result.fail("经纬度参数范围不正确");
        }

        try {
            List<AttractionDTO> attractions = mapService.getNearbyAttractions(lat, lng, radius);
            if (attractions.isEmpty()) {
                return Result.ok("附近未找到景点", attractions);
            }
            return Result.ok(attractions);
        } catch (Exception e) {
            log.error("获取周边景点失败", e);
            return Result.fail("获取周边景点失败");
        }
    }

    /* ==================== 地理编码接口 ==================== */

    @GetMapping("/geocode")
    @RateLimit(max = 60, duration = 60, key = "map_geocode")
    public Result<double[]> geocode(@RequestParam String address) {
        log.info("地理编码请求: address={}", address);
        if (address == null || address.trim().isEmpty()) {
            return Result.fail("请提供地址");
        }
        try {
            // 1) 国内：高德/百度
            double[] coords = mapService.geocode(address.trim());
            if (coords != null) return Result.ok(coords);

            // 2) 国际兜底：Nominatim (OpenStreetMap，免费不限 Key)
            coords = nominatimGeocode(address.trim());
            if (coords != null) return Result.ok(coords);

            return Result.fail("未找到该地址");
        } catch (Exception e) {
            log.error("地理编码失败: {}", address, e);
            return Result.fail("地理编码失败");
        }
    }

    /** Nominatim 全球地理编码（免费，1次/秒限速） */
    private double[] nominatimGeocode(String address) {
        try {
            String url = "https://nominatim.openstreetmap.org/search?q="
                    + java.net.URLEncoder.encode(address, "UTF-8")
                    + "&format=json&limit=1&accept-language=zh";
            java.net.URL obj = new java.net.URL(url);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) obj.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "TravelApp/1.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            java.io.BufferedReader in = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) sb.append(line);
            in.close();
            conn.disconnect();

            // 解析 JSON（简单解析，避免依赖）
            String body = sb.toString();
            if (body.length() < 5 || !body.startsWith("[")) return null;
            // 提取第一个 lat/lon
            int latIdx = body.indexOf("\"lat\":\"");
            int lonIdx = body.indexOf("\"lon\":\"");
            if (latIdx < 0 || lonIdx < 0) return null;
            String latStr = body.substring(latIdx + 7, body.indexOf("\"", latIdx + 7));
            String lonStr = body.substring(lonIdx + 7, body.indexOf("\"", lonIdx + 7));
            return new double[]{Double.parseDouble(latStr), Double.parseDouble(lonStr)};
        } catch (Exception e) {
            log.warn("Nominatim 地理编码失败: {} — {}", address, e.getMessage());
            return null;
        }
    }

    /* ==================== 轮播景点图片（供首页线路规划卡片使用） ==================== */

    /** 16 个中国热门景点固定图（seed 策略：同景点永远同一张） */
    private static final List<String> SCENIC_PHOTOS = List.of(
        "https://picsum.photos/seed/gugong/800/400",
        "https://picsum.photos/seed/changchen/800/400",
        "https://picsum.photos/seed/xihu/800/400",
        "https://picsum.photos/seed/huangshan/800/400",
        "https://picsum.photos/seed/jiuzhaigou/800/400",
        "https://picsum.photos/seed/zhangjiajie/800/400",
        "https://picsum.photos/seed/lijiang/800/400",
        "https://picsum.photos/seed/bingmayong/800/400",
        "https://picsum.photos/seed/zhuozheng/800/400",
        "https://picsum.photos/seed/budalagong/800/400",
        "https://picsum.photos/seed/yalongwan/800/400",
        "https://picsum.photos/seed/gulangyu/800/400",
        "https://picsum.photos/seed/xiongmao/800/400",
        "https://picsum.photos/seed/laoshan/800/400",
        "https://picsum.photos/seed/erhai/800/400",
        "https://picsum.photos/seed/guilin/800/400"
    );

    @GetMapping("/scenic-photos")
    @RateLimit(max = 60, duration = 60, key = "scenic_photos")
    public Result<List<String>> getScenicPhotos() {
        return Result.ok(SCENIC_PHOTOS);
    }

    /* ==================== 地图标记数据接口（供 TripMapView 使用） ==================== */

    /**
     * 获取城市地标数据（含景点、地标建筑坐标）
     * 用于前端地图渲染 Marker 标注
     */
    @GetMapping("/landmarks")
    @RateLimit(max = 60, duration = 60, key = "map_landmarks")
    public Result<List<MapMarkerDTO>> getLandmarks(@RequestParam(required = false) String city) {
        log.info("获取城市地标: city={}", city);

        if (city == null || city.trim().isEmpty()) {
            return Result.fail("请提供城市名称");
        }

        try {
            List<MapMarkerDTO> landmarks = getLandmarkData(city.trim());
            return Result.ok(landmarks);
        } catch (Exception e) {
            log.error("获取城市地标失败: city={}", city, e);
            return Result.fail("获取地标数据失败");
        }
    }

    /**
     * 获取城市地铁站点坐标
     * 用于前端地图渲染地铁线路标注
     */
    @GetMapping("/metro-stations")
    @RateLimit(max = 60, duration = 60, key = "map_metro")
    public Result<List<MapMarkerDTO>> getMetroStations(@RequestParam(required = false) String city) {
        log.info("获取地铁站点: city={}", city);

        if (city == null || city.trim().isEmpty()) {
            return Result.fail("请提供城市名称");
        }

        try {
            List<MapMarkerDTO> stations = getMetroData(city.trim());
            return Result.ok(stations);
        } catch (Exception e) {
            log.error("获取地铁站点失败: city={}", city, e);
            return Result.fail("获取地铁数据失败");
        }
    }

    /* ==================== 地标种子数据（常用旅游城市） ==================== */

    /** 城市地标预设数据缓存 */
    private final ConcurrentHashMap<String, List<MapMarkerDTO>> landmarkCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<MapMarkerDTO>> metroCache = new ConcurrentHashMap<>();

    private List<MapMarkerDTO> getLandmarkData(String city) {
        return landmarkCache.computeIfAbsent(city, k -> normalizeMarkers(buildLandmarks(city)));
    }

    private List<MapMarkerDTO> getMetroData(String city) {
        return metroCache.computeIfAbsent(city, k -> normalizeMarkers(buildMetroStations(city)));
    }

    /**
     * 坐标归一化：硬编码数据基于 WGS-84，根据地图提供商转换为对应坐标系
     * - 百度地图需要 BD-09
     * - 高德地图需要 GCJ-02
     * - 海外坐标（巴黎等）不转换，直接使用 WGS-84
     */
    private List<MapMarkerDTO> normalizeMarkers(List<MapMarkerDTO> markers) {
        String provider = mapService.getProviderName();
        for (MapMarkerDTO m : markers) {
            double lat = m.getLatitude();
            double lng = m.getLongitude();
            // 仅转换中国境内坐标
            if (isInChina(lat, lng)) {
                double[] converted;
                if ("amap".equals(provider)) {
                    converted = CoordinateUtil.wgs84ToGcj02(lat, lng);
                } else {
                    // baidu: 转为 BD-09
                    converted = CoordinateUtil.wgs84ToBd09(lat, lng);
                }
                m.setLatitude(converted[0]);
                m.setLongitude(converted[1]);
            }
        }
        return markers;
    }

    /** 粗略判断坐标是否在中国境内 */
    private boolean isInChina(double lat, double lng) {
        return lng >= 72.0 && lng <= 137.8 && lat >= 0.8 && lat <= 55.8;
    }

    /**
     * 构建城市地标数据（预设热门旅游城市）
     */
    private List<MapMarkerDTO> buildLandmarks(String city) {
        List<MapMarkerDTO> list = new ArrayList<>();
        String cleanCity = city.replace("市", "");

        switch (cleanCity) {
            case "北京":
                list.add(marker("天安门广场", city, "landmark", 39.909, 116.397, "首都地标"));
                list.add(marker("故宫博物院", city, "attraction", 39.916, 116.397, "世界最大宫殿群"));
                list.add(marker("颐和园", city, "attraction", 39.999, 116.275, "皇家园林"));
                list.add(marker("天坛公园", city, "attraction", 39.882, 116.407, "明清祭天场所"));
                list.add(marker("鸟巢（国家体育场）", city, "landmark", 39.992, 116.388, "2008奥运主场馆"));
                list.add(marker("水立方", city, "landmark", 39.991, 116.384, "奥运游泳馆"));
                list.add(marker("798艺术区", city, "attraction", 39.984, 116.495, "当代艺术聚集地"));
                list.add(marker("南锣鼓巷", city, "attraction", 39.938, 116.403, "胡同文化街区"));
                list.add(marker("王府井大街", city, "landmark", 39.914, 116.411, "百年商业街"));
                list.add(marker("三里屯太古里", city, "landmark", 39.933, 116.455, "时尚潮流地标"));
                break;
            case "上海":
                list.add(marker("外滩", city, "landmark", 31.240, 121.490, "万国建筑博览"));
                list.add(marker("东方明珠塔", city, "landmark", 31.240, 121.500, "上海地标"));
                list.add(marker("豫园", city, "attraction", 31.229, 121.493, "江南古典园林"));
                list.add(marker("南京路步行街", city, "landmark", 31.238, 121.476, "中华第一商业街"));
                list.add(marker("迪士尼乐园", city, "attraction", 31.144, 121.658, "主题乐园"));
                list.add(marker("田子坊", city, "attraction", 31.210, 121.470, "艺术创意街区"));
                list.add(marker("上海博物馆", city, "attraction", 31.230, 121.474, "顶级博物馆"));
                list.add(marker("陆家嘴", city, "landmark", 31.236, 121.502, "金融中心"));
                break;
            case "巴黎":
                list.add(marker("埃菲尔铁塔", city, "landmark", 48.858, 2.294, "巴黎象征"));
                list.add(marker("卢浮宫", city, "attraction", 48.861, 2.336, "世界最大博物馆"));
                list.add(marker("凯旋门", city, "landmark", 48.874, 2.295, "拿破仑时期建筑"));
                list.add(marker("巴黎圣母院", city, "attraction", 48.853, 2.350, "哥特式教堂"));
                list.add(marker("蒙马特高地", city, "attraction", 48.887, 2.343, "艺术街区"));
                list.add(marker("塞纳河", city, "landmark", 48.858, 2.347, "巴黎母亲河"));
                list.add(marker("奥赛博物馆", city, "attraction", 48.860, 2.326, "印象派艺术收藏"));
                list.add(marker("圣心大教堂", city, "attraction", 48.887, 2.343, "蒙马特山顶教堂"));
                list.add(marker("凡尔赛宫", city, "attraction", 48.805, 2.120, "皇家宫殿"));
                list.add(marker("香榭丽舍大道", city, "landmark", 48.870, 2.308, "世界最美大道"));
                break;
            default:
                // 动态从地图服务获取城市景点作为地标
                List<AttractionDTO> attractions = mapService.getCityAttractions(city);
                if (!attractions.isEmpty()) {
                    for (int i = 0; i < Math.min(attractions.size(), 10); i++) {
                        AttractionDTO a = attractions.get(i);
                        if (a.getLat() != null && a.getLng() != null) {
                            list.add(marker(a.getName(), city, "attraction", a.getLat(), a.getLng(),
                                    a.getAddress() != null ? a.getAddress() : ""));
                        }
                    }
                }
                if (list.isEmpty()) {
                    list.add(marker(city + "市中心", city, "landmark", 39.915, 116.404, "城市中心"));
                }
                break;
        }
        return list;
    }

    /**
     * 构建地铁站点数据（预设热门旅游城市主要线路站点）
     */
    private List<MapMarkerDTO> buildMetroStations(String city) {
        List<MapMarkerDTO> list = new ArrayList<>();
        String cleanCity = city.replace("市", "");

        switch (cleanCity) {
            case "北京":
                list.add(marker("天安门东站", city, "metro", 39.908, 116.398, "1号线"));
                list.add(marker("西单站", city, "metro", 39.908, 116.375, "1号线/4号线"));
                list.add(marker("国贸站", city, "metro", 39.909, 116.461, "1号线/10号线"));
                list.add(marker("王府井站", city, "metro", 39.914, 116.411, "1号线"));
                list.add(marker("南锣鼓巷站", city, "metro", 39.938, 116.403, "6号线/8号线"));
                list.add(marker("奥林匹克公园站", city, "metro", 39.990, 116.391, "8号线/15号线"));
                list.add(marker("海淀黄庄站", city, "metro", 39.977, 116.308, "4号线/10号线"));
                list.add(marker("前门站", city, "metro", 39.899, 116.396, "2号线"));
                break;
            case "上海":
                list.add(marker("人民广场站", city, "metro", 31.233, 121.475, "1/2/8号线"));
                list.add(marker("南京东路站", city, "metro", 31.238, 121.485, "2号线/10号线"));
                list.add(marker("陆家嘴站", city, "metro", 31.236, 121.502, "2号线"));
                list.add(marker("豫园站", city, "metro", 31.229, 121.493, "10号线/14号线"));
                list.add(marker("静安寺站", city, "metro", 31.224, 121.447, "2号线/7号线"));
                list.add(marker("徐家汇站", city, "metro", 31.195, 121.437, "1/9/11号线"));
                break;
            case "巴黎":
                list.add(marker("Châtelet", city, "metro", 48.858, 2.347, "1/4/7/11/14号线"));
                list.add(marker("Concorde", city, "metro", 48.866, 2.323, "1/8/12号线"));
                list.add(marker("Charles de Gaulle-Étoile", city, "metro", 48.874, 2.295, "1/2/6号线"));
                list.add(marker("Opéra", city, "metro", 48.871, 2.332, "3/7/8号线"));
                list.add(marker("Saint-Germain-des-Prés", city, "metro", 48.854, 2.333, "4号线"));
                list.add(marker("Montparnasse", city, "metro", 48.842, 2.322, "4/6/12/13号线"));
                break;
            default:
                list.add(marker(city + "中心站", city, "metro", 39.915, 116.404, "地铁站"));
                break;
        }
        return list;
    }

    /** 快速构建 MapMarkerDTO 辅助方法 */
    private MapMarkerDTO marker(String name, String city, String type, double lat, double lng, String desc) {
        MapMarkerDTO dto = new MapMarkerDTO();
        dto.setName(name);
        dto.setCity(city);
        dto.setType(type);
        dto.setLatitude(lat);
        dto.setLongitude(lng);
        dto.setDescription(desc);
        return dto;
    }
}
