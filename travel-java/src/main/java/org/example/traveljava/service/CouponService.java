package org.example.traveljava.service;

import org.example.traveljava.entity.Coupon;
import org.example.traveljava.entity.Order;
import org.example.traveljava.repository.CouponRepository;
import org.example.traveljava.repository.OrderRepository;
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
    private final OrderRepository orderRepository;

    public CouponService(CouponRepository couponRepository, OrderRepository orderRepository) {
        this.couponRepository = couponRepository;
        this.orderRepository = orderRepository;
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

        // 校验订单存在、归属本人、金额达到使用门槛
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if (!order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权使用该订单的优惠券");
        }
        // 只允许在待支付订单上使用，已支付/已完成/已取消订单不可再套用
        if (!"pending".equals(order.getStatus())) {
            throw new IllegalArgumentException("当前订单状态不可使用优惠券");
        }
        // COUPON-2 修复：同一订单已有优惠券则不允许叠加使用
        if (order.getCouponId() != null) {
            throw new IllegalArgumentException("该订单已使用优惠券，不可叠加使用");
        }
        if (order.getPrice() < coupon.getMinAmount()) {
            throw new IllegalArgumentException("订单金额未达优惠券使用门槛");
        }

        // 原子占位，防止并发重复使用同一张券
        int claimed = couponRepository.claimCoupon(couponId, LocalDateTime.now(), orderId);
        if (claimed == 0) {
            throw new IllegalArgumentException("优惠券已被使用");
        }

        // COUPON-1 修复：用原子 UPDATE 替代全字段 merge，避免与支付回调并发时旧实体（pending）覆盖回写
        long discount = Math.min(coupon.getValue().longValue(), order.getPrice());
        int updated = orderRepository.applyCouponIfPending(orderId, discount, couponId, (int) discount);
        if (updated == 0) {
            // 订单状态已变（被并发支付/取消），回滚优惠券占用
            coupon.setStatus("unused");
            coupon.setUsedAt(null);
            coupon.setOrderId(null);
            couponRepository.save(coupon);
            throw new IllegalStateException("订单状态已变更，优惠券使用失败");
        }

        Coupon saved = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("优惠券不存在"));
        log.info("使用优惠券：userId={}, couponId={}, orderId={}, 抵扣={}元", userId, couponId, orderId, discount);
        return saved;
    }

    /** 订单取消/作废时释放占用的优惠券，供再次使用 */
    @Transactional
    public void releaseByOrder(Long orderId) {
        if (orderId == null) return;
        for (Coupon c : couponRepository.findByOrderId(orderId)) {
            if ("used".equals(c.getStatus())) {
                c.setStatus("unused");
                c.setUsedAt(null);
                c.setOrderId(null);
                couponRepository.save(c);
                log.info("释放优惠券：couponId={}, orderId={}", c.getId(), orderId);
            }
        }
    }
}
