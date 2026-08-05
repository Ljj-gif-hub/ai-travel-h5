package org.example.traveljava.service;

import org.example.traveljava.entity.SavedTravelPlan;
import org.example.traveljava.entity.ShareRecord;
import org.example.traveljava.repository.ShareRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 行程分享服务
 * - 创建分享：校验属主 → 生成 8 位短码 → 落库 ShareRecord
 * - 公开读取：短码 → 只读返回行程摘要（免登录，配合 SavedTravelPlanService.getPlanPublic）
 */
@Service
public class ShareService {

    private static final Logger log = LoggerFactory.getLogger(ShareService.class);

    private static final String TOKEN_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final ShareRecordRepository shareRecordRepository;
    private final SavedTravelPlanService savedTravelPlanService;

    public ShareService(ShareRecordRepository shareRecordRepository, SavedTravelPlanService savedTravelPlanService) {
        this.shareRecordRepository = shareRecordRepository;
        this.savedTravelPlanService = savedTravelPlanService;
    }

    /**
     * 创建行程分享（需登录，校验属主）
     * @return {token, shareUrl, destination}
     */
    public Map<String, Object> createShare(Long userId, Long planId) {
        SavedTravelPlan plan = savedTravelPlanService.getPlanById(userId, planId);

        ShareRecord record = new ShareRecord();
        record.setToken(generateToken());
        record.setPlanId(plan.getId());
        record.setDestination(plan.getDestination());
        shareRecordRepository.save(record);

        log.info("创建行程分享：token={}, planId={}, userId={}", record.getToken(), planId, userId);

        Map<String, Object> data = new HashMap<>();
        data.put("token", record.getToken());
        data.put("shareUrl", "/share/" + record.getToken());
        data.put("destination", plan.getDestination());
        return data;
    }

    /**
     * 公开读取分享行程（免登录，只读快照）
     */
    public Map<String, Object> getSharedPlan(String token) {
        ShareRecord record = shareRecordRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("分享链接不存在或已失效"));
        return savedTravelPlanService.getPlanPublic(record.getPlanId());
    }

    /** 生成 8 位 base62 短码（UUID 哈希），带唯一性检查 */
    private String generateToken() {
        for (int attempt = 0; attempt < 10; attempt++) {
            long l = UUID.randomUUID().getMostSignificantBits() & 0x7fffffffffffffffL;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(TOKEN_CHARS.charAt((int) (l % 62)));
                l /= 62;
            }
            String token = sb.toString();
            if (!shareRecordRepository.existsByToken(token)) {
                return token;
            }
        }
        throw new RuntimeException("生成分享链接失败，请重试");
    }
}
