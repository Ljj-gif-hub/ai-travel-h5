package org.example.traveljava.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.example.traveljava.config.MapConfig;
import org.example.traveljava.dto.AttractionDTO;
import org.example.traveljava.dto.HotDestinationDTO;
import org.example.traveljava.dto.POIDetailDTO;
import org.example.traveljava.dto.POISuggestionDTO;
import org.example.traveljava.entity.AttractionImage;
import org.example.traveljava.repository.AttractionImageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(name = "map.provider", havingValue = "baidu", matchIfMissing = true)
public class BaiduMapService implements MapService {

    private static final Logger log = LoggerFactory.getLogger(BaiduMapService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AttractionImageRepository attractionImageRepository;
    private final MapConfig mapConfig;

    private String ak;

    private static final String SUGGESTION_URL = "https://api.map.baidu.com/place/v2/suggestion";
    private static final String DETAIL_URL = "https://api.map.baidu.com/place/v2/detail";
    private static final String PLACE_SEARCH_URL = "https://api.map.baidu.com/place/v2/search";
    private static String imageUrl(String name) {
        try {
            return "https://picsum.photos/seed/" + java.net.URLEncoder.encode(name, "UTF-8") + "/400/300";
        } catch (Exception e) { return "https://picsum.photos/400/300"; }
    }

    /** 热门目的地缓存过期时间：1小时 */
    private static final long HOT_DEST_CACHE_TTL_MS = 60 * 60 * 1000L;

    /** 热门旅游城市固定列表 */
    private static final List<String> HOT_CITIES = Arrays.asList(
            "北京", "上海", "广州", "重庆", "成都", "三亚", "西安", "杭州", "深圳", "南京");

    /** 热门目的地缓存：key固定，value为带过期时间的缓存条目 */
    private final ConcurrentHashMap<String, CacheEntry<List<HotDestinationDTO>>> hotDestCache = new ConcurrentHashMap<>();

    public BaiduMapService(RestTemplate restTemplate, ObjectMapper objectMapper,
                          AttractionImageRepository attractionImageRepository,
                          MapConfig mapConfig) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.attractionImageRepository = attractionImageRepository;
        this.mapConfig = mapConfig;
    }

    @PostConstruct
    private void init() {
        this.ak = mapConfig.getBaidu().getAk();
        if (ak == null || ak.isBlank()) {
            log.info("百度地图AK未配置 — 地图功能将使用本地默认数据（设置环境变量 BAIDU_MAP_AK 以启用实时地图）");
        } else {
            log.info("百度地图AK已配置，地图功能正常");
        }
    }

    @Override
    public String getProviderName() { return "baidu"; }

    @Override
    public String getCoordinateSystem() { return "bd09"; }

    @Override
    public double[] geocode(String address) {
        if (ak == null || ak.isBlank() || address == null || address.isBlank()) return null;
        try {
            String url = UriComponentsBuilder.fromHttpUrl("https://api.map.baidu.com/geocoding/v3/")
                    .queryParam("address", address)
                    .queryParam("output", "json")
                    .queryParam("ak", ak)
                    .toUriString();
            String resp = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(resp);
            if (root.has("status") && root.get("status").asInt() == 0) {
                JsonNode loc = root.get("result").get("location");
                return new double[]{loc.get("lat").asDouble(), loc.get("lng").asDouble()};
            }
        } catch (Exception e) { log.warn("百度地理编码失败: {}", address); }
        return null;
    }

    /** 简单内存缓存条目，包含数据与过期时间戳 */
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

    public List<POISuggestionDTO> getSuggestions(String keyword, String city) {
        if (keyword == null || keyword.trim().isEmpty()) {
            log.warn("keyword为空，直接返回空列表");
            return Collections.emptyList();
        }

        if (ak == null || ak.isBlank()) {
            log.info("百度地图AK未配置，使用模拟数据（keyword={}）", keyword);
            return getMockSuggestions();
        }

        log.info("调用百度地图Suggestion API, keyword={}, city={}", keyword, city);

        try {
            String url = UriComponentsBuilder.fromHttpUrl(SUGGESTION_URL)
                    .queryParam("query", keyword.trim())
                    .queryParam("region", (city != null && !city.isBlank()) ? city.trim() : "全国")
                    .queryParam("city_limit", false)
                    .queryParam("output", "json")
                    .queryParam("ak", ak)
                    .toUriString();

            String response = restTemplate.getForObject(url, String.class);
            return parseSuggestionResponse(response);
        } catch (RestClientException e) {
            log.error("调用百度地图Suggestion API失败", e);
            return getMockSuggestions();
        }
    }

