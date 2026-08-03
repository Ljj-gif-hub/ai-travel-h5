package org.example.traveljava.repository;

import org.example.traveljava.entity.Note;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoteRepository extends JpaRepository<Note, Long> {
    List<Note> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Note> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
    int countByUserId(Long userId);
    int countByUserIdAndStatus(Long userId, String status);

    /** 社区发现页：获取所有已发布的游记，按时间倒序 */
    List<Note> findByStatusOrderByCreatedAtDesc(String status);

    /** 社区发现页：分页获取已发布游记 */
    Page<Note> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    /** 原子自增浏览量，避免并发丢失更新 */
    @Modifying
    @Query("update Note n set n.views = n.views + 1 where n.id = :id")
    int incrementViews(@Param("id") Long id);
}
