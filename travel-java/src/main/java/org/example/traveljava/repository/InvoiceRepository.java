package org.example.traveljava.repository;

import org.example.traveljava.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 【新功能】发票仓储
 */
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    /** 一单一票去重 */
    boolean existsByOrderId(Long orderId);

    /** 发票号唯一性检查（生成时重试兜底） */
    boolean existsByInvoiceNo(String invoiceNo);

    /** 我的发票列表 */
    List<Invoice> findByUserIdOrderByCreatedAtDesc(Long userId);
}
