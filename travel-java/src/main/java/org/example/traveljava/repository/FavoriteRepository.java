package org.example.traveljava.repository;

import org.example.traveljava.entity.Favorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    List<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 分页版本（修复全表加载） */
    Page<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<Favorite> findByUserIdAndTargetTypeOrderByCreatedAtDesc(Long userId, String targetType);

    Page<Favorite> findByUserIdAndTargetTypeOrderByCreatedAtDesc(Long userId, String targetType, Pageable pageable);

    Optional<Favorite> findByUserIdAndTargetIdAndTargetType(Long userId, Long targetId, String targetType);
    boolean existsByUserIdAndTargetIdAndTargetType(Long userId, Long targetId, String targetType);
    int countByUserId(Long userId);
    int countByUserIdAndTargetType(Long userId, String targetType);
}
