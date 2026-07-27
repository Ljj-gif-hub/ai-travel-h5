package org.example.traveljava.controller;

import org.example.traveljava.entity.Coupon;
import org.example.traveljava.service.CouponService;
import org.example.traveljava.util.JwtUtil;
import org.example.traveljava.util.AuthUtils;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private static final Logger log = LoggerFactory.getLogger(CouponController.class);

    private final CouponService couponService;
    private final JwtUtil jwtUtil;

    public CouponController(CouponService couponService, JwtUtil jwtUtil) {
        this.couponService = couponService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> getCoupons(@RequestHeader("Authorization") String authHeader,
                                                       @RequestParam(required = false) String status) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            List<Coupon> coupons;
            if (status != null && !status.isEmpty()) {
                coupons = couponService.getCouponsByStatus(userId, status);
            } else {
                coupons = couponService.getCoupons(userId);
            }

            List<Map<String, Object>> result = coupons.stream().map(coupon -> {
                Map<String, Object> item = new HashMap<>();
                item.put("id", coupon.getId());
                item.put("status", coupon.getStatus());
                item.put("value", coupon.getValue());
                item.put("minAmount", coupon.getMinAmount());
                item.put("title", coupon.getTitle());
                item.put("validUntil", coupon.getValidUntil().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                item.put("category", coupon.getCategory());
                return item;
            }).toList();

            return Result.ok(result);
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (Exception e) {
            log.error("获取优惠券列表失败", e);
            return Result.fail("获取优惠券列表失败");
        }
    }

    @GetMapping("/count")
    public Result<Map<String, Object>> getCouponCount(@RequestHeader("Authorization") String authHeader,
                                                      @RequestParam(required = false) String status) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            Map<String, Object> result = new HashMap<>();
            if (status != null && !status.isEmpty()) {
                result.put("count", couponService.getCouponCountByStatus(userId, status));
            } else {
                result.put("count", couponService.getCouponCount(userId));
            }

            return Result.ok(result);
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (Exception e) {
            log.error("获取优惠券数量失败", e);
            return Result.fail("获取优惠券数量失败");
        }
    }

    @PostMapping("/use/{id}")
    public Result<String> useCoupon(@RequestHeader("Authorization") String authHeader,
                                    @PathVariable Long id,
                                    @RequestBody(required = false) Map<String, Long> body) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            Long orderId = body != null ? body.get("orderId") : null;
            couponService.useCoupon(userId, id, orderId);
            return Result.ok("使用成功");
        } catch (IllegalArgumentException e) {
            log.warn("使用优惠券失败：{}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (Exception e) {
            log.error("使用优惠券异常", e);
            return Result.fail("使用优惠券失败");
        }
    }
}