    private List<POISuggestionDTO> getMockSuggestions() {
        List<POISuggestionDTO> suggestions = new ArrayList<>();
        
        POISuggestionDTO beijing = new POISuggestionDTO();
        beijing.setName("北京");
        beijing.setAddress("北京市");
        beijing.setLat(39.9042);
        beijing.setLng(116.4074);
        suggestions.add(beijing);
        
        POISuggestionDTO shanghai = new POISuggestionDTO();
        shanghai.setName("上海");
        shanghai.setAddress("上海市");
        shanghai.setLat(31.2304);
        shanghai.setLng(121.4737);
        suggestions.add(shanghai);
        
        POISuggestionDTO hangzhou = new POISuggestionDTO();
        hangzhou.setName("杭州");
        hangzhou.setAddress("浙江省杭州市");
        hangzhou.setLat(30.2741);
        hangzhou.setLng(120.1551);
        suggestions.add(hangzhou);
        
        POISuggestionDTO chengdu = new POISuggestionDTO();
        chengdu.setName("成都");
        chengdu.setAddress("四川省成都市");
        chengdu.setLat(30.5728);
        chengdu.setLng(104.0668);
        suggestions.add(chengdu);
        
        POISuggestionDTO guangzhou = new POISuggestionDTO();
        guangzhou.setName("广州");
        guangzhou.setAddress("广东省广州市");
        guangzhou.setLat(23.1291);
        guangzhou.setLng(113.2644);
        suggestions.add(guangzhou);
        
        return suggestions;
    }

    public POIDetailDTO getPOIDetail(String uid, Double lat, Double lng) {
        if (uid == null || uid.trim().isEmpty()) {
            if (lat == null || lng == null) {
                log.warn("uid和经纬度都为空");
                return null;
            }
            return getPOIDetailByLocation(lat, lng);
        }

        log.info("调用百度地图Detail API, uid={}", uid);

        try {
            String url = UriComponentsBuilder.fromHttpUrl(DETAIL_URL)
                    .queryParam("uid", uid)
                    .queryParam("output", "json")
                    .queryParam("scope", 2)
                    .queryParam("ak", ak)
                    .toUriString();

            String response = restTemplate.getForObject(url, String.class);
            return parseDetailResponse(response);
        } catch (RestClientException e) {
            log.error("调用百度地图Detail API失败", e);
            return null;
        }
    }

    private POIDetailDTO getPOIDetailByLocation(Double lat, Double lng) {
        log.info("调用百度地图Detail API, lat={}, lng={}", lat, lng);

        try {
            String url = UriComponentsBuilder.fromHttpUrl(DETAIL_URL)
                    .queryParam("location", lat + "," + lng)
                    .queryParam("output", "json")
                    .queryParam("scope", 2)
                    .queryParam("ak", ak)
                    .toUriString();

            String response = restTemplate.getForObject(url, String.class);
            return parseDetailResponse(response);
        } catch (RestClientException e) {
            log.error("调用百度地图Detail API(经纬度)失败", e);
            return null;
        }
    }

    private List<POISuggestionDTO> parseSuggestionResponse(String response) {
        List<POISuggestionDTO> result = new ArrayList<>();

        if (response == null || response.isEmpty()) {
            log.warn("百度地图Suggestion API返回为空");
            return result;
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            int status = root.has("status") ? root.get("status").asInt() : -1;

            if (status != 0) {
                log.warn("百度地图Suggestion API返回错误, status={}, 使用模拟数据", status);
                return getMockSuggestions();
            }

            JsonNode results = root.get("result");
            if (results != null && results.isArray()) {
                for (JsonNode item : results) {
                    String name = item.has("name") ? item.get("name").asText() : "";
                    String address = item.has("address") ? item.get("address").asText() : "";
                    String uid = item.has("uid") ? item.get("uid").asText() : "";
                    String type = item.has("type") ? item.get("type").asText() : "";

                    if (name.isEmpty()) {
                        continue;
                    }

                    POISuggestionDTO dto = new POISuggestionDTO();
                    dto.setName(name);
                    dto.setAddress(address);
                    dto.setUid(uid);
                    dto.setType(type);

                    JsonNode location = item.get("location");
                    if (location != null) {
                        if (location.has("lat")) {
                            dto.setLat(location.get("lat").asDouble());
                        }
                        if (location.has("lng")) {
                            dto.setLng(location.get("lng").asDouble());
                        }
                    }

                    result.add(dto);
                }
            }
        } catch (Exception e) {
            log.error("解析百度地图Suggestion响应失败", e);
        }

        log.info("百度地图Suggestion返回{}条结果", result.size());
        return result;
    }

