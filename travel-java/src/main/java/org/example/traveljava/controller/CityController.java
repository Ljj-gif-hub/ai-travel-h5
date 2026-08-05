package org.example.traveljava.controller;

import org.example.traveljava.dto.CityDTO.*;
import org.example.traveljava.entity.AttractionImage;
import org.example.traveljava.entity.CityImage;
import org.example.traveljava.entity.CityMaterial;
import org.example.traveljava.annotation.RateLimit;
import org.example.traveljava.repository.AttractionImageRepository;
import org.example.traveljava.repository.CityImageRepository;
import org.example.traveljava.repository.CityMaterialRepository;
import org.example.traveljava.service.AmapService;
import org.example.traveljava.service.CityMaterialService;
import org.example.traveljava.service.CityService;
import org.example.traveljava.util.AuthUtils;
import org.example.traveljava.util.JwtUtil;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 城市选择接口 — 境内/境外城市列表、模糊搜索、定位解析
 */
@RestController
@RequestMapping("/api/city")
@io.swagger.v3.oas.annotations.tags.Tag(name = "地图")
public class CityController {

    private static final Logger log = LoggerFactory.getLogger(CityController.class);
    private final CityService cityService;
    private final CityImageRepository cityImageRepository;
    private final CityMaterialService cityMaterialService;
    private final CityMaterialRepository cityMaterialRepository;
    private final AttractionImageRepository attractionImageRepository;
    private final JwtUtil jwtUtil;

    @Autowired(required = false)
    private AmapService amapService;

