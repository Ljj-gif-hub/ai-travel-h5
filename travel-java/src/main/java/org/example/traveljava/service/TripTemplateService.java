package org.example.traveljava.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.traveljava.dto.SavedPlanRequest;
import org.example.traveljava.entity.SavedTravelPlan;
import org.example.traveljava.entity.TripTemplate;
import org.example.traveljava.repository.TripTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 【新功能】行程模板服务 — 模板市场。
 * 管理员创建/删除模板；用户可将模板实例化为自己的行程（下载数原子 +1）。
 */
@Service
public class TripTemplateService {

    private static final Logger log = LoggerFactory.getLogger(TripTemplateService.class);

    private final TripTemplateRepository templateRepository;
    private final SavedTravelPlanService savedTravelPlanService;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public TripTemplateService(TripTemplateRepository templateRepository,
                               SavedTravelPlanService savedTravelPlanService,
                               ObjectMapper objectMapper,
                               AuditService auditService) {
        this.templateRepository = templateRepository;
        this.savedTravelPlanService = savedTravelPlanService;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    /** 创建模板（管理员） */
    public TripTemplate create(Long adminId, TripTemplate template) {
        template.setId(null);
        template.setCreatorId(adminId);
        template.setStatus(TripTemplate.STATUS_PUBLISHED);
        if (template.getDownloads() == null) template.setDownloads(0);
        TripTemplate saved = templateRepository.save(template);
        log.info("创建行程模板: id={}, name={}, adminId={}", saved.getId(), saved.getName(), adminId);
        return saved;
    }

    /** 模板市场列表（支持关键字搜索） */
    public Page<TripTemplate> market(String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return templateRepository.findByStatusOrderByDownloadsDesc(TripTemplate.STATUS_PUBLISHED, pageable);
        }
        return templateRepository.searchPublished(TripTemplate.STATUS_PUBLISHED, keyword.trim(), pageable);
    }

    /** 模板详情 */
    public TripTemplate detail(Long id) {
        TripTemplate t = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在"));
        if (TripTemplate.STATUS_DELETED.equals(t.getStatus())) {
            throw new IllegalArgumentException("模板已下架");
        }
        return t;
    }

    /**
     * 实例化模板：为当前用户生成一份新的 SavedTravelPlan。
     * 下载数原子 +1（@Modifying 更新语句，防并发覆盖）。
     */
    @Transactional
    public Map<String, Object> instantiate(Long userId, Long templateId) {
        TripTemplate template = detail(templateId);

        SavedPlanRequest request = new SavedPlanRequest();
        request.setDestination(template.getDestination());
        request.setDays(template.getDays());
        request.setBudget(template.getBudget());
        request.setPeople(template.getPeople());
        request.setSource("template");
        try {
            request.setPlanData(objectMapper.readValue(template.getPlanJson(), Object.class));
        } catch (Exception e) {
            log.warn("模板 planJson 解析失败，使用空数据: templateId={}", templateId);
            request.setPlanData(Map.of());
        }

        SavedTravelPlan plan = savedTravelPlanService.savePlan(userId, request);

        int updated = templateRepository.incrementDownloads(templateId);
        if (updated == 0) {
            log.warn("模板下载数自增失败: templateId={}", templateId);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("planId", plan.getId());
        result.put("templateId", templateId);
        result.put("destination", template.getDestination());
        log.info("实例化模板: templateId={}, userId={}, planId={}", templateId, userId, plan.getId());
        return result;
    }

    /** 软删除模板（管理员）+ 审计 */
    @Transactional
    public void delete(Long adminId, Long templateId) {
        TripTemplate t = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在"));
        if (TripTemplate.STATUS_DELETED.equals(t.getStatus())) {
            return;
        }
        t.setStatus(TripTemplate.STATUS_DELETED);
        templateRepository.save(t);
        // 【新功能】审计：TEMPLATE_DELETED（异步非阻塞）
        auditService.record(AuditService.TEMPLATE_DELETED, Map.of(
                "templateId", templateId,
                "name", t.getName() != null ? t.getName() : "",
                "adminId", adminId
        ));
        log.info("删除行程模板: id={}, adminId={}", templateId, adminId);
    }
}
