package org.example.traveljava.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.traveljava.config.MapConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 【新功能】天气服务 — 基于高德天气 API（weatherInfo）。
 *
 * 查询策略：
 *  1. 城市名直接查 weatherInfo?city={city}&extensions=all
 *  2. 失败时降级：config/district 接口取城市 adcode，再用 adcode 查
 *  3. Key 未配置 / 全部失败：抛 IllegalStateException，由 Controller 返回 HTTP 502 友好提示
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private static final String WEATHER_URL = "https://restapi.amap.com/v3/weather/weatherInfo";
    private static final String DISTRICT_URL = "https://restapi.amap.com/v3/config/district";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final MapConfig mapConfig;

    public WeatherService(RestTemplate restTemplate, ObjectMapper objectMapper, MapConfig mapConfig) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.mapConfig = mapConfig;
    }

    /**
     * 查询城市天气（含今明后 4 天预报）。
     * @throws IllegalStateException Key 未配置或高德接口失败（调用方转 502）
     */
    public Map<String, Object> getWeather(String city) {
        String key = mapConfig.getAmap().getWebKey();
        if (key == null || key.isBlank()) {
            log.warn("高德天气 Key 未配置，无法查询天气");
            throw new IllegalStateException("天气服务暂不可用：地图服务 Key 未配置，请联系管理员");
        }
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("城市名不能为空");
        }

        // 1) 城市名直接查
        JsonNode root = callWeather(city, key);
        if (root == null) {
            // 2) 降级：城市名 → adcode → 再查
            String adcode = resolveAdcode(city, key);
            if (adcode != null && !adcode.isBlank()) {
                log.info("天气城市名查询失败，改用 adcode 查询: city={}, adcode={}", city, adcode);
                root = callWeather(adcode, key);
            }
        }
        if (root == null) {
            throw new IllegalStateException("天气服务暂时不可用，请稍后重试");
        }
        return buildWeatherMap(root, city);
    }

    /** 调用高德 weatherInfo（extensions=all 返回实时+预报） */
    private JsonNode callWeather(String cityOrAdcode, String key) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(WEATHER_URL)
                    .queryParam("city", cityOrAdcode)
                    .queryParam("key", key)
                    .queryParam("extensions", "all")
                    .build().encode().toUriString();
            String resp = restTemplate.getForObject(url, String.class);
            if (resp == null || resp.isEmpty()) {
                log.warn("天气接口返回空: {}", cityOrAdcode);
                return null;
            }
            JsonNode root = objectMapper.readTree(resp);
            if (root == null || !"1".equals(root.path("status").asText())) {
                log.warn("天气接口返回错误: city={}, info={}", cityOrAdcode, root != null ? root.path("info").asText() : "null");
                return null;
            }
            return root;
        } catch (Exception e) {
            log.warn("天气接口调用异常: city={}, error={}", cityOrAdcode, e.getMessage());
            return null;
        }
    }

    /** config/district 接口：城市名 → adcode（区县级兜底） */
    private String resolveAdcode(String city, String key) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(DISTRICT_URL)
                    .queryParam("keywords", city)
                    .queryParam("key", key)
                    .queryParam("subdistrict", "0")
                    .build().encode().toUriString();
            String resp = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(resp);
            JsonNode districts = root.path("districts");
            if (districts.isArray() && districts.size() > 0) {
                String adcode = districts.get(0).path("adcode").asText();
                if (!adcode.isEmpty()) return adcode;
            }
        } catch (Exception e) {
            log.warn("行政区划查询失败: city={}, error={}", city, e.getMessage());
        }
        return null;
    }

    /** 组装友好天气结构：实时 + 预报列表 */
    private Map<String, Object> buildWeatherMap(JsonNode root, String city) {
        Map<String, Object> result = new LinkedHashMap<>();
        JsonNode lives = root.path("lives");
        if (lives.isArray() && lives.size() > 0) {
            JsonNode live = lives.get(0);
            result.put("city", text(live, "city", city));
            result.put("reportTime", text(live, "reporttime", ""));
            result.put("weather", text(live, "weather", ""));
            result.put("temperature", text(live, "temperature", ""));
            result.put("windDirection", text(live, "winddirection", ""));
            result.put("windPower", text(live, "windpower", ""));
            result.put("humidity", text(live, "humidity", ""));
        }
        List<Map<String, Object>> forecast = new ArrayList<>();
        JsonNode forecasts = root.path("forecasts");
        if (forecasts.isArray() && forecasts.size() > 0) {
            JsonNode casts = forecasts.get(0).path("casts");
            if (casts.isArray()) {
                for (JsonNode cast : casts) {
                    Map<String, Object> day = new LinkedHashMap<>();
                    day.put("date", text(cast, "date", ""));
                    day.put("week", text(cast, "week", ""));
                    day.put("dayWeather", text(cast, "dayweather", ""));
                    day.put("nightWeather", text(cast, "nightweather", ""));
                    day.put("dayTemp", text(cast, "daytemp", ""));
                    day.put("nightTemp", text(cast, "nighttemp", ""));
                    day.put("dayWind", text(cast, "daywind", ""));
                    day.put("nightWind", text(cast, "nightwind", ""));
                    day.put("dayPower", text(cast, "daypower", ""));
                    day.put("nightPower", text(cast, "nightpower", ""));
                    forecast.add(day);
                }
            }
        }
        result.put("forecast", forecast);
        return result;
    }

    private String text(JsonNode node, String field, String defaultValue) {
        JsonNode v = node.get(field);
        return v != null && !v.asText().isEmpty() ? v.asText() : defaultValue;
    }
}
