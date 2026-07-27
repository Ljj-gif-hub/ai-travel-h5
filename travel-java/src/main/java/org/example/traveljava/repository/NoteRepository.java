package org.example.traveljava.repository;

import org.example.traveljava.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