    public CityController(CityService cityService, CityImageRepository cityImageRepository,
                          CityMaterialService cityMaterialService, CityMaterialRepository cityMaterialRepository,
                          AttractionImageRepository attractionImageRepository, JwtUtil jwtUtil) {
        this.cityService = cityService;
        this.cityImageRepository = cityImageRepository;
        this.cityMaterialService = cityMaterialService;
        this.cityMaterialRepository = cityMaterialRepository;
        this.attractionImageRepository = attractionImageRepository;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 1. 境内城市接口
     * GET /api/city/domestic
     * 返回：热门城市 + 分组(直辖市/港澳台) + 省份列表
     */
    @GetMapping("/domestic")
    public Result<DomesticResponse> getDomesticCities() {
        try {
            DomesticResponse data = cityService.getDomesticCities();
            return Result.ok(data);
        } catch (Exception e) {
            log.error("获取境内城市列表失败", e);
            return Result.fail("获取境内城市列表失败");
        }
    }

    /**
     * 2. 境外城市接口
     * GET /api/city/overseas
     * 返回：热门城市 + 大洲 → 国家 → 城市
     */
    @GetMapping("/overseas")
    public Result<OverseasResponse> getOverseasCities() {
        try {
            OverseasResponse data = cityService.getOverseasCities();
            return Result.ok(data);
        } catch (Exception e) {
            log.error("获取境外城市列表失败", e);
            return Result.fail("获取境外城市列表失败");
        }
    }

    /**
     * 3. 城市搜索模糊匹配接口
     * GET /api/city/search?keyword=北京
     * 返回：匹配的境内外城市列表（最多20条）
     */
    @GetMapping("/search")
    public Result<List<SearchResult>> searchCities(@RequestParam String keyword) {
        try {
            if (keyword == null || keyword.trim().isEmpty()) {
                return Result.fail("请输入搜索关键词");
            }
            List<SearchResult> data = cityService.searchCities(keyword.trim());
            return Result.ok(data);
        } catch (Exception e) {
            log.error("城市搜索失败: keyword={}", keyword, e);
            return Result.fail("搜索失败，请重试");
        }
    }

    /**
     * 4. 定位解析接口
     * GET /api/city/location?lat=22.5431&lng=114.0579
     * 调用百度地图逆地理编码，返回城市名称
     */
    @GetMapping("/location")
    public Result<LocationResult> reverseGeocode(@RequestParam double lat, @RequestParam double lng) {
        try {
            LocationResult data = cityService.reverseGeocode(lat, lng);
            if (data.getCity().isEmpty()) {
                return Result.fail("未能解析当前位置");
            }
            return Result.ok(data);
        } catch (Exception e) {
            log.error("定位解析失败: lat={}, lng={}", lat, lng, e);
            return Result.fail("定位解析失败");
        }
    }

    /**
     * 获取热门城市（快捷接口）
     * GET /api/city/hot?type=domestic|overseas
     */
    @GetMapping("/hot")
    public Result<Map<String, Object>> getHotCities(@RequestParam(defaultValue = "domestic") String type) {
        try {
            Map<String, Object> data = new HashMap<>();
            if ("overseas".equals(type)) {
                OverseasResponse overseas = cityService.getOverseasCities();
                data.put("overseas", overseas.getHotCities());
            } else {
                DomesticResponse domestic = cityService.getDomesticCities();
                data.put("domestic", domestic.getHotCities());
            }
            return Result.ok(data);
        } catch (Exception e) {
            log.error("获取热门城市失败", e);
            return Result.fail("获取热门城市失败");
        }
    }

    /**
     * 5. 城市图片代理接口
     * GET /api/city/image?name=深圳
     * 优先数据库缓存 → 高德 POI 实景照片 → Picsum 兜底
     */
    @GetMapping("/image")
    public ResponseEntity<Void> getCityImage(@RequestParam String name) {
        String url;
        // 1) 查数据库缓存
        Optional<CityImage> dbImage = cityImageRepository.findByCityName(name);
        if (dbImage.isPresent() && dbImage.get().getImageUrl() != null && !dbImage.get().getImageUrl().isEmpty()) {
            url = dbImage.get().getImageUrl();
        } else {
            // 2) 尝试高德 POI 搜索获取实景照片
            String amapPhoto = null;
            if (amapService != null) {
                try {
                    amapPhoto = amapService.fetchCityPhoto(name);
                } catch (Exception e) {
                    log.debug("高德图片搜索失败: {} — {}", name, e.getMessage());
                }
            }
            if (amapPhoto != null && !amapPhoto.isEmpty()) {
                // 缓存到数据库，下次直接用
                cityImageRepository.save(new CityImage(name, amapPhoto, "amap"));
                url = amapPhoto;
            } else {
                // 3) 兜底 Picsum
                url = "https://picsum.photos/seed/" + name + "/400/300";
            }
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.LOCATION, url);
        headers.set(HttpHeaders.CACHE_CONTROL, "public, max-age=86400");
        return ResponseEntity.status(HttpStatus.FOUND).headers(headers).build();
    }

    /**
     * 6. 批量保存城市图片到数据库
     * POST /api/city/images/batch
     * Body: [{"cityName":"北京","imageUrl":"https://...","source":"bing"}, ...]
     */
    @PostMapping("/images/batch")
    @RateLimit(max = 30, duration = 60, key = "city_admin")
    public Result<Map<String, Object>> batchSave(@RequestHeader("Authorization") String authHeader,
                                                 @RequestBody List<Map<String, String>> images) {
        AuthUtils.requireAdmin(authHeader, jwtUtil);
        int saved = 0, skipped = 0;
        for (Map<String, String> img : images) {
            String name = img.get("cityName");
            String url = img.get("imageUrl");
            String source = img.getOrDefault("source", "bing");
            if (name == null || url == null || url.isEmpty()) continue;

            Optional<CityImage> existing = cityImageRepository.findByCityName(name);
            if (existing.isPresent()) {
                CityImage ci = existing.get();
                ci.setImageUrl(url);
                ci.setSource(source);
                ci.setUpdatedAt(LocalDateTime.now());
                cityImageRepository.save(ci);
                skipped++;
            } else {
                cityImageRepository.save(new CityImage(name, url, source));
                saved++;
            }
        }
        log.info("批量保存城市图片: 新增{}条, 更新{}条", saved, skipped);
        return Result.ok(Map.of("saved", saved, "updated", skipped));
    }

    /**
     * 7. 获取所有城市图片映射
     * GET /api/city/images/map
     */
    @GetMapping("/images/map")
    public Result<Map<String, String>> getImageMap() {
        return Result.ok(cityMaterialService.getImageMap());
    }

    /** 手动触发全量刷新城市图片（从高德重新爬取） */
    @PostMapping("/images/refresh")
    @RateLimit(max = 10, duration = 60, key = "city_admin")
    public Result<Map<String, Object>> refreshAllCityImages(@RequestHeader("Authorization") String authHeader) {
        AuthUtils.requireAdmin(authHeader, jwtUtil);
        int fetched = cityMaterialService.refreshAllPhotos();
        return Result.ok(Map.of("fetched", fetched, "message", "已从高德重新爬取 " + fetched + " 张城市照片"));
    }

    /** 清空城市素材 */
    @DeleteMapping("/materials/clear")
    @RateLimit(max = 10, duration = 60, key = "city_admin")
    public Result<String> clearMaterials(@RequestHeader("Authorization") String authHeader) {
        AuthUtils.requireAdmin(authHeader, jwtUtil);
        long count = cityMaterialRepository.count();
        cityMaterialRepository.deleteAll();
        log.info("已清空 {} 条素材记录", count);
        return Result.ok("已清空 " + count + " 条记录");
    }

    /** 批量导入素材到 city_material */
    @PostMapping("/materials/batch")
    @RateLimit(max = 30, duration = 60, key = "city_admin")
    public Result<Map<String, Object>> batchMaterials(@RequestHeader("Authorization") String authHeader,
                                                      @RequestBody List<Map<String, String>> items) {
        AuthUtils.requireAdmin(authHeader, jwtUtil);
        int saved = 0, updated = 0, idx = 0;
        for (Map<String, String> item : items) {
            String name = item.get("cityName");
            String url = item.get("imageUrl");
            if (name == null || url == null || url.isEmpty()) continue;
            Optional<CityMaterial> exist = cityMaterialRepository.findByCityName(name);
            if (exist.isPresent()) {
                exist.get().setThumbImg(url);
                exist.get().setImgSource("imported");
                cityMaterialRepository.save(exist.get());
                updated++;
            } else {
                CityMaterial cm = new CityMaterial();
                cm.setCityCode("IMP_" + (idx++) + "_" + System.currentTimeMillis());
                cm.setCityName(name);
                cm.setThumbImg(url);
                cm.setImgSource("imported");
                cm.setMaterialLevel(2);
                cityMaterialRepository.save(cm);
                saved++;
            }
        }
        return Result.ok(Map.of("saved", saved, "updated", updated));
    }

    // ==================== 景点图片接口 ====================

    /** 获取所有景点图片映射 */
    @GetMapping("/attraction/images/map")
    public Result<Map<String, String>> getAttractionImageMap() {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        for (AttractionImage ai : attractionImageRepository.findAll()) {
            if (ai.getImageUrl() != null && !ai.getImageUrl().isEmpty()) {
                map.put(ai.getAttractionName(), ai.getImageUrl());
            }
        }
        return Result.ok(map);
    }

    /** 批量保存景点图片 */
    @PostMapping("/attraction/images/batch")
    @RateLimit(max = 30, duration = 60, key = "city_admin")
    public Result<Map<String, Object>> batchSaveAttractionImages(@RequestHeader("Authorization") String authHeader,
                                                                 @RequestBody List<Map<String, String>> images) {
        AuthUtils.requireAdmin(authHeader, jwtUtil);
        int saved = 0, updated = 0;
        for (Map<String, String> img : images) {
            String name = img.get("attractionName");
            String url = img.get("imageUrl");
            String source = img.getOrDefault("source", "bing");
            if (name == null || url == null || url.isEmpty()) continue;

            java.util.Optional<AttractionImage> existing = attractionImageRepository.findByAttractionName(name);
            if (existing.isPresent()) {
                AttractionImage ai = existing.get();
                ai.setImageUrl(url);
                ai.setSource(source);
                ai.setUpdatedAt(java.time.LocalDateTime.now());
                attractionImageRepository.save(ai);
                updated++;
            } else {
                attractionImageRepository.save(new AttractionImage(name, url, source));
                saved++;
            }
        }
        log.info("批量保存景点图片: 新增{}条, 更新{}条", saved, updated);
        return Result.ok(Map.of("saved", saved, "updated", updated));
    }
}
