package org.example.traveljava.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * 【修复】引入 spring-boot-starter-security 后的兜底安全配置。
 *
 * 当前策略：全站 permitAll + 关闭 CSRF（API 无表单/Cookie 会话，无 CSRF 面）。
 * 与引入 Security 前的行为完全一致：鉴权仍由现有 JwtUtil/AuthUtils 的
 * HMAC 签名校验 + RateLimitInterceptor 等拦截器承担，Security 仅作为
 * 框架级防线兜底（如异常防护、安全头、未来方法级鉴权底座）。
 *
 * 迁移路线：待各控制器统一走 AuthUtils 后，可将 permitAll 按路径/方法
 * 逐步收敛为显式访问规则，并迁移到 @PreAuthorize 方法级鉴权。
 *
 * 【新功能-安全头】在 Security 链上统一追加：
 *  - X-Content-Type-Options: nosniff
 *  - X-Frame-Options: DENY
 *  - Referrer-Policy: strict-origin-when-cross-origin
 *  CSP 刻意不在此设置：前端依赖百度地图/高德地图等外部脚本域及大量内联样式，
 *  收紧 CSP 会导致页面白屏；现有 SecurityHeaderFilter 已提供宽松 CSP 兜底。
 */
@Configuration
public class ApiSecurityConfig {

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .headers(headers -> headers
                        // nosniff：禁止浏览器 MIME 嗅探
                        .contentTypeOptions(contentTypeOptions -> {})
                        // 禁止页面被嵌入 iframe（防点击劫持）
                        .frameOptions(frame -> frame.deny())
                        // 跨域降级时只带 origin，不带完整 URL
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                );
        return http.build();
    }
}
