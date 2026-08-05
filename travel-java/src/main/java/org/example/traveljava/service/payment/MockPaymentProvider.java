package org.example.traveljava.service.payment;

import org.example.traveljava.entity.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 模拟支付渠道（默认）— 未配置真实商户密钥时使用
 *
 * 完整演示支付闭环：发起支付返回 mock 支付页地址 → 前端跳转 → mock 支付页确认 → 标记已支付。
 * 无需任何外部密钥，开箱即用。
 */
@Service
@ConditionalOnProperty(name = "payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentProvider.class);

    @Override
    public PaymentResult createPayment(Order order) {
        String orderNo = order.getOrderNo();
        String payUrl = "/api/payment/mock-pay?orderNo=" + orderNo;
        String tradeNo = "MOCK" + order.getId() + System.currentTimeMillis();
        log.info("[Mock支付] 发起支付：orderNo={}, payUrl={}", orderNo, payUrl);
        return new PaymentResult(payUrl, tradeNo);
    }

    @Override
    public boolean verifyNotify(Map<String, String> params) {
        // 模拟渠道：默认放行（真实渠道此处为 RSA/MD5 验签）
        return true;
    }

    @Override
    public String getProviderName() {
        return "mock";
    }
}
