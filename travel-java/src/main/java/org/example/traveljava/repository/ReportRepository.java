package org.example.traveljava.repository;

import org.example.traveljava.entity.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 【新功能】举报仓储
 */
public interface ReportRepository extends JpaRepository<Report, Long> {

    /** 同一用户对同一目标是否已举报（去重） */
    boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, String targetType, Long targetId);

    /** 某目标累计被举报次数（≥5 自动隐藏） */
    int countByTargetTypeAndTargetId(String targetType, Long targetId);

    /**
     * REPORT-1 修复：某目标累计被举报次数（排除已驳回 ignored），防止驳回决定被新举报推翻。
     */
    int countByTargetTypeAndTargetIdAndStatusNot(String targetType, Long targetId, String status);

    /** 管理端：按状态过滤 + 分页 */
    Page<Report> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    /** 管理端：全量分页 */
    Page<Report> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
