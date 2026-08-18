package org.example.traveljava.service.booking;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.traveljava.config.BookingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 真实第三方机票渠道接入位（骨架）。
 *
 * 启用：application.yml 设 booking.flight.provider=real，并配置
 *   booking.flight.endpoint / booking.flight.api-key。
 *
 * 已实现一个最小 HTTP 调用框架：GET endpoint?fromCity=&toCity=&date=，
 * 期望渠道返回 JSON 数组，字段与 FlightOffer 一致即可解析。
 * 未配置 endpoint 时抛出明确错误（不静默降级，防止误以为已对接）。
 */
@Component
@ConditionalOnProperty(prefix = "booking.flight", name = "provider", havingValue = "real")
public class RealFlightProvider implements FlightProvider {

    private static final Logger log = LoggerFactory.getLogger(RealFlightProvider.class);

    private final BookingProperties props;
    private final RestTemplate restTemplate;

    public RealFlightProvider(BookingProperties props, RestTemplate restTemplate) {
        this.props = props;
        this.restTemplate = restTemplate;
    }

    @Override
    public String getProviderName() {
        return "real:" + (props.getFlight().getEndpoint().isEmpty() ? "unconfigured" : "configured");
    }

    @Override
    public List<FlightOffer> search(String fromCity, String toCity, LocalDate date) {
        String endpoint = props.getFlight().getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("真实机票渠道未配置：请在 booking.flight.endpoint 配置第三方接口地址");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (props.getFlight().getApiKey() != null && !props.getFlight().getApiKey().isBlank()) {
                headers.setBearerAuth(props.getFlight().getApiKey());
            }
            // 【安全】查询参数 URL 编码：防止城市名包含 & / # 等字符破坏 URL 结构或注入额外参数
            String url = endpoint + "?fromCity=" + URLEncoder.encode(fromCity, StandardCharsets.UTF_8)
                    + "&toCity=" + URLEncoder.encode(toCity, StandardCharsets.UTF_8)
                    + "&date=" + URLEncoder.encode(String.valueOf(date), StandardCharsets.UTF_8);
            JsonNode resp = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET,
                    new HttpEntity<>(headers), JsonNode.class).getBody();
            return parse(resp);
        } catch (Exception e) {
            throw new IllegalStateException("真实机票渠道调用失败：" + e.getMessage(), e);
        }
    }

    private List<FlightOffer> parse(JsonNode node) throws Exception {
        if (node == null) return List.of();
        JsonNode arr = node.isArray() ? node : node.get("data");
        if (arr == null || !arr.isArray()) return List.of();
        List<FlightOffer> list = new ArrayList<>();
        for (JsonNode n : arr) {
            list.add(new FlightOffer(
                    n.path("flightNo").asText(),
                    n.path("airline").asText(),
                    n.path("fromCity").asText(),
                    n.path("toCity").asText(),
                    n.path("date").asText(),
                    n.path("departTime").asText(),
                    n.path("arrivalTime").asText(),
                    n.path("durationMin").asInt(0),
                    n.path("cabin").asText("经济舱"),
                    n.path("price").asLong(0)
            ));
        }
        return list;
    }
}
