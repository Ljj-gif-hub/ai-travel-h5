package org.example.traveljava.config;

import org.example.traveljava.interceptor.AiQuotaInterceptor;
import org.example.traveljava.interceptor.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SecurityConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final AiQuotaInterceptor aiQuotaInterceptor;

    public SecurityConfig(RateLimitInterceptor rateLimitInterceptor, AiQuotaInterceptor aiQuotaInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.aiQuotaInterceptor = aiQuotaInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/**");
        // 【新功能】AI 用量配额：仅拦截 agent / travel AI / stream 相关路径
        registry.addInterceptor(aiQuotaInterceptor)
                .addPathPatterns("/api/agent/**", "/api/travel/ai/**", "/api/travel/stream/**");
    }
}
