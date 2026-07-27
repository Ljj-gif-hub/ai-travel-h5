package org.example.traveljava.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.example.traveljava.config.MapConfig;
import org.example.traveljava.dto.AttractionDTO;
import org.example.traveljava.dto.HotDestinationDTO;
import org.example.traveljava.dto.POIDetailDTO;
import org.example.traveljava.dto.POISuggestionDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 高德地图（Amap）服务实现
 *
 * 高德 Web 服务 API 文档：https://lbs.amap.com/api/webservice/summary
 * 坐标系：GCJ-02（火星坐标）
 */
@Service
@ConditionalOnProperty(name = "map.provider", havingValue = "amap")
public class AmapService implements MapService {

    private static final Logger log = LoggerFactory.getLogger(AmapService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final MapConfig mapConfig;

    private String key;

    /** 全局调用限速：保证两次 API 调用至少间隔 300ms，避免触发高德 QPS 限制 */
    private static long lastApiCallTime = 0;
    private static final Object rateLock = new Object();

    private void rateLimit() {
        synchronized (rateLock) {
            long now = System.currentTimeMillis();
            long gap = 300 - (now - lastApiCallTime);
            if (gap > 0) {
                try { Thread.sleep(gap); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            lastApiCallTime = System.currentTimeMillis();
        }
    }

    /** 高德 API 端点 */
    private static final String INPUT_TIPS_URL = "https://restapi.amap.com/v3/assistant/inputtips";
    private static final String PLACE_DETAIL_URL = "https://restapi.amap.com/v3/place/detail";
    private static final String PLACE_TEXT_URL = "https://restapi.amap.com/v3/place/text";
    private static final String PLACE_AROUND_URL = "https://restapi.amap.com/v3/place/around";
    private static String imageUrl(String cityName) {
        return "https://picsum.photos/seed/" + java.net.URLEncoder.encode(cityName, java.nio.charset.StandardCharsets.UTF_8) + "/400/300";
    }

    /** 热门目的地缓存过期时间：1小时 */
    private static final long HOT_DEST_CACHE_TTL_MS = 60 * 60 * 1000L;

    /** 热门旅游城市固定列表 */
    private static final List<String> HOT_CITIES = Arrays.asList(
            "北京", "上海", "广州", "重庆", "成都", "三亚", "西安", "杭州", "深圳", "南京");

    /** 热门目的地缓存 */
    private final ConcurrentHashMap<String, CacheEntry<List<HotDestinationDTO>>> hotDestCache = new ConcurrentHashMap<>();

    public AmapService(RestTemplate restTemplate, ObjectMapper objectMapper, MapConfig mapConfig) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.mapConfig = mapConfig;
    }

    @PostConstruct
    private void init() {
        this.key = mapConfig.getAmap().getWebKey();
        if (key == null || key.isBlank()) {
            log.info("高德地图Web Key未配置 — 地图功能将使用本地默认数据（设置环境变量 AMAP_WEB_KEY 以启用实时地图）");
        } else {
            log.info("高德地图Web Key已配置，地图功能正常（坐标系: GCJ-02）");
        }
    }

    @Override
    public String getProviderName() { return "amap"; }

    @Override
    public String getCoordinateSystem() { return "gcj02"; }

    @Override
    public double[] geocode(String address) {
        if (key == null || key.isBlank() || address == null || address.isBlank()) return null;
        rateLimit();
        try {
            String url = UriComponentsBuilder.fromHttpUrl("https://restapi.amap.com/v3/geocode/geo")
                    .queryParam("address", address)
                    .queryParam("key", key)
                    .queryParam("output", "JSON")
                    .toUriString();
            String resp = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(resp);
            if ("1".equals(root.get("status").asText()) && root.get("geocodes").isArray()) {
                JsonNode geo = root.get("geocodes").get(0);
                String loc = geo.get("location").asText(); // "lng,lat"
                String[] parts = loc.split(",");
                return new double[]{Double.parseDouble(parts[1]), Double.parseDouble(parts[0])};
            }
        } catch (Exception e) { log.warn("高德地理编码失败: {}", address); }
        return null;
    }

    // ==================== 输入提示 ====================

    @Override
    public List<POISuggestionDTO> getSuggestions(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            log.warn("keyword为空，直接返回空列表");
            return Collections.emptyList();
        }

        if (key == null || key.isBlank()) {
            log.info("高德地图Key未配置，使用模拟数据（keyword={}）", keyword);
            return getMockSuggestions();
        }

        log.info("调用高德地图Input Tips API, keyword={}", keyword);
        rateLimit();
        try {
            String url = UriComponentsBuilder.fromHttpUrl(INPUT_TIPS_URL)
                    .queryParam("keywords", keyword.trim())
                    .queryParam("key", key)
                    .queryParam("datatype", "all")
                    .toUriString();

            String response = restTemplate.getForObject(url, String.class);
            return parseSuggestionResponse(response);
        } catch (RestClientException e) {
            log.error("调用高德地图Input Tips API失败", e);
            return getMockSuggestions();
        }
    }

    // ==================== POI 详情 ====================

    @Override
    public POIDetailDTO getPOIDetail(String uid, Double lat, Double lng) {
        if ((uid == null || uid.trim().isEmpty()) && (lat == null || lng == null)) {
            log.warn("uid和经纬度都为空");
            return null;
        }

        if (key == null || key.isBlank()) {
            log.info("高德地图Key未配置，POI详情返回null");
            return null;
        }

        log.info("调用高德地图Detail API, uid={}, lat={}, lng={}", uid, lat, lng);
        rateLimit();
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(PLACE_DETAIL_URL)
                    .queryParam("key", key);

            if (uid != null && !uid.trim().isEmpty()) {
                builder.queryParam("id", uid.trim());
            } else if (lat != null && lng != null) {
                // 高德 location 格式为 "lng,lat"
                builder.queryParam("location", lng + "," + lat);
            }

            String response = restTemplate.getForObject(builder.toUriString(), String.class);
            return parseDetailResponse(response);
        } catch (RestClientException e) {
            log.error("调用高德地图Detail API失败", e);
            return null;
        }
    }

