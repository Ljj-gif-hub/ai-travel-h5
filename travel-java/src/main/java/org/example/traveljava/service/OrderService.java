package org.example.traveljava.service;

import org.example.traveljava.entity.Order;
import org.example.traveljava.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final Set<String> VALID_TYPES = Set.of("flight", "hotel", "ticket");
    private static final Set<String> KNOWN_STATUS = Set.of("pending", "paid", "completed", "cancelled");
    /** 合法状态转移白名单：pending→paid/completed/cancelled，paid→completed/cancelled，终态不可变 */
    private static final Map<String, Set<String>> STATUS_TRANSITIONS = Map.of(
            "pending", Set.of("paid", "completed", "cancelled"),
            "paid", Set.of("completed", "cancelled"),
            "completed", Set.of(),
            "cancelled", Set.of()
    );

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public List<Order> getOrders(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<Order> getOrdersByType(Long userId, String type) {
        return orderRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type);
    }

    public List<Order> getOrdersByStatus(Long userId, String status) {
        return orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status);
    }

    public int getOrderCount(Long userId) {
        return orderRepository.countByUserId(userId);
    }

    public int getOrderCountByStatus(Long userId, String status) {
        return orderRepository.countByUserIdAndStatus(userId, status);
    }

    /**
     * 校验订单属主并返回（支付发起前校验）
     */
    public Order getOwnedOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if (!order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作该订单");
        }
        return order;
    }

    /**
     * 支付回调标记已支付（幂等）— 公开回调路径，不校验属主（由支付渠道验签保证真实性）
     * pending→paid；已 paid 直接返回（幂等）；其余状态不可支付
     */
    @Transactional
    public void markOrderPaid(String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在: " + orderNo));
        if ("paid".equals(order.getStatus())) {
            log.info("订单已支付（幂等忽略）：orderNo={}", orderNo);
            return;
        }
        if (!"pending".equals(order.getStatus())) {
            throw new IllegalArgumentException("订单当前状态不可支付: " + order.getStatus());
        }
        order.setStatus("paid");
        order.setPaidAt(LocalDateTime.now());
        orderRepository.save(order);
        log.info("订单支付成功：orderNo={}", orderNo);
    }

    @Transactional
    public Order createOrder(Long userId, Map<String, Object> params) {
        Order order = new Order();
        order.setUserId(userId);
        order.setOrderNo("ORD" + System.currentTimeMillis() + (int)(Math.random() * 1000));

        String type = (String) params.get("type");
        if (type == null || !VALID_TYPES.contains(type)) {
            throw new IllegalArgumentException("无效的订单类型");
        }
        order.setType(type);
        order.setStatus("pending");

        Object priceObj = params.get("price");
        if (priceObj == null) {
            throw new IllegalArgumentException("价格无效");
        }
        Long price;
        try {
            price = ((Number) priceObj).longValue();
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("价格无效");
        }
        if (price <= 0) {
            throw new IllegalArgumentException("价格无效");
        }
        order.setPrice(price);

        if (params.containsKey("quantity") && params.get("quantity") != null) {
            try {
                int qty = ((Number) params.get("quantity")).intValue();
                if (qty >= 1) {
                    order.setQuantity(qty);
                }
            } catch (ClassCastException ignored) {
                // 非法数量忽略，保持默认 1
            }
        }

        if ("flight".equals(type)) {
            order.setFlightNo((String) params.get("flightNo"));
            order.setFromCity((String) params.get("fromCity"));
            order.setToCity((String) params.get("toCity"));
            // 补齐航班时间字段（此前从未赋值，导致列表永远为 null）
            order.setDepartureTime(parseDateTime(params.get("departureTime")));
            order.setArrivalTime(parseDateTime(params.get("arrivalTime")));
        } else if ("hotel".equals(type)) {
            order.setHotelName((String) params.get("hotelName"));
            order.setCheckInTime(parseDateTime(params.get("checkInTime")));
            order.setCheckOutTime(parseDateTime(params.get("checkOutTime")));
        } else if ("ticket".equals(type)) {
            order.setScenicName((String) params.get("scenicName"));
            order.setTicketDate(parseDateTime(params.get("ticketDate")));
        }

        Order saved = orderRepository.save(order);
        log.info("创建订单：userId={}, orderNo={}, type={}", userId, saved.getOrderNo(), type);
        return saved;
    }

    /**
     * 把请求体中的日期统一解析为 LocalDateTime（兼容 ISO 字符串与 LocalDateTime 两种传参）。
     */
    private LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (value instanceof String s) {
            String str = s.trim();
            if (str.isEmpty()) {
                return null;
            }
            try {
                return LocalDateTime.parse(str);
            } catch (Exception e) {
                throw new IllegalArgumentException("日期格式不正确: " + str);
            }
        }
        throw new IllegalArgumentException("日期格式不正确");
    }

    @Transactional
    public Order updateOrderStatus(Long userId, Long orderId, String status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));

        if (!order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作该订单");
        }

        if (status == null || !KNOWN_STATUS.contains(status)) {
            throw new IllegalArgumentException("无效的订单状态");
        }
        String current = order.getStatus();
        if (!STATUS_TRANSITIONS.getOrDefault(current, Set.of()).contains(status)) {
            throw new IllegalArgumentException("订单状态不能从 " + current + " 变更为 " + status);
        }

        order.setStatus(status);
        Order saved = orderRepository.save(order);
        log.info("更新订单状态：orderId={}, status={}", orderId, status);
        return saved;
    }

    @Transactional
    public void cancelOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));

        if (!order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作该订单");
        }

        String current = order.getStatus();
        if (!("pending".equals(current) || "paid".equals(current))) {
            throw new IllegalArgumentException("当前订单状态不可取消");
        }

        order.setStatus("cancelled");
        orderRepository.save(order);
        log.info("取消订单：orderId={}", orderId);
    }
}
