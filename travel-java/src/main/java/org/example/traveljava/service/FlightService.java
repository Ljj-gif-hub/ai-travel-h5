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
     * 机票下单 — 金额以服务端报价为准（防篡改），创建 flight 订单（pending）。
     * @param userId 下单用户
     * @param params 需包含 flightNo/fromCity/toCity/date/passengers；
     *               客户端提交的 price 会被忽略，实际金额由服务端按航班重新报价
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
        LocalDate date = parseDate(params.get("date"));
        if (date == null) {
            throw new IllegalArgumentException("请提供出行日期");
        }

        // 安全：服务端重新报价。金额一律以供应方搜索到的该航班报价为准，
        // 不使用客户端传入的 price，防止改价下单。
        FlightOffer offer = flightProvider.search(fromCity, toCity, date).stream()
                .filter(o -> flightNo.equals(o.flightNo()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("航班不存在或已下架，请重新选择"));
        long unitPrice = offer.price();

        int passengers = params.get("passengers") == null ? 1 : Math.max(1, ((Number) params.get("passengers")).intValue());

        Map<String, Object> orderParams = new java.util.HashMap<>();
        orderParams.put("type", "flight");
        orderParams.put("price", unitPrice * passengers);
        orderParams.put("quantity", passengers);
        orderParams.put("flightNo", offer.flightNo());
        orderParams.put("fromCity", offer.fromCity());
        orderParams.put("toCity", offer.toCity());
        orderParams.put("departureTime", params.getOrDefault("departureTime", LocalDateTime.now()));
        orderParams.put("arrivalTime", params.getOrDefault("arrivalTime", LocalDateTime.now()));

        Order order = orderService.createOrder(userId, orderParams);
        log.info("[航班] 下单成功：orderNo={} {}→{} 票数={} 金额={}",
                order.getOrderNo(), fromCity, toCity, passengers, unitPrice * passengers);
        return order;
    }

    private LocalDate parseDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate d) return d;
        try {
            return LocalDate.parse(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }
}
