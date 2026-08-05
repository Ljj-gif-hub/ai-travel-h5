package org.example.traveljava.service.booking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 酒店供应方抽象 — 与支付层 PaymentProvider 同款「Mock/Real 双实现」模式。
 *
 *  - MockHotelProvider（默认）：本地酒店库 + 种子数据（现状行为）
 *  - RealHotelProvider：第三方渠道（携程/飞猪/Booking 等），配置 booking.hotel.provider=real 启用
 */
public interface HotelProvider {

    String getProviderName();

    /** 搜索酒店（含分页） */
    Map<String, Object> search(String city, String district,
                               BigDecimal minPrice, BigDecimal maxPrice, int page, int size);

    /** 报价（下单金额快照） */
    Map<String, Object> quote(Long hotelId, LocalDate checkIn, LocalDate checkOut, int rooms);
}
