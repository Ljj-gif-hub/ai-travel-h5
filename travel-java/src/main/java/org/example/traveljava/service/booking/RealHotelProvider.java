package org.example.traveljava.service.booking;

import org.example.traveljava.config.BookingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 真实第三方酒店渠道接入位（骨架）。
 *
 * 启用：application.yml 设 booking.hotel.provider=real，并配置
 *   booking.hotel.endpoint / booking.hotel.api-key（第三方供应商接口）。
 *
 * 接入示例：在 search()/quote() 内用 RestTemplate/WebClient 调用渠道接口，
 * 将第三方房源与报价映射为与 Mock 一致的返回结构，业务层与前端零改动。
 */
@Component
@ConditionalOnProperty(prefix = "booking.hotel", name = "provider", havingValue = "real")
public class RealHotelProvider implements HotelProvider {

    private final BookingProperties props;

    public RealHotelProvider(BookingProperties props) {
        this.props = props;
    }

    @Override
    public String getProviderName() {
        return "real:" + (props.getHotel().getEndpoint().isEmpty() ? "unconfigured" : "configured");
    }

    @Override
    public Map<String, Object> search(String city, String district,
                                      BigDecimal minPrice, BigDecimal maxPrice, int page, int size) {
        throw new UnsupportedOperationException("真实酒店渠道尚未接入：请在 booking.hotel 配置第三方 endpoint/api-key 并实现 RealHotelProvider.search()");
    }

    @Override
    public Map<String, Object> quote(Long hotelId, LocalDate checkIn, LocalDate checkOut, int rooms) {
        throw new UnsupportedOperationException("真实酒店渠道尚未接入：请在 booking.hotel 配置第三方 endpoint/api-key 并实现 RealHotelProvider.quote()");
    }
}
