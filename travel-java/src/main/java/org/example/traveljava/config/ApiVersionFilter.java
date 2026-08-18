package org.example.traveljava.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 【新功能】API 版本化过滤器。
 *
 * 把 /api/v1/xxx 请求内部 forward 重写为 /api/xxx（保留 query 与 body），
 * 并回写响应头 X-API-Version: v1，客户端升级无痛。
 * - 通过 @Component 自动生效，现有 /api 路径完全不变；
 * - forward 用 request.getRequestDispatcher(...).forward(...)，body 由容器自动透传；
 * - 防重入：OncePerRequestFilter 自带 ALREADY_FILTERED 标记，另加显式 request attribute 双保险。
 */
@Component
public class ApiVersionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiVersionFilter.class);

    public static final String VERSION_PREFIX = "/api/v1/";
    /** 防重入标记（forward 后同一请求再次经过 filter 链时直接放行） */
    private static final String ATTR_FORWARDED = ApiVersionFilter.class.getName() + ".FORWARDED";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(VERSION_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getAttribute(ATTR_FORWARDED) != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();
        // "/api/v1/travel/plan" → "/api/travel/plan"
        String stripped = uri.substring("/api/v1".length());
        String query = request.getQueryString();
        String target = (query == null || query.isEmpty()) ? stripped : stripped + "?" + query;

        // 回写版本响应头（forward 前设置，响应阶段自然带上）
        response.setHeader("X-API-Version", "v1");
        request.setAttribute(ATTR_FORWARDED, Boolean.TRUE);
        log.debug("API 版本化转发: {} → {}", uri, stripped);
        // forward 保留原始 method/query/body
        request.getRequestDispatcher(target).forward(request, response);
    }
}
