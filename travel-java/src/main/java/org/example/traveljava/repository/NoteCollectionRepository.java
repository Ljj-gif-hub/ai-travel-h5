package org.example.traveljava.repository;

import org.example.traveljava.entity.NoteCollection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

/**
 * 【新功能】游记收藏夹仓储
 */
public interface NoteCollectionRepository extends JpaRepository<NoteCollection, Long> {

    /** 我的收藏夹 */
    List<NoteCollection> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 公开收藏夹分页（支持名称/描述关键字搜索） */
    Page<NoteCollection> findByIsPublicTrueAndNameContainingIgnoreCaseOrderByCreatedAtDesc(String keyword, Pageable pageable);

    /** 公开收藏夹分页（无关键字） */
    Page<NoteCollection> findByIsPublicTrueOrderByCreatedAtDesc(Pageable pageable);

    /**
     * COLL-1 修复：悲观锁查询收藏夹，序列化并发添加/移除笔记操作，
     * 防止 noteIds JSON 数组读-改-写丢失更新。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from NoteCollection c where c.id = :id")
    Optional<NoteCollection> findByIdForUpdate(@Param("id") Long id);
}
