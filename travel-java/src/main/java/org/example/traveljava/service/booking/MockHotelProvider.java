package org.example.traveljava.service.booking;

import org.example.traveljava.service.HotelService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 默认酒店供应方（mock）：复用现有 HotelService（本地酒店库 + 种子数据）。
 * 后续接入真实第三方时，实现 RealHotelProvider 并在配置切换即可，业务层无需改动。
 */
@Component
@ConditionalOnProperty(prefix = "booking.hotel", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockHotelProvider implements HotelProvider {

    private final HotelService hotelService;

    public MockHotelProvider(HotelService hotelService) {
        this.hotelService = hotelService;
    }

    @Override
    public String getProviderName() {
        return "mock";
    }

    @Override
    public Map<String, Object> search(String city, String district,
                                      BigDecimal minPrice, BigDecimal maxPrice, int page, int size) {
        return hotelService.searchHotels(city, district, minPrice, maxPrice, page, size);
    }

    @Override
    public Map<String, Object> quote(Long hotelId, LocalDate checkIn, LocalDate checkOut, int rooms) {
        return hotelService.bookHotel(hotelId, checkIn, checkOut, rooms);
    }
}
