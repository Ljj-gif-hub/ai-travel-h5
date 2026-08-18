package org.example.traveljava.repository;

import org.example.traveljava.entity.TripShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 【新功能】行程分享仓储
 */
public interface TripShareRepository extends JpaRepository<TripShare, Long> {

    Optional<TripShare> findByToken(String token);

    /** 某计划未过期的分享（复用检查） */
    List<TripShare> findByPlanIdAndExpireAtAfter(Long planId, LocalDateTime now);

    /** 清理过期分享 */
    void deleteByExpireAtBefore(LocalDateTime now);
}
