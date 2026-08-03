package org.example.traveljava.repository;

import org.example.traveljava.entity.Comment;
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

    /** 某条评论的所有回复，按点赞数降序（最热回复在前） */
    List<Comment> findByParentIdOrderByLikesDescCreatedAtAsc(Long parentId);

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
}
