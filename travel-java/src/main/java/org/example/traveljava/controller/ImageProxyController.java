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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
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

            // 防 DNS 重绑定 SSRF：白名单域名解析出的 IP 必须全部为公网地址
            // （L-CTRL-5：校验后 execute 会再次解析域名，理论存在 TOCTOU 窗口；
            //  因白名单全为 HTTPS 域名，无法以 IP 直连（会破坏 TLS 证书校验），此处保留该残余风险，
            //  依赖「白名单域名均为知名公网 CDN/地图服务、域名不受攻击者控制」来降低实际暴露面）
            if (resolvesToInternalAddress(domain)) {
                log.warn("图片代理域名解析到内网/保留地址，拒绝: domain={}", domain);
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "不允许的图片来源");
                return;
            }

            // 【修复】流式下载并封顶：先查 Content-Length 超限直接拒绝，
            // 读取过程同样限流（防上游不返回或谎报 Content-Length），避免整包缓冲到内存
            final String[] contentTypeHolder = new String[1];
            byte[] body = restTemplate.execute(url, HttpMethod.GET, null, proxyResponse -> {
                // 上游非 2xx 视为失败（与原 exchange 抛异常 → 500 的行为一致）
                if (!proxyResponse.getStatusCode().is2xxSuccessful()) {
                    throw new UpstreamErrorException("上游返回状态码 " + proxyResponse.getStatusCode().value());
                }

                HttpHeaders headers = proxyResponse.getHeaders();
                String contentType = headers.getFirst(HttpHeaders.CONTENT_TYPE);
                if (contentType == null || !contentType.startsWith("image/")) {
                    throw new InvalidContentTypeException(contentType);
                }
                contentTypeHolder[0] = contentType;

                long declaredLength = headers.getContentLength();
                if (declaredLength > MAX_CONTENT_LENGTH) {
                    throw new ContentTooLargeException(declaredLength);
                }

                try (InputStream in = proxyResponse.getBody()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream(
                            (int) Math.min(Math.max(declaredLength, 8192L), MAX_CONTENT_LENGTH));
                    byte[] buf = new byte[8192];
                    int total = 0;
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        total += n;
                        if (total > MAX_CONTENT_LENGTH) {
                            throw new ContentTooLargeException(total);
                        }
                        out.write(buf, 0, n);
                    }
                    return out.toByteArray();
                }
            });

            if (body == null || body.length == 0) {
                log.warn("图片代理返回空内容: url={}", url);
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "图片不存在");
                return;
            }

            response.setContentType(contentTypeHolder[0]);
            response.setContentLength(body.length);
            response.setHeader("Cache-Control", "public, max-age=86400");
            // CORS 由全局 WebConfig 白名单统一处理
            response.getOutputStream().write(body);
            response.getOutputStream().flush();

            log.debug("图片代理成功: url={}, size={}", url.substring(0, Math.min(url.length(), 60)), body.length);

        } catch (ContentTooLargeException e) {
            log.warn("图片代理内容过大: url={}, size={}", url, e.getSize());
            try {
                response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "图片过大");
            } catch (IOException ex) {
                log.error("发送错误响应失败", ex);
            }
        } catch (InvalidContentTypeException e) {
            log.warn("图片代理内容类型不正确: url={}, contentType={}", url, e.getContentType());
            try {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "无效的图片格式");
            } catch (IOException ex) {
                log.error("发送错误响应失败", ex);
            }
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

    /**
     * 检查域名是否解析到内网/保留地址（防 DNS 重绑定 SSRF）。
     * 解析失败视为不安全（拒绝），宁可误拒也不放行。
     */
    private boolean resolvesToInternalAddress(String domain) {
        try {
            InetAddress[] addrs = InetAddress.getAllByName(domain);
            if (addrs.length == 0) {
                return true;
            }
            for (InetAddress a : addrs) {
                if (isInternalOrReserved(a)) {
                    return true;
                }
            }
            return false;
        } catch (UnknownHostException e) {
            return true;
        }
    }

    /**
     * 【L-CTRL-5 修复】判断 IP 是否属于内网/保留/不应访问的段。
     * 在 Java 内置 isAnyLocal/isLoopback/isLinkLocal/isSiteLocal 基础上，
     * 补齐被漏掉的段：CGNAT 100.64.0.0/10、基准测试 198.18.0.0/15、组播 224.0.0.0/4、
     * 保留 240.0.0.0/4、文档示例段等。只要有一个地址命中即拒绝。
     */
    private boolean isInternalOrReserved(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress()
                || a.isLinkLocalAddress() || a.isSiteLocalAddress()) {
            return true;
        }
        if (a instanceof Inet4Address) {
            int ip = ByteBuffer.wrap(a.getAddress()).getInt();
            // 100.64.0.0/10 CGNAT（运营商级 NAT 段）
            if ((ip & 0xffc00000) == 0x64400000) return true;
            // 198.18.0.0/15 基准测试保留段
            if ((ip & 0xffff0000) == 0xc6120000) return true;
            // 192.0.0.0/24 IETF 协议保留
            if ((ip & 0xffffff00) == 0xc0000000) return true;
            // 192.0.2.0/24、198.51.100.0/24、203.0.113.0/24 文档示例段
            if ((ip & 0xffffff00) == 0xc0000200) return true;
            if ((ip & 0xffffff00) == 0xc6336400) return true;
            if ((ip & 0xffffff00) == 0xcb007100) return true;
            // 224.0.0.0/4 组播
            if ((ip & 0xf0000000) == 0xe0000000) return true;
            // 240.0.0.0/4 保留（含 255.255.255.255 广播）
            if ((ip & 0xf0000000) == 0xf0000000) return true;
        } else if (a instanceof Inet6Address) {
            byte[] b = a.getAddress();
            // fc00::/7 唯一本地地址（isSiteLocalAddress 对 IPv6 覆盖的是 fec0::/10 弃用段，fc00 需手动）
            if ((b[0] & 0xfe) == 0xfc) return true;
        }
        return false;
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

    /** 上游返回非 2xx（转为 500，与原 exchange 抛异常行为一致） */
    private static class UpstreamErrorException extends RuntimeException {
        UpstreamErrorException(String message) { super(message); }
    }

    /** 内容超过 MAX_CONTENT_LENGTH（413） */
    private static class ContentTooLargeException extends RuntimeException {
        private final long size;
        ContentTooLargeException(long size) { super("content too large: " + size); this.size = size; }
        long getSize() { return size; }
    }

    /** Content-Type 不是 image/*（400） */
    private static class InvalidContentTypeException extends RuntimeException {
        private final String contentType;
        InvalidContentTypeException(String contentType) { super("invalid content type: " + contentType); this.contentType = contentType; }
        String getContentType() { return contentType; }
    }
}
