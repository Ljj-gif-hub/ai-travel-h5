package org.example.traveljava.controller;

import org.example.traveljava.entity.Feedback;
import org.example.traveljava.service.FeedbackService;
import org.example.traveljava.util.JwtUtil;
import org.example.traveljava.util.AuthUtils;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private static final Logger log = LoggerFactory.getLogger(FeedbackController.class);

    private final FeedbackService feedbackService;
    private final JwtUtil jwtUtil;

    public FeedbackController(FeedbackService feedbackService, JwtUtil jwtUtil) {
        this.feedbackService = feedbackService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> getFeedbacks(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            List<Feedback> feedbacks = feedbackService.getFeedbacks(userId);

            List<Map<String, Object>> result = feedbacks.stream().map(feedback -> {
                Map<String, Object> item = new HashMap<>();
                item.put("id", feedback.getId());
                item.put("type", feedback.getType());
                item.put("content", feedback.getContent());
                item.put("status", feedback.getStatus());
                item.put("date", feedback.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                return item;
            }).toList();

            return Result.ok(result);
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (Exception e) {
            log.error("获取反馈列表失败", e);
            return Result.fail("获取反馈列表失败");
        }
    }

    @PostMapping
    public Result<String> createFeedback(@RequestHeader("Authorization") String authHeader, @RequestBody Map<String, Object> params) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            feedbackService.createFeedback(userId, params);
            return Result.ok("提交成功");
        } catch (IllegalArgumentException e) {
            log.warn("提交反馈失败：{}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (Exception e) {
            log.error("提交反馈异常", e);
            return Result.fail("提交反馈失败");
        }
    }
}
