package org.example.traveljava.repository;

import org.example.traveljava.entity.NoteCollection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

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
}