    // ==================== 热门目的地 ====================

    @Override
    public List<HotDestinationDTO> getHotDestinations() {
        String cacheKey = "hot_destinations_amap";
        CacheEntry<List<HotDestinationDTO>> cached = hotDestCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.info("命中热门目的地缓存，直接返回");
            return cached.data;
        }

        if (key == null || key.isBlank()) {
            log.info("高德地图Key未配置，使用默认热门目的地数据");
            List<HotDestinationDTO> result = new ArrayList<>();
            for (String city : HOT_CITIES) {
                HotDestinationDTO dto = getDefaultHotDestination(city);
                int idx = HOT_CITIES.indexOf(city);
                dto.setHeat(100 - idx * 5);
                result.add(dto);
            }
            return result;
        }

        log.info("缓存未命中或已过期，开始调用高德地图获取热门目的地");
        List<HotDestinationDTO> result = new ArrayList<>();

        for (String city : HOT_CITIES) {
            try {
                HotDestinationDTO dto = fetchHotDestination(city);
                result.add(dto);
            } catch (Exception e) {
                log.error("获取城市[{}]热门信息失败，使用默认数据", city, e);
                result.add(getDefaultHotDestination(city));
            }
        }

        hotDestCache.put(cacheKey, new CacheEntry<>(result, System.currentTimeMillis() + HOT_DEST_CACHE_TTL_MS));
        log.info("热门目的地获取完成，共{}个城市，已写入缓存", result.size());
        return result;
    }

    // ==================== 城市景点 ====================

    @Override
    public List<AttractionDTO> getCityAttractions(String cityName) {
        if (cityName == null || cityName.trim().isEmpty()) {
            return Collections.emptyList();
        }

        if (key == null || key.isBlank()) {
            log.info("高德地图Key未配置，城市景点返回空列表（city={}）", cityName);
            return Collections.emptyList();
        }

        log.info("调用高德地图Text Search API获取城市景点, city={}", cityName);
        rateLimit();
        try {
            // type=110000 风景名胜, 110100 公园广场 — 过滤掉商铺/餐饮
            String url = UriComponentsBuilder.fromHttpUrl(PLACE_TEXT_URL)
                    .queryParam("keywords", "景点")
                    .queryParam("types", "110000|110100|140000")
                    .queryParam("city", cityName.trim())
                    .queryParam("key", key)
                    .queryParam("offset", 20)
                    .queryParam("page", 1)
                    .queryParam("extensions", "all")
                    .toUriString();

            String response = restTemplate.getForObject(url, String.class);
            return parseAttractionsResponse(response);
        } catch (RestClientException e) {
            log.error("调用高德地图Text Search API(城市景点)失败, city={}", cityName, e);
            return Collections.emptyList();
        }
    }

    // ==================== 周边景点 ====================

    @Override
    public List<AttractionDTO> getNearbyAttractions(double lat, double lng, int radius) {
        if (key == null || key.isBlank()) {
            log.info("高德地图Key未配置，周边景点返回空列表（lat={}, lng={}）", lat, lng);
            return Collections.emptyList();
        }

        log.info("调用高德地图Around Search API获取周边景点, lat={}, lng={}", lat, lng);
        rateLimit();
        try {
            // 高德 location 格式为 "lng,lat"
            String url = UriComponentsBuilder.fromHttpUrl(PLACE_AROUND_URL)
                    .queryParam("keywords", "景点")
                    .queryParam("location", lng + "," + lat)
                    .queryParam("radius", radius)
                    .queryParam("key", key)
                    .queryParam("offset", 20)
                    .queryParam("page", 1)
                    .queryParam("extensions", "all")
                    .toUriString();

            String response = restTemplate.getForObject(url, String.class);
            return parseAttractionsResponse(response);
        } catch (RestClientException e) {
            log.error("调用高德地图Around Search API(周边景点)失败, lat={}, lng={}", lat, lng, e);
            return Collections.emptyList();
        }
    }

    // ==================== 响应解析 ====================

    private List<POISuggestionDTO> parseSuggestionResponse(String response) {
        List<POISuggestionDTO> result = new ArrayList<>();

        if (response == null || response.isEmpty()) {
            log.warn("高德地图Input Tips API返回为空");
            return result;
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            // 高德 status=1 表示成功
            int status = root.has("status") ? root.get("status").asInt() : -1;

            if (status != 1) {
                String info = root.has("info") ? root.get("info").asText() : "";
                log.warn("高德地图Input Tips API返回错误, status={}, info={}, 使用模拟数据", status, info);
                return getMockSuggestions();
            }

            JsonNode tips = root.get("tips");
            if (tips != null && tips.isArray()) {
                for (JsonNode item : tips) {
                    String name = item.has("name") ? item.get("name").asText() : "";
                    String address = item.has("address") ? item.get("address").asText() : "";
                    String id = item.has("id") ? item.get("id").asText() : "";

                    if (name.isEmpty()) continue;

                    POISuggestionDTO dto = new POISuggestionDTO();
                    dto.setName(name);
                    dto.setAddress(address);
                    dto.setUid(id);

                    // 高德 location 格式 "lng,lat"
                    if (item.has("location") && !item.get("location").asText().isEmpty()) {
                        String[] loc = item.get("location").asText().split(",");
                        if (loc.length == 2) {
                            dto.setLng(Double.parseDouble(loc[0]));
                            dto.setLat(Double.parseDouble(loc[1]));
                        }
                    }

                    if (item.has("typecode")) {
                        dto.setType(item.get("typecode").asText());
                    }

                    result.add(dto);
                }
            }
        } catch (Exception e) {
            log.error("解析高德地图Input Tips响应失败", e);
        }

        log.info("高德地图Input Tips返回{}条结果", result.size());
        return result;
    }

    private POIDetailDTO parseDetailResponse(String response) {
        if (response == null || response.isEmpty()) {
            log.warn("高德地图Detail API返回为空");
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            int status = root.has("status") ? root.get("status").asInt() : -1;

            if (status != 1) {
                String info = root.has("info") ? root.get("info").asText() : "";
                log.warn("高德地图Detail API返回错误, status={}, info={}", status, info);
                return null;
            }

            JsonNode pois = root.get("pois");
            if (pois == null || !pois.isArray() || pois.size() == 0) {
                return null;
            }

            JsonNode poi = pois.get(0);
            POIDetailDTO dto = new POIDetailDTO();
            dto.setName(poi.has("name") ? poi.get("name").asText() : "");
            dto.setAddress(poi.has("address") ? poi.get("address").asText() : "");
            dto.setTelephone(poi.has("tel") ? poi.get("tel").asText() : "");
            dto.setType(poi.has("type") ? poi.get("type").asText() : "");

            // 高德 location 格式 "lng,lat"
            if (poi.has("location") && !poi.get("location").asText().isEmpty()) {
                String[] loc = poi.get("location").asText().split(",");
                if (loc.length == 2) {
                    dto.setLng(Double.parseDouble(loc[0]));
                    dto.setLat(Double.parseDouble(loc[1]));
                }
            }

            // 扩展信息
            if (poi.has("biz_ext")) {
                JsonNode biz = poi.get("biz_ext");
                if (biz.has("rating")) {
                    try { dto.setRating(Double.parseDouble(biz.get("rating").asText())); } catch (Exception e) {}
                }
                if (biz.has("cost")) {
                    dto.setPrice(biz.get("cost").asText());
                }
                if (biz.has("open_time")) {
                    dto.setOpenTime(biz.get("open_time").asText());
                }
            }

            // 深度信息
            if (poi.has("deep_info")) {
                JsonNode deep = poi.get("deep_info");
                if (deep.has("intro")) {
                    dto.setOverview(deep.get("intro").asText());
                }
            }

            // 图片
            if (poi.has("photos") && poi.get("photos").isArray()) {
                List<String> images = new ArrayList<>();
                for (JsonNode photo : poi.get("photos")) {
                    if (photo.has("url")) {
                        images.add(photo.get("url").asText());
                    }
                }
                dto.setImages(images);
            }

            return dto;
        } catch (Exception e) {
            log.error("解析高德地图Detail响应失败", e);
            return null;
        }
    }

    private List<AttractionDTO> parseAttractionsResponse(String response) {
        List<AttractionDTO> result = new ArrayList<>();

        if (response == null || response.isEmpty()) {
            log.warn("高德地图搜索API返回为空");
            return result;
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            int status = root.has("status") ? root.get("status").asInt() : -1;

            if (status != 1) {
                String info = root.has("info") ? root.get("info").asText() : "";
                log.warn("高德地图搜索API返回错误, status={}, info={}", status, info);
                return result;
            }

            JsonNode pois = root.get("pois");
            if (pois != null && pois.isArray()) {
                for (JsonNode poi : pois) {
                    String name = poi.has("name") ? poi.get("name").asText() : "";
                    if (name.isEmpty()) continue;

                    AttractionDTO dto = new AttractionDTO();
                    dto.setName(name);
                    dto.setAddress(poi.has("address") ? poi.get("address").asText() : "");
                    dto.setUid(poi.has("id") ? poi.get("id").asText() : "");
                    dto.setTag(poi.has("type") ? poi.get("type").asText() : "");

                    // 高德 location 格式 "lng,lat"
                    if (poi.has("location") && !poi.get("location").asText().isEmpty()) {
                        String[] loc = poi.get("location").asText().split(",");
                        if (loc.length == 2) {
                            dto.setLng(Double.parseDouble(loc[0]));
                            dto.setLat(Double.parseDouble(loc[1]));
                        }
                    }

                    // 评分
                    if (poi.has("biz_ext") && poi.get("biz_ext").has("rating")) {
                        try { dto.setRating(Double.parseDouble(poi.get("biz_ext").get("rating").asText())); } catch (Exception e) {}
                    }

                    // 图片（使用第一张照片作为景点图片）
                    if (poi.has("photos") && poi.get("photos").isArray() && poi.get("photos").size() > 0) {
                        JsonNode firstPhoto = poi.get("photos").get(0);
                        if (firstPhoto.has("url")) {
                            dto.setImageUrl(firstPhoto.get("url").asText());
                        }
                    }

                    // 无图片时不生成假图，前端自行兜底
                    if (dto.getImageUrl() == null || dto.getImageUrl().isEmpty()) {
                        dto.setImageUrl("");
                    }

                    result.add(dto);
                }
            }
        } catch (Exception e) {
            log.error("解析高德地图搜索响应失败", e);
        }

        log.info("高德地图搜索返回{}条景点结果", result.size());
        return result;
    }

    // ==================== 热门目的地获取 ====================

    private HotDestinationDTO fetchHotDestination(String cityName) {
        rateLimit();
        try {
            String url = UriComponentsBuilder.fromHttpUrl(PLACE_TEXT_URL)
                    .queryParam("keywords", "景点")
                    .queryParam("city", cityName)
                    .queryParam("key", key)
                    .queryParam("offset", 10)
                    .queryParam("page", 1)
                    .toUriString();

            String response = restTemplate.getForObject(url, String.class);
            HotDestinationDTO dto = parseHotDestinationResponse(response, cityName);
            if (dto != null) return dto;
        } catch (Exception e) {
            log.warn("调用高德地图Text Search API(城市搜索)失败，城市={}", cityName, e);
        }
        return getDefaultHotDestination(cityName);
    }

    private HotDestinationDTO parseHotDestinationResponse(String response, String cityName) {
        if (response == null || response.isEmpty()) return null;

        try {
            JsonNode root = objectMapper.readTree(response);
            int status = root.has("status") ? root.get("status").asInt() : -1;

            if (status != 1) return null;

            HotDestinationDTO dto = getDefaultHotDestination(cityName);

            // count 字段作为热度值
            int count = root.has("count") ? root.get("count").asInt() : 0;
            dto.setHeat(count);

            // 从第一条结果提取经纬度
            JsonNode pois = root.get("pois");
            if (pois != null && pois.isArray() && pois.size() > 0) {
                JsonNode first = pois.get(0);
                if (first.has("location") && !first.get("location").asText().isEmpty()) {
                    String[] loc = first.get("location").asText().split(",");
                    if (loc.length == 2) {
                        dto.setLng(Double.parseDouble(loc[0]));
                        dto.setLat(Double.parseDouble(loc[1]));
                    }
                }
            }

            return dto;
        } catch (Exception e) {
            log.error("解析高德地图搜索响应失败，城市={}", cityName, e);
            return null;
        }
    }

    // ==================== 模拟数据 ====================

    private List<POISuggestionDTO> getMockSuggestions() {
        List<POISuggestionDTO> suggestions = new ArrayList<>();

        String[][] cities = {
            {"北京", "北京市", "39.9042", "116.4074"},
            {"上海", "上海市", "31.2304", "121.4737"},
            {"杭州", "浙江省杭州市", "30.2741", "120.1551"},
            {"成都", "四川省成都市", "30.5728", "104.0668"},
            {"广州", "广东省广州市", "23.1291", "113.2644"},
        };

        for (String[] c : cities) {
            POISuggestionDTO dto = new POISuggestionDTO();
            dto.setName(c[0]);
            dto.setAddress(c[1]);
            dto.setLat(Double.parseDouble(c[2]));
            dto.setLng(Double.parseDouble(c[3]));
            suggestions.add(dto);
        }

        return suggestions;
    }

    private HotDestinationDTO getDefaultHotDestination(String cityName) {
        HotDestinationDTO dto = new HotDestinationDTO();
        dto.setName(cityName);
        dto.setImageUrl(imageUrl(cityName));

        switch (cityName) {
            case "北京": dto.setProvince("北京市"); dto.setLat(39.9042); dto.setLng(116.4074); dto.setDescription("千年古都，故宫长城尽显皇家气派"); break;
            case "上海": dto.setProvince("上海市"); dto.setLat(31.2304); dto.setLng(121.4737); dto.setDescription("魔都风情，外滩夜景与现代都市交融"); break;
            case "广州": dto.setProvince("广东省"); dto.setLat(23.1291); dto.setLng(113.2644); dto.setDescription("花城广州，美食天堂与岭南文化"); break;
            case "重庆": dto.setProvince("重庆市"); dto.setLat(29.5630); dto.setLng(106.5516); dto.setDescription("山城重庆，火锅与魔幻8D地形的代名词"); break;
            case "成都": dto.setProvince("四川省"); dto.setLat(30.5728); dto.setLng(104.0668); dto.setDescription("天府之国，熊猫故乡与悠闲慢生活"); break;
            case "三亚": dto.setProvince("海南省"); dto.setLat(18.2528); dto.setLng(109.5119); dto.setDescription("东方夏威夷，碧海蓝天与热带风情"); break;
            case "西安": dto.setProvince("陕西省"); dto.setLat(34.3416); dto.setLng(108.9398); dto.setDescription("十三朝古都，兵马俑诉说千年历史"); break;
            case "杭州": dto.setProvince("浙江省"); dto.setLat(30.2741); dto.setLng(120.1551); dto.setDescription("人间天堂，西湖美景与江南韵味"); break;
            case "深圳": dto.setProvince("广东省"); dto.setLat(22.5431); dto.setLng(114.0579); dto.setDescription("创新之城，主题乐园与都市活力"); break;
            case "南京": dto.setProvince("江苏省"); dto.setLat(32.0603); dto.setLng(118.7969); dto.setDescription("六朝古都，秦淮风光与历史沉淀"); break;
            default: dto.setProvince(""); dto.setDescription(cityName + "热门旅游目的地"); break;
        }
        return dto;
    }

    // ==================== 城市/景点图片搜索 ====================

    /** 全国城市 → 标志性地标精确映射 */
    private static final java.util.Map<String, String> CITY_LANDMARKS = new java.util.LinkedHashMap<>();
    static {
        // 直辖市
        CITY_LANDMARKS.put("北京", "天安门"); CITY_LANDMARKS.put("上海", "东方明珠");
        CITY_LANDMARKS.put("天津", "天津之眼"); CITY_LANDMARKS.put("重庆", "洪崖洞");
        // 港澳台
        CITY_LANDMARKS.put("香港", "维多利亚港"); CITY_LANDMARKS.put("澳门", "大三巴牌坊");
        CITY_LANDMARKS.put("台北", "台北101大楼"); CITY_LANDMARKS.put("新北", "九份老街");
        CITY_LANDMARKS.put("高雄", "高雄85大楼"); CITY_LANDMARKS.put("台中", "台中歌剧院");
        CITY_LANDMARKS.put("台南", "赤崁楼"); CITY_LANDMARKS.put("花莲", "太鲁阁");
        CITY_LANDMARKS.put("台东", "三仙台");
        // 广东
        CITY_LANDMARKS.put("广州", "广州塔"); CITY_LANDMARKS.put("深圳", "平安金融中心");
        CITY_LANDMARKS.put("珠海", "港珠澳大桥"); CITY_LANDMARKS.put("东莞", "国贸中心");
        CITY_LANDMARKS.put("佛山", "祖庙"); CITY_LANDMARKS.put("中山", "孙中山故居");
        CITY_LANDMARKS.put("惠州", "惠州西湖泗州塔"); CITY_LANDMARKS.put("汕头", "南澳岛");
        CITY_LANDMARKS.put("江门", "开平碉楼"); CITY_LANDMARKS.put("湛江", "海湾大桥");
        CITY_LANDMARKS.put("茂名", "中国第一滩"); CITY_LANDMARKS.put("肇庆", "七星岩");
        CITY_LANDMARKS.put("梅州", "客家围龙屋"); CITY_LANDMARKS.put("汕尾", "红海湾");
        CITY_LANDMARKS.put("河源", "万绿湖"); CITY_LANDMARKS.put("阳江", "海陵岛");
        CITY_LANDMARKS.put("清远", "古龙峡"); CITY_LANDMARKS.put("潮州", "广济桥");
        CITY_LANDMARKS.put("揭阳", "揭阳楼"); CITY_LANDMARKS.put("云浮", "国恩寺");
        CITY_LANDMARKS.put("韶关", "丹霞山");
        // 浙江
        CITY_LANDMARKS.put("杭州", "雷峰塔"); CITY_LANDMARKS.put("宁波", "天一阁");
        CITY_LANDMARKS.put("温州", "雁荡山"); CITY_LANDMARKS.put("嘉兴", "乌镇");
        CITY_LANDMARKS.put("湖州", "南浔古镇"); CITY_LANDMARKS.put("绍兴", "鲁迅故里");
        CITY_LANDMARKS.put("金华", "横店影视城"); CITY_LANDMARKS.put("舟山", "普陀山");
        CITY_LANDMARKS.put("衢州", "江郎山"); CITY_LANDMARKS.put("台州", "神仙居");
        CITY_LANDMARKS.put("丽水", "古堰画乡");
        // 江苏
        CITY_LANDMARKS.put("南京", "中山陵"); CITY_LANDMARKS.put("苏州", "拙政园");
        CITY_LANDMARKS.put("无锡", "灵山大佛"); CITY_LANDMARKS.put("常州", "天目湖");
        CITY_LANDMARKS.put("南通", "狼山"); CITY_LANDMARKS.put("扬州", "瘦西湖");
        CITY_LANDMARKS.put("镇江", "金山寺"); CITY_LANDMARKS.put("徐州", "云龙湖");
        CITY_LANDMARKS.put("盐城", "大丰麋鹿园"); CITY_LANDMARKS.put("泰州", "溱潼古镇");
        CITY_LANDMARKS.put("淮安", "周恩来纪念馆"); CITY_LANDMARKS.put("连云港", "花果山");
        CITY_LANDMARKS.put("宿迁", "项王故里");
        // 四川
        CITY_LANDMARKS.put("成都", "宽窄巷子"); CITY_LANDMARKS.put("绵阳", "越王楼");
        CITY_LANDMARKS.put("德阳", "三星堆"); CITY_LANDMARKS.put("宜宾", "蜀南竹海");
        CITY_LANDMARKS.put("南充", "阆中古城"); CITY_LANDMARKS.put("泸州", "泸州老窖");
        CITY_LANDMARKS.put("乐山", "乐山大佛"); CITY_LANDMARKS.put("眉山", "三苏祠");
        CITY_LANDMARKS.put("自贡", "恐龙博物馆"); CITY_LANDMARKS.put("攀枝花", "二滩水电站");
        CITY_LANDMARKS.put("广元", "剑门关"); CITY_LANDMARKS.put("遂宁", "灵泉寺");
        CITY_LANDMARKS.put("内江", "张大千纪念馆"); CITY_LANDMARKS.put("广安", "邓小平故里");
        CITY_LANDMARKS.put("达州", "巴山大峡谷"); CITY_LANDMARKS.put("雅安", "碧峰峡");
        CITY_LANDMARKS.put("巴中", "光雾山"); CITY_LANDMARKS.put("资阳", "安岳石刻");
        CITY_LANDMARKS.put("阿坝", "九寨沟"); CITY_LANDMARKS.put("甘孜", "稻城亚丁");
        CITY_LANDMARKS.put("凉山", "泸沽湖");
        // 湖北
        CITY_LANDMARKS.put("武汉", "黄鹤楼"); CITY_LANDMARKS.put("宜昌", "三峡大坝");
        CITY_LANDMARKS.put("襄阳", "襄阳古城"); CITY_LANDMARKS.put("荆州", "荆州古城");
        CITY_LANDMARKS.put("恩施", "恩施大峡谷");
        // 湖南
        CITY_LANDMARKS.put("长沙", "橘子洲"); CITY_LANDMARKS.put("株洲", "炎帝陵");
        CITY_LANDMARKS.put("湘潭", "韶山"); CITY_LANDMARKS.put("衡阳", "南岳衡山");
        CITY_LANDMARKS.put("岳阳", "岳阳楼"); CITY_LANDMARKS.put("常德", "桃花源");
        CITY_LANDMARKS.put("张家界", "天门山"); CITY_LANDMARKS.put("凤凰", "凤凰古城");
        // 福建
        CITY_LANDMARKS.put("福州", "三坊七巷"); CITY_LANDMARKS.put("厦门", "鼓浪屿");
        CITY_LANDMARKS.put("泉州", "开元寺"); CITY_LANDMARKS.put("漳州", "南靖土楼");
        // 山东
        CITY_LANDMARKS.put("济南", "趵突泉"); CITY_LANDMARKS.put("青岛", "五四广场");
        CITY_LANDMARKS.put("烟台", "蓬莱阁"); CITY_LANDMARKS.put("威海", "刘公岛");
        CITY_LANDMARKS.put("泰安", "泰山"); CITY_LANDMARKS.put("曲阜", "孔庙");
        CITY_LANDMARKS.put("潍坊", "风筝广场"); CITY_LANDMARKS.put("日照", "万平口");
        // 河南
        CITY_LANDMARKS.put("郑州", "二七纪念塔"); CITY_LANDMARKS.put("洛阳", "龙门石窟");
        CITY_LANDMARKS.put("开封", "清明上河园"); CITY_LANDMARKS.put("安阳", "殷墟");
        CITY_LANDMARKS.put("南阳", "武侯祠");
        // 河北
        CITY_LANDMARKS.put("石家庄", "赵州桥"); CITY_LANDMARKS.put("唐山", "清东陵");
        CITY_LANDMARKS.put("秦皇岛", "山海关"); CITY_LANDMARKS.put("承德", "避暑山庄");
        CITY_LANDMARKS.put("保定", "直隶总督署");
        // 辽宁
        CITY_LANDMARKS.put("沈阳", "沈阳故宫"); CITY_LANDMARKS.put("大连", "星海广场");
        CITY_LANDMARKS.put("鞍山", "千山"); CITY_LANDMARKS.put("丹东", "鸭绿江断桥");
        // 陕西
        CITY_LANDMARKS.put("西安", "大雁塔"); CITY_LANDMARKS.put("延安", "宝塔山");
        CITY_LANDMARKS.put("咸阳", "兵马俑"); CITY_LANDMARKS.put("宝鸡", "法门寺");
        CITY_LANDMARKS.put("华山", "华山");
        // 云南
        CITY_LANDMARKS.put("昆明", "石林"); CITY_LANDMARKS.put("大理", "大理古城");
        CITY_LANDMARKS.put("丽江", "丽江古城"); CITY_LANDMARKS.put("香格里拉", "松赞林寺");
        CITY_LANDMARKS.put("西双版纳", "大金塔"); CITY_LANDMARKS.put("腾冲", "热海");
        // 贵州
        CITY_LANDMARKS.put("贵阳", "甲秀楼"); CITY_LANDMARKS.put("遵义", "遵义会议会址");
        CITY_LANDMARKS.put("安顺", "黄果树瀑布"); CITY_LANDMARKS.put("毕节", "百里杜鹃");
        CITY_LANDMARKS.put("铜仁", "梵净山"); CITY_LANDMARKS.put("黔东南", "千户苗寨");
        CITY_LANDMARKS.put("黔南", "小七孔"); CITY_LANDMARKS.put("黔西南", "万峰林");
        CITY_LANDMARKS.put("六盘水", "乌蒙大草原");
        // 广西
        CITY_LANDMARKS.put("南宁", "东盟会展中心"); CITY_LANDMARKS.put("桂林", "象鼻山");
        CITY_LANDMARKS.put("柳州", "马鞍山"); CITY_LANDMARKS.put("北海", "涠洲岛");
        CITY_LANDMARKS.put("阳朔", "阳朔山水"); CITY_LANDMARKS.put("玉林", "云天宫");
        // 海南
        CITY_LANDMARKS.put("海口", "骑楼老街"); CITY_LANDMARKS.put("三亚", "天涯海角");
        // 安徽
        CITY_LANDMARKS.put("合肥", "逍遥津"); CITY_LANDMARKS.put("黄山", "迎客松");
        CITY_LANDMARKS.put("芜湖", "方特"); CITY_LANDMARKS.put("九华山", "九华山");
        // 江西
        CITY_LANDMARKS.put("南昌", "滕王阁"); CITY_LANDMARKS.put("九江", "庐山");
        CITY_LANDMARKS.put("景德镇", "景德镇古窑"); CITY_LANDMARKS.put("井冈山", "井冈山");
        CITY_LANDMARKS.put("婺源", "婺源油菜花");
        // 山西
        CITY_LANDMARKS.put("太原", "晋祠"); CITY_LANDMARKS.put("大同", "云冈石窟");
        CITY_LANDMARKS.put("平遥", "平遥古城"); CITY_LANDMARKS.put("五台山", "五台山");
        // 吉林
        CITY_LANDMARKS.put("长春", "伪满皇宫"); CITY_LANDMARKS.put("吉林", "雾凇岛");
        CITY_LANDMARKS.put("长白山", "长白山天池");
        // 黑龙江
        CITY_LANDMARKS.put("哈尔滨", "索菲亚教堂"); CITY_LANDMARKS.put("漠河", "北极村");
        CITY_LANDMARKS.put("雪乡", "雪乡");
        // 甘肃
        CITY_LANDMARKS.put("兰州", "黄河铁桥"); CITY_LANDMARKS.put("嘉峪关", "嘉峪关");
        CITY_LANDMARKS.put("敦煌", "莫高窟"); CITY_LANDMARKS.put("张掖", "七彩丹霞");
        CITY_LANDMARKS.put("酒泉", "鸣沙山月牙泉");
        // 内蒙古
        CITY_LANDMARKS.put("呼和浩特", "大召寺"); CITY_LANDMARKS.put("包头", "五当召");
        CITY_LANDMARKS.put("鄂尔多斯", "成吉思汗陵"); CITY_LANDMARKS.put("呼伦贝尔", "呼伦贝尔草原");
        CITY_LANDMARKS.put("满洲里", "国门"); CITY_LANDMARKS.put("额济纳", "胡杨林");
        // 新疆
        CITY_LANDMARKS.put("乌鲁木齐", "国际大巴扎"); CITY_LANDMARKS.put("吐鲁番", "火焰山");
        CITY_LANDMARKS.put("喀什", "艾提尕尔清真寺"); CITY_LANDMARKS.put("伊犁", "那拉提草原");
        CITY_LANDMARKS.put("阿勒泰", "喀纳斯"); CITY_LANDMARKS.put("天山", "天山天池");
        // 西藏
        CITY_LANDMARKS.put("拉萨", "布达拉宫"); CITY_LANDMARKS.put("日喀则", "扎什伦布寺");
        CITY_LANDMARKS.put("林芝", "南迦巴瓦峰"); CITY_LANDMARKS.put("珠峰", "珠峰大本营");
        // 青海
        CITY_LANDMARKS.put("西宁", "塔尔寺"); CITY_LANDMARKS.put("青海湖", "青海湖");
        // 宁夏
        CITY_LANDMARKS.put("银川", "西夏王陵"); CITY_LANDMARKS.put("中卫", "沙坡头");
        // 国际
        CITY_LANDMARKS.put("东京", "东京塔"); CITY_LANDMARKS.put("大阪", "大阪城");
        CITY_LANDMARKS.put("首尔", "南山塔"); CITY_LANDMARKS.put("曼谷", "大皇宫");
        CITY_LANDMARKS.put("新加坡", "鱼尾狮"); CITY_LANDMARKS.put("吉隆坡", "双子塔");
        CITY_LANDMARKS.put("巴厘岛", "海神庙"); CITY_LANDMARKS.put("迪拜", "哈利法塔");
        CITY_LANDMARKS.put("巴黎", "埃菲尔铁塔"); CITY_LANDMARKS.put("伦敦", "大本钟");
        CITY_LANDMARKS.put("纽约", "自由女神像"); CITY_LANDMARKS.put("罗马", "斗兽场");
        CITY_LANDMARKS.put("悉尼", "悉尼歌剧院"); CITY_LANDMARKS.put("马尔代夫", "水上屋");
        CITY_LANDMARKS.put("布拉格", "查理大桥"); CITY_LANDMARKS.put("莫斯科", "圣瓦西里大教堂");
        CITY_LANDMARKS.put("威尼斯", "圣马可广场"); CITY_LANDMARKS.put("巴塞罗那", "圣家堂");
        CITY_LANDMARKS.put("米兰", "米兰大教堂"); CITY_LANDMARKS.put("雅典", "帕特农神庙");
        CITY_LANDMARKS.put("圣托里尼", "蓝顶教堂"); CITY_LANDMARKS.put("雷克雅未克", "哈尔格林姆教堂");
        CITY_LANDMARKS.put("洛杉矶", "好莱坞标志"); CITY_LANDMARKS.put("旧金山", "金门大桥");
        CITY_LANDMARKS.put("多伦多", "CN塔"); CITY_LANDMARKS.put("伊斯坦布尔", "蓝色清真寺");
        CITY_LANDMARKS.put("开罗", "金字塔"); CITY_LANDMARKS.put("吴哥窟", "吴哥窟");
    }

    /**
     * 从高德 POI 搜索获取城市标志性建筑照片
     * 优先用已知地标名搜索，再用城市名+标志性建筑
     */
    public String fetchCityPhoto(String cityName) {
        if (key == null || key.isBlank() || cityName == null || cityName.isBlank()) {
            return null;
        }

        // 策略1：用已知地标名精确搜索
        String landmark = CITY_LANDMARKS.get(cityName);
        if (landmark != null) {
            String photo = searchPhotoByKeywords(landmark, cityName);
            if (photo != null) return photo;
        }

        // 策略2：城市名 + 标志性建筑
        String photo = searchPhotoByKeywords(cityName + " 标志性建筑", cityName);
        if (photo != null) return photo;

        // 策略3：城市名 + 著名景点
        return searchPhotoByKeywords(cityName + " 著名景点", cityName);
    }

    /**
     * 从高德 POI 搜索获取景点图片
     * @param scenicName 景点名称
     * @param city 所在城市
     * @return 图片URL，未找到返回 null
     */
    public String fetchAttractionPhoto(String scenicName, String city) {
        if (key == null || key.isBlank() || scenicName == null || scenicName.isBlank()) {
            return null;
        }
        return searchPhotoByKeywords(scenicName, city != null ? city : "全国");
    }

    private String searchPhotoByKeywords(String keywords, String city) {
        rateLimit();
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(PLACE_TEXT_URL)
                    .queryParam("keywords", keywords)
                    .queryParam("key", key)
                    .queryParam("offset", 1)
                    .queryParam("page", 1)
                    .queryParam("extensions", "all");
            // city 参数可选，不传则全国搜索
            if (city != null && !city.isEmpty() && !"全国".equals(city)) {
                builder.queryParam("city", city);
            }
            String apiUrl = builder.toUriString();

            String response = restTemplate.getForObject(apiUrl, String.class);
            if (response == null) return null;

            JsonNode root = objectMapper.readTree(response);
            if (!root.has("status") || !"1".equals(root.get("status").asText())) {
                String info = root.has("info") ? root.get("info").asText() : "未知错误";
                log.warn("高德POI图片搜索失败: keywords={}, status={}, info={}", keywords,
                        root.has("status") ? root.get("status").asText() : "?", info);
                return null;
            }

            JsonNode pois = root.get("pois");
            if (pois == null || !pois.isArray() || pois.size() == 0) return null;

            JsonNode poi = pois.get(0);

            // 优先取 photos 中第一张非用户评论的官方照片
            if (poi.has("photos") && poi.get("photos").isArray() && poi.get("photos").size() > 0) {
                for (JsonNode photo : poi.get("photos")) {
                    if (photo.has("url")) {
                        String url = photo.get("url").asText();
                        if (!url.isEmpty() && !url.contains("/comment/") && !url.contains("comment/")) {
                            return url;
                        }
                    }
                }
                // 兜底：所有照片都是评论图，取第一张
                JsonNode first = poi.get("photos").get(0);
                if (first.has("url") && !first.get("url").asText().isEmpty()) {
                    return first.get("url").asText();
                }
            }

            // 兜底：高德静态图（不消耗调用额度，但不保证有照片）
            return null;
        } catch (Exception e) {
            log.warn("高德POI图片搜索失败: keywords={}, city={}, error={}", keywords, city, e.getMessage());
            return null;
        }
    }

    // ==================== 缓存工具 ====================

    private static class CacheEntry<T> {
        final T data;
        final long expireAt;

        CacheEntry(T data, long expireAt) {
            this.data = data;
            this.expireAt = expireAt;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
}
