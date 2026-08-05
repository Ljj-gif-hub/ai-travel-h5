package org.example.traveljava.service.payment;

import org.example.traveljava.entity.Order;

import java.util.Map;

/**
 * 支付渠道抽象接口 — 「可配置对接层」核心
 *
 * 仿 MapService 的多供应商模式：由 PaymentConfig.provider + @ConditionalOnProperty 决定激活哪个实现。
 * - MockPaymentProvider：无真实商户密钥时的模拟实现（默认）
 * - RealPaymentProvider：接入支付宝/微信支付时激活（填 Key 即切换）
 *
 * 新增真实渠道时只需新增一个实现类并实现本接口，业务层无感知。
 */
public interface PaymentProvider {

    /**
     * 发起支付
     * @param order 待支付订单（状态必须为 pending）
     * @return 支付跳转地址 + 渠道交易号
     */
    PaymentResult createPayment(Order order);

    /**
     * 校验支付回调签名
     * @param params 回调参数（orderNo、交易号、状态等）
     * @return 签名是否有效
     */
    boolean verifyNotify(Map<String, String> params);

    /**
     * @return 当前渠道标识，如 "mock" / "alipay" / "wechat"
     */
    String getProviderName();
}
