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
                                                       @RequestParam(required = false) String type) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            List<Order> orders;
            if (type != null && !type.isEmpty()) {
                orders = orderService.getOrdersByType(userId, type);
            } else {
                orders = orderService.getOrders(userId);
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

    @PostMapping
    public Result<Map<String, Object>> createOrder(@RequestHeader("Authorization") String authHeader, @RequestBody Map<String, Object> params) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            Order order = orderService.createOrder(userId, params);

            Map<String, Object> result = new HashMap<>();
            result.put("id", order.getId());
            result.put("orderNo", order.getOrderNo());
            result.put("type", order.getType());
            result.put("status", order.getStatus());
            result.put("price", order.getPrice());

            return Result.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("创建订单失败：{}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (Exception e) {
            log.error("创建订单异常", e);
            return Result.fail("创建订单失败");
        }
    }

    @PutMapping("/{id}/status")
    public Result<String> updateOrderStatus(@RequestHeader("Authorization") String authHeader,
                                            @PathVariable Long id,
                                            @RequestBody Map<String, String> body) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            String status = body.get("status");
            orderService.updateOrderStatus(userId, id, status);
            return Result.ok("更新成功");
        } catch (IllegalArgumentException e) {
            log.warn("更新订单状态失败：{}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (Exception e) {
            log.error("更新订单状态异常", e);
            return Result.fail("更新订单状态失败");
        }
    }

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
