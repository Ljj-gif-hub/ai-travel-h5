package org.example.traveljava.controller;

import org.example.traveljava.service.RecommendationService;
import org.example.traveljava.util.AuthUtils;
import org.example.traveljava.util.JwtUtil;
import org.example.traveljava.vo.RecommendItem;
import org.example.traveljava.vo.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 智能推荐接口。
 *
 * 登录用户 → 个性化推荐；未登录 → 热门兜底。
 */
@RestController
@RequestMapping("/api/recommend")
@io.swagger.v3.oas.annotations.tags.Tag(name = "行程规划")
public class RecommendController {

    private final RecommendationService recommendationService;
    private final JwtUtil jwtUtil;

    public RecommendController(RecommendationService recommendationService, JwtUtil jwtUtil) {
        this.recommendationService = recommendationService;
        this.jwtUtil = jwtUtil;
    }

    /** 通用推荐（内容 + 协同 + 热门混合） */
    @GetMapping("/items")
    public Result<List<RecommendItem>> recommend(@RequestHeader(value = "Authorization", required = false) String auth,
                                                 @RequestParam(required = false, defaultValue = "10") int limit,
                                                 @RequestParam(required = false) String type,
                                                 @RequestParam(required = false) String city) {
        Long userId = AuthUtils.optionalUserId(auth, jwtUtil);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return Result.ok(recommendationService.recommend(userId, type, city, safeLimit));
    }

    /** 目的地推荐（行程目的地 + 城市收藏 + 热门城市兜底） */
    @GetMapping("/destinations")
    public Result<List<RecommendItem>> recommendDestinations(@RequestHeader(value = "Authorization", required = false) String auth,
                                                             @RequestParam(required = false, defaultValue = "10") int limit) {
        Long userId = AuthUtils.optionalUserId(auth, jwtUtil);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return Result.ok(recommendationService.recommendDestinations(userId, safeLimit));
    }
}
