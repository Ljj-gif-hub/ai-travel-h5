package org.example.traveljava.controller;

import org.example.traveljava.config.PaymentConfig;
import org.example.traveljava.service.PaymentService;
import org.example.traveljava.util.AuthUtils;
import org.example.traveljava.util.JwtUtil;
import org.example.traveljava.util.NumberUtil;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付控制器
 *
 * - POST /api/payment/create  发起支付（需登录）→ 返回支付跳转地址
 * - POST /api/payment/notify  支付渠道异步回调（公开，验签后幂等标记已支付）
 * - GET  /api/payment/mock-pay 模拟支付确认页（mock 渠道专用，公开）
 */
@RestController
@RequestMapping("/api/payment")
@io.swagger.v3.oas.annotations.tags.Tag(name = "电商")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;
    private final JwtUtil jwtUtil;
    private final PaymentConfig paymentConfig;

    public PaymentController(PaymentService paymentService, JwtUtil jwtUtil, PaymentConfig paymentConfig) {
        this.paymentService = paymentService;
        this.jwtUtil = jwtUtil;
        this.paymentConfig = paymentConfig;
    }

    /**
     * 发起支付
     */
    @PostMapping("/create")
    public Result<Map<String, Object>> create(@RequestHeader("Authorization") String authHeader,
                                              @RequestBody Map<String, Object> body) {
        Long userId = AuthUtils.requireUserId(authHeader, jwtUtil);
        try {
            Object orderIdObj = body.get("orderId");
            if (orderIdObj == null) {
                return Result.fail("缺少订单ID");
            }
            Long orderId = NumberUtil.toLong(orderIdObj, 0L);
            Map<String, Object> data = paymentService.createPayment(userId, orderId);
            return Result.ok(data);
        } catch (IllegalArgumentException e) {
            log.warn("发起支付失败：{}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (AuthUtils.AuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("发起支付异常", e);
            return Result.fail("发起支付失败");
        }
    }

    /**
     * 支付渠道异步回调（公开，无需登录）
     */
    @PostMapping("/notify")
    public Result<String> notify(@RequestBody Map<String, Object> body) {
        try {
            Map<String, String> params = new HashMap<>();
            body.forEach((k, v) -> params.put(k, v == null ? null : String.valueOf(v)));
            String orderNo = paymentService.handleNotify(params);
            return Result.ok("支付成功", orderNo);
        } catch (IllegalArgumentException e) {
            log.warn("支付回调处理失败：{}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("支付回调处理异常", e);
            return Result.fail("支付回调处理失败");
        }
    }

    /**
     * 模拟支付确认（mock 渠道专用）：GET 直接标记订单已支付
     * 【安全】需登录且必须是订单属主：未登录 401，非属主 403（防止凭订单号给他人免单）。
     */
    @GetMapping("/mock-pay")
    public Result<String> mockPay(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                  @RequestParam String orderNo) {
        // 安全：生产环境必须关闭模拟支付（payment.mock-pay-enabled=false），
        // 否则任何人可凭订单号把任意订单标记为已支付（免单漏洞）。
        if (!paymentConfig.isMockPayEnabled()) {
            return Result.fail("模拟支付已关闭");
        }
        try {
            Long userId = AuthUtils.requireUserId(authHeader, jwtUtil);
            paymentService.mockConfirm(userId, orderNo);
            return Result.ok("模拟支付成功");
        } catch (AuthUtils.AuthException | AuthUtils.ForbiddenException e) {
            throw e; // 401 / 403 由 GlobalExceptionHandler 统一返回
        } catch (IllegalArgumentException e) {
            log.warn("模拟支付失败：{}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("模拟支付异常", e);
            return Result.fail("模拟支付失败");
        }
    }
}
