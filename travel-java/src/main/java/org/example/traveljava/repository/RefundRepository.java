package org.example.traveljava.repository;

import org.example.traveljava.entity.Refund;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 【新功能】退款单仓储
 */
public interface RefundRepository extends JpaRepository<Refund, Long> {

    /** 我的退款单列表 */
    List<Refund> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 某订单是否存在待处理退款（去重，避免重复申请） */
    boolean existsByOrderIdAndStatus(Long orderId, String status);

    /** 某订单是否存在指定状态集合中的退款（如已退款/处理中，防止重复退款） */
    boolean existsByOrderIdAndStatusIn(Long orderId, List<String> statuses);

    /** 管理端：按状态过滤 + 分页 */
    Page<Refund> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    /** 管理端：全量分页 */
    Page<Refund> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 【并发安全】原子状态转移：仅当退款单仍为 pending 时置为 processing。
     * 两个管理员并发批准同一退款单时，只有一个线程返回 1（继续执行渠道退款），
     * 另一个返回 0（视为已处理），杜绝渠道双倍退款。
     */
    @Modifying(clearAutomatically = true)
    @Query("update Refund r set r.status = 'processing', r.handledBy = :adminId, r.handledAt = :now " +
            "where r.id = :id and r.status = 'pending'")
    int markProcessingIfPending(@Param("id") Long id, @Param("adminId") Long adminId,
                                @Param("now") java.time.LocalDateTime now);
}
