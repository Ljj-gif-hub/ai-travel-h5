package org.example.traveljava.service;

import org.example.traveljava.entity.Comment;
import org.example.traveljava.entity.CommentLike;
import org.example.traveljava.repository.CommentLikeRepository;
import org.example.traveljava.repository.CommentRepository;
import org.example.traveljava.repository.NoteRepository;
import org.example.traveljava.util.TextCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentService.class);

    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final NoteRepository noteRepository;
    private final UserService userService;
    private final ContentModerationService moderationService;

    public CommentService(CommentRepository commentRepository, CommentLikeRepository commentLikeRepository,
                          NoteRepository noteRepository, UserService userService,
                          ContentModerationService moderationService) {
        this.commentRepository = commentRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.noteRepository = noteRepository;
        this.userService = userService;
        this.moderationService = moderationService;
    }

    /** 获取顶级评论列表（不含回复） */
    public List<Comment> getComments(Long noteId) {
        return getComments(noteId, 0, 20);
    }

    /** 分页获取顶级评论（page 从 0 开始，修复全表加载；过滤被举报隐藏） */
    public List<Comment> getComments(Long noteId, int page, int size) {
        return commentRepository.findByNoteIdAndParentIdIsNullAndHiddenFalseOrderByCreatedAtAsc(noteId,
                org.springframework.data.domain.PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)))
                .getContent();
    }

    /** 获取某条评论的所有回复，按点赞降序（过滤被举报隐藏） */
    public List<Comment> getReplies(Long parentId) {
        return commentRepository.findByParentIdAndHiddenFalseOrderByLikesDescCreatedAtAsc(parentId);
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

    /** 批量获取多条评论的回复数（一次 GROUP BY 查询，避免 N+1） */
    public Map<Long, Integer> getReplyCounts(List<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return commentRepository.countRepliesByParentIds(parentIds).stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[1]).intValue()));
    }

    /** 批量获取每条评论点赞最多的那条热评回复（一次 IN 查询，避免 N+1） */
    public Map<Long, Comment> getTopReplies(List<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Comment> top = new HashMap<>();
        for (Comment reply : commentRepository.findByParentIdInOrderByLikesDescCreatedAtAsc(parentIds)) {
            top.putIfAbsent(reply.getParentId(), reply); // 按点赞降序，首个即热评
        }
        return top;
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
        noteRepository.findById(noteId)
                .orElseThrow(() -> new IllegalArgumentException("游记不存在"));

        // 如果是回复，验证父评论存在且属于同一篇游记（防止跨笔记孤儿回复）
        if (parentId != null) {
            Comment parent = commentRepository.findById(parentId)
                    .orElseThrow(() -> new IllegalArgumentException("原评论不存在"));
            if (!parent.getNoteId().equals(noteId)) {
                throw new IllegalArgumentException("回复的评论不属于该游记");
            }
        }

        Comment comment = new Comment();
        comment.setNoteId(noteId);
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setContent(hasContent ? TextCleaner.sanitizeHtml(content.trim()) : null);
        comment.setImage(image);
        comment.setVideo(video);
        comment.setLikes(0);

        // 【新功能】内容审核（开关控制，LLM 失败 fail-open）
        ContentModerationService.ModerationResult m = moderationService.check(comment.getContent());
        if (!m.isSafe()) {
            throw new IllegalArgumentException("内容包含违规信息，请修改后重试");
        }

        Comment saved = commentRepository.save(comment);

        // 【并发安全】同步更新游记评论数（包含回复）：原子 +1，不再 count()+save() 读改写
        noteRepository.adjustComments(noteId, 1);

        // 【新功能】发评论 +2 积分
        userService.addPoints(userId, 2);

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

        // 【并发安全】同步更新游记评论数（包含回复）：原子 -1（comments>=0 条件下不会减成负数）
        noteRepository.adjustComments(noteId, -1);

        log.info("删除评论：commentId={}, userId={}", commentId, userId);
    }

    /** 点赞评论或回复（幂等：同一用户对同一评论只能点赞一次） */
    @Transactional
    public Comment likeComment(Long userId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("评论不存在"));

        if (commentLikeRepository.existsByCommentIdAndUserId(commentId, userId)) {
            return comment; // 已点过，幂等返回
        }
        try {
            commentLikeRepository.save(new CommentLike(commentId, userId));
            commentRepository.incrementLikes(commentId);
            // 同步一级缓存，避免 bulk 更新后 findById 返回旧点赞数
            comment.setLikes(comment.getLikes() + 1);
        } catch (DataIntegrityViolationException e) {
            // 并发重复点赞，唯一约束兜底，视为已点赞
            log.debug("评论点赞并发冲突：commentId={}, userId={}", commentId, userId);
        }
        return comment;
    }
}
