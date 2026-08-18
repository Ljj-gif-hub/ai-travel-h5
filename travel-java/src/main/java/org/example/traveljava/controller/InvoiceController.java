package org.example.traveljava.controller;

import org.example.traveljava.annotation.RateLimit;
import org.example.traveljava.entity.Invoice;
import org.example.traveljava.service.InvoiceService;
import org.example.traveljava.util.AuthUtils;
import org.example.traveljava.util.JwtUtil;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 【新功能】发票接口。
 * - POST /api/order/{orderId}/invoice 开具发票（仅已支付订单，一单一票）
 * - GET  /api/order/invoices          我的发票列表
 */
@RestController
@RequestMapping("/api/order")
@io.swagger.v3.oas.annotations.tags.Tag(name = "发票")
public class InvoiceController {

    private static final Logger log = LoggerFactory.getLogger(InvoiceController.class);

    private final InvoiceService invoiceService;
    private final JwtUtil jwtUtil;

    public InvoiceController(InvoiceService invoiceService, JwtUtil jwtUtil) {
        this.invoiceService = invoiceService;
        this.jwtUtil = jwtUtil;
    }

    /** 开具发票 */
    @PostMapping("/{orderId}/invoice")
    @RateLimit(max = 10, duration = 60, key = "invoice")
    public Result<Invoice> issue(@RequestHeader("Authorization") String authHeader,
                                 @PathVariable Long orderId,
                                 @RequestBody(required = false) Map<String, Object> body) {
        Long userId = AuthUtils.requireUserId(authHeader, jwtUtil);
        try {
            String title = body != null && body.get("title") != null ? String.valueOf(body.get("title")) : null;
            String taxNo = body != null && body.get("taxNo") != null ? String.valueOf(body.get("taxNo")) : null;
            String type = body != null && body.get("type") != null ? String.valueOf(body.get("type")) : "personal";
            return Result.ok(invoiceService.issue(userId, orderId, title, taxNo, type));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("开票失败: orderId={}", orderId, e);
            return Result.fail("开票失败，请稍后重试");
        }
    }

    /** 我的发票列表 */
    @GetMapping("/invoices")
    public Result<java.util.List<Invoice>> listMy(@RequestHeader("Authorization") String authHeader) {
        Long userId = AuthUtils.requireUserId(authHeader, jwtUtil);
        try {
            return Result.ok(invoiceService.listMy(userId));
        } catch (Exception e) {
            log.error("查询发票失败", e);
            return Result.fail("查询发票失败");
        }
    }
}
