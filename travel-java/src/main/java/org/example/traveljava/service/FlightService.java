package org.example.traveljava.service;

import org.example.traveljava.entity.Order;
import org.example.traveljava.service.booking.FlightOffer;
import org.example.traveljava.service.booking.FlightProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 航班服务 — 通过 FlightProvider（mock/real）查询航班并下单。
 */
@Service
public class FlightService {

    private static final Logger log = LoggerFactory.getLogger(FlightService.class);

    private final FlightProvider flightProvider;
    private final OrderService orderService;

    public FlightService(FlightProvider flightProvider, OrderService orderService) {
        this.flightProvider = flightProvider;
        this.orderService = orderService;
    }

    /** 查询航班（供应方：mock 内置确定性数据 / real 第三方渠道） */
    public List<FlightOffer> searchFlights(String fromCity, String toCity, LocalDate date) {
        List<FlightOffer> offers = flightProvider.search(fromCity, toCity, date);
        log.info("[航班] 查询 {}→{} {} 共 {} 班（供应方={}）", fromCity, toCity, date, offers.size(), flightProvider.getProviderName());
        return offers;
    }

    /**
     * 机票下单 — 校验航班与价格后创建 flight 订单（pending）。
     * @param userId 下单用户
     * @param params 需包含 flightNo/fromCity/toCity/departureTime/arrivalTime/price/passengers
     */
    public Order bookFlight(Long userId, Map<String, Object> params) {
        String flightNo = (String) params.get("flightNo");
        String fromCity = (String) params.get("fromCity");
        String toCity = (String) params.get("toCity");
        if (flightNo == null || flightNo.isBlank() || fromCity == null || toCity == null) {
            throw new IllegalArgumentException("请选择有效的航班");
        }
        if (fromCity.isBlank() || toCity.isBlank() || fromCity.equals(toCity)) {
            throw new IllegalArgumentException("出发/到达城市不合法");
        }
        Object priceObj = params.get("price");
        if (priceObj == null) {
            throw new IllegalArgumentException("价格无效");
        }
        long price = ((Number) priceObj).longValue();
        if (price <= 0) {
            throw new IllegalArgumentException("价格无效");
        }

        int passengers = params.get("passengers") == null ? 1 : Math.max(1, ((Number) params.get("passengers")).intValue());

        Map<String, Object> orderParams = new java.util.HashMap<>();
        orderParams.put("type", "flight");
        orderParams.put("price", price * passengers);
        orderParams.put("quantity", passengers);
        orderParams.put("flightNo", flightNo);
        orderParams.put("fromCity", fromCity);
        orderParams.put("toCity", toCity);
        orderParams.put("departureTime", params.getOrDefault("departureTime", LocalDateTime.now()));
        orderParams.put("arrivalTime", params.getOrDefault("arrivalTime", LocalDateTime.now()));

        Order order = orderService.createOrder(userId, orderParams);
        log.info("[航班] 下单成功：orderNo={} {}→{} 票数={} 金额={}",
                order.getOrderNo(), fromCity, toCity, passengers, price * passengers);
        return order;
    }
}
