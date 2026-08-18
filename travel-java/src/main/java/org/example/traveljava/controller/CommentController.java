package org.example.traveljava.controller;

import org.example.traveljava.entity.Comment;
import org.example.traveljava.entity.User;
import org.example.traveljava.repository.UserRepository;
import org.example.traveljava.service.CommentService;
import org.example.traveljava.util.JwtUtil;
import org.example.traveljava.util.AuthUtils;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@io.swagger.v3.oas.annotations.tags.Tag(name = "社交")
public class CommentController {

    private static final Logger log = LoggerFactory.getLogger(CommentController.class);

    private final CommentService commentService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public CommentController(CommentService commentService, JwtUtil jwtUtil, UserRepository userRepository) {
        this.commentService = commentService;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    /**
     * 获取顶级评论列表（不含回复）。
     * 每条评论附带：作者信息、回复总数、点赞最多的一条热评回复。
     */
    @GetMapping("/notes/{noteId}/comments")
    public Result<List<Map<String, Object>>> getComments(@PathVariable Long noteId,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        try {
            // 分页（page 从 0 开始，默认 0/20，向后兼容：响应仍为列表）
            List<Comment> comments = commentService.getComments(noteId, page, size);
            if (comments.isEmpty()) {
                return Result.ok(Collections.emptyList());
            }

            // 批量加载：回复数 + 热评回复 + 所有涉及的用户，避免逐条 N+1 查询
            List<Long> ids = comments.stream().map(Comment::getId).collect(Collectors.toList());
            Map<Long, Integer> replyCounts = commentService.getReplyCounts(ids);
            Map<Long, Comment> topReplies = commentService.getTopReplies(ids);

            Set<Long> userIds = new HashSet<>();
            comments.forEach(c -> userIds.add(c.getUserId()));
            topReplies.values().forEach(r -> userIds.add(r.getUserId()));
            Map<Long, User> users = loadUsers(userIds);

            List<Map<String, Object>> result = comments.stream().map(c -> {
                Map<String, Object> item = commentToMap(c, users);
                int replyCount = replyCounts.getOrDefault(c.getId(), 0);
                item.put("replyCount", replyCount);
                Comment topReply = topReplies.get(c.getId());
                if (topReply != null) {
                    item.put("topReply", commentToMap(topReply, users));
                }
                return item;
            }).collect(Collectors.toList());

            return Result.ok(result);
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (Exception e) {
            log.error("获取评论列表异常", e);
            return Result.fail("获取评论失败");
        }
    }

    /** 获取某条评论的所有回复 */
    @GetMapping("/comments/{id}/replies")
    public Result<List<Map<String, Object>>> getReplies(@PathVariable Long id) {
        try {
            List<Comment> replies = commentService.getReplies(id);
            // 批量预加载作者，避免逐条 findById
            Set<Long> userIds = replies.stream().map(Comment::getUserId).collect(Collectors.toSet());
            Map<Long, User> users = loadUsers(userIds);
            List<Map<String, Object>> result = replies.stream()
                    .map(c -> commentToMap(c, users))
                    .collect(Collectors.toList());
            return Result.ok(result);
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (Exception e) {
            log.error("获取回复列表异常", e);
            return Result.fail("获取回复失败");
        }
    }

    /**
     * 添加评论或回复。
     * 请求体可包含 parentId 字段：不传或 null = 顶级评论，传值 = 回复某条评论。
     */
    @PostMapping("/notes/{noteId}/comments")
    public Result<Map<String, Object>> addComment(@RequestHeader("Authorization") String authHeader,
                                                   @PathVariable Long noteId,
                                                   @RequestBody Map<String, String> params) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            String content = params.get("content");
            String image = params.get("image");
            String video = params.get("video");
            String parentIdStr = params.get("parentId");
            Long parentId = (parentIdStr != null && !parentIdStr.isEmpty()) ? Long.valueOf(parentIdStr) : null;

            Comment comment = commentService.addReply(userId, noteId, parentId, content, image, video);

            Map<String, Object> result = commentToMap(comment, loadUsers(Set.of(comment.getUserId())));
            return Result.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("添加评论失败：{}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (Exception e) {
            log.error("添加评论异常", e);
            return Result.fail("添加评论失败");
        }
    }

    @DeleteMapping("/comments/{id}")
    public Result<String> deleteComment(@RequestHeader("Authorization") String authHeader,
                                         @PathVariable Long id) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            commentService.deleteComment(userId, id);
            return Result.ok("删除成功");
        } catch (IllegalArgumentException e) {
            log.warn("删除评论失败：{}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (Exception e) {
            log.error("删除评论异常", e);
            return Result.fail("删除评论失败");
        }
    }

    /** 点赞评论（同一用户对同一评论幂等，防止刷量） */
    @PostMapping("/comments/{id}/like")
    public Result<Map<String, Object>> likeComment(@RequestHeader("Authorization") String authHeader,
                                                     @PathVariable Long id) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token); // auth check
            if (userId == null) {
                throw new AuthUtils.AuthException("请先登录");
            }
            Comment comment = commentService.likeComment(userId, id);
            Map<String, Object> result = new HashMap<>();
            result.put("id", comment.getId());
            result.put("likes", comment.getLikes());
            return Result.ok(result);
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (Exception e) {
            log.error("点赞评论异常", e);
            return Result.fail("点赞失败");
        }
    }

    /** 批量加载用户，返回 id → User 映射（一次 IN 查询，避免逐条 findById） */
    private Map<Long, User> loadUsers(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    /** 将 Comment 实体转为前端 Map（作者信息从预加载的 users 映射中取） */
    private Map<String, Object> commentToMap(Comment c, Map<Long, User> users) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", c.getId());
        item.put("noteId", c.getNoteId());
        item.put("userId", c.getUserId());
        item.put("parentId", c.getParentId());
        item.put("content", c.getContent());
        item.put("image", c.getImage());
        item.put("video", c.getVideo());
        item.put("likes", c.getLikes());
        item.put("date", c.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        // 作者信息（已预加载，不再逐条查库）
        User author = users.get(c.getUserId());
        if (author != null) {
            item.put("authorName", author.getNickname() != null ? author.getNickname() : author.getUsername());
            item.put("authorAvatar", author.getAvatar());
        }
        return item;
    }
}
