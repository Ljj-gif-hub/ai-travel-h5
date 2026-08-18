package org.example.traveljava.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.example.traveljava.entity.AsyncAudit;
import org.example.traveljava.repository.AsyncAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 【新功能】异步审计服务 — 业务事件审计落库（async_audit 表），
 * 单线程守护队列异步写入，不阻塞业务主流程（非阻塞）。
 *
 * 扩展事件类型：
 *  - REFUND_APPROVED  退款审核通过
 *  - REFUND_REJECTED  退款审核驳回
 *  - REPORT_HANDLED   举报处理完成
 *  - TEMPLATE_DELETED 模板删除
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    public static final String REFUND_APPROVED = "REFUND_APPROVED";
    public static final String REFUND_REJECTED = "REFUND_REJECTED";
    public static final String REPORT_HANDLED = "REPORT_HANDLED";
    public static final String TEMPLATE_DELETED = "TEMPLATE_DELETED";

    private final AsyncAuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    /** 审计写入线程：单线程串行，守护线程不阻塞 JVM 退出 */
    private final ExecutorService auditExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "audit-writer");
        t.setDaemon(true);
        return t;
    });

    public AuditService(AsyncAuditRepository auditRepository, ObjectMapper objectMapper) {
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
    }

    /** 异步记录审计事件（立即返回，写入失败只记日志不影响业务） */
    public void record(String eventType, Map<String, Object> payload) {
        try {
            auditExecutor.submit(() -> {
                try {
                    AsyncAudit audit = new AsyncAudit();
                    audit.setEventId(UUID.randomUUID().toString().replace("-", ""));
                    audit.setEventType(eventType);
                    audit.setPayload(payload == null ? "{}" : objectMapper.writeValueAsString(payload));
                    audit.setCreatedAt(LocalDateTime.now());
                    auditRepository.save(audit);
                } catch (Exception e) {
                    log.warn("审计写入失败: type={}, err={}", eventType, e.getMessage());
                }
            });
        } catch (Exception e) {
            // 线程池拒收等极端情况：仅记日志
            log.warn("审计任务提交失败: type={}, err={}", eventType, e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        auditExecutor.shutdown();
    }
}
