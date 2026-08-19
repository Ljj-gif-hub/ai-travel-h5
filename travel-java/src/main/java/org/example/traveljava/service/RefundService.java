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
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionTemplate transactionTemplate;

    public RefundService(RefundRepository refundRepository, OrderRepository orderRepository,
                         RefundProvider refundProvider, CouponService couponService,
                         AuditService auditService, TransactionTemplate transactionTemplate) {
        this.refundRepository = refundRepository;
        this.orderRepository = orderRepository;
        this.refundProvider = refundProvider;
        this.couponService = couponService;
        this.auditService = auditService;
        this.transactionTemplate = transactionTemplate;
    }

    /** 用户申请退款：仅已支付订单可退 */
    @Transactional
    public Refund requestRefund(Long userId, Long orderId, String reason) {
        // 悲观锁锁定订单行，序列化同一订单的并发退款申请（REFUND-2① 修复）
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if (!order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作该订单");
        }
        if (!"paid".equals(order.getStatus())) {
            throw new IllegalArgumentException("仅已支付订单可申请退款");
        }
        // 去重：同一订单仅允许一笔待处理/处理中退款（REFUND-2① 修复：悲观锁 + 状态集合检查）
        if (refundRepository.existsByOrderIdAndStatusIn(orderId,
                java.util.List.of(Refund.STATUS_PENDING, Refund.STATUS_PROCESSING))) {
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
     * 【并发安全】REFUND-1 修复：用原子 CAS（pending→processing）替代「读-判-写」，
     * 两个管理员并发批准同一退款单时只有一个线程返回 1，另一个返回 0（幂等忽略），杜绝渠道双倍退款。
     * 【REFUND-2② 修复】打款前校验该订单不存在已退款记录，防止顺序批准两笔退款单也双倍退款。
     * 【REFUND-3 修复】CAS 提交（事务1）→ 渠道调用（无事务）→ 落库 refunded（事务2），
     * 渠道成功后 DB 失败不会回滚 processing 状态，重试时 CAS 失败不会重复调渠道。
     */
    public Refund handle(Long adminId, Long refundId, String action) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new IllegalArgumentException("退款单不存在"));

        boolean approve = "approve".equalsIgnoreCase(action);

        if (approve) {
            // === 事务1：原子 CAS pending→processing 并提交（REFUND-3 修复） ===
            int claimed = transactionTemplate.execute(status -> {
                int c = refundRepository.markProcessingIfPending(refundId, adminId, java.time.LocalDateTime.now());
                if (c == 0) {
                    status.setRollbackOnly();
                }
                return c;
            });
            if (claimed == 0) {
                throw new IllegalArgumentException("该退款单已处理或状态已变更");
            }

            // 刷新实体
            refund = refundRepository.findById(refundId).orElseThrow();

            // 打款前校验该订单不存在已退款记录（REFUND-2② 修复）
            if (refundRepository.existsByOrderIdAndStatus(refund.getOrderId(), Refund.STATUS_REFUNDED)) {
                transactionTemplate.executeWithoutResult(status -> {
                    Refund r = refundRepository.findById(refundId).orElseThrow();
                    r.setStatus(Refund.STATUS_REJECTED);
                    refundRepository.save(r);
                });
                log.warn("订单已存在已退款记录，拒绝重复退款: refundId={}, orderId={}", refundId, refund.getOrderId());
                throw new IllegalStateException("该订单已退款，不可重复退款");
            }

            // === 渠道调用（无事务，REFUND-3 修复：渠道成功后 DB 失败不会回滚 processing） ===
            String refundNo;
            try {
                refundNo = refundProvider.refund(refund.getOrderId(), String.valueOf(refund.getOrderId()),
                        refund.getAmount(), refund.getReason());
            } catch (Exception e) {
                // 渠道失败：回滚到 pending 允许重试
                transactionTemplate.executeWithoutResult(status -> {
                    Refund r = refundRepository.findById(refundId).orElseThrow();
                    r.setStatus(Refund.STATUS_PENDING);
                    refundRepository.save(r);
                });
                log.error("退款渠道调用失败: refundId={}", refundId, e);
                throw new IllegalStateException("退款渠道调用失败，请稍后重试");
            }

            // === 事务2：落库 refunded + 取消订单 + 审计（REFUND-3 修复） ===
            final String finalRefundNo = refundNo;
            transactionTemplate.executeWithoutResult(status -> {
                Refund r = refundRepository.findById(refundId).orElseThrow();
                r.setRefundNo(finalRefundNo);
                r.setStatus(Refund.STATUS_REFUNDED);
                refundRepository.save(r);

                // 原子取消订单（仅当仍为 paid）
                Order order = orderRepository.findById(r.getOrderId()).orElse(null);
                if (order != null && "paid".equals(order.getStatus())) {
                    int updated = orderRepository.cancelIfPaid(order.getId());
                    if (updated == 1 && order.getCouponId() != null) {
                        couponService.releaseByOrder(order.getId());
                    }
                }
                auditService.record(AuditService.REFUND_APPROVED, Map.of(
                        "refundId", refundId,
                        "orderId", r.getOrderId(),
                        "amount", r.getAmount(),
                        "refundNo", finalRefundNo,
                        "adminId", adminId
                ));
            });
            log.info("退款审核通过: refundId={}, refundNo={}, adminId={}", refundId, refundNo, adminId);
            return refundRepository.findById(refundId).orElseThrow();
        } else {
            if (!Refund.STATUS_PENDING.equals(refund.getStatus())) {
                throw new IllegalArgumentException("该退款单已处理");
            }
            refund.setHandledBy(adminId);
            refund.setHandledAt(java.time.LocalDateTime.now());
            refund.setStatus(Refund.STATUS_REJECTED);
            Refund saved = refundRepository.save(refund);
            auditService.record(AuditService.REFUND_REJECTED, Map.of(
                    "refundId", refundId,
                    "orderId", refund.getOrderId(),
                    "amount", refund.getAmount(),
                    "adminId", adminId
            ));
            log.info("退款审核驳回: refundId={}, adminId={}", refundId, adminId);
            return saved;
        }
    }
}
