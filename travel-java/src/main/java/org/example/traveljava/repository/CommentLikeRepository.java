package org.example.traveljava.repository;

import org.example.traveljava.entity.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {
    boolean existsByCommentIdAndUserId(Long commentId, Long userId);

    /**
     * LIKE-1 修复：原子「插入不重复」——按受影响行数判断是否新点赞。
     * 不用 save()+catch(DataIntegrityViolationException)：IDENTITY 下 INSERT 立即执行，
     * 唯一约束冲突会让 Hibernate 标记 rollback-only，catch 后提交仍抛 UnexpectedRollbackException。
     * INSERT IGNORE 冲突时不抛异常、返回 0，事务保持干净，计数只在真正新插入时 +1。
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO comment_likes (comment_id, user_id, created_at) VALUES (:commentId, :userId, NOW())", nativeQuery = true)
    int insertIfAbsent(@Param("commentId") Long commentId, @Param("userId") Long userId);
    void deleteByCommentIdAndUserId(Long commentId, Long userId);
    int countByCommentId(Long commentId);

    /** L-COMMENT-1 修复：删除评论/回复时清理其点赞记录，防 comment_likes 留孤儿行 */
    void deleteByCommentId(Long commentId);
}
