package org.example.traveljava.service;

import org.example.traveljava.entity.Invoice;
import org.example.traveljava.entity.Order;
import org.example.traveljava.repository.InvoiceRepository;
import org.example.traveljava.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 【新功能】发票服务：
 * - 仅已支付订单可开票（paid / completed）
 * - 一单一票（order_id 唯一约束 + exists 前置检查）
 * - 发票号：INV + yyyyMMdd + 6 位随机数字（唯一冲突时重试）
 */
@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;

    public InvoiceService(InvoiceRepository invoiceRepository, OrderRepository orderRepository) {
        this.invoiceRepository = invoiceRepository;
        this.orderRepository = orderRepository;
    }

    /** 开具发票 */
    @Transactional
    public Invoice issue(Long userId, Long orderId, String title, String taxNo, String type) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if (!order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作该订单");
        }
        // 仅已支付订单可开票
        String status = order.getStatus();
        if (!"paid".equals(status) && !"completed".equals(status)) {
            throw new IllegalArgumentException("订单支付完成后才能开具发票");
        }
        // 一单一票
        if (invoiceRepository.existsByOrderId(orderId)) {
            throw new IllegalArgumentException("该订单已开具发票");
        }

        String invoiceTitle = title != null && !title.isBlank() ? title.trim() : "个人";
        if (invoiceTitle.length() > 200) {
            invoiceTitle = invoiceTitle.substring(0, 200);
        }
        String invoiceType = "company".equalsIgnoreCase(type) ? "company" : "personal";
        if ("company".equals(invoiceType) && (taxNo == null || taxNo.isBlank())) {
            throw new IllegalArgumentException("企业发票需填写税号");
        }

        Invoice invoice = new Invoice();
        invoice.setOrderId(orderId);
        invoice.setUserId(userId);
        invoice.setTitle(invoiceTitle);
        invoice.setTaxNo(taxNo != null && !taxNo.isBlank() ? taxNo.trim() : null);
        invoice.setType(invoiceType);
        invoice.setAmount(order.getPrice());
        invoice.setInvoiceNo(generateInvoiceNo());
        Invoice saved = invoiceRepository.save(invoice);
        log.info("发票开具成功: invoiceNo={}, orderId={}, userId={}, amount={}",
                saved.getInvoiceNo(), orderId, userId, saved.getAmount());
        return saved;
    }

    /** 我的发票列表 */
    public List<Invoice> listMy(Long userId) {
        return invoiceRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /** 生成发票号：INV + yyyyMMdd + 6 位随机数字（唯一冲突时重试 10 次） */
    private String generateInvoiceNo() {
        for (int i = 0; i < 10; i++) {
            String candidate = "INV" + LocalDate.now().format(DATE_FMT)
                    + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
            if (!invoiceRepository.existsByInvoiceNo(candidate)) {
                return candidate;
            }
        }
        // 极端冲突：用纳秒时间戳兜底（仍满足格式长度要求）
        return "INV" + LocalDate.now().format(DATE_FMT)
                + String.format("%06d", Math.abs((int) (System.nanoTime() % 1_000_000)));
    }
}
