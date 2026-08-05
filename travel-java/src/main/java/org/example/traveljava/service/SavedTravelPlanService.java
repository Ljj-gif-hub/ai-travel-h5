package org.example.traveljava.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.traveljava.dto.SavedPlanRequest;
import org.example.traveljava.entity.SavedTravelPlan;
import org.example.traveljava.repository.SavedTravelPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SavedTravelPlanService {

    private static final Logger log = LoggerFactory.getLogger(SavedTravelPlanService.class);

    private final SavedTravelPlanRepository repository;
    private final ObjectMapper objectMapper;

    public SavedTravelPlanService(SavedTravelPlanRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public SavedTravelPlan savePlan(Long userId, SavedPlanRequest request) {
        try {
            SavedTravelPlan plan = new SavedTravelPlan();
            plan.setUserId(userId);
            plan.setDestination(request.getDestination());
            plan.setDays(request.getDays());
            plan.setBudget(request.getBudget());
            plan.setPeople(request.getPeople());
            plan.setPlanJson(objectMapper.writeValueAsString(request.getPlanData()));
            plan.setSource(request.getSource() != null ? request.getSource() : "trip");

            SavedTravelPlan saved = repository.save(plan);
            log.info("保存旅行规划成功：id={}, userId={}, destination={}", saved.getId(), userId, saved.getDestination());
            return saved;
        } catch (Exception e) {
            log.error("保存旅行规划失败", e);
            throw new RuntimeException("保存规划失败：" + e.getMessage());
        }
    }

    /**
     * 【修复】严格按 userId 过滤，禁止返回全量数据
     * 根因：旧代码 userId==null 时返回 repository.findAll()，导致未登录用户可看到所有人的行程
     */
    public List<SavedTravelPlan> getAllPlans(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("请先登录");
        }
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public SavedTravelPlan getPlanById(Long userId, Long id) {
        if (userId == null) {
            throw new IllegalArgumentException("请先登录");
        }
        SavedTravelPlan plan = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("规划不存在，id=" + id));

        if (!userId.equals(plan.getUserId())) {
            throw new RuntimeException("无权访问该规划");
        }

        return plan;
    }

    /**
     * 分享专用：按 id 只读返回行程摘要（绕过属主校验）— 仅用于分享链接公开访问
     * 返回只读快照（不含 userId），不暴露属主信息
     */
    public Map<String, Object> getPlanPublic(Long id) {
        SavedTravelPlan plan = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("规划不存在，id=" + id));
        return toResponseMap(plan);
    }

    public void deletePlan(Long userId, Long id) {
        if (userId == null) {
            throw new IllegalArgumentException("请先登录");
        }
        SavedTravelPlan plan = getPlanById(userId, id);
        repository.deleteById(id);
        log.info("删除旅行规划：id={}, userId={}", id, userId);
    }

    public Map<String, Object> toResponseMap(SavedTravelPlan plan) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", plan.getId());
        map.put("destination", plan.getDestination());
        map.put("days", plan.getDays());
        map.put("budget", plan.getBudget());
        map.put("people", plan.getPeople());
        map.put("source", plan.getSource() != null ? plan.getSource() : "trip");
        map.put("createdAt", plan.getCreatedAt());

        try {
            map.put("planData", objectMapper.readValue(plan.getPlanJson(), Object.class));
        } catch (Exception e) {
            log.warn("解析 planJson 失败：id={}", plan.getId(), e);
            map.put("planData", null);
        }

        return map;
    }
}
