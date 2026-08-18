package org.example.traveljava.controller;

import org.example.traveljava.entity.Order;
import org.example.traveljava.service.OrderService;
import org.example.traveljava.util.JwtUtil;
import org.example.traveljava.util.AuthUtils;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@io.swagger.v3.oas.annotations.tags.Tag(name = "电商")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;
    private final JwtUtil jwtUtil;

    public OrderController(OrderService orderService, JwtUtil jwtUtil) {
        this.orderService = orderService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> getOrders(@RequestHeader("Authorization") String authHeader,
                                                       @RequestParam(required = false) String type,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            // 分页（page 从 0 开始，默认 0/20，向后兼容：响应仍为列表）
            List<Order> orders;
            if (type != null && !type.isEmpty()) {
                orders = orderService.getOrdersByType(userId, type, page, size);
            } else {
                orders = orderService.getOrders(userId, page, size);
            }

            List<Map<String, Object>> result = orders.stream().map(order -> {
                Map<String, Object> item = new HashMap<>();
                item.put("id", order.getId());
                item.put("orderNo", order.getOrderNo());
                item.put("type", order.getType());
                item.put("status", order.getStatus());
                item.put("price", order.getPrice());

                if ("flight".equals(order.getType())) {
                    item.put("flightNo", order.getFlightNo());
                    item.put("fromCity", order.getFromCity());
                    item.put("toCity", order.getToCity());
                    if (order.getDepartureTime() != null) {
                        item.put("date", order.getDepartureTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                    }
                } else if ("hotel".equals(order.getType())) {
                    item.put("hotelName", order.getHotelName());
                    if (order.getCheckInTime() != null && order.getCheckOutTime() != null) {
                        item.put("checkIn", order.getCheckInTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                        item.put("checkOut", order.getCheckOutTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                    }
                } else if ("ticket".equals(order.getType())) {
                    item.put("scenicName", order.getScenicName());
                    if (order.getTicketDate() != null) {
                        item.put("date", order.getTicketDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                    }
                }

                return item;
            }).toList();

            return Result.ok(result);
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (Exception e) {
            log.error("获取订单列表失败", e);
            return Result.fail("获取订单列表失败");
        }
    }

    // 注意：裸 POST /api/orders（创建订单）与 PUT /api/orders/{id}/status（改状态）端点已移除——
    // 二者曾允许客户端任意指定 price（改价下单）、pending 直接置 completed（跳过支付）。
    // 下单请走 /api/hotel/book、/api/flight/book 等服务端报价接口；取消订单走下方 /{id}/cancel。

    @PostMapping("/{id}/cancel")
    public Result<String> cancelOrder(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            orderService.cancelOrder(userId, id);
            return Result.ok("取消成功");
        } catch (IllegalArgumentException e) {
            log.warn("取消订单失败：{}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (Exception e) {
            log.error("取消订单异常", e);
            return Result.fail("取消订单失败");
        }
    }

    @GetMapping("/count")
    public Result<Map<String, Object>> getOrderCount(@RequestHeader("Authorization") String authHeader,
                                                     @RequestParam(required = false) String status) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            Map<String, Object> result = new HashMap<>();
            if (status != null && !status.isEmpty()) {
                result.put("count", orderService.getOrderCountByStatus(userId, status));
            } else {
                result.put("count", orderService.getOrderCount(userId));
            }

            return Result.ok(result);
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (Exception e) {
            log.error("获取订单数量失败", e);
            return Result.fail("获取订单数量失败");
        }
    }
}
