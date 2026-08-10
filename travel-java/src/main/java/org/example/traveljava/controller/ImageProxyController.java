package org.example.traveljava.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.example.traveljava.annotation.RateLimit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/proxy")
@io.swagger.v3.oas.annotations.tags.Tag(name = "系统")
public class ImageProxyController {

    private static final Logger log = LoggerFactory.getLogger(ImageProxyController.class);

    private static final int MAX_URL_LENGTH = 2048;
    private static final int TIMEOUT_MS = 10000;
    private static final int MAX_CONTENT_LENGTH = 5 * 1024 * 1024;

    private static final List<String> ALLOWED_DOMAINS = Arrays.asList(
            "api.map.baidu.com",
            "map.baidu.com",
            "restapi.amap.com",
            "webapi.amap.com",
            "picsum.photos",
            "trae-api-cn.mchost.guru"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp"
    );

    private final RestTemplate restTemplate;

    public ImageProxyController() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                // 禁止跟随 3xx 重定向，防止白名单域名 302 跳转内网造成 SSRF
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(TIMEOUT_MS);
        factory.setReadTimeout(TIMEOUT_MS);
        this.restTemplate = new RestTemplate(factory);
    }

    @GetMapping("/image")
    @RateLimit(max = 30, duration = 60, key = "proxy_image")
    public void proxyImage(@RequestParam String url, HttpServletResponse response) {
        try {
            validateUrl(url);

            URI uri = new URI(url);
            String domain = uri.getHost();

            if (!ALLOWED_DOMAINS.contains(domain)) {
                log.warn("图片代理域名不在白名单: domain={}, url={}", domain, url);
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "不允许的图片来源");
                return;
            }

            org.springframework.http.ResponseEntity<byte[]> proxyResponse = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    byte[].class
            );

            byte[] body = proxyResponse.getBody();
            if (body == null || body.length == 0) {
                log.warn("图片代理返回空内容: url={}", url);
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "图片不存在");
                return;
            }

            if (body.length > MAX_CONTENT_LENGTH) {
                log.warn("图片代理内容过大: url={}, size={}", url, body.length);
                response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "图片过大");
                return;
            }

            String contentType = proxyResponse.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
            if (contentType == null || !contentType.startsWith("image/")) {
                log.warn("图片代理内容类型不正确: url={}, contentType={}", url, contentType);
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "无效的图片格式");
                return;
            }

            response.setContentType(contentType);
            response.setContentLength(body.length);
            response.setHeader("Cache-Control", "public, max-age=86400");
            // CORS 由全局 WebConfig 白名单统一处理
            response.getOutputStream().write(body);
            response.getOutputStream().flush();

            log.debug("图片代理成功: url={}, size={}", url.substring(0, Math.min(url.length(), 60)), body.length);

        } catch (IllegalArgumentException e) {
            log.warn("图片代理参数校验失败: {}", e.getMessage());
            try {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            } catch (IOException ex) {
                log.error("发送错误响应失败", ex);
            }
        } catch (Exception e) {
            log.error("图片代理异常: url={}", url, e);
            try {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "图片加载失败");
            } catch (IOException ex) {
                log.error("发送错误响应失败", ex);
            }
        }
    }

    private void validateUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("URL不能为空");
        }

        if (url.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("URL过长");
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("URL必须以http://或https://开头");
        }

        try {
            URL parsedUrl = new URL(url);
            String protocol = parsedUrl.getProtocol();
            if (!"http".equals(protocol) && !"https".equals(protocol)) {
                throw new IllegalArgumentException("不支持的协议");
            }

            String path = parsedUrl.getPath().toLowerCase();
            boolean hasValidExtension = ALLOWED_EXTENSIONS.stream()
                    .anyMatch(path::endsWith);
            if (!hasValidExtension) {
                throw new IllegalArgumentException("不支持的图片格式");
            }

            if (url.contains("..")) {
                throw new IllegalArgumentException("URL包含非法字符");
            }

        } catch (java.net.MalformedURLException e) {
            throw new IllegalArgumentException("URL格式不正确");
        }
    }
}
