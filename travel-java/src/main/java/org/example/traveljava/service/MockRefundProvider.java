package org.example.traveljava.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 【新功能】Mock 退款渠道。
 * 模拟第三方渠道耗时 300ms 后返回退款单号，用于演示退款全流程。
 */
@Service
public class MockRefundProvider implements RefundProvider {

    private static final Logger log = LoggerFactory.getLogger(MockRefundProvider.class);

    @Override
    public String refund(Long orderId, String orderNo, Long amount, String reason) {
        // 模拟第三方退款接口耗时
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("退款处理被中断");
        }
        String refundNo = "MOCKRF" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        log.info("Mock 退款成功: orderId={}, orderNo={}, amount={}元, refundNo={}", orderId, orderNo, amount, refundNo);
        return refundNo;
    }
}
