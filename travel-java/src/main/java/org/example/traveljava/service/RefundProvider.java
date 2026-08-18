package org.example.traveljava.service;

/**
 * 【新功能】退款渠道抽象。
 * 当前仅 Mock 实现；未来可扩展支付宝/微信原路退回等真实渠道。
 */
public interface RefundProvider {

    /**
     * 执行退款，返回渠道退款单号。
     *
     * @param orderId  订单 id
     * @param orderNo  订单号
     * @param amount   退款金额（分）
     * @param reason   退款原因
     */
    String refund(Long orderId, String orderNo, Long amount, String reason);
}
