package org.example.traveljava.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.example.traveljava.entity.SavedTravelPlan;
import org.example.traveljava.service.SavedTravelPlanService;
import org.example.traveljava.service.TripShareService;
import org.example.traveljava.util.AuthUtils;
import org.example.traveljava.util.JwtUtil;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 【新功能】行程计划接口（/api/trip 前缀，区别于 /api/travel/plan 的保存类接口）。
 * 提供：ICS 日历导出 + 24 小时分享链接（创建/匿名读取/撤销）。
 */
@RestController
@RequestMapping("/api/trip")
@io.swagger.v3.oas.annotations.tags.Tag(name = "行程计划")
public class TripPlanController {

    private static final Logger log = LoggerFactory.getLogger(TripPlanController.class);

    private final SavedTravelPlanService savedTravelPlanService;
    private final TripShareService tripShareService;
    private final JwtUtil jwtUtil;

    public TripPlanController(SavedTravelPlanService savedTravelPlanService,
                              TripShareService tripShareService,
                              JwtUtil jwtUtil) {
        this.savedTravelPlanService = savedTravelPlanService;
        this.tripShareService = tripShareService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 导出已保存行程为 ICS 日历文件（每天一个 VEVENT，日期浮动从今天起）。
     * 仅限行程所有者导出（否则 403）。
     */
    @GetMapping(value = "/{planId}/ics", produces = "text/calendar;charset=UTF-8")
    public String exportIcs(@RequestHeader("Authorization") String authHeader,
                            @PathVariable Long planId,
                            HttpServletResponse response) {
        Long userId = AuthUtils.requireUserId(authHeader, jwtUtil);
        SavedTravelPlan plan = savedTravelPlanService.getPlanById(userId, planId);
        if (!userId.equals(plan.getUserId())) {
            // 属主校验兜底（getPlanById 已校验，双保险）
            throw new AuthUtils.ForbiddenException("无权导出该行程");
        }

        String ics = savedTravelPlanService.buildIcs(plan);
        String filename = URLEncoder.encode(plan.getDestination(), StandardCharsets.UTF_8) + "行程.ics";
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + filename.replace("+", "%20") + "\"; filename*=UTF-8''" + filename);
        log.info("导出 ICS: planId={}, userId={}, bytes={}", planId, userId, ics.length());
        return ics;
    }

    /* ==================== 【新功能】24 小时分享链接 ==================== */

    /** 创建分享（仅行程所有者，24 小时有效） */
    @PostMapping("/{planId}/share")
    public Result<Map<String, Object>> createShare(@RequestHeader("Authorization") String authHeader,
                                                   @PathVariable Long planId) {
        Long userId = AuthUtils.requireUserId(authHeader, jwtUtil);
        try {
            return Result.ok(tripShareService.createShare(userId, planId));
        } catch (AuthUtils.ForbiddenException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("创建分享失败: planId={}", planId, e);
            return Result.fail("创建分享失败，请稍后重试");
        }
    }

    /** 匿名读取分享行程（token 未过期即可访问） */
    @GetMapping("/share/{token}")
    public Result<Map<String, Object>> getShare(@PathVariable String token) {
        try {
            return Result.ok(tripShareService.getSharedPlan(token));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.warn("读取分享失败: token={}", token, e);
            return Result.fail("分享不存在或已失效");
        }
    }

    /** 撤销分享（仅创建者） */
    @DeleteMapping("/share/{token}")
    public Result<Void> revokeShare(@RequestHeader("Authorization") String authHeader,
                                    @PathVariable String token) {
        Long userId = AuthUtils.requireUserId(authHeader, jwtUtil);
        try {
            tripShareService.revokeShare(userId, token);
            return Result.ok(null);
        } catch (AuthUtils.ForbiddenException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("撤销分享失败: token={}", token, e);
            return Result.fail("撤销失败，请稍后重试");
        }
    }
}
