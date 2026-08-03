package org.example.traveljava.repository;

import org.example.traveljava.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    Optional<PostLike> findByPostIdAndUserId(Long postId, Long userId);

    int countByPostId(Long postId);

    void deleteByPostIdAndUserId(Long postId, Long userId);

    /** 批量查询当前用户点赞过的动态，用于列表接口消除 N+1 */
    List<PostLike> findByPostIdInAndUserId(List<Long> postIds, Long userId);
}
