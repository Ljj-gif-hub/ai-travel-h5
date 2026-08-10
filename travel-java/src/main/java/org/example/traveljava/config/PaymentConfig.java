package org.example.traveljava.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 第三方支付多渠道配置
 *
 * 配置结构（application.yml）：
 * <pre>
 * payment:
 *   provider: mock           # mock | alipay | wechat
 *   mock:
 *     secret: xxx            # mock 验签密钥（可选）
 *   alipay:
 *     app-id: xxx
 *     private-key: xxx
 *     public-key: xxx
 *     notify-url: xxx
 *   wechat:
 *     app-id: xxx
 *     mch-id: xxx
 *     api-key: xxx
 *     notify-url: xxx
 * </pre>
 *
 * 设计：仿 MapConfig 的「可配置对接层 + 未配置时 mock 降级」模式。
 * 未配置任何真实渠道密钥时默认走 MockPaymentProvider，填 Key 后改 payment.provider 即切换真实渠道。
 */
@Configuration
@ConfigurationProperties(prefix = "payment")
public class PaymentConfig {

    /** 支付渠道：mock / alipay / wechat */
    private String provider = "mock";

    /**
     * 是否允许公开的模拟支付确认端点（GET /api/payment/mock-pay）。
     * 生产环境必须关闭（prod profile 置 false），否则任何人可凭订单号把任意订单标记为已支付。
     */
    private boolean mockPayEnabled = true;

    /** 模拟支付配置 */
    private MockConfig mock = new MockConfig();

    /** 支付宝配置 */
    private AlipayConfig alipay = new AlipayConfig();

    /** 微信支付配置 */
    private WechatConfig wechat = new WechatConfig();

    // ==================== Getter / Setter ====================

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public boolean isMockPayEnabled() { return mockPayEnabled; }
    public void setMockPayEnabled(boolean mockPayEnabled) { this.mockPayEnabled = mockPayEnabled; }

    public MockConfig getMock() { return mock; }
    public void setMock(MockConfig mock) { this.mock = mock; }

    public AlipayConfig getAlipay() { return alipay; }
    public void setAlipay(AlipayConfig alipay) { this.alipay = alipay; }

    public WechatConfig getWechat() { return wechat; }
    public void setWechat(WechatConfig wechat) { this.wechat = wechat; }

    // ==================== 便捷方法 ====================

    /**
     * 解析最终使用的支付渠道
     * - 显式指定 mock/alipay/wechat → 直接使用
     * - 未配置真实渠道密钥时强制回退 mock（保证下单后始终有可用支付渠道）
     */
    public String resolveProvider() {
        if ("alipay".equalsIgnoreCase(provider) && hasAlipayKey()) {
            return "alipay";
        }
        if ("wechat".equalsIgnoreCase(provider) && hasWechatKey()) {
            return "wechat";
        }
        if ("mock".equalsIgnoreCase(provider)) {
            return "mock";
        }
        // 指定了真实渠道但缺密钥 → 静默降级 mock，避免支付流程不可用
        return "mock";
    }

    private boolean hasAlipayKey() {
        return notBlank(alipay.getAppId()) && notBlank(alipay.getPrivateKey());
    }

    private boolean hasWechatKey() {
        return notBlank(wechat.getAppId()) && notBlank(wechat.getMchId()) && notBlank(wechat.getApiKey());
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    // ==================== 嵌套配置类 ====================

    public static class MockConfig {
        /** 模拟支付验签密钥（前端/回调需携带，可选） */
        private String secret = "dev-mock-secret";

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
    }

    public static class AlipayConfig {
        private String appId;
        private String privateKey;
        private String publicKey;
        private String notifyUrl;

        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getPrivateKey() { return privateKey; }
        public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }
        public String getPublicKey() { return publicKey; }
        public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
        public String getNotifyUrl() { return notifyUrl; }
        public void setNotifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; }
    }

    public static class WechatConfig {
        private String appId;
        private String mchId;
        private String apiKey;
        private String notifyUrl;

        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getMchId() { return mchId; }
        public void setMchId(String mchId) { this.mchId = mchId; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getNotifyUrl() { return notifyUrl; }
        public void setNotifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; }
    }
}
