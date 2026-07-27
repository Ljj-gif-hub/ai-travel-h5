package org.example.traveljava.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.traveljava.entity.Feedback;
import org.example.traveljava.repository.FeedbackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private final FeedbackRepository feedbackRepository;
    private final ObjectMapper objectMapper;

    public FeedbackService(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
        this.objectMapper = new ObjectMapper();
    }

    public List<Feedback> getFeedbacks(Long userId) {
        return feedbackRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public Feedback createFeedback(Long userId, Map<String, Object> params) {
        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setType((String) params.get("type"));
        feedback.setContent((String) params.get("content"));
        
        if (params.containsKey("images")) {
            try {
                feedback.setImages(objectMapper.writeValueAsString(params.get("images")));
            } catch (JsonProcessingException e) {
                log.warn("序列化图片列表失败", e);
            }
        }
        
        if (params.containsKey("contact")) {
            feedback.setContact((String) params.get("contact"));
        }
        
        feedback.setStatus("pending");

        Feedback saved = feedbackRepository.save(feedback);
        log.info("提交反馈：userId={}, type={}", userId, feedback.getType());
        return saved;
    }
}
