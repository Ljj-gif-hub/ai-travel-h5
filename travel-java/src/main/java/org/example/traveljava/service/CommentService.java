package org.example.traveljava.service;

import org.example.traveljava.entity.Comment;
import org.example.traveljava.entity.Note;
import org.example.traveljava.repository.CommentRepository;
import org.example.traveljava.repository.NoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentService.class);

    private final CommentRepository commentRepository;
    private final NoteRepository noteRepository;

    public CommentService(CommentRepository commentRepository, NoteRepository noteRepository) {
        this.commentRepository = commentRepository;
        this.noteRepository = noteRepository;
    }

    /** 获取顶级评论列表（不含回复） */
    public List<Comment> getComments(Long noteId) {
        return commentRepository.findByNoteIdAndParentIdIsNullOrderByCreatedAtAsc(noteId);
    }

    /** 获取某条评论的所有回复，按点赞降序 */
    public List<Comment> getReplies(Long parentId) {
        return commentRepository.findByParentIdOrderByLikesDescCreatedAtAsc(parentId);
    }

    /** 获取点赞最多的那条回复（抖音风格：默认只展示一条热评回复） */
    public Optional<Comment> getTopReply(Long parentId) {
        List<Comment> replies = commentRepository.findByParentIdOrderByLikesDescCreatedAtAsc(parentId);
        return replies.isEmpty() ? Optional.empty() : Optional.of(replies.get(0));
    }

    /** 某条评论的回复总数 */
    public int getReplyCount(Long parentId) {
        return commentRepository.countByParentId(parentId);
    }

    public int getCommentCount(Long noteId) {
        // 【修复】总评论数 = 顶级评论 + 所有回复
        return commentRepository.countByNoteId(noteId);
    }

    @Transactional
    public Comment addComment(Long userId, Long noteId, String content, String image, String video) {
        return addReply(userId, noteId, null, content, image, video);
    }

    /** 添加评论或回复。parentId 为 null 表示顶级评论，非 null 表示回复某条评论 */
    @Transactional
    public Comment addReply(Long userId, Long noteId, Long parentId,
                            String content, String image, String video) {
        boolean hasContent = content != null && !content.trim().isEmpty();
        boolean hasImage = image != null && !image.trim().isEmpty();
        boolean hasVideo = video != null && !video.trim().isEmpty();
        if (!hasContent && !hasImage && !hasVideo) {
            throw new IllegalArgumentException("请至少输入文字、上传图片或上传视频");
        }

        // 验证游记存在
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new IllegalArgumentException("游记不存在"));

        // 如果是回复，验证父评论存在
        if (parentId != null) {
            if (!commentRepository.existsById(parentId)) {
                throw new IllegalArgumentException("原评论不存在");
            }
        }

        Comment comment = new Comment();
        comment.setNoteId(noteId);
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setContent(hasContent ? content.trim() : null);
        comment.setImage(image);
        comment.setVideo(video);
        comment.setLikes(0);

        Comment saved = commentRepository.save(comment);

        // 同步更新游记评论数（包含回复）
        note.setComments(commentRepository.countByNoteId(noteId));
        noteRepository.save(note);

        log.info("添加{}：noteId={}, userId={}, parentId={}, hasImage={}, hasVideo={}",
                parentId == null ? "评论" : "回复", noteId, userId, parentId, hasImage, hasVideo);
        return saved;
    }

    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("评论不存在"));

        if (!comment.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权删除该评论");
        }

        Long noteId = comment.getNoteId();
        commentRepository.delete(comment);

        // 同步更新游记评论数（包含回复）
        noteRepository.findById(noteId).ifPresent(note -> {
            note.setComments(commentRepository.countByNoteId(noteId));
            noteRepository.save(note);
        });

        log.info("删除评论：commentId={}, userId={}", commentId, userId);
    }

    /** 点赞评论或回复 */
    @Transactional
    public Comment likeComment(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("评论不存在"));
        comment.setLikes(comment.getLikes() + 1);
        return commentRepository.save(comment);
    }
}
