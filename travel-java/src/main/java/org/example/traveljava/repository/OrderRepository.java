package org.example.traveljava.repository;

import org.example.traveljava.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderNo(String orderNo);

    /**
     * 【并发安全】原子状态转移：仅当订单仍为 pending 时才置为 paid。
     * 并发双重支付时只有一个线程受影响行数为 1（拿到奖励/审计），其余为 0（幂等忽略）。
     * clearAutomatically 清空一级缓存，避免后续 findById 读到 bulk update 之前的旧状态。
     */
    @Modifying(clearAutomatically = true)
    @Query("update Order o set o.status = 'paid', o.paidAt = :now, o.payChannel = :channel " +
            "where o.orderNo = :orderNo and o.status = 'pending'")
    int markPaidIfPending(@Param("orderNo") String orderNo,
                          @Param("now") LocalDateTime now,
                          @Param("channel") String channel);
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 分页版本（修复全表加载） */
    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<Order> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, String type);

    Page<Order> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, String type, Pageable pageable);

    List<Order> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
    int countByUserIdAndStatus(Long userId, String status);
    int countByUserId(Long userId);

    /**
     * 【新功能-退款】原子取消：仅当订单仍为 paid 时置为 cancelled。
     * 退款审核通过后调用，避免与用户侧并发取消/完成订单产生竞争。
     */
    @Modifying(clearAutomatically = true)
    @Query("update Order o set o.status = 'cancelled' where o.id = :id and o.status = 'paid'")
    int cancelIfPaid(@Param("id") Long id);

    /**
     * 【并发安全】悲观锁查询订单，序列化同一订单的并发退款申请（REFUND-2① 修复）。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    /**
     * 【并发安全】原子应用优惠券：仅当订单仍为 pending 时更新 price/couponId/couponValue。
     * 避免与支付回调并发时全字段 merge 把已支付订单打回 pending（COUPON-1 修复）。
     */
    @Modifying(clearAutomatically = true)
    @Query("update Order o set o.price = o.price - :discount, o.couponId = :couponId, o.couponValue = :couponValue " +
            "where o.id = :orderId and o.status = 'pending'")
    int applyCouponIfPending(@Param("orderId") Long orderId, @Param("discount") long discount,
                             @Param("couponId") Long couponId, @Param("couponValue") int couponValue);

    /**
     * 【并发安全】原子更新支付渠道信息：仅当订单仍为 pending 时更新 payChannel/payTradeNo。
     * 避免全字段 merge 在并发回调下把已支付订单打回 pending（PAYMENT-1 修复）。
     */
    @Modifying(clearAutomatically = true)
    @Query("update Order o set o.payChannel = :channel, o.payTradeNo = :tradeNo " +
            "where o.id = :orderId and o.status = 'pending'")
    int updatePaymentInfoIfPending(@Param("orderId") Long orderId, @Param("channel") String channel,
                                   @Param("tradeNo") String tradeNo);
}