    private POIDetailDTO parseDetailResponse(String response) {
        if (response == null || response.isEmpty()) {
            log.warn("百度地图Detail API返回为空");
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            int status = root.has("status") ? root.get("status").asInt() : -1;

            if (status != 0) {
                log.warn("百度地图Detail API返回错误, status={}", status);
                return null;
            }

            JsonNode result = root.get("result");
            if (result == null) {
                return null;
            }

            POIDetailDTO dto = new POIDetailDTO();
            dto.setName(result.has("name") ? result.get("name").asText() : "");
            dto.setAddress(result.has("address") ? result.get("address").asText() : "");
            dto.setTelephone(result.has("telephone") ? result.get("telephone").asText() : "");
            dto.setType(result.has("type") ? result.get("type").asText() : "");
            dto.setTag(result.has("tag") ? result.get("tag").asText() : "");
            dto.setPrice(result.has("price") ? result.get("price").asText() : "");
            dto.setOpenTime(result.has("open_time") ? result.get("open_time").asText() : "");

            if (result.has("overall_rating")) {
                dto.setRating(result.get("overall_rating").asDouble());
            }
            if (result.has("comment_num")) {
                dto.setCommentCount(result.get("comment_num").asInt());
            }

            if (result.has("detail_info")) {
                JsonNode detail = result.get("detail_info");
                if (detail.has("tag")) {
                    dto.setTag(detail.get("tag").asText());
                }
                if (detail.has("price")) {
                    dto.setPrice(detail.get("price").asText());
                }
                if (detail.has("open_time")) {
                    dto.setOpenTime(detail.get("open_time").asText());
                }
                if (detail.has("overall_rating")) {
                    dto.setRating(detail.get("overall_rating").asDouble());
                }
                if (detail.has("comment_num")) {
                    dto.setCommentCount(detail.get("comment_num").asInt());
                }
            }

            if (result.has("location")) {
                JsonNode location = result.get("location");
                if (location.has("lat")) {
                    dto.setLat(location.get("lat").asDouble());
                }
                if (location.has("lng")) {
                    dto.setLng(location.get("lng").asDouble());
                }
            }

            if (result.has("photos")) {
                List<String> images = new ArrayList<>();
                JsonNode photos = result.get("photos");
                for (JsonNode photo : photos) {
                    if (photo.has("url")) {
                        images.add(photo.get("url").asText());
                    }
                }
                dto.setImages(images);
            }

            if (result.has("introduction")) {
                dto.setOverview(result.get("introduction").asText());
            } else if (result.has("detail_info") && result.get("detail_info").has("introduction")) {
                dto.setOverview(result.get("detail_info").get("introduction").asText());
            }

            return dto;
        } catch (Exception e) {
            log.error("解析百度地图Detail响应失败", e);
            return null;
        }
    }

    /**
     * 获取全国热门旅游城市列表
     * 使用内存缓存（1小时过期），避免频繁请求百度地图
     */
    public List<HotDestinationDTO> getHotDestinations() {
        String cacheKey = "hot_destinations";
        CacheEntry<List<HotDestinationDTO>> cached = hotDestCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.info("命中热门目的地缓存，直接返回");
            return cached.data;
        }

        if (ak == null || ak.isBlank()) {
            log.info("百度地图AK未配置，使用默认热门目的地数据");
            List<HotDestinationDTO> result = new ArrayList<>();
            for (String city : HOT_CITIES) {
                HotDestinationDTO dto = getDefaultHotDestination(city);
                int idx = HOT_CITIES.indexOf(city);
                dto.setHeat(100 - idx * 5); // 手动热度排序
                result.add(dto);
            }
            return result;
        }

        log.info("缓存未命中或已过期，开始调用百度地图获取热门目的地");
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

