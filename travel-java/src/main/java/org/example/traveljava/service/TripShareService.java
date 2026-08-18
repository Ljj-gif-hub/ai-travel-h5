package org.example.traveljava.service;

import org.example.traveljava.entity.SavedTravelPlan;
import org.example.traveljava.entity.TripShare;
import org.example.traveljava.repository.TripShareRepository;
import org.example.traveljava.util.AuthUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 【新功能】行程分享服务 — 24 小时有效期的临时分享链接。
 * 区别于 ShareService（永久短码分享），本分享可撤销、自动过期。
 */
@Service
public class TripShareService {

    private static final Logger log = LoggerFactory.getLogger(TripShareService.class);

    private final TripShareRepository tripShareRepository;
    private final SavedTravelPlanService savedTravelPlanService;

    public TripShareService(TripShareRepository tripShareRepository,
                            SavedTravelPlanService savedTravelPlanService) {
        this.tripShareRepository = tripShareRepository;
        this.savedTravelPlanService = savedTravelPlanService;
    }

    /** 创建分享（同计划已有未过期分享时复用） */
    @Transactional
    public Map<String, Object> createShare(Long userId, Long planId) {
        SavedTravelPlan plan = savedTravelPlanService.getPlanById(userId, planId);
        if (!userId.equals(plan.getUserId())) {
            throw new AuthUtils.ForbiddenException("无权分享该行程");
        }

        LocalDateTime now = LocalDateTime.now();
        TripShare share = tripShareRepository.findByPlanIdAndExpireAtAfter(planId, now)
                .stream().findFirst().orElse(null);
        if (share == null) {
            share = new TripShare();
            share.setPlanId(planId);
            share.setUserId(userId);
            share.setToken(UUID.randomUUID().toString().replace("-", ""));
            share.setCreatedAt(now);
            share.setExpireAt(now.plusHours(24));
            share = tripShareRepository.save(share);
            log.info("创建行程分享: planId={}, userId={}, token={}", planId, userId, share.getToken());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("token", share.getToken());
        result.put("expireAt", share.getExpireAt());
        result.put("shareUrl", "/api/trip/share/" + share.getToken());
        return result;
    }

    /** 匿名读取分享的行程（只读快照，不含属主信息） */
    public Map<String, Object> getSharedPlan(String token) {
        TripShare share = tripShareRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("分享链接不存在或已撤销"));
        if (share.getExpireAt() == null || share.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("分享链接已过期");
        }
        Map<String, Object> plan = savedTravelPlanService.getPlanPublic(share.getPlanId());
        plan.put("sharedAt", share.getCreatedAt());
        return plan;
    }

    /** 撤销分享（仅属主） */
    @Transactional
    public void revokeShare(Long userId, String token) {
        TripShare share = tripShareRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("分享链接不存在"));
        if (!userId.equals(share.getUserId())) {
            throw new AuthUtils.ForbiddenException("无权撤销该分享");
        }
        tripShareRepository.delete(share);
        log.info("撤销行程分享: token={}, userId={}", token, userId);
    }

    /** 每小时清理过期分享记录 */
    @Scheduled(fixedDelay = 60 * 60_000L)
    @Transactional
    public void purgeExpiredShares() {
        tripShareRepository.deleteByExpireAtBefore(LocalDateTime.now());
    }
}
