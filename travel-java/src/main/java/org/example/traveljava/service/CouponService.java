package org.example.traveljava.service;

import org.example.traveljava.entity.Coupon;
import org.example.traveljava.repository.CouponRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CouponService {

    private static final Logger log = LoggerFactory.getLogger(CouponService.class);

    private final CouponRepository couponRepository;

    public CouponService(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    public List<Coupon> getCoupons(Long userId) {
        List<Coupon> coupons = couponRepository.findByUserIdOrderByValidUntilDesc(userId);
        coupons.forEach(this::updateExpiredStatus);
        return coupons;
    }

    public List<Coupon> getCouponsByStatus(Long userId, String status) {
        List<Coupon> coupons = couponRepository.findByUserIdAndStatusOrderByValidUntilDesc(userId, status);
        if ("unused".equals(status)) {
            coupons.forEach(this::updateExpiredStatus);
        }
        return coupons;
    }

    public int getCouponCount(Long userId) {
        return couponRepository.countByUserId(userId);
    }

    public int getCouponCountByStatus(Long userId, String status) {
        if ("unused".equals(status)) {
            return couponRepository.countByUserIdAndStatusAndValidUntilAfter(userId, status, LocalDateTime.now());
        }
        return couponRepository.countByUserIdAndStatus(userId, status);
    }

    private void updateExpiredStatus(Coupon coupon) {
        if ("unused".equals(coupon.getStatus()) && coupon.getValidUntil().isBefore(LocalDateTime.now())) {
            coupon.setStatus("expired");
            couponRepository.save(coupon);
        }
    }

    @Transactional
    public Coupon createCoupon(Long userId, int value, int minAmount, String title, LocalDateTime validUntil, String category) {
        Coupon coupon = new Coupon();
        coupon.setUserId(userId);
        coupon.setValue(value);
        coupon.setMinAmount(minAmount);
        coupon.setTitle(title);
        coupon.setValidUntil(validUntil);
        coupon.setCategory(category);
        coupon.setStatus("unused");

        Coupon saved = couponRepository.save(coupon);
        log.info("创建优惠券：userId={}, value={}", userId, value);
        return saved;
    }

    @Transactional
    public Coupon useCoupon(Long userId, Long couponId, Long orderId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("优惠券不存在"));
        
        if (!coupon.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权使用该优惠券");
        }
        
        if (!"unused".equals(coupon.getStatus())) {
            throw new IllegalArgumentException("优惠券不可用");
        }
        
        if (coupon.getValidUntil().isBefore(LocalDateTime.now())) {
            coupon.setStatus("expired");
            throw new IllegalArgumentException("优惠券已过期");
        }

        coupon.setStatus("used");
        coupon.setUsedAt(LocalDateTime.now());
        coupon.setOrderId(orderId);

        Coupon saved = couponRepository.save(coupon);
        log.info("使用优惠券：userId={}, couponId={}", userId, couponId);
        return saved;
    }
}
