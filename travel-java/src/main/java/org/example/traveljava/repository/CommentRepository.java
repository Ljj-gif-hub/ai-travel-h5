package org.example.traveljava.repository;

import org.example.traveljava.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** 顶级评论（非回复），按时间正序 */
    List<Comment> findByNoteIdAndParentIdIsNullOrderByCreatedAtAsc(Long noteId);

    /** 顶级评论分页版本（修复全表加载） */
    Page<Comment> findByNoteIdAndParentIdIsNullOrderByCreatedAtAsc(Long noteId, Pageable pageable);

    /** 【新功能】顶级评论（过滤被举报隐藏的评论） */
    List<Comment> findByNoteIdAndParentIdIsNullAndHiddenFalseOrderByCreatedAtAsc(Long noteId);

    /** 【新功能】顶级评论分页（过滤被举报隐藏的评论） */
    Page<Comment> findByNoteIdAndParentIdIsNullAndHiddenFalseOrderByCreatedAtAsc(Long noteId, Pageable pageable);

    /** 某条评论的所有回复，按点赞数降序（最热回复在前） */
    List<Comment> findByParentIdOrderByLikesDescCreatedAtAsc(Long parentId);

    /** 【新功能】某条评论的回复（过滤被举报隐藏的回复） */
    List<Comment> findByParentIdAndHiddenFalseOrderByLikesDescCreatedAtAsc(Long parentId);

    /** 某条评论的回复数 */
    int countByParentId(Long parentId);

    /** 某条笔记的顶级评论数 */
    int countByNoteIdAndParentIdIsNull(Long noteId);

    int countByNoteId(Long noteId);

    void deleteByNoteId(Long noteId);

    /** 原子自增点赞数，配合 comment_likes 去重表使用 */
    @Modifying
    @Query("update Comment c set c.likes = c.likes + 1 where c.id = :id")
    int incrementLikes(@Param("id") Long id);

    /** 批量统计多条评论的回复数（parentId → 回复数），一次 GROUP BY 避免 N+1 */
    @Query("SELECT c.parentId, COUNT(c) FROM Comment c WHERE c.parentId IN :parentIds GROUP BY c.parentId")
    List<Object[]> countRepliesByParentIds(@Param("parentIds") List<Long> parentIds);

    /** 批量获取多条评论的回复，按点赞降序（用于取每条的热评回复），一次 IN 避免 N+1 */
    List<Comment> findByParentIdInOrderByLikesDescCreatedAtAsc(List<Long> parentIds);
}
