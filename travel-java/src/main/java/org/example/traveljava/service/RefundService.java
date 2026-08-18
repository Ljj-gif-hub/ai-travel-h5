package org.example.traveljava.service;

import org.example.traveljava.entity.Order;
import org.example.traveljava.entity.Refund;
import org.example.traveljava.repository.OrderRepository;
import org.example.traveljava.repository.RefundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 【新功能】退款流程：
 * - 用户对已支付订单发起退款（同一订单仅一笔待处理退款）
 * - 管理员审核：approve → 调用退款渠道（Mock 300ms）→ 状态 refunded + 订单原子取消 + 释放优惠券
 *              reject  → 状态 rejected
 * - 处理动作写审计（REFUND_APPROVED / REFUND_REJECTED，异步非阻塞）
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final RefundProvider refundProvider;
    private final CouponService couponService;
    private final AuditService auditService;

    public RefundService(RefundRepository refundRepository, OrderRepository orderRepository,
                         RefundProvider refundProvider, CouponService couponService,
                         AuditService auditService) {
        this.refundRepository = refundRepository;
        this.orderRepository = orderRepository;
        this.refundProvider = refundProvider;
        this.couponService = couponService;
        this.auditService = auditService;
    }

    /** 用户申请退款：仅已支付订单可退 */
    @Transactional
    public Refund requestRefund(Long userId, Long orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if (!order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作该订单");
        }
        if (!"paid".equals(order.getStatus())) {
            throw new IllegalArgumentException("仅已支付订单可申请退款");
        }
        // 去重：同一订单仅允许一笔待处理退款
        if (refundRepository.existsByOrderIdAndStatus(orderId, Refund.STATUS_PENDING)) {
            throw new IllegalArgumentException("该订单已有退款申请处理中，请勿重复提交");
        }

        Refund refund = new Refund();
        refund.setOrderId(orderId);
        refund.setUserId(userId);
        refund.setAmount(order.getPrice());
        refund.setReason(reason != null && reason.length() > 200 ? reason.substring(0, 200) : reason);
        Refund saved = refundRepository.save(refund);
        log.info("退款申请提交: refundId={}, orderId={}, userId={}, amount={}", saved.getId(), orderId, userId, saved.getAmount());
        return saved;
    }

    /** 我的退款单列表 */
    public java.util.List<Refund> listMyRefunds(Long userId) {
        return refundRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /** 管理端退款单列表（可按状态过滤） */
    public Page<Refund> listAll(String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            return refundRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        }
        return refundRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /**
     * 管理员审核退款：approve（通过，执行渠道退款并取消订单）/ reject（驳回）。
     */
    @Transactional
    public Refund handle(Long adminId, Long refundId, String action) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new IllegalArgumentException("退款单不存在"));
        if (!Refund.STATUS_PENDING.equals(refund.getStatus())) {
            throw new IllegalArgumentException("该退款单已处理");
        }

        boolean approve = "approve".equalsIgnoreCase(action);
        refund.setHandledBy(adminId);
        refund.setHandledAt(java.time.LocalDateTime.now());

        if (approve) {
            // 1. 调用退款渠道（Mock 模拟 300ms）
            String refundNo;
            try {
                refundNo = refundProvider.refund(refund.getOrderId(), String.valueOf(refund.getOrderId()),
                        refund.getAmount(), refund.getReason());
            } catch (Exception e) {
                log.error("退款渠道调用失败: refundId={}", refundId, e);
                throw new IllegalStateException("退款渠道调用失败，请稍后重试");
            }
            refund.setRefundNo(refundNo);
            refund.setStatus(Refund.STATUS_REFUNDED);

            // 2. 原子取消订单（仅当仍为 paid；并发下已被取消/完成则不重复处理）
            Order order = orderRepository.findById(refund.getOrderId()).orElse(null);
            if (order != null && "paid".equals(order.getStatus())) {
                int updated = orderRepository.cancelIfPaid(order.getId());
                if (updated == 1 && order.getCouponId() != null) {
                    couponService.releaseByOrder(order.getId());
                }
            }
            auditService.record(AuditService.REFUND_APPROVED, Map.of(
                    "refundId", refundId,
                    "orderId", refund.getOrderId(),
                    "amount", refund.getAmount(),
                    "refundNo", refundNo,
                    "adminId", adminId
            ));
            log.info("退款审核通过: refundId={}, refundNo={}, adminId={}", refundId, refundNo, adminId);
        } else {
            refund.setStatus(Refund.STATUS_REJECTED);
            auditService.record(AuditService.REFUND_REJECTED, Map.of(
                    "refundId", refundId,
                    "orderId", refund.getOrderId(),
                    "amount", refund.getAmount(),
                    "adminId", adminId
            ));
            log.info("退款审核驳回: refundId={}, adminId={}", refundId, adminId);
        }

        return refundRepository.save(refund);
    }
}
