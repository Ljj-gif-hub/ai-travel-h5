package org.example.traveljava.service.booking;

import java.time.LocalDate;
import java.util.List;

/**
 * 航班供应方抽象 — 与酒店/支付层同款「Mock/Real 双实现」模式。
 *
 *  - MockFlightProvider（默认）：内置确定性模拟航班（按 城市×日期 生成稳定结果）
 *  - RealFlightProvider：第三方机票渠道，配置 booking.flight.provider=real 启用
 */
public interface FlightProvider {

    String getProviderName();

    /** 查询某日某航线航班列表 */
    List<FlightOffer> search(String fromCity, String toCity, LocalDate date);
}
