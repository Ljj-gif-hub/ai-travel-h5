package org.example.traveljava.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.example.traveljava.config.MapConfig;
import org.example.traveljava.dto.SceneImageDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * 景点图片服务（仅后台预生成阶段调用）
 * 实现二级降级逻辑：
 * 1. 优先级1：百度地图POI详情接口获取官方实景图片
 * 2. 优先级2：公开静态兜底图（无防盗链）
 *
 * 百度地图POI详情接口文档：
 * https://lbsyun.baidu.com/index.php?title=webapi/guide/webservice-placeapi
 *
 * AK申请规范：
 * 1. 应用类型选择「服务端」
 * 2. IP白名单设置为服务器IP或0.0.0.0/0（开发环境）
 */
@Service
public class SceneImageService {

    private static final Logger log = LoggerFactory.getLogger(SceneImageService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final MapConfig mapConfig;

    private static final String BAIDU_PLACE_DETAIL_URL = "https://api.map.baidu.com/place/v2/detail";
    private static final String BAIDU_PLACE_SEARCH_URL = "https://api.map.baidu.com/place/v2/search";

    private static final String DEFAULT_IMAGE_URL = "https://picsum.photos/id/1036/800/480";

    private static final int TIMEOUT_MS = 3000;

    private final Cache<String, SceneImageDTO> imageCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(500)
            .build();

    public SceneImageService(ObjectMapper objectMapper, MapConfig mapConfig) {
        this.objectMapper = objectMapper;
        this.mapConfig = mapConfig;
        this.restTemplate = createRestTemplateWithTimeout();
    }

    private RestTemplate createRestTemplateWithTimeout() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MS);
        factory.setReadTimeout(TIMEOUT_MS);
        return new RestTemplate(factory);
    }

    public SceneImageDTO getSceneImage(String scenicName, String scenicDesc) {
        if (scenicName == null || scenicName.trim().isEmpty()) {
            log.warn("[图片获取] 景点名称为空，返回默认图片");
            return getDefaultImage();
        }

        String cacheKey = scenicName.trim();
        SceneImageDTO cached = imageCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("[图片获取] 命中缓存: {}, url: {}", scenicName, cached.getImgUrl());
            return cached;
        }

        log.info("[图片获取开始] 景点: {}", scenicName);
        
        SceneImageDTO result = fetchImageWithFallback(scenicName);
        imageCache.put(cacheKey, result);
        
        log.info("[图片获取完成] 景点: {}, 来源: {}, url: {}", 
                scenicName, result.getSource(), result.getImgUrl());
        
        return result;
    }

    private SceneImageDTO fetchImageWithFallback(String scenicName) {
        log.info("[降级第1级] 尝试百度地图POI接口获取实景图片...");
        try {
            String imageUrl = fetchFromBaiduMap(scenicName);
            if (imageUrl != null && !imageUrl.isEmpty()) {
                log.info("[降级第1级] 百度地图命中成功: {}", scenicName);
                return new SceneImageDTO(imageUrl, "map");
            }
            log.info("[降级第1级] 百度地图无图片，进入下一级");
        } catch (Exception e) {
            log.warn("[降级第1级] 百度地图获取失败，进入下一级: {}", scenicName, e);
        }

        log.info("[降级第2级] 使用静态兜底图: {}", scenicName);
        return getDefaultImage();
    }

    private String fetchFromBaiduMap(String scenicName) {
        long startTime = System.currentTimeMillis();
        log.debug("[百度地图] 开始获取图片: {}", scenicName);
        
        try {
            String uid = searchPOIUid(scenicName);
            if (uid == null || uid.isEmpty()) {
                log.debug("[百度地图] 搜索POI未找到uid: {}", scenicName);
                return null;
            }
            log.debug("[百度地图] 搜索POI成功，uid: {}", uid);

            String url = UriComponentsBuilder.fromHttpUrl(BAIDU_PLACE_DETAIL_URL)
                    .queryParam("uid", uid)
                    .queryParam("output", "json")
                    .queryParam("scope", 2)
                    .queryParam("ak", mapConfig.getBaidu().getAk())
                    .toUriString();

            log.debug("[百度地图] POI详情请求URL: {}", url);

            String response = restTemplate.getForObject(url, String.class);
            String imageUrl = parseBaiduDetailResponse(response);
            
            log.debug("[百度地图] 请求耗时: {}ms, 图片URL: {}", 
                    System.currentTimeMillis() - startTime, 
                    imageUrl != null ? imageUrl.substring(0, Math.min(imageUrl.length(), 50)) + "..." : "null");
            
            return imageUrl;
        } catch (RestClientException e) {
            log.warn("[百度地图] REST请求异常: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("[百度地图] 获取图片异常: {}", scenicName, e);
            return null;
        }
    }

    private String searchPOIUid(String scenicName) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(BAIDU_PLACE_SEARCH_URL)
                    .queryParam("query", scenicName)
                    .queryParam("region", "全国")
                    .queryParam("output", "json")
                    .queryParam("ak", mapConfig.getBaidu().getAk())
                    .toUriString();

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            if (root.has("status") && root.get("status").asInt() == 0) {
                JsonNode results = root.get("results");
                if (results != null && results.isArray() && results.size() > 0) {
                    JsonNode firstResult = results.get(0);
                    if (firstResult.has("uid")) {
                        return firstResult.get("uid").asText();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[百度地图] 搜索POI失败: {}", scenicName, e);
        }
        return null;
    }

    private String parseBaiduDetailResponse(String response) {
        if (response == null || response.isEmpty()) {
            return null;
        }

        try {
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
            log.error("[百度地图] 解析详情响应失败", e);
        }
        return null;
    }

    private SceneImageDTO getDefaultImage() {
        log.debug("[兜底图] 返回静态默认图片");
        return new SceneImageDTO(DEFAULT_IMAGE_URL, "default");
    }
}