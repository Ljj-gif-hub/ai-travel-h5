package org.example.traveljava.controller;

import org.example.traveljava.entity.Favorite;
import org.example.traveljava.service.FavoriteService;
import org.example.traveljava.util.AuthUtils;
import org.example.traveljava.util.JwtUtil;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private static final Logger log = LoggerFactory.getLogger(FavoriteController.class);

    private final FavoriteService favoriteService;
    private final JwtUtil jwtUtil;

    public FavoriteController(FavoriteService favoriteService, JwtUtil jwtUtil) {
        this.favoriteService = favoriteService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> getFavorites(@RequestHeader("Authorization") String authHeader,
                                                          @RequestParam(required = false) String type) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String username = jwtUtil.extractUsername(token);
            Long userId = jwtUtil.extractUserId(token);

            List<Favorite> favorites;
            if (type != null && !type.isEmpty()) {
                favorites = favoriteService.getFavoritesByType(userId, type);
            } else {
                favorites = favoriteService.getFavorites(userId);
            }

            List<Map<String, Object>> result = favorites.stream().map(favorite -> {
                Map<String, Object> item = new HashMap<>();
                item.put("id", favorite.getId());
                item.put("type", favorite.getTargetType());
                item.put("name", favorite.getTargetName());
                item.put("cover", favorite.getTargetCover());
                item.put("rating", favorite.getRating());
                item.put("author", favorite.getAuthor());
                item.put("likes", favorite.getLikes());
                item.put("desc", favorite.getDescription());
                return item;
            }).toList();

            return Result.ok(result);
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (Exception e) {
            log.error("获取收藏列表失败", e);
            return Result.fail("获取收藏列表失败");
        }
    }

    @PostMapping
    public Result<String> addFavorite(@RequestHeader("Authorization") String authHeader, @RequestBody Map<String, Object> params) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            favoriteService.addFavorite(userId, params);
            return Result.ok("收藏成功");
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (IllegalArgumentException e) {
            log.warn("添加收藏失败：{}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("添加收藏异常", e);
            return Result.fail("添加收藏失败");
        }
    }

    @DeleteMapping("/{id}")
    public Result<String> deleteFavorite(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            favoriteService.deleteFavoriteById(userId, id);
            return Result.ok("取消收藏成功");
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (IllegalArgumentException e) {
            log.warn("取消收藏失败：{}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("取消收藏异常", e);
            return Result.fail("取消收藏失败");
        }
    }

    @GetMapping("/count")
    public Result<Map<String, Object>> getFavoriteCount(@RequestHeader("Authorization") String authHeader,
                                                        @RequestParam(required = false) String type) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            Map<String, Object> result = new HashMap<>();
            if (type != null && !type.isEmpty()) {
                result.put("count", favoriteService.getFavoriteCountByType(userId, type));
            } else {
                result.put("count", favoriteService.getFavoriteCount(userId));
            }

            return Result.ok(result);
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (Exception e) {
            log.error("获取收藏数量失败", e);
            return Result.fail("获取收藏数量失败");
        }
    }
}
