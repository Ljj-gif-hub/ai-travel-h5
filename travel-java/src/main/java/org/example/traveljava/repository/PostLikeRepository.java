package org.example.traveljava.repository;

import org.example.traveljava.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    /**
     * LIKE-1 修复：原子「插入不重复」——按受影响行数判断是否新点赞。
     * 不用 save()+catch(DataIntegrityViolationException)：IDENTITY 下 INSERT 立即执行，
     * 唯一约束冲突会让 Hibernate 标记 rollback-only，catch 后提交仍抛 UnexpectedRollbackException。
     * INSERT IGNORE 冲突时不抛异常、返回 0，事务保持干净，计数只在真正新插入时 +1。
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO post_likes (post_id, user_id, created_at) VALUES (:postId, :userId, NOW())", nativeQuery = true)
    int insertIfAbsent(@Param("postId") Long postId, @Param("userId") Long userId);

    Optional<PostLike> findByPostIdAndUserId(Long postId, Long userId);

    int countByPostId(Long postId);

    void deleteByPostIdAndUserId(Long postId, Long userId);

    /** 批量查询当前用户点赞过的动态，用于列表接口消除 N+1 */
    List<PostLike> findByPostIdInAndUserId(List<Long> postIds, Long userId);
}
