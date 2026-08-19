package org.example.traveljava.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    /**
     * 【AVAIL-1 修复】全局 RestTemplate 设置连接/读取超时，防止外部端挂起耗尽 Tomcat 工作线程。
     * connectTimeout 5s、readTimeout 10s，被 Amap/Baidu/Weather/CityService 共用。
     * 注：用 setConnectTimeout/setReadTimeout —— 本项目 spring-boot 3.2.12 的
     * RestTemplateBuilder 只有这两个方法（connectTimeout(Duration) 是更新版本才有的 API）。
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }
}