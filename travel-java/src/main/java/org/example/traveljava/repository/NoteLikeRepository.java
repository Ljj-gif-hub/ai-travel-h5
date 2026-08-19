package org.example.traveljava.repository;

import org.example.traveljava.entity.NoteLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NoteLikeRepository extends JpaRepository<NoteLike, Long> {

    boolean existsByNoteIdAndUserId(Long noteId, Long userId);

    /**
     * LIKE-1 修复：原子「插入不重复」——按受影响行数判断是否新点赞。
     * 不用 save()+catch(DataIntegrityViolationException)：IDENTITY 下 INSERT 立即执行，
     * 唯一约束冲突会让 Hibernate 标记 rollback-only，catch 后提交仍抛 UnexpectedRollbackException。
     * INSERT IGNORE 冲突时不抛异常、返回 0，事务保持干净，计数只在真正新插入时 +1。
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO note_likes (note_id, user_id, created_at) VALUES (:noteId, :userId, NOW())", nativeQuery = true)
    int insertIfAbsent(@Param("noteId") Long noteId, @Param("userId") Long userId);

    Optional<NoteLike> findByNoteIdAndUserId(Long noteId, Long userId);

    int countByNoteId(Long noteId);

    void deleteByNoteIdAndUserId(Long noteId, Long userId);

    /** 批量查询当前用户点赞过的笔记，用于列表接口消除 N+1 */
    List<NoteLike> findByNoteIdInAndUserId(List<Long> noteIds, Long userId);

    /** 查询某用户点赞过的全部笔记（推荐引擎构建用户画像用） */
    List<NoteLike> findByUserId(Long userId);
}
