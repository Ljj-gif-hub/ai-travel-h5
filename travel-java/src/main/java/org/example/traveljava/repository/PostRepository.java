package org.example.traveljava.repository;

import org.example.traveljava.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 社区广场：获取所有用户的动态，按时间倒序（分页） */
    Page<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** 【新功能】社区广场（过滤被举报隐藏的动态） */
    Page<Post> findByHiddenFalseOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 【并发安全】原子增减点赞数（delta=+1 点赞 / -1 取消点赞）。
     * 条件 p.likes + :delta >= 0 防止取消点赞把计数减成负数；
     * clearAutomatically 清空一级缓存，保证后续 findById 读到更新后的值。
     */
    @Modifying(clearAutomatically = true)
    @Query("update Post p set p.likes = p.likes + :delta where p.id = :id and p.likes + :delta >= 0")
    int adjustLikes(@Param("id") Long id, @Param("delta") int delta);
}
