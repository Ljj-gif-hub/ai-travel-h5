package org.example.traveljava.repository;

import org.example.traveljava.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {
    List<Coupon> findByUserIdOrderByValidUntilDesc(Long userId);
    List<Coupon> findByUserIdAndStatusOrderByValidUntilDesc(Long userId, String status);
    int countByUserIdAndStatus(Long userId, String status);
    int countByUserIdAndStatusAndValidUntilAfter(Long userId, String status, LocalDateTime dateTime);
    int countByUserId(Long userId);
    List<Coupon> findByUserIdAndStatusAndValidUntilAfter(Long userId, String status, LocalDateTime dateTime);

    /** 原子占位：仅当 status='unused' 时置为 used，返回受影响行数（0=已被并发使用） */
    @Modifying
    @Query("update Coupon c set c.status = 'used', c.usedAt = :now, c.orderId = :orderId " +
            "where c.id = :id and c.status = 'unused'")
    int claimCoupon(@Param("id") Long id, @Param("now") LocalDateTime now, @Param("orderId") Long orderId);

    /** 查询某订单占用的优惠券（取消订单时释放用） */
    List<Coupon> findByOrderId(Long orderId);
}
