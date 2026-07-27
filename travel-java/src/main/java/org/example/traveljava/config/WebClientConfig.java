package org.example.traveljava.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI WebClient 管理器 — 支持动态多供应商
 * 每个供应商独立一个 WebClient 实例，缓存复用
 * "OpenAI兼容协议"是关键：几乎所有 AI API 都支持 /chat/completions 格式
 *
 * 支持的供应商（只要 API 兼容 OpenAI 协议即可接入）:
 *   DeepSeek | OpenAI | Claude(Anthropic) | Gemini(Google) | Groq | Ollama | 自定义代理
 */
@Configuration
public class WebClientConfig {

    private static final Logger log = LoggerFactory.getLogger(WebClientConfig.class);

    private final AIProviderConfig aiConfig;
    // 缓存已构建的 WebClient，key = provider name
    private final Map<String, WebClient> clientCache = new ConcurrentHashMap<>();

    public WebClientConfig(AIProviderConfig aiConfig) {
        this.aiConfig = aiConfig;
    }

    /**
     * 获取当前活跃供应商的 WebClient（Spring Bean）
     * AIService 直接注入这个 Bean 即可，无需关心底层供应商
     */
    @Bean
    public WebClient aiWebClient() {
        return getOrCreateClient(aiConfig.getActiveProvider());
    }

    /**
     * 暴露管理器给 Controller 用于运行时切换供应商
     */
    @Bean
    public AIWebClientManager aiWebClientManager() {
        return new AIWebClientManager();
    }

    /**
     * 按供应商名获取或创建 WebClient（带缓存）
     */
    private WebClient getOrCreateClient(String providerName) {
        AIProviderConfig.ProviderConfig config = aiConfig.getProviders().get(providerName);
        if (config == null) {
            throw new IllegalStateException(
                "AI供应商 '" + providerName + "' 未配置，请在 ai.providers 中定义");
        }
        return clientCache.computeIfAbsent(providerName, name -> buildClient(config, name));
    }

    /**
     * 构建单个供应商的 WebClient
     */
    private WebClient buildClient(AIProviderConfig.ProviderConfig config, String name) {
        log.info("🔧 初始化 AI 供应商 [{}]: baseUrl={}, model={}", name, config.getBaseUrl(), config.getModel());

        ConnectionProvider connectionProvider = ConnectionProvider.builder("ai-pool-" + name)
                .maxConnections(100)
                .pendingAcquireMaxCount(200)
                .pendingAcquireTimeout(Duration.ofSeconds(60))
                .maxIdleTime(Duration.ofSeconds(30))
                .build();

        HttpClient httpClient = HttpClient.create(connectionProvider)
                .responseTimeout(Duration.ofSeconds(300))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15000)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .doOnConnected(conn -> {
                    conn.addHandlerLast(new ReadTimeoutHandler(300));
                    conn.addHandlerLast(new WriteTimeoutHandler(300));
                })
                .wiretap("DEBUG".equals(System.getenv("LOG_LEVEL")));

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(config.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Content-Type", "application/json");

        // 鉴权方式：不同供应商头部名不同
        String authHeader = config.getExtraHeaders().getOrDefault("Authorization-Header", "Authorization");
        builder.defaultHeader(authHeader, "Bearer " + config.getApiKey());

        // 自定义额外请求头（Claude 的 api-key / x-api-key 等非 Bearer 鉴权场景）
        config.getExtraHeaders().forEach((key, value) -> {
            if (!"Authorization-Header".equals(key)) {
                builder.defaultHeader(key, value);
            }
        });

        return builder.build();
    }

    /**
     * AI WebClient 管理器 — 供 Controller 运行时切换供应商
     */
    public class AIWebClientManager {
        /**
         * 切换到指定供应商，返回新的 WebClient
         * 同时更新 AIProviderConfig.activeProvider
         */
        public WebClient switchProvider(String providerName) {
            if (!aiConfig.getProviders().containsKey(providerName)) {
                throw new IllegalArgumentException("未知供应商: " + providerName);
            }
            aiConfig.setActiveProvider(providerName);
            WebClient client = getOrCreateClient(providerName);
            log.info("🔄 AI 供应商已切换至: {}", providerName);
            return client;
        }

        /** 获取当前活跃供应商名称 */
        public String getActiveProviderName() {
            return aiConfig.getActiveProvider();
        }

        /** 获取所有已配置的供应商名称 */
        public java.util.Set<String> getAvailableProviders() {
            return aiConfig.getProviders().keySet();
        }
    }
}
