package org.example.traveljava.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * AI 多供应商配置 — 自动识别已配置的 API Key，无需手动指定供应商
 *
 * 自动检测逻辑（启动时执行）：
 * 1. 如果手动设了 AI_PROVIDER 环境变量 → 优先使用
 * 2. 否则扫描所有 providers，找第一个 api-key 非空的供应商
 * 3. 检测顺序：custom → deepseek → openai → claude → gemini
 *
 * 使用方式：
 *   只需配一个环境变量即可，不用管 AI_PROVIDER：
 *   DEEPSEEK_API_KEY=sk-xxx        → 自动用 deepseek
 *   OPENAI_API_KEY=sk-xxx          → 自动用 openai
 *   CUSTOM_AI_KEY=xxx CUSTOM_AI_URL=https://... → 自动用 custom
 */
@Component
@ConfigurationProperties(prefix = "ai")
public class AIProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(AIProviderConfig.class);

    /** 检测优先级：排前面的优先匹配 */
    private static final List<String> DETECT_ORDER = Arrays.asList(
        "custom", "deepseek", "openai", "claude", "gemini"
    );

    /** 当前活跃的供应商名称 */
    private String activeProvider = "deepseek";

    /** 所有供应商配置 */
    private Map<String, ProviderConfig> providers = new HashMap<>();

    /** 标记是否用户显式指定了 activeProvider */
    private boolean userSpecified = false;

    // ==================== Spring 属性注入 ====================

    public String getActiveProvider() {
        return activeProvider;
    }

    public void setActiveProvider(String activeProvider) {
        if (activeProvider == null || "auto".equals(activeProvider)) {
            // auto = 让启动时自动检测
            return;
        }
        // 显式指定了具体供应商名 → 标记为用户指定
        this.userSpecified = true;
        this.activeProvider = activeProvider;
    }

    public Map<String, ProviderConfig> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, ProviderConfig> providers) {
        this.providers = providers;
    }

    // ==================== 启动时自动检测 ====================

    @PostConstruct
    public void autoDetect() {
        if (userSpecified) {
            log.info("🎯 AI供应商已手动指定: {}", activeProvider);
            return;
        }

        // 按优先级扫描：第一个配置了 api-key 的供应商自动激活
        for (String name : DETECT_ORDER) {
            ProviderConfig cfg = providers.get(name);
            if (cfg != null && cfg.getApiKey() != null && !cfg.getApiKey().isBlank()) {
                this.activeProvider = name;
                log.info("🔍 自动检测到AI供应商 [{}]: model={}, baseUrl={}",
                    name, cfg.getModel(), cfg.getBaseUrl());
                return;
            }
        }

        // 兜底：扫描 providers 中任意一个有 key 的
        for (Map.Entry<String, ProviderConfig> entry : providers.entrySet()) {
            ProviderConfig cfg = entry.getValue();
            if (cfg != null && cfg.getApiKey() != null && !cfg.getApiKey().isBlank()) {
                this.activeProvider = entry.getKey();
                log.info("🔍 自动检测到AI供应商 [{}]: model={}, baseUrl={}",
                    entry.getKey(), cfg.getModel(), cfg.getBaseUrl());
                return;
            }
        }

        log.warn("⚠️  未检测到任何有效的 AI API Key，请设置 DEEPSEEK_API_KEY / OPENAI_API_KEY / CUSTOM_AI_KEY 等环境变量");
    }

    // ==================== 便捷方法 ====================

    public ProviderConfig getActiveConfig() {
        ProviderConfig config = providers.get(activeProvider);
        if (config == null) {
            throw new IllegalStateException(
                "未找到AI供应商配置: " + activeProvider + "，请在 application.yml 中配置 ai.providers." + activeProvider);
        }
        return config;
    }

    public String getActiveBaseUrl() {
        return getActiveConfig().getBaseUrl();
    }

    public String getActiveApiKey() {
        return getActiveConfig().getApiKey();
    }

    public String getActiveModel() {
        return getActiveConfig().getModel();
    }

    /**
     * 单个供应商的参数配置
     */
    public static class ProviderConfig {
        /** API 密钥 */
        private String apiKey = "";
        /** API 基础 URL（如 https://api.deepseek.com） */
        private String baseUrl = "https://api.openai.com/v1";
        /** 模型名称（如 deepseek-chat / gpt-4o / claude-sonnet-4-20250514） */
        private String model = "gpt-3.5-turbo";
        /** 默认最大 token 数 */
        private int maxTokens = 4096;
        /** 默认温度 */
        private double temperature = 0.7;
        /** 额外 HTTP 请求头（JSON 格式，如 {"x-custom":"val"}） */
        private Map<String, String> extraHeaders = new HashMap<>();
        /** API 路径（默认 /chat/completions，OpenAI兼容） */
        private String chatPath = "/chat/completions";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }

        public Map<String, String> getExtraHeaders() { return extraHeaders; }
        public void setExtraHeaders(Map<String, String> extraHeaders) { this.extraHeaders = extraHeaders; }

        public String getChatPath() { return chatPath; }
        public void setChatPath(String chatPath) { this.chatPath = chatPath; }
    }
}
