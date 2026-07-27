package org.example.traveljava.controller;

import org.example.traveljava.service.FollowService;
import org.example.traveljava.util.JwtUtil;
import org.example.traveljava.util.AuthUtils;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FollowController {

    private static final Logger log = LoggerFactory.getLogger(FollowController.class);

    private final FollowService followService;
    private final JwtUtil jwtUtil;

    public FollowController(FollowService followService, JwtUtil jwtUtil) {
        this.followService = followService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/user/following")
    public Result<List<Map<String, Object>>> getFollowing(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null) return Result.ok(java.util.Collections.emptyList());
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            List<Map<String, Object>> following = followService.getFollowing(userId);
            return Result.ok(following);
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (Exception e) {
            log.error("获取关注列表失败", e);
            return Result.fail("获取关注列表失败");
        }
    }

    @GetMapping("/user/followers")
    public Result<List<Map<String, Object>>> getFollowers(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            List<Map<String, Object>> followers = followService.getFollowers(userId);
            return Result.ok(followers);
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (Exception e) {
            log.error("获取粉丝列表失败", e);
            return Result.fail("获取粉丝列表失败");
        }
    }

    @PostMapping("/user/follow/{id}")
    public Result<String> follow(@RequestHeader(value = "Authorization", required = false) String authHeader, @PathVariable Long id) {
        if (authHeader == null) return Result.fail("请先登录");
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            followService.follow(userId, id);
            return Result.ok("关注成功");
        } catch (IllegalArgumentException e) {
            log.warn("关注失败：{}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (Exception e) {
            log.error("关注异常", e);
            return Result.fail("关注失败");
        }
    }

    @PostMapping("/user/unfollow/{id}")
    public Result<String> unfollow(@RequestHeader(value = "Authorization", required = false) String authHeader, @PathVariable Long id) {
        if (authHeader == null) return Result.fail("请先登录");
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            followService.unfollow(userId, id);
            return Result.ok("取消关注成功");
        } catch (IllegalArgumentException e) {
            log.warn("取消关注失败：{}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (Exception e) {
            log.error("取消关注异常", e);
            return Result.fail("取消关注失败");
        }
    }

    @GetMapping("/user/following/count")
    public Result<Map<String, Object>> getFollowingCount(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            Map<String, Object> result = new HashMap<>();
            result.put("count", followService.getFollowingCount(userId));
            return Result.ok(result);
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (Exception e) {
            log.error("获取关注数量失败", e);
            return Result.fail("获取关注数量失败");
        }
    }

    @GetMapping("/user/followers/count")
    public Result<Map<String, Object>> getFollowersCount(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            Map<String, Object> result = new HashMap<>();
            result.put("count", followService.getFollowersCount(userId));
            return Result.ok(result);
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (Exception e) {
            log.error("获取粉丝数量失败", e);
            return Result.fail("获取粉丝数量失败");
        }
    }
}
