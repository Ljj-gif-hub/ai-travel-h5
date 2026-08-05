package org.example.traveljava.repository;

import org.example.traveljava.entity.AsyncAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AsyncAuditRepository extends JpaRepository<AsyncAudit, Long> {
}
