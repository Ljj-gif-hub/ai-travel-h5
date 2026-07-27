package org.example.traveljava.controller;

import org.example.traveljava.entity.Comment;
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
    public Result<List<Map<String, Object>>> getComments(@PathVariable Long noteId) {
        try {
            List<Comment> comments = commentService.getComments(noteId);

            List<Map<String, Object>> result = comments.stream().map(c -> {
                Map<String, Object> item = commentToMap(c);
                // 回复总数
                int replyCount = commentService.getReplyCount(c.getId());
                item.put("replyCount", replyCount);
                // 点赞最多的那条热评回复（抖音风格：只展示一条）
                if (replyCount > 0) {
                    commentService.getTopReply(c.getId()).ifPresent(topReply -> {
                        item.put("topReply", commentToMap(topReply));
                    });
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
            List<Map<String, Object>> result = replies.stream()
                    .map(this::commentToMap)
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

            Map<String, Object> result = commentToMap(comment);
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

    /** 点赞评论 */
    @PostMapping("/comments/{id}/like")
    public Result<Map<String, Object>> likeComment(@RequestHeader("Authorization") String authHeader,
                                                     @PathVariable Long id) {
        try {
            String token = authHeader.replace("Bearer ", "");
            jwtUtil.extractUserId(token); // auth check
            Comment comment = commentService.likeComment(id);
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

    /** 将 Comment 实体转为前端 Map */
    private Map<String, Object> commentToMap(Comment c) {
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
        // 作者信息
        userRepository.findById(c.getUserId()).ifPresent(author -> {
            item.put("authorName", author.getNickname() != null ? author.getNickname() : author.getUsername());
            item.put("authorAvatar", author.getAvatar());
        });
        return item;
    }
}
