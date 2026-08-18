package org.example.traveljava.controller;

import org.example.traveljava.annotation.RateLimit;
import org.example.traveljava.service.WeatherService;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 【新功能】天气接口（/api/weather）。
 * 匿名可访问；高德 Key 未配置或查询失败时返回 HTTP 502 友好提示。
 */
@RestController
@RequestMapping("/api/weather")
@io.swagger.v3.oas.annotations.tags.Tag(name = "天气")
public class WeatherController {

    private static final Logger log = LoggerFactory.getLogger(WeatherController.class);

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping("/{city}")
    @RateLimit(max = 30, duration = 60, key = "weather")
    public ResponseEntity<Result<Map<String, Object>>> getWeather(@PathVariable String city) {
        try {
            Map<String, Object> weather = weatherService.getWeather(city);
            return ResponseEntity.ok(Result.ok(weather));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Result.fail(e.getMessage()));
        } catch (Exception e) {
            // 高德 Key 未配置 / 接口失败 → 502 友好提示
            log.warn("天气查询失败: city={}, error={}", city, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Result.fail(e.getMessage() != null && !e.getMessage().isBlank()
                            ? e.getMessage()
                            : "天气服务暂时不可用，请稍后重试"));
        }
    }
}
