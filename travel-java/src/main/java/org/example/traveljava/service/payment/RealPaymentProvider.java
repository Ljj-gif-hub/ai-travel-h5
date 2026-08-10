package org.example.traveljava.service.payment;

import org.example.traveljava.entity.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 真实支付渠道（支付宝/微信）— 填写商户密钥并设置 payment.provider=alipay|wechat 后激活
 *
 * 【对接骨架】当前为占位实现，需按渠道接入：
 *   - 支付宝：应用 alipay-sdk-java，用 RSA2 签名，调用 alipay.trade.page.pay 获取收银台跳转地址
 *   - 微信：调用 微信支付 Native/JSAPI 下单接口，用 mch 私钥签名，获取支付链接
 * 完成渠道对接后替换 createPayment / verifyNotify 的具体实现即可，业务层（PaymentService）无需改动。
 */
@Service
@ConditionalOnExpression("'${payment.provider:mock}'.equalsIgnoreCase('alipay') || '${payment.provider:mock}'.equalsIgnoreCase('wechat')")
public class RealPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(RealPaymentProvider.class);

    @Value("${payment.provider:mock}")
    private String provider;

    @Override
    public PaymentResult createPayment(Order order) {
        log.warn("[{}支付] 真实渠道未完成接入：orderNo={}，请填写商户密钥并实现渠道 SDK 调用", provider, order.getOrderNo());
        throw new UnsupportedOperationException("真实支付渠道尚未接入，请在 application.yml 配置 " + provider + " 商户密钥后使用");
    }

    @Override
    public boolean verifyNotify(Map<String, String> params) {
        // 安全：真实渠道验签未实现前必须 fail-closed（拒绝），绝不能放行。
        // 接入渠道后在此实现 支付宝 RSA2 / 微信平台证书 验签，通过才返回 true。
        log.error("[{}支付] 回调验签未实现，已拒绝该回调（防伪造支付）", provider);
        throw new UnsupportedOperationException("真实支付渠道验签未实现，已拒绝回调");
    }

    @Override
    public String getProviderName() {
        return provider;
    }
}
