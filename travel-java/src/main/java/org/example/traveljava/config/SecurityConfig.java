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
        // 【新功能】AI 用量配额：拦截全部烧钱的 AI 路径（agent / travel AI / plan / chat / planner / trip-ai）
        // QUOTA-1 修复：注册路径必须与 AiQuotaInterceptor 内判断的前缀一致，否则新路径不进拦截器（假修复）
        registry.addInterceptor(aiQuotaInterceptor)
                .addPathPatterns(
                        "/api/agent/**",
                        "/api/travel/ai/**",
                        "/api/travel/stream/**",
                        "/api/travel/plan",
                        "/api/travel/plan/stream",
                        "/api/travel/plan/structured",
                        "/api/travel/chat",
                        "/api/travel/chat/stream",
                        "/api/travel/recommend",
                        "/api/travel/planner/**",
                        "/api/travel/trip/**",
                        "/api/trip/ai/**");
    }
}
