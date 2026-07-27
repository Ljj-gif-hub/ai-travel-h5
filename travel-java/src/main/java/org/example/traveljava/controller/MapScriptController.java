package org.example.traveljava.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.example.traveljava.config.MapConfig;
import org.example.traveljava.dto.MapConfigDTO;
import org.example.traveljava.vo.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 地图脚本代理 + 配置控制器
 *
 * GET /api/map/script — 根据 map.provider 代理加载对应的 JS SDK
 * GET /api/map/config — 返回前端地图配置（提供商类型等）
 */
@RestController
@RequestMapping("/api/map")
public class MapScriptController {

    private final RestTemplate restTemplate;
    private final MapConfig mapConfig;

    public MapScriptController(RestTemplate restTemplate, MapConfig mapConfig) {
        this.restTemplate = restTemplate;
        this.mapConfig = mapConfig;
    }

    /**
     * 地图 JS SDK 代理加载
     * 根据 map.provider 配置决定加载百度 GL SDK 还是高德 JS API v2.0
     */
    @GetMapping("/script")
    public void getMapScript(HttpServletResponse response) {
        String provider = mapConfig.resolveProvider();

        if ("amap".equals(provider)) {
            loadAmapScript(response);
        } else {
            loadBaiduScript(response);
        }
    }

    private void loadBaiduScript(HttpServletResponse response) {
        try {
            String ak = mapConfig.getBaidu().getAk();
            if (ak == null || ak.isEmpty()) {
                writeFallbackScript(response, "baidu");
                return;
            }

            String scriptUrl = String.format(
                    "https://api.map.baidu.com/api?v=3.0&ak=%s&type=webgl&callback=_baiduMapInit",
                    ak
            );

            String scriptContent = restTemplate.getForObject(scriptUrl, String.class);

            if (scriptContent == null || scriptContent.isEmpty()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "地图脚本加载失败");
                return;
            }

            response.setContentType("application/javascript;charset=UTF-8");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "public, max-age=86400");
            response.getOutputStream().write(scriptContent.getBytes(StandardCharsets.UTF_8));
            response.getOutputStream().flush();

        } catch (Exception e) {
            writeFallbackScript(response, "baidu");
        }
    }

    private void loadAmapScript(HttpServletResponse response) {
        try {
            String jsKey = mapConfig.getAmap().getJsKey();
            if (jsKey == null || jsKey.isEmpty()) {
                jsKey = mapConfig.getAmap().getWebKey();
            }
            if (jsKey == null || jsKey.isEmpty()) {
                writeFallbackScript(response, "amap");
                return;
            }

            // 高德 JS API v2.0，不传 callback 避免未定义错误
            String scriptUrl = String.format(
                    "https://webapi.amap.com/maps?v=2.0&key=%s",
                    jsKey
            );

            String scriptContent = restTemplate.getForObject(scriptUrl, String.class);

            if (scriptContent == null || scriptContent.isEmpty()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "地图脚本加载失败");
                return;
            }

            response.setContentType("application/javascript;charset=UTF-8");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "public, max-age=86400");
            response.getOutputStream().write(scriptContent.getBytes(StandardCharsets.UTF_8));
            response.getOutputStream().flush();

        } catch (Exception e) {
            writeFallbackScript(response, "amap");
        }
    }

    /**
     * AK/Key 未配置时的降级脚本 — 通知前端使用 Leaflet
     */
    private void writeFallbackScript(HttpServletResponse response, String provider) {
        try {
            String fallbackScript =
                "// " + provider + " 地图 Key 未配置，请在前端使用 Leaflet 免费地图\n" +
                "console.warn('[地图] " + provider + " 地图 Key 未配置，前端将降级使用 Leaflet/OSM 免费地图');\n" +
                "window.BMapGL = undefined;\n" +
                "window.AMap = undefined;\n" +
                "window._baiduMapUnavailable = true;\n" +
                "window._amapUnavailable = true;\n";
            response.setContentType("application/javascript;charset=UTF-8");
            response.setCharacterEncoding("UTF-8");
            response.getOutputStream().write(fallbackScript.getBytes(StandardCharsets.UTF_8));
            response.getOutputStream().flush();
        } catch (IOException ex) {
            // ignore
        }
    }

    /**
     * 返回前端地图配置信息
     * GET /api/map/config
     */
    @GetMapping("/config")
    public Result<MapConfigDTO> getMapConfig() {
        String provider = mapConfig.resolveProvider();
        String coordSystem = "amap".equals(provider) ? "gcj02" : "bd09";
        boolean available = mapConfig.hasAnyKey();

        MapConfigDTO dto = new MapConfigDTO(provider, coordSystem, available);

        // 如果 Key 可用，生成 JS SDK URL（方便前端直接从 CDN 加载）
        if (available) {
            if ("amap".equals(provider)) {
                String jsKey = mapConfig.getAmap().getJsKey();
                if (jsKey == null || jsKey.isEmpty()) jsKey = mapConfig.getAmap().getWebKey();
                dto.setJsSdkUrl("https://webapi.amap.com/maps?v=2.0&key=" + jsKey);
            } else {
                dto.setJsSdkUrl(null); // 百度通过后端代理加载，不暴露 AK
            }
        }

        return Result.ok(dto);
    }
}
