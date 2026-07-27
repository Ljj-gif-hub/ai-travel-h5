package org.example.traveljava.repository;

import org.example.traveljava.entity.NoteLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NoteLikeRepository extends JpaRepository<NoteLike, Long> {

    boolean existsByNoteIdAndUserId(Long noteId, Long userId);

    Optional<NoteLike> findByNoteIdAndUserId(Long noteId, Long userId);

    int countByNoteId(Long noteId);

    void deleteByNoteIdAndUserId(Long noteId, Long userId);
}
