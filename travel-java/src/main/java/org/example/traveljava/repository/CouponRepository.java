package org.example.traveljava.repository;

import org.example.traveljava.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
