package org.example.traveljava.controller;

import org.example.traveljava.annotation.RateLimit;
import org.example.traveljava.entity.Refund;
import org.example.traveljava.service.RefundService;
import org.example.traveljava.util.AuthUtils;
import org.example.traveljava.util.JwtUtil;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 【新功能】退款接口（/api 前缀）。
 * - POST /api/order/{orderId}/refund   用户申请退款
 * - GET  /api/order/refunds            我的退款单
 * - GET  /api/admin/refunds            管理员退款单列表
 * - POST /api/admin/refund/{id}/handle 管理员审核（approve/reject）
 */
@RestController
@RequestMapping("/api")
@io.swagger.v3.oas.annotations.tags.Tag(name = "退款")
public class RefundController {

    private static final Logger log = LoggerFactory.getLogger(RefundController.class);

    private final RefundService refundService;
    private final JwtUtil jwtUtil;

    public RefundController(RefundService refundService, JwtUtil jwtUtil) {
        this.refundService = refundService;
        this.jwtUtil = jwtUtil;
    }

    /** 用户申请退款 */
    @PostMapping("/order/{orderId}/refund")
    @RateLimit(max = 5, duration = 60, key = "refund")
    public Result<Refund> requestRefund(@RequestHeader("Authorization") String authHeader,
                                        @PathVariable Long orderId,
                                        @RequestBody(required = false) Map<String, Object> body) {
        Long userId = AuthUtils.requireUserId(authHeader, jwtUtil);
        try {
            String reason = body != null && body.get("reason") != null ? String.valueOf(body.get("reason")) : null;
            return Result.ok(refundService.requestRefund(userId, orderId, reason));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("退款申请失败: orderId={}", orderId, e);
            return Result.fail("退款申请失败，请稍后重试");
        }
    }

    /** 我的退款单列表 */
    @GetMapping("/order/refunds")
    public Result<java.util.List<Refund>> listMyRefunds(@RequestHeader("Authorization") String authHeader) {
        Long userId = AuthUtils.requireUserId(authHeader, jwtUtil);
        try {
            return Result.ok(refundService.listMyRefunds(userId));
        } catch (Exception e) {
            log.error("查询退款单失败", e);
            return Result.fail("查询退款单失败");
        }
    }

    /** 管理员：退款单列表 */
    @GetMapping("/admin/refunds")
    public Result<Map<String, Object>> listAll(@RequestHeader("Authorization") String authHeader,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        AuthUtils.requireAdmin(authHeader, jwtUtil);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<Refund> result = refundService.listAll(status, PageRequest.of(Math.max(page, 0), safeSize));
        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getContent());
        data.put("total", result.getTotalElements());
        data.put("page", result.getNumber());
        data.put("size", result.getSize());
        return Result.ok(data);
    }

    /** 管理员：审核退款（action=approve 通过 / reject 驳回） */
    @PostMapping("/admin/refund/{id}/handle")
    public Result<Refund> handle(@RequestHeader("Authorization") String authHeader,
                                 @PathVariable Long id,
                                 @RequestBody Map<String, Object> body) {
        Long adminId = AuthUtils.requireAdmin(authHeader, jwtUtil);
        String action = body.get("action") != null ? String.valueOf(body.get("action")) : null;
        if (!"approve".equals(action) && !"reject".equals(action)) {
            return Result.fail("action 必须为 approve 或 reject");
        }
        try {
            return Result.ok(refundService.handle(adminId, id, action));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (IllegalStateException e) {
            log.error("退款审核执行失败: refundId={}, err={}", id, e.getMessage());
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("退款审核失败: refundId={}", id, e);
            return Result.fail("处理失败，请稍后重试");
        }
    }
}
