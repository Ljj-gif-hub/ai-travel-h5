package org.example.traveljava.service;

import org.example.traveljava.entity.Order;
import org.example.traveljava.repository.OrderRepository;
import org.example.traveljava.service.payment.PaymentProvider;
import org.example.traveljava.service.payment.PaymentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付服务 — 桥接「订单系统」与「支付渠道对接层」
 *
 * - 发起支付：校验订单属主 + pending 状态 → 调渠道 createPayment → 回写 payChannel/payTradeNo
 * - 支付回调：验签 → 幂等地将订单标记为已支付（复用 OrderService 的 pending→paid 状态机）
 *
 * MVP 决策：不单独建支付记录表，用 Order 的 payChannel/payTradeNo/paidAt 三字段 + 回调幂等代替；
 * 后续对账需求出现时再抽 PaymentRecord 实体。
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentProvider paymentProvider;
    private final OrderService orderService;
    private final OrderRepository orderRepository;

    public PaymentService(PaymentProvider paymentProvider, OrderService orderService, OrderRepository orderRepository) {
        this.paymentProvider = paymentProvider;
        this.orderService = orderService;
        this.orderRepository = orderRepository;
    }

    /**
     * 发起支付（需登录）
     * @param userId  当前用户（校验订单属主）
     * @param orderId 待支付订单
     * @return {orderNo, payUrl, providerTradeNo}
     */
    @Transactional
    public Map<String, Object> createPayment(Long userId, Long orderId) {
        Order order = orderService.getOwnedOrder(userId, orderId);
        if (!"pending".equals(order.getStatus())) {
            throw new IllegalArgumentException("当前订单状态不可支付");
        }

        PaymentResult result = paymentProvider.createPayment(order);
        order.setPayChannel(paymentProvider.getProviderName());
        order.setPayTradeNo(result.providerTradeNo());
        orderRepository.save(order);

        log.info("发起支付：userId={}, orderNo={}, channel={}", userId, order.getOrderNo(), paymentProvider.getProviderName());

        Map<String, Object> data = new HashMap<>();
        data.put("orderNo", order.getOrderNo());
        data.put("payUrl", result.payUrl());
        data.put("providerTradeNo", result.providerTradeNo());
        data.put("channel", paymentProvider.getProviderName());
        return data;
    }

    /**
     * 处理支付渠道回调（公开接口，验签后幂等标记已支付）
     * @param params 回调参数（必须含 orderNo）
     * @return 已支付的订单号
     */
    @Transactional
    public String handleNotify(Map<String, String> params) {
        if (!paymentProvider.verifyNotify(params)) {
            throw new IllegalArgumentException("支付回调验签失败");
        }
        String orderNo = params.get("orderNo");
        if (orderNo == null || orderNo.isBlank()) {
            throw new IllegalArgumentException("支付回调缺少订单号");
        }
        orderService.markOrderPaid(orderNo);
        log.info("支付回调处理完成：orderNo={}", orderNo);
        return orderNo;
    }

    /**
     * @return 当前激活的支付渠道标识（mock / alipay / wechat）
     */
    public String getProviderName() {
        return paymentProvider.getProviderName();
    }
}
