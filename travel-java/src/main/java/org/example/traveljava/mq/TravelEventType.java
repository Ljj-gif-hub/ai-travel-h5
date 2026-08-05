package org.example.traveljava.mq;

/**
 * 业务事件类型。
 */
public enum TravelEventType {

    /** 订单支付成功（payload: orderNo, userId, amount, channel） */
    ORDER_PAID,

    /** AI 行程规划完成（payload: destination, days, budget） */
    TRIP_GENERATED
}
