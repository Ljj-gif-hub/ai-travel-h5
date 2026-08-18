package org.example.traveljava.repository;

import org.example.traveljava.entity.Feedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 分页版本（修复全表加载） */
    Page<Feedback> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
