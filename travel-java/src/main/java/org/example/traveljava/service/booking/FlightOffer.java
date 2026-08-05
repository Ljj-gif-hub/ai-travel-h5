package org.example.traveljava.service.booking;

/**
 * 航班报价条目（供应方输出，可被第三方渠道映射为同构数据）。
 */
public record FlightOffer(
        String flightNo,
        String airline,
        String fromCity,
        String toCity,
        String date,          // yyyy-MM-dd
        String departTime,    // HH:mm
        String arrivalTime,   // HH:mm
        int durationMin,
        String cabin,         // 经济舱 / 商务舱
        long price
) {
}
