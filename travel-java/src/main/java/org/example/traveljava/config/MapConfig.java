package org.example.traveljava.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 地图多供应商配置
 *
 * 配置结构（application.yml）：
 * <pre>
 * map:
 *   provider: baidu          # baidu | amap | auto
 *   baidu:
 *     ak: xxx
 *   amap:
 *     web-key: xxx           # Web服务API key
 *     js-key: xxx            # JS API v2.0 key
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "map")
public class MapConfig {

    /** 地图提供商：baidu / amap / auto（自动检测） */
    private String provider = "amap";

    /** 百度地图配置 */
    private BaiduConfig baidu = new BaiduConfig();

    /** 高德地图配置 */
    private AmapConfig amap = new AmapConfig();

    // ==================== Getter / Setter ====================

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public BaiduConfig getBaidu() { return baidu; }
    public void setBaidu(BaiduConfig baidu) { this.baidu = baidu; }

    public AmapConfig getAmap() { return amap; }
    public void setAmap(AmapConfig amap) { this.amap = amap; }

    // ==================== 便捷方法 ====================

    /**
     * 解析最终使用的提供商
     * - 显式指定 baidu/amap → 直接使用
     * - auto → 优先高德（有key），其次百度（有AK），否则默认高德
     */
    public String resolveProvider() {
        if ("amap".equalsIgnoreCase(provider)) {
            return "amap";
        }
        if ("baidu".equalsIgnoreCase(provider)) {
            return "baidu";
        }
        // auto 模式：检测哪个 key 已配置，优先高德
        if (amap.getWebKey() != null && !amap.getWebKey().isBlank()) {
            return "amap";
        }
        if (baidu.getAk() != null && !baidu.getAk().isBlank()) {
            return "baidu";
        }
        // 都不配置 → 默认高德（降级到模拟数据）
        return "amap";
    }

    /**
     * 是否至少有一个地图 API Key 已配置
     */
    public boolean hasAnyKey() {
        boolean hasBaidu = baidu.getAk() != null && !baidu.getAk().isBlank();
        boolean hasAmap = amap.getWebKey() != null && !amap.getWebKey().isBlank();
        return hasBaidu || hasAmap;
    }

    /**
     * 获取当前激活提供商的 API Key
     */
    public String getActiveKey() {
        String p = resolveProvider();
        if ("amap".equals(p)) {
            return amap.getWebKey();
        }
        return baidu.getAk();
    }

    /**
     * 获取 JS SDK Key（前端加载地图用）
     */
    public String getActiveJsKey() {
        String p = resolveProvider();
        if ("amap".equals(p)) {
            return amap.getJsKey() != null ? amap.getJsKey() : amap.getWebKey();
        }
        return baidu.getAk();
    }

    // ==================== 嵌套配置类 ====================

    public static class BaiduConfig {
        private String ak;

        public String getAk() { return ak; }
        public void setAk(String ak) { this.ak = ak; }
    }

    public static class AmapConfig {
        /** Web服务 API key（服务端调用） */
        private String webKey;
        /** JS API v2.0 key（前端加载） */
        private String jsKey;

        public String getWebKey() { return webKey; }
        public void setWebKey(String webKey) { this.webKey = webKey; }

        public String getJsKey() { return jsKey; }
        public void setJsKey(String jsKey) { this.jsKey = jsKey; }
    }
}
