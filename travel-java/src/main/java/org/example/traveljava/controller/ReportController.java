package org.example.traveljava.controller;

import org.example.traveljava.annotation.RateLimit;
import org.example.traveljava.entity.Report;
import org.example.traveljava.service.ReportService;
import org.example.traveljava.util.AuthUtils;
import org.example.traveljava.util.JwtUtil;
import org.example.traveljava.util.NumberUtil;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 【新功能】举报接口（/api 前缀，与 CommentController 并存无冲突）。
 * - POST /api/report                用户举报（登录）
 * - GET  /api/admin/reports         管理员举报列表
 * - POST /api/admin/report/{id}/handle 管理员处理（confirm/dismiss）
 */
@RestController
@RequestMapping("/api")
@io.swagger.v3.oas.annotations.tags.Tag(name = "举报")
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    private final ReportService reportService;
    private final JwtUtil jwtUtil;

    public ReportController(ReportService reportService, JwtUtil jwtUtil) {
        this.reportService = reportService;
        this.jwtUtil = jwtUtil;
    }

    /** 用户举报内容 */
    @PostMapping("/report")
    @RateLimit(max = 10, duration = 60, key = "report")
    public Result<Map<String, Object>> report(@RequestHeader("Authorization") String authHeader,
                                              @RequestBody Map<String, Object> body) {
        Long userId = AuthUtils.requireUserId(authHeader, jwtUtil);
        try {
            String targetType = body.get("targetType") != null ? String.valueOf(body.get("targetType")) : null;
            Long targetId = body.get("targetId") != null ? NumberUtil.toLong(body.get("targetId"), 0L) : null;
            String reason = body.get("reason") != null ? String.valueOf(body.get("reason")) : null;
            return Result.ok(reportService.report(userId, targetType, targetId, reason));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("举报提交失败", e);
            return Result.fail("举报失败，请稍后重试");
        }
    }

    /** 管理员：举报列表 */
    @GetMapping("/admin/reports")
    public Result<Map<String, Object>> listReports(@RequestHeader("Authorization") String authHeader,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        AuthUtils.requireAdmin(authHeader, jwtUtil);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<Report> result = reportService.listReports(status, PageRequest.of(Math.max(page, 0), safeSize));
        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getContent());
        data.put("total", result.getTotalElements());
        data.put("page", result.getNumber());
        data.put("size", result.getSize());
        return Result.ok(data);
    }

    /** 管理员：处理举报（action=confirm 确认违规隐藏 / dismiss 驳回） */
    @PostMapping("/admin/report/{id}/handle")
    public Result<Report> handle(@RequestHeader("Authorization") String authHeader,
                                 @PathVariable Long id,
                                 @RequestBody Map<String, Object> body) {
        Long adminId = AuthUtils.requireAdmin(authHeader, jwtUtil);
        String action = body.get("action") != null ? String.valueOf(body.get("action")) : null;
        if (!"confirm".equals(action) && !"dismiss".equals(action)) {
            return Result.fail("action 必须为 confirm 或 dismiss");
        }
        try {
            return Result.ok(reportService.handle(adminId, id, action));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("处理举报失败: reportId={}", id, e);
            return Result.fail("处理失败，请稍后重试");
        }
    }
}
