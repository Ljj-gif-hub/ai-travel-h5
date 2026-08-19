package org.example.traveljava.repository;

import org.example.traveljava.entity.AsyncAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AsyncAuditRepository extends JpaRepository<AsyncAudit, Long> {

    /** MQ-1 修复：按 eventId 幂等查重，防止消息 redelivery 重复插入审计行 */
    boolean existsByEventId(String eventId);
}
