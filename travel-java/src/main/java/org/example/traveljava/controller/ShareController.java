package org.example.traveljava.controller;

import org.example.traveljava.annotation.RateLimit;
import org.example.traveljava.service.ShareService;
import org.example.traveljava.util.AuthUtils;
import org.example.traveljava.util.JwtUtil;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 行程分享控制器
 * - POST /api/share       创建分享链接（需登录）
 * - GET  /api/share/{token} 公开读取分享行程（免登录，只读快照）
 */
@RestController
@RequestMapping("/api/share")
public class ShareController {

    private static final Logger log = LoggerFactory.getLogger(ShareController.class);

    private final ShareService shareService;
    private final JwtUtil jwtUtil;

    public ShareController(ShareService shareService, JwtUtil jwtUtil) {
        this.shareService = shareService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestHeader("Authorization") String authHeader,
                                              @RequestBody Map<String, Object> body) {
        Long userId = AuthUtils.requireUserId(authHeader, jwtUtil);
        try {
            Object planIdObj = body.get("planId");
            if (planIdObj == null) {
                return Result.fail("缺少行程ID");
            }
            Long planId = ((Number) planIdObj).longValue();
            return Result.ok(shareService.createShare(userId, planId));
        } catch (AuthUtils.AuthException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("创建行程分享失败", e);
            return Result.fail("创建分享失败");
        }
    }

    @GetMapping("/{token}")
    @RateLimit(max = 30, duration = 60, key = "share_get")
    public Result<Map<String, Object>> get(@PathVariable String token) {
        try {
            return Result.ok(shareService.getSharedPlan(token));
        } catch (Exception e) {
            log.warn("读取分享失败：token={}, err={}", token, e.getMessage());
            return Result.fail(e.getMessage());
        }
    }
}
