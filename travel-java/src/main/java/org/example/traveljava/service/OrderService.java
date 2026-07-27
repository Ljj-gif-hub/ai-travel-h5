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

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

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

    @Transactional
    public Order createOrder(Long userId, Map<String, Object> params) {
        Order order = new Order();
        order.setUserId(userId);
        order.setOrderNo("ORD" + System.currentTimeMillis() + (int)(Math.random() * 1000));
        
        String type = (String) params.get("type");
        order.setType(type);
        order.setStatus("pending");
        
        Long price = ((Number) params.get("price")).longValue();
        order.setPrice(price);

        if ("flight".equals(type)) {
            order.setFlightNo((String) params.get("flightNo"));
            order.setFromCity((String) params.get("fromCity"));
            order.setToCity((String) params.get("toCity"));
        } else if ("hotel".equals(type)) {
            order.setHotelName((String) params.get("hotelName"));
            order.setCheckInTime((LocalDateTime) params.get("checkInTime"));
            order.setCheckOutTime((LocalDateTime) params.get("checkOutTime"));
        } else if ("ticket".equals(type)) {
            order.setScenicName((String) params.get("scenicName"));
            order.setTicketDate((LocalDateTime) params.get("ticketDate"));
        }

        Order saved = orderRepository.save(order);
        log.info("创建订单：userId={}, orderNo={}, type={}", userId, saved.getOrderNo(), type);
        return saved;
    }

    @Transactional
    public Order updateOrderStatus(Long userId, Long orderId, String status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        
        if (!order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作该订单");
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

        order.setStatus("cancelled");
        orderRepository.save(order);
        log.info("取消订单：orderId={}", orderId);
    }
}
