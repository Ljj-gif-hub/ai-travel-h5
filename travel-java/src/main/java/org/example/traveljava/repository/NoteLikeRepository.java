package org.example.traveljava.repository;

import org.example.traveljava.entity.NoteLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NoteLikeRepository extends JpaRepository<NoteLike, Long> {

    boolean existsByNoteIdAndUserId(Long noteId, Long userId);

    Optional<NoteLike> findByNoteIdAndUserId(Long noteId, Long userId);

    int countByNoteId(Long noteId);

    void deleteByNoteIdAndUserId(Long noteId, Long userId);

    /** 批量查询当前用户点赞过的笔记，用于列表接口消除 N+1 */
    List<NoteLike> findByNoteIdInAndUserId(List<Long> noteIds, Long userId);

    /** 查询某用户点赞过的全部笔记（推荐引擎构建用户画像用） */
    List<NoteLike> findByUserId(Long userId);
}
