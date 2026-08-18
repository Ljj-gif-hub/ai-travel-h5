package org.example.traveljava.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 【新功能】内容审核服务 — LLM 判定用户发布内容是否违规。
 *
 * - 开关：app.moderation.enabled（默认 false，开启后才走 LLM 审核）
 * - 判定：LLM 返回 JSON {"safe":true/false,"reason":"违规类型"}
 * - fail-open：LLM 调用异常 / 返回空时放行（不因审核服务故障阻断用户发内容）
 */
@Service
public class ContentModerationService {

    private static final Logger log = LoggerFactory.getLogger(ContentModerationService.class);

    @Value("${app.moderation.enabled:false}")
    private boolean enabled;

    private final AIService aiService;

    public ContentModerationService(AIService aiService) {
        this.aiService = aiService;
    }

    /** 审核结果 */
    public static class ModerationResult {
        private final boolean safe;
        private final String reason;

        public ModerationResult(boolean safe, String reason) {
            this.safe = safe;
            this.reason = reason;
        }

        public boolean isSafe() { return safe; }
        public String getReason() { return reason; }
    }

    /**
     * 审核内容。
     * @param content 待审核文本（标题/正文拼接均可）
     * @return safe=true 放行；safe=false 拒绝（调用方抛业务异常）
     */
    public ModerationResult check(String content) {
        // 未开启审核：直接放行
        if (!enabled) {
            return new ModerationResult(true, "");
        }
        if (content == null || content.isBlank()) {
            return new ModerationResult(true, "");
        }
        try {
            Map<String, Object> result = aiService.judgeContent(content);
            if (result == null || result.isEmpty()) {
                // fail-open：LLM 返回空视为放行
                log.warn("内容审核 LLM 返回空，按 fail-open 放行");
                return new ModerationResult(true, "");
            }
            Object safeObj = result.get("safe");
            boolean safe = !Boolean.FALSE.equals(safeObj);
            String reason = result.get("reason") != null ? String.valueOf(result.get("reason")) : "";
            if (!safe) {
                log.warn("内容审核判定违规: reason={}", reason);
            }
            return new ModerationResult(safe, reason);
        } catch (Exception e) {
            // fail-open：LLM 异常不阻断用户发布
            log.warn("内容审核 LLM 调用异常，按 fail-open 放行: {}", e.getMessage());
            return new ModerationResult(true, "");
        }
    }
}
