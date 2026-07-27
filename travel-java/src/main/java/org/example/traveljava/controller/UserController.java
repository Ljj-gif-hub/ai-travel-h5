package org.example.traveljava.controller;

import org.example.traveljava.annotation.RateLimit;
import org.example.traveljava.entity.User;
import org.example.traveljava.service.UserService;
import org.example.traveljava.util.AuthUtils;
import org.example.traveljava.util.JwtUtil;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public UserController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/profile")
    public Result<Map<String, Object>> getProfile(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String username = jwtUtil.extractUsername(token);
            User user = userService.getUserByUsername(username);

            Map<String, Object> profile = new HashMap<>();
            profile.put("id", user.getId());
            profile.put("username", user.getUsername());
            profile.put("nickname", user.getNickname());
            profile.put("avatar", user.getAvatar());
            profile.put("bio", user.getBio());
            profile.put("phone", user.getPhone());
            profile.put("email", user.getEmail());
            profile.put("level", user.getLevel());
            profile.put("points", user.getPoints());
            profile.put("following", user.getFollowingCount());
            profile.put("followers", user.getFollowersCount());
            profile.put("travelNotes", user.getNotesCount());
            profile.put("citiesVisited", user.getCitiesVisited());
            profile.put("totalDays", user.getTotalDays());
            profile.put("totalSpent", user.getTotalSpent());
            profile.put("totalPhotos", user.getTotalPhotos());

            return Result.ok(profile);
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (Exception e) {
            log.error("获取用户资料失败", e);
            return Result.fail("获取用户资料失败");
        }
    }

    @PutMapping("/profile")
    public Result<Map<String, Object>> updateProfile(@RequestHeader("Authorization") String authHeader, @RequestBody Map<String, Object> params) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String username = jwtUtil.extractUsername(token);
            User user = userService.getUserByUsername(username);

            User updatedUser = userService.updateProfile(user.getId(), params);

            Map<String, Object> profile = new HashMap<>();
            profile.put("id", updatedUser.getId());
            profile.put("username", updatedUser.getUsername());
            profile.put("nickname", updatedUser.getNickname());
            profile.put("avatar", updatedUser.getAvatar());
            profile.put("bio", updatedUser.getBio());
            profile.put("phone", updatedUser.getPhone());
            profile.put("email", updatedUser.getEmail());
            profile.put("level", updatedUser.getLevel());
            profile.put("points", updatedUser.getPoints());
            profile.put("following", updatedUser.getFollowingCount());
            profile.put("followers", updatedUser.getFollowersCount());
            profile.put("travelNotes", updatedUser.getNotesCount());
            profile.put("citiesVisited", updatedUser.getCitiesVisited());
            profile.put("totalDays", updatedUser.getTotalDays());
            profile.put("totalSpent", updatedUser.getTotalSpent());
            profile.put("totalPhotos", updatedUser.getTotalPhotos());

            return Result.ok(profile);
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (IllegalArgumentException e) {
            log.warn("更新资料失败：{}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("更新资料异常", e);
            return Result.fail("更新资料失败");
        }
    }

    @PostMapping("/logout")
    public Result<String> logout(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            userService.logout(token);
            return Result.ok("退出成功");
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (Exception e) {
            log.error("退出登录异常", e);
            return Result.ok("退出成功");
        }
    }
}
