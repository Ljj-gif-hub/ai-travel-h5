package org.example.traveljava.controller;

import org.example.traveljava.dto.SavedPlanRequest;
import org.example.traveljava.entity.SavedTravelPlan;
import org.example.traveljava.service.SavedTravelPlanService;
import org.example.traveljava.util.AuthUtils;
import org.example.traveljava.util.JwtUtil;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/travel/plan")
public class SavedTravelPlanController {

    private static final Logger log = LoggerFactory.getLogger(SavedTravelPlanController.class);

    private final SavedTravelPlanService savedTravelPlanService;
    private final JwtUtil jwtUtil;

    public SavedTravelPlanController(SavedTravelPlanService savedTravelPlanService, JwtUtil jwtUtil) {
        this.savedTravelPlanService = savedTravelPlanService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/save")
    public Result<Map<String, Object>> savePlan(@RequestHeader("Authorization") String authHeader,
                                                @RequestBody SavedPlanRequest request) {
        try {
            Long userId = AuthUtils.requireUserId(authHeader, jwtUtil);
            SavedTravelPlan saved = savedTravelPlanService.savePlan(userId, request);
            return Result.ok(savedTravelPlanService.toResponseMap(saved));
        } catch (AuthUtils.AuthException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("保存旅行规划失败", e);
            return Result.fail("保存失败：" + e.getMessage());
        }
    }

    @GetMapping("/saved")
    public Result<List<Map<String, Object>>> getAllPlans(@RequestHeader("Authorization") String authHeader) {
        try {
            Long userId = AuthUtils.requireUserId(authHeader, jwtUtil);
            List<SavedTravelPlan> plans = savedTravelPlanService.getAllPlans(userId);
            List<Map<String, Object>> result = plans.stream()
                    .map(savedTravelPlanService::toResponseMap)
                    .collect(Collectors.toList());
            return Result.ok(result);
        } catch (AuthUtils.AuthException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("获取旅行规划列表失败", e);
            return Result.fail("获取列表失败：" + e.getMessage());
        }
    }

    @GetMapping("/saved/{id}")
    public Result<Map<String, Object>> getPlanById(@RequestHeader("Authorization") String authHeader,
                                                   @PathVariable Long id) {
        try {
            Long userId = AuthUtils.requireUserId(authHeader, jwtUtil);
            SavedTravelPlan plan = savedTravelPlanService.getPlanById(userId, id);
            return Result.ok(savedTravelPlanService.toResponseMap(plan));
        } catch (AuthUtils.AuthException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("获取旅行规划详情失败：id={}", id, e);
            return Result.fail(e.getMessage());
        }
    }

    @DeleteMapping("/saved/{id}")
    public Result<Void> deletePlan(@RequestHeader("Authorization") String authHeader,
                                   @PathVariable Long id) {
        try {
            Long userId = AuthUtils.requireUserId(authHeader, jwtUtil);
            savedTravelPlanService.deletePlan(userId, id);
            return Result.ok(null);
        } catch (AuthUtils.AuthException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("删除旅行规划失败：id={}", id, e);
            return Result.fail(e.getMessage());
        }
    }
}