        // 写入缓存
        hotDestCache.put(cacheKey, new CacheEntry<>(result, System.currentTimeMillis() + HOT_DEST_CACHE_TTL_MS));
        log.info("热门目的地获取完成，共{}个城市，已写入缓存", result.size());
        return result;
    }

    private HotDestinationDTO fetchHotDestination(String cityName) {
        try {
            String urlStr = PLACE_SEARCH_URL + "?query=" + java.net.URLEncoder.encode("景点", "UTF-8") + "&region=" + java.net.URLEncoder.encode(cityName, "UTF-8") + "&output=json&page_size=10&ak=" + ak;

            java.net.URL url = new java.net.URL(urlStr);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            conn.disconnect();
            
            HotDestinationDTO dto = parseHotDestinationResponse(response.toString(), cityName);
            if (dto != null) {
                return dto;
            }
        } catch (Exception e) {
            log.warn("调用百度地图Place API(城市搜索)失败，城市={}，使用默认数据", cityName, e);
        }
        return getDefaultHotDestination(cityName);
    }

    private HotDestinationDTO parseHotDestinationResponse(String response, String cityName) {
        if (response == null || response.isEmpty()) {
            log.warn("百度地图Place API返回为空，城市={}", cityName);
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            int status = root.has("status") ? root.get("status").asInt() : -1;

            if (status != 0) {
                log.warn("百度地图Place API返回错误, status={}, 城市={}", status, cityName);
                return null;
            }

            HotDestinationDTO dto = getDefaultHotDestination(cityName);

            // 使用total字段作为热度值
            int total = root.has("total") ? root.get("total").asInt() : 0;
            dto.setHeat(total);

            // 尝试从第一条结果中提取经纬度，使数据更精确
            JsonNode results = root.get("results");
            if (results != null && results.isArray() && results.size() > 0) {
                JsonNode first = results.get(0);
                JsonNode location = first.get("location");
                if (location != null) {
                    if (location.has("lat")) {
                        dto.setLat(location.get("lat").asDouble());
                    }
                    if (location.has("lng")) {
                        dto.setLng(location.get("lng").asDouble());
                    }
                }
            }

            return dto;
        } catch (Exception e) {
            log.error("解析百度地图Place响应失败，城市={}", cityName, e);
            return null;
        }
    }

    /** 预设默认热门城市数据（含城市名+经纬度+简介） */
    private HotDestinationDTO getDefaultHotDestination(String cityName) {
        HotDestinationDTO dto = new HotDestinationDTO();
        dto.setName(cityName);
        dto.setImageUrl(imageUrl(cityName));

        switch (cityName) {
            case "北京":
                dto.setProvince("北京市");
                dto.setLat(39.9042);
                dto.setLng(116.4074);
                dto.setDescription("千年古都，故宫长城尽显皇家气派");
                break;
            case "上海":
                dto.setProvince("上海市");
                dto.setLat(31.2304);
                dto.setLng(121.4737);
                dto.setDescription("魔都风情，外滩夜景与现代都市交融");
                break;
            case "广州":
                dto.setProvince("广东省");
                dto.setLat(23.1291);
                dto.setLng(113.2644);
                dto.setDescription("花城广州，美食天堂与岭南文化");
                break;
            case "重庆":
                dto.setProvince("重庆市");
                dto.setLat(29.5630);
                dto.setLng(106.5516);
                dto.setDescription("山城重庆，火锅与魔幻8D地形的代名词");
                break;
            case "成都":
                dto.setProvince("四川省");
                dto.setLat(30.5728);
                dto.setLng(104.0668);
                dto.setDescription("天府之国，熊猫故乡与悠闲慢生活");
                break;
            case "三亚":
                dto.setProvince("海南省");
                dto.setLat(18.2528);
                dto.setLng(109.5119);
                dto.setDescription("东方夏威夷，碧海蓝天与热带风情");
                break;
            case "西安":
                dto.setProvince("陕西省");
                dto.setLat(34.3416);
                dto.setLng(108.9398);
                dto.setDescription("十三朝古都，兵马俑诉说千年历史");
                break;
            case "杭州":
                dto.setProvince("浙江省");
                dto.setLat(30.2741);
                dto.setLng(120.1551);
                dto.setDescription("人间天堂，西湖美景与江南韵味");
                break;
            case "深圳":
                dto.setProvince("广东省");
                dto.setLat(22.5431);
                dto.setLng(114.0579);
                dto.setDescription("创新之城，主题乐园与都市活力");
                break;
            case "南京":
                dto.setProvince("江苏省");
                dto.setLat(32.0603);
                dto.setLng(118.7969);
                dto.setDescription("六朝古都，秦淮风光与历史沉淀");
                break;
            default:
                dto.setProvince("");
                dto.setDescription(cityName + "热门旅游目的地");
        }
        return dto;
    }

    /**
     * 获取城市内景点列表
     */
    public List<AttractionDTO> getCityAttractions(String cityName) {
        if (cityName == null || cityName.trim().isEmpty()) {
            log.warn("cityName为空，直接返回空列表");
            return Collections.emptyList();
        }

        if (ak == null || ak.isBlank()) {
            log.info("百度地图AK未配置，城市景点返回空列表（city={}）", cityName);
            return Collections.emptyList();
        }

        log.info("调用百度地图Place API获取城市景点, city={}", cityName);

        try {
            String urlStr = PLACE_SEARCH_URL + "?query=" + java.net.URLEncoder.encode("景点", "UTF-8") + "&region=" + java.net.URLEncoder.encode(cityName.trim(), "UTF-8") + "&output=json&page_size=20&ak=" + ak;

            log.info("请求URL: {}", urlStr);
            
            java.net.URL url = new java.net.URL(urlStr);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            conn.disconnect();
            
            log.info("HTTP响应码: {}", conn.getResponseCode());
            String responseBody = response.toString();
            log.info("响应长度: {}", responseBody.length());
            log.info("响应前500字符: {}", responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody);
            
            return parseAttractionsResponse(responseBody);
        } catch (Exception e) {
            log.error("调用百度地图Place API(城市景点)失败, city={}", cityName, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取周边景点
     */
    public List<AttractionDTO> getNearbyAttractions(double lat, double lng, int radius) {
        if (ak == null || ak.isBlank()) {
            log.info("百度地图AK未配置，周边景点返回空列表（lat={}, lng={}）", lat, lng);
            return Collections.emptyList();
        }

        log.info("调用百度地图Place API获取周边景点, lat={}, lng={}", lat, lng);

        try {
            String urlStr = PLACE_SEARCH_URL + "?query=" + java.net.URLEncoder.encode("景点", "UTF-8") + "&location=" + lat + "," + lng + "&radius=" + radius + "&output=json&page_size=20&ak=" + ak;

            log.info("请求URL: {}", urlStr);
            
            java.net.URL url = new java.net.URL(urlStr);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            conn.disconnect();
            
            return parseAttractionsResponse(response.toString());
        } catch (Exception e) {
            log.error("调用百度地图Place API(周边景点)失败, lat={}, lng={}", lat, lng, e);
            return Collections.emptyList();
        }
    }

    private List<AttractionDTO> parseAttractionsResponse(String response) {
        List<AttractionDTO> result = new ArrayList<>();

        if (response == null || response.isEmpty()) {
            log.warn("百度地图Place API返回为空");
            return result;
        }

        log.info("百度地图Place API响应长度: {}", response.length());
        log.info("百度地图Place API响应前500字符: {}", response.length() > 500 ? response.substring(0, 500) + "..." : response);

        try {
            JsonNode root = objectMapper.readTree(response);
            int status = root.has("status") ? root.get("status").asInt() : -1;

            log.info("百度地图Place API status: {}", status);

            if (status != 0) {
                log.warn("百度地图Place API返回错误, status={}", status);
                return result;
            }

            JsonNode results = root.get("results");
            log.info("百度地图Place API results节点: {}", results == null ? "null" : (results.isArray() ? "array, size=" + results.size() : "not array"));

            if (results != null && results.isArray()) {
                for (JsonNode item : results) {
                    String name = item.has("name") ? item.get("name").asText() : "";
                    log.info("景点名称: {}", name);
                    if (name.isEmpty()) {
                        continue;
                    }

                    AttractionDTO dto = new AttractionDTO();
                    dto.setName(name);
                    dto.setAddress(item.has("address") ? item.get("address").asText() : "");
                    dto.setUid(item.has("uid") ? item.get("uid").asText() : "");
                    dto.setTag(item.has("tag") ? item.get("tag").asText() : "");

                    String imageUrl = getAttractionImageUrl(name, dto.getUid());
                    dto.setImageUrl(imageUrl);

                    JsonNode location = item.get("location");
                    if (location != null) {
                        if (location.has("lat")) {
                            dto.setLat(location.get("lat").asDouble());
                        }
                        if (location.has("lng")) {
                            dto.setLng(location.get("lng").asDouble());
                        }
                    }

                    // 评分可能在detail_info中
                    if (item.has("detail_info")) {
                        JsonNode detail = item.get("detail_info");
                        if (detail.has("overall_rating")) {
                            try {
                                dto.setRating(Double.parseDouble(detail.get("overall_rating").asText()));
                            } catch (NumberFormatException nfe) {
                                log.debug("评分解析失败: {}", detail.get("overall_rating").asText());
                            }
                        }
                        if (detail.has("tag")) {
                            dto.setTag(detail.get("tag").asText());
                        }
                        if (detail.has("introduction")) {
                            dto.setOverview(detail.get("introduction").asText());
                        }
                    }

                    result.add(dto);
                }
            }
        } catch (Exception e) {
            log.error("解析百度地图Place响应失败", e);
        }

        log.info("百度地图Place返回{}条景点结果", result.size());
        return result;
    }

    private String getAttractionImageUrl(String attractionName, String uid) {
        Optional<AttractionImage> dbImage = attractionImageRepository.findByAttractionName(attractionName);
        if (dbImage.isPresent() && dbImage.get().getImageUrl() != null && !dbImage.get().getImageUrl().isEmpty()) {
            log.debug("[景点图片] 数据库已有缓存: {}", attractionName);
            return dbImage.get().getImageUrl();
        }

        String imageUrl = fetchRealImageFromBaidu(attractionName, uid);
        if (imageUrl != null && !imageUrl.isEmpty()) {
            saveAttractionImage(attractionName, imageUrl, "baidu_map");
            return imageUrl;
        }

        String fallbackUrl = imageUrl(attractionName);
        saveAttractionImage(attractionName, fallbackUrl, "picsum");
        return fallbackUrl;
    }

    private String fetchRealImageFromBaidu(String attractionName, String uid) {
        if (uid == null || uid.isEmpty()) {
            log.debug("[百度地图] 无uid，跳过图片获取: {}", attractionName);
            return null;
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl(DETAIL_URL)
                    .queryParam("uid", uid)
                    .queryParam("output", "json")
                    .queryParam("scope", 2)
                    .queryParam("ak", ak)
                    .toUriString();

            log.debug("[百度地图] 获取图片详情URL: {}", url);

            String response = restTemplate.getForObject(url, String.class);
            if (response == null || response.isEmpty()) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response);
            if (root.has("status") && root.get("status").asInt() != 0) {
                log.warn("[百度地图] 返回错误状态: {}", root.get("status").asInt());
                return null;
            }

            JsonNode result = root.get("result");
            if (result == null) {
                return null;
            }

            if (result.has("photos")) {
                JsonNode photos = result.get("photos");
                if (photos.isArray() && photos.size() > 0) {
                    JsonNode firstPhoto = photos.get(0);
                    if (firstPhoto.has("url")) {
                        return firstPhoto.get("url").asText();
                    }
                }
            }

            if (result.has("detail_info") && result.get("detail_info").has("image_list")) {
                JsonNode imageList = result.get("detail_info").get("image_list");
                if (imageList.isArray() && imageList.size() > 0) {
                    JsonNode firstImage = imageList.get(0);
                    if (firstImage.has("url")) {
                        return firstImage.get("url").asText();
                    }
                }
            }

        } catch (Exception e) {
            log.warn("[百度地图] 获取图片失败: {}", attractionName, e);
        }

        return null;
    }

    private void saveAttractionImage(String attractionName, String imageUrl, String source) {
        try {
            Optional<AttractionImage> existing = attractionImageRepository.findByAttractionName(attractionName);
            if (existing.isPresent()) {
                AttractionImage ai = existing.get();
                if (!imageUrl.equals(ai.getImageUrl())) {
                    ai.setImageUrl(imageUrl);
                    ai.setSource(source);
                    ai.setUpdatedAt(LocalDateTime.now());
                    attractionImageRepository.save(ai);
                    log.info("[景点图片] 更新数据库: {} -> {}", attractionName, source);
                }
            } else {
                AttractionImage ai = new AttractionImage(attractionName, imageUrl, source);
                attractionImageRepository.save(ai);
                log.info("[景点图片] 保存到数据库: {} -> {}", attractionName, source);
            }
        } catch (Exception e) {
            log.error("[景点图片] 保存数据库失败: {}", attractionName, e);
        }
    }
}