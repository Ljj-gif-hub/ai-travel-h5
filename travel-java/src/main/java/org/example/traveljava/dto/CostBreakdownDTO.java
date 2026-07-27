package org.example.traveljava.dto;

import java.math.BigDecimal;

/**
 * 费用明细数据传输对象
 * 用于展示旅行预算的各项费用分解，帮助用户了解每一类支出的占比
 */
public class CostBreakdownDTO {

    /** 酒店住宿费用 */
    private BigDecimal hotelCost;

    /** 景点门票费用 */
    private BigDecimal ticketCost;

    /** 餐饮费用 */
    private BigDecimal foodCost;

    /** 交通费用（含往返大交通和市内交通） */
    private BigDecimal transportCost;

    /** 其他杂项费用（购物、保险等） */
    private BigDecimal otherCost;

    /** 总费用合计 */
    private BigDecimal totalCost;

    /** 货币单位，默认人民币 */
    private String currency;

    /** 费用说明备注 */
    private String detailNote;

    // ==================== 无参构造 ====================
    public CostBreakdownDTO() {
        this.currency = "CNY";
        this.hotelCost = BigDecimal.ZERO;
        this.ticketCost = BigDecimal.ZERO;
        this.foodCost = BigDecimal.ZERO;
        this.transportCost = BigDecimal.ZERO;
        this.otherCost = BigDecimal.ZERO;
        this.totalCost = BigDecimal.ZERO;
    }

    // ==================== Getter / Setter ====================

    public BigDecimal getHotelCost() {
        return hotelCost;
    }

    public void setHotelCost(BigDecimal hotelCost) {
        this.hotelCost = hotelCost;
    }

    public BigDecimal getTicketCost() {
        return ticketCost;
    }

    public void setTicketCost(BigDecimal ticketCost) {
        this.ticketCost = ticketCost;
    }

    public BigDecimal getFoodCost() {
        return foodCost;
    }

    public void setFoodCost(BigDecimal foodCost) {
        this.foodCost = foodCost;
    }

    public BigDecimal getTransportCost() {
        return transportCost;
    }

    public void setTransportCost(BigDecimal transportCost) {
        this.transportCost = transportCost;
    }

    public BigDecimal getOtherCost() {
        return otherCost;
    }

    public void setOtherCost(BigDecimal otherCost) {
        this.otherCost = otherCost;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getDetailNote() {
        return detailNote;
    }

    public void setDetailNote(String detailNote) {
        this.detailNote = detailNote;
    }
}
