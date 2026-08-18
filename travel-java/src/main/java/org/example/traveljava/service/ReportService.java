package org.example.traveljava.service;

import org.example.traveljava.entity.Comment;
import org.example.traveljava.entity.Note;
import org.example.traveljava.entity.Post;
import org.example.traveljava.entity.Report;
import org.example.traveljava.repository.CommentRepository;
import org.example.traveljava.repository.NoteRepository;
import org.example.traveljava.repository.PostRepository;
import org.example.traveljava.repository.ReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * 【新功能】举报系统：
 * - 用户举报内容（同一人对同一目标只能举报一次）
 * - 同一目标累计举报 ≥5 次 → 自动隐藏（hidden=true，各列表/详情接口已过滤）
 * - 管理员处理：确认（隐藏目标）/ 驳回（忽略），处理动作写审计
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    /** 自动隐藏阈值 */
    private static final int AUTO_HIDE_THRESHOLD = 5;
    private static final Set<String> VALID_TYPES = Set.of("note", "post", "comment");

    private final ReportRepository reportRepository;
    private final NoteRepository noteRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final AuditService auditService;

    public ReportService(ReportRepository reportRepository, NoteRepository noteRepository,
                         PostRepository postRepository, CommentRepository commentRepository,
                         AuditService auditService) {
        this.reportRepository = reportRepository;
        this.noteRepository = noteRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.auditService = auditService;
    }

    /** 用户举报 */
    @Transactional
    public Map<String, Object> report(Long reporterId, String targetType, Long targetId, String reason) {
        if (!VALID_TYPES.contains(targetType)) {
            throw new IllegalArgumentException("举报类型无效");
        }
        if (targetId == null) {
            throw new IllegalArgumentException("举报目标无效");
        }
        // 目标必须存在（不能举报不存在的对象）
        validateTargetExists(targetType, targetId);

        // 去重：同一用户对同一目标只能举报一次
        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(reporterId, targetType, targetId)) {
            throw new IllegalArgumentException("您已举报过该内容，请等待处理");
        }

        Report report = new Report();
        report.setReporterId(reporterId);
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReason(reason != null && reason.length() > 200 ? reason.substring(0, 200) : reason);
        reportRepository.save(report);

        // 累计举报 ≥5 → 自动隐藏
        int total = reportRepository.countByTargetTypeAndTargetId(targetType, targetId);
        boolean autoHidden = false;
        if (total >= AUTO_HIDE_THRESHOLD) {
            autoHidden = hideTarget(targetType, targetId);
            log.warn("举报达 {} 次，自动隐藏: type={}, targetId={}", total, targetType, targetId);
        }

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("reported", true);
        result.put("reportCount", total);
        result.put("autoHidden", autoHidden);
        return result;
    }

    /** 管理员：举报列表 */
    public Page<Report> listReports(String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            return reportRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        }
        return reportRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /**
     * 管理员处理举报：action=confirm 确认违规（隐藏目标）/ dismiss 驳回（忽略）。
     * 处理后写审计（异步非阻塞）。
     */
    @Transactional
    public Report handle(Long adminId, Long reportId, String action) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("举报记录不存在"));
        if (!Report.STATUS_PENDING.equals(report.getStatus())) {
            throw new IllegalArgumentException("该举报已处理");
        }

        boolean confirm = "confirm".equalsIgnoreCase(action);
        report.setStatus(confirm ? Report.STATUS_HANDLED : Report.STATUS_IGNORED);
        report.setHandledBy(adminId);
        report.setHandledAt(LocalDateTime.now());
        Report saved = reportRepository.save(report);

        if (confirm) {
            hideTarget(report.getTargetType(), report.getTargetId());
        }

        // 【新功能】审计：REPORT_HANDLED（异步非阻塞）
        auditService.record(AuditService.REPORT_HANDLED, Map.of(
                "reportId", reportId,
                "targetType", report.getTargetType(),
                "targetId", report.getTargetId(),
                "action", confirm ? "confirm" : "dismiss",
                "adminId", adminId
        ));
        log.info("举报处理完成: reportId={}, action={}, adminId={}", reportId, action, adminId);
        return saved;
    }

    /** 校验被举报目标存在 */
    private void validateTargetExists(String targetType, Long targetId) {
        switch (targetType) {
            case "note" -> noteRepository.findById(targetId)
                    .orElseThrow(() -> new IllegalArgumentException("游记不存在"));
            case "post" -> postRepository.findById(targetId)
                    .orElseThrow(() -> new IllegalArgumentException("动态不存在"));
            case "comment" -> commentRepository.findById(targetId)
                    .orElseThrow(() -> new IllegalArgumentException("评论不存在"));
            default -> throw new IllegalArgumentException("举报类型无效");
        }
    }

    /** 隐藏目标（幂等） */
    private boolean hideTarget(String targetType, Long targetId) {
        try {
            switch (targetType) {
                case "note" -> {
                    Note n = noteRepository.findById(targetId).orElse(null);
                    if (n != null && !Boolean.TRUE.equals(n.getHidden())) {
                        n.setHidden(true);
                        noteRepository.save(n);
                        return true;
                    }
                }
                case "post" -> {
                    Post p = postRepository.findById(targetId).orElse(null);
                    if (p != null && !Boolean.TRUE.equals(p.getHidden())) {
                        p.setHidden(true);
                        postRepository.save(p);
                        return true;
                    }
                }
                case "comment" -> {
                    Comment c = commentRepository.findById(targetId).orElse(null);
                    if (c != null && !Boolean.TRUE.equals(c.getHidden())) {
                        c.setHidden(true);
                        commentRepository.save(c);
                        return true;
                    }
                }
                default -> { return false; }
            }
        } catch (Exception e) {
            log.error("隐藏举报目标失败: type={}, targetId={}", targetType, targetId, e);
        }
        return false;
    }
}
