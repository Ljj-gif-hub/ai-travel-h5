package org.example.traveljava.service;

import org.example.traveljava.entity.Order;
import org.example.traveljava.entity.Refund;
import org.example.traveljava.repository.OrderRepository;
import org.example.traveljava.repository.RefundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 【新功能】退款流程：
 * - 用户对已支付订单发起退款（同一订单仅一笔待处理退款）
 * - 管理员审核：approve → 调用退款渠道（Mock 300ms）→ 状态 refunded + 订单原子取消 + 释放优惠券
 *              reject  → 状态 rejected
 * - 处理动作写审计（REFUND_APPROVED / REFUND_REJECTED，异步非阻塞）
 *
 * 【REFUND-4 修复】补偿任务：handle() 里事务2（落库 refunded）失败或进程崩溃时，
 * 退款单可能卡在 processing（渠道侧可能已退款成功）。定时扫描超时 processing 单做幂等恢复：
 * 重新调渠道补记 refunded；渠道持续失败超过 5 次 → 置 failed 标记人工介入。
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    /** 【REFUND-4】补偿任务：卡在 processing 超过该时长（3 分钟）即触发恢复 */
    private static final Duration STUCK_THRESHOLD = Duration.ofMinutes(3);
    /** 【REFUND-4】补偿重试上限：渠道连续失败超过该次数 → 置 failed 需人工 */
    private static final int MAX_RECOVER_RETRIES = 5;
    /** 【REFUND-4】补偿重试计数 Redis key 前缀（进程重启后计数丢失，可接受：重启后按新周期重试） */
    private static final String RECOVER_COUNT_PREFIX = "refund:recover:";

    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final RefundProvider refundProvider;
    private final CouponService couponService;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;
    private final StringRedisTemplate redisTemplate;

    public RefundService(RefundRepository refundRepository, OrderRepository orderRepository,
                         RefundProvider refundProvider, CouponService couponService,
                         AuditService auditService, TransactionTemplate transactionTemplate,
                         StringRedisTemplate redisTemplate) {
        this.refundRepository = refundRepository;
        this.orderRepository = orderRepository;
        this.refundProvider = refundProvider;
        this.couponService = couponService;
        this.auditService = auditService;
        this.transactionTemplate = transactionTemplate;
        this.redisTemplate = redisTemplate;
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

    /* ==================== 【REFUND-4 修复】processing 卡死补偿 ==================== */

    /**
     * 补偿任务：扫描卡在 processing 超过 3 分钟的退款单做幂等恢复。
     * 触发场景：handle() 中事务2（落库 refunded）失败 / 进程崩溃——渠道侧可能已退款成功，
     * DB 却停在 processing。每 60s 扫描一次：
     *  - 渠道可调通 → 补记 refunded + 取消订单 + 审计（与 handle() 事务2 同构，幂等）；
     *  - 渠道持续失败 → 每失败一次计数，超过 5 次置 failed 并留日志标记人工介入。
     */
    @Scheduled(fixedDelay = 60_000)
    public void recoverStuckProcessingRefunds() {
        List<Refund> stuck = refundRepository.findByStatusAndUpdatedAtBefore(
                Refund.STATUS_PROCESSING, LocalDateTime.now().minus(STUCK_THRESHOLD));
        if (stuck.isEmpty()) {
            return;
        }
        log.info("退款补偿任务扫描到卡死 processing 退款单 {} 笔", stuck.size());
        for (Refund refund : stuck) {
            try {
                recoverOne(refund);
            } catch (Exception e) {
                log.error("退款补偿任务处理失败: refundId={}", refund.getId(), e);
            }
        }
    }

    /** 单笔退款单的幂等恢复 */
    private void recoverOne(Refund refund) {
        Long refundId = refund.getId();
        // 幂等：重新调用渠道（outRefundNo 用 orderId，真实渠道按单号幂等，Mock 渠道无副作用）
        try {
            String refundNo = refundProvider.refund(refund.getOrderId(), String.valueOf(refund.getOrderId()),
                    refund.getAmount(), refund.getReason());
            completeRefundedByRecovery(refundId, refundNo);
            clearRecoverCount(refundId);
            log.info("退款补偿成功: refundId={}, refundNo={}", refundId, refundNo);
        } catch (Exception e) {
            long retries = incrementRecoverCount(refundId);
            if (retries >= MAX_RECOVER_RETRIES) {
                // 超过重试上限：置 failed 标记人工介入（status 列 varchar(20)，直接存 "failed"）
                transactionTemplate.executeWithoutResult(status -> {
                    Refund r = refundRepository.findById(refundId).orElse(null);
                    if (r != null && Refund.STATUS_PROCESSING.equals(r.getStatus())) {
                        r.setStatus(Refund.STATUS_FAILED);
                        refundRepository.save(r);
                    }
                });
                log.error("退款补偿连续{}次失败，退款单标记 failed 需人工介入: refundId={}", retries, refundId);
            } else {
                log.warn("退款补偿第{}次失败，稍后重试: refundId={}, err={}", retries, refundId, e.getMessage());
            }
        }
    }

    /** 补记 refunded：仅当仍为 processing 时生效（幂等，防与 handle() 并发双写） */
    private void completeRefundedByRecovery(Long refundId, String refundNo) {
        transactionTemplate.executeWithoutResult(status -> {
            Refund r = refundRepository.findById(refundId).orElse(null);
            if (r == null || !Refund.STATUS_PROCESSING.equals(r.getStatus())) {
                return; // 已被并发处理（refunded/rejected/failed），跳过
            }
            r.setRefundNo(refundNo);
            r.setStatus(Refund.STATUS_REFUNDED);
            refundRepository.save(r);

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
                    "refundNo", refundNo,
                    "adminId", "recovery"
            ));
        });
    }

    /** 补偿失败计数（Redis；不可用时按 0 处理，不阻断补偿） */
    private long incrementRecoverCount(Long refundId) {
        try {
            String key = RECOVER_COUNT_PREFIX + refundId;
            Long c = redisTemplate.opsForValue().increment(key);
            if (c != null && c == 1) {
                redisTemplate.expire(key, Duration.ofHours(1));
            }
            return c == null ? 0 : c;
        } catch (Exception e) {
            log.warn("退款补偿计数失败（Redis 不可用？）: {}", e.getMessage());
            return 0;
        }
    }

    /** 补偿成功清零计数 */
    private void clearRecoverCount(Long refundId) {
        try {
            redisTemplate.delete(RECOVER_COUNT_PREFIX + refundId);
        } catch (Exception e) {
            log.warn("清除退款补偿计数失败: {}", e.getMessage());
        }
    }
}
