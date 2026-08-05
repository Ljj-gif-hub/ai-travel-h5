package org.example.traveljava.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 第三方预订对接层配置（booking.*）。
 *
 * 与支付层同款「可配置 + 模拟实现」模式：
 *  - booking.hotel.provider  = mock（默认，走本地酒店库/种子数据）| real（第三方渠道）
 *  - booking.flight.provider = mock（默认，内置确定性模拟航班） | real（第三方渠道）
 *
 * 切换到 real 前必须配置对应的 endpoint 与 api-key，
 * 否则 RealProvider 会抛出明确错误，不会静默降级（防止误以为已对接真实渠道）。
 */
@ConfigurationProperties(prefix = "booking")
public class BookingProperties {

    private final Hotel hotel = new Hotel();
    private final Flight flight = new Flight();

    public Hotel getHotel() { return hotel; }
    public Flight getFlight() { return flight; }

    public static class Hotel {
        private String provider = "mock";
        private String endpoint = "";
        private String apiKey = "";

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }

    public static class Flight {
        private String provider = "mock";
        private String endpoint = "";
        private String apiKey = "";

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }
}
