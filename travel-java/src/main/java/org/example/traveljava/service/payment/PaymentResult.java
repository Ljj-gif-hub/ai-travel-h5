package org.example.traveljava.service.payment;

/**
 * 支付发起结果
 * @param payUrl           支付跳转地址（真实渠道为第三方收银台，mock 为模拟支付页）
 * @param providerTradeNo  支付渠道交易号（真实渠道为第三方流水号，mock 为 MOCK 前缀流水号）
 */
public record PaymentResult(String payUrl, String providerTradeNo) {
}
