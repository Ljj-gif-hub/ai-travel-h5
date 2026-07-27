package org.example.traveljava.repository;

import org.example.traveljava.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 社区广场：获取所有用户的动态，按时间倒序 */
    List<Post> findAllByOrderByCreatedAtDesc();
}
