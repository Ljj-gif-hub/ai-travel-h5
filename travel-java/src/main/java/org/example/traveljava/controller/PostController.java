package org.example.traveljava.controller;

import org.example.traveljava.entity.Post;
import org.example.traveljava.service.PostService;
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
@RequestMapping("/api/posts")
public class PostController {

    private static final Logger log = LoggerFactory.getLogger(PostController.class);

    private final PostService postService;
    private final JwtUtil jwtUtil;

    public PostController(PostService postService, JwtUtil jwtUtil) {
        this.postService = postService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 【修复】社区广场：返回所有用户的动态（非仅当前用户）
     * 附带作者昵称/头像 + 当前用户是否已点赞
     * 未登录也可浏览，但不会标记点赞状态
     */
    @GetMapping
    public Result<List<Map<String, Object>>> getPosts(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            Long userId = null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    String token = authHeader.replace("Bearer ", "");
                    userId = jwtUtil.extractUserId(token);
                } catch (AuthUtils.AuthException e) {
                    // 可选认证：过期/无效 token → 以游客身份浏览
                    log.debug("动态列表可选认证失败: {}", e.getMessage());
                    userId = null;
                }
            }

            List<Map<String, Object>> result = postService.getPosts(userId);
            return Result.ok(result);
        } catch (Exception e) {
            log.error("获取动态列表失败", e);
            return Result.fail("获取动态列表失败");
        }
    }

    @PostMapping
    public Result<Map<String, Object>> createPost(@RequestHeader("Authorization") String authHeader, @RequestBody Map<String, Object> params) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            Post post = postService.createPost(userId, params);

            Map<String, Object> result = new HashMap<>();
            result.put("id", post.getId());
            result.put("content", post.getContent());

            return Result.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("创建动态失败：{}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (Exception e) {
            log.error("创建动态异常", e);
            return Result.fail("创建动态失败");
        }
    }

    @DeleteMapping("/{id}")
    public Result<String> deletePost(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            postService.deletePost(userId, id);
            return Result.ok("删除成功");
        } catch (IllegalArgumentException e) {
            log.warn("删除动态失败：{}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (Exception e) {
            log.error("删除动态异常", e);
            return Result.fail("删除动态失败");
        }
    }

    /**
     * 【修复】点赞/取消点赞 — 需要登录，按用户追踪，每人只能点赞一次
     */
    @PostMapping("/{id}/like")
    public Result<Map<String, Object>> likePost(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            Map<String, Object> result = postService.toggleLike(id, userId);
            return Result.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("点赞失败：{}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (Exception e) {
            log.error("点赞异常", e);
            return Result.fail("点赞失败");
        }
    }
}
