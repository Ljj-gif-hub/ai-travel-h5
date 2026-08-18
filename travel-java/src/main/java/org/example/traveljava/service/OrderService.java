package org.example.traveljava.service;

import org.example.traveljava.entity.Order;
import org.example.traveljava.mq.TravelEvent;
import org.example.traveljava.mq.TravelEventPublisher;
import org.example.traveljava.mq.TravelEventType;
import org.example.traveljava.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final Set<String> VALID_TYPES = Set.of("flight", "hotel", "ticket");
    private static final Set<String> KNOWN_STATUS = Set.of("pending", "paid", "completed", "cancelled");
    /** 合法状态转移白名单：pending 只能先转 paid（防跳过支付），paid→completed/cancelled，终态不可变 */
    private static final Map<String, Set<String>> STATUS_TRANSITIONS = Map.of(
            "pending", Set.of("paid"),
            "paid", Set.of("completed", "cancelled"),
            "completed", Set.of(),
            "cancelled", Set.of()
    );

    private final OrderRepository orderRepository;
    private final TravelEventPublisher eventPublisher;
    private final CouponService couponService;
    private final UserService userService;

    public OrderService(OrderRepository orderRepository, TravelEventPublisher eventPublisher, CouponService couponService,
                        UserService userService) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.couponService = couponService;
        this.userService = userService;
    }

    public List<Order> getOrders(Long userId) {
        return getOrders(userId, 0, 20);
    }

    /** 分页获取（page 从 0 开始，修复全表加载） */
    public List<Order> getOrders(Long userId, int page, int size) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(safePage(page), safeSize(size)))
                .getContent();
    }

    public List<Order> getOrdersByType(Long userId, String type) {
        return getOrdersByType(userId, type, 0, 20);
    }

    public List<Order> getOrdersByType(Long userId, String type, int page, int size) {
        return orderRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type, PageRequest.of(safePage(page), safeSize(size)))
                .getContent();
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

    private static int safePage(int page) {
        return Math.max(page, 0);
    }

    private static int safeSize(int size) {
        return Math.min(Math.max(size, 1), 100);
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
     * 支付回调标记已支付（幂等、并发安全）— 公开回调路径，不校验属主（由支付渠道验签保证真实性）
     * pending→paid 用单条原子 UPDATE 完成，避免「读→判→写」在并发回调下双重支付；
     * 受影响行数为 1 时本请求才是唯一生效者，才发布 ORDER_PAID 事件（事件在事务提交后发布，消除双写）。
     * 受影响行数为 0 表示已被并发请求标记或非 pending 状态，视为已处理（幂等返回）。
     */
    @Transactional
    public void markOrderPaid(String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在: " + orderNo));

        int updated = orderRepository.markPaidIfPending(orderNo, LocalDateTime.now(),
                order.getPayChannel() == null ? "mock" : order.getPayChannel());
        if (updated != 1) {
            log.info("订单已支付（幂等忽略）：orderNo={}", orderNo);
            return;
        }
        log.info("订单支付成功：orderNo={}", orderNo);

        // 【新功能】支付成功 +10 积分（仅 markPaidIfPending 返回 1 时发放，保证幂等不重复发奖）
        if (order.getUserId() != null) {
            try {
                userService.addPoints(order.getUserId(), 10);
            } catch (Exception e) {
                log.warn("支付积分发放失败: orderNo={}, err={}", orderNo, e.getMessage());
            }
        }

        // 发布「订单支付成功」事件 → RabbitMQ 异步处理（未启用 MQ 时同步降级记录）
        // 事务提交后发布，避免「DB 已提交但 MQ 未投递」/「MQ 已投递但 DB 回滚」的双写不一致
        Map<String, Object> payload = Map.of(
                "orderNo", orderNo,
                "userId", order.getUserId() == null ? 0L : order.getUserId(),
                "amount", order.getPrice() == null ? 0L : order.getPrice(),
                "channel", order.getPayChannel() == null ? "mock" : order.getPayChannel()
        );
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishOrderPaid(payload);
                }
            });
        } else {
            // 兜底：无活动事务时直接发布（正常调用链总是包在事务里）
            publishOrderPaid(payload);
        }
    }

    private void publishOrderPaid(Map<String, Object> payload) {
        try {
            eventPublisher.publish(TravelEvent.of(TravelEventType.ORDER_PAID, payload));
        } catch (Exception e) {
            // 事件发布绝不影响支付主流程
            log.warn("发布 ORDER_PAID 事件失败（忽略）: err={}", e.getMessage());
        }
    }

    @Transactional
    public Order createOrder(Long userId, Map<String, Object> params) {
        Order order = new Order();
        order.setUserId(userId);
        // 订单号使用随机不可枚举的 UUID 片段（防枚举/防伪造回调）
        order.setOrderNo("ORD" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());

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
        // 作废订单时释放其占用的优惠券
        if ("cancelled".equals(status)) {
            couponService.releaseByOrder(orderId);
        }
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
        // 释放优惠券，供再次使用
        couponService.releaseByOrder(order.getId());
        log.info("取消订单：orderId={}", orderId);
    }
}
