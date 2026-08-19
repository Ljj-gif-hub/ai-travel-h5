package org.example.traveljava.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 【新功能】发票。
 * 仅已支付（paid/completed）订单可开票，且一单一票；
 * 发票号格式：INV + yyyyMMdd + 6 位随机数字。
 */
@Entity
@Table(name = "invoices", indexes = {
        @Index(name = "idx_invoices_user_id", columnList = "user_id")
})
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联订单（一单一票） */
    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    /** 所属用户 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 发票号：INV+yyyyMMdd+6 位随机数字 */
    @Column(name = "invoice_no", nullable = false, unique = true, length = 32)
    private String invoiceNo;

    /** 发票抬头 */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** 税号（企业抬头时填写） */
    @Column(name = "tax_no", length = 50)
    private String taxNo;

    /** 发票类型：personal 个人 / company 企业 */
    @Column(length = 20)
    private String type = "personal";

    /** 开票金额（元，取自订单实付金额） */
    @Column(nullable = false)
    private Long amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Invoice() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getInvoiceNo() { return invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTaxNo() { return taxNo; }
    public void setTaxNo(String taxNo) { this.taxNo = taxNo; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
