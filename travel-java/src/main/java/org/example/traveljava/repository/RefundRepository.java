package org.example.traveljava.repository;

import org.example.traveljava.entity.Refund;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 【新功能】退款单仓储
 */
public interface RefundRepository extends JpaRepository<Refund, Long> {

    /** 我的退款单列表 */
    List<Refund> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 某订单是否存在待处理退款（去重，避免重复申请） */
    boolean existsByOrderIdAndStatus(Long orderId, String status);

    /** 管理端：按状态过滤 + 分页 */
    Page<Refund> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    /** 管理端：全量分页 */
    Page<Refund> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
