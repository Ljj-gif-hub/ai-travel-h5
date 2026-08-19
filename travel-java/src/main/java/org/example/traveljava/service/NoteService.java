package org.example.traveljava.service;

import org.example.traveljava.entity.Note;
import org.example.traveljava.repository.NoteLikeRepository;
import org.example.traveljava.repository.NoteRepository;
import org.example.traveljava.util.TextCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NoteService {

    private static final Logger log = LoggerFactory.getLogger(NoteService.class);

    private final NoteRepository noteRepository;
    private final NoteLikeRepository noteLikeRepository;
    private final UserService userService;
    private final ContentModerationService moderationService;

    public NoteService(NoteRepository noteRepository, NoteLikeRepository noteLikeRepository,
                       UserService userService, ContentModerationService moderationService) {
        this.noteRepository = noteRepository;
        this.noteLikeRepository = noteLikeRepository;
        this.userService = userService;
        this.moderationService = moderationService;
    }

    public List<Note> getNotes(Long userId) {
        return noteRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, "published");
    }

    /**
     * 【新增】社区发现页：获取所有用户已发布的游记（过滤被举报隐藏）
     */
    public List<Note> getAllPublishedNotes() {
        return noteRepository.findByStatusAndHiddenFalseOrderByCreatedAtDesc("published");
    }

    /** 社区发现页：分页获取已发布游记（page 从 1 开始，过滤被举报隐藏） */
    public Page<Note> getAllPublishedNotes(int page, int size) {
        return noteRepository.findByStatusAndHiddenFalseOrderByCreatedAtDesc("published", PageRequest.of(page - 1, size));
    }

    public Note getNoteById(Long noteId) {
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new IllegalArgumentException("游记不存在"));
        // 软删除的游记不可再通过详情接口读取
        if ("deleted".equals(note.getStatus())) {
            throw new IllegalArgumentException("游记不存在");
        }
        // 【新功能】被举报隐藏的游记不可通过详情接口读取
        if (Boolean.TRUE.equals(note.getHidden())) {
            throw new IllegalArgumentException("游记不存在");
        }
        return note;
    }

    public int getNoteCount(Long userId) {
        return noteRepository.countByUserIdAndStatus(userId, "published");
    }

    @Transactional
    public Note createNote(Long userId, Map<String, Object> params) {
        Note note = new Note();
        note.setUserId(userId);
        note.setTitle((String) params.get("title"));
        note.setContent(TextCleaner.sanitizeHtml((String) params.get("content")));
        note.setCover((String) params.get("cover"));
        
        if (params.containsKey("tags")) {
            Object tagsObj = params.get("tags");
            if (tagsObj instanceof List<?> list) {
                note.setTags(list.stream().map(Object::toString).reduce((a, b) -> a + "," + b).orElse(""));
            } else if (tagsObj instanceof String) {
                note.setTags((String) tagsObj);
            }
        }

        note.setStatus("published");
        note.setViews(0);
        note.setLikes(0);
        note.setComments(0);

        // 【新功能】内容审核（开关控制，LLM 失败 fail-open）
        ContentModerationService.ModerationResult m = moderationService.check(
                (note.getTitle() == null ? "" : note.getTitle()) + "\n" +
                (note.getContent() == null ? "" : note.getContent()));
        if (!m.isSafe()) {
            throw new IllegalArgumentException("内容包含违规信息，请修改后重试");
        }

        Note saved = noteRepository.save(note);
        log.info("创建游记：userId={}, title={}", userId, note.getTitle());
        return saved;
    }

    @Transactional
    public Note updateNote(Long userId, Long noteId, Map<String, Object> params) {
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new IllegalArgumentException("游记不存在"));
        
        if (!note.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权编辑该游记");
        }

        if (params.containsKey("title")) {
            note.setTitle((String) params.get("title"));
        }
        if (params.containsKey("content")) {
            note.setContent(TextCleaner.sanitizeHtml((String) params.get("content")));
        }
        if (params.containsKey("cover")) {
            note.setCover((String) params.get("cover"));
        }
        if (params.containsKey("tags")) {
            Object tagsObj = params.get("tags");
            if (tagsObj instanceof List<?> list) {
                note.setTags(list.stream().map(Object::toString).reduce((a, b) -> a + "," + b).orElse(""));
            } else if (tagsObj instanceof String) {
                note.setTags((String) tagsObj);
            }
        }

        // 【新功能】内容审核（更新标题/正文时）
        if (params.containsKey("title") || params.containsKey("content")) {
            ContentModerationService.ModerationResult m = moderationService.check(
                    (note.getTitle() == null ? "" : note.getTitle()) + "\n" +
                    (note.getContent() == null ? "" : note.getContent()));
            if (!m.isSafe()) {
                throw new IllegalArgumentException("内容包含违规信息，请修改后重试");
            }
        }

        Note saved = noteRepository.save(note);
        log.info("更新游记：noteId={}", noteId);
        return saved;
    }

    @Transactional
    public void deleteNote(Long userId, Long noteId) {
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new IllegalArgumentException("游记不存在"));
        
        if (!note.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权删除该游记");
        }

        note.setStatus("deleted");
        noteRepository.save(note);
        log.info("删除游记：noteId={}", noteId);
    }

    @Transactional
    public Note incrementViews(Long noteId) {
        // 原子自增，避免并发读改写丢失更新
        noteRepository.incrementViews(noteId);
        return noteRepository.findById(noteId)
                .orElseThrow(() -> new IllegalArgumentException("游记不存在"));
    }

    @Transactional
    public Map<String, Object> toggleLike(Long noteId, Long userId) {
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new IllegalArgumentException("游记不存在"));

        boolean alreadyLiked = noteLikeRepository.existsByNoteIdAndUserId(noteId, userId);

        if (alreadyLiked) {
            // 取消点赞：计数原子 -1（likes>=0 条件下不会减成负数）
            noteLikeRepository.deleteByNoteIdAndUserId(noteId, userId);
            noteRepository.adjustLikes(noteId, -1);
        } else {
            // LIKE-1 修复：原子 INSERT IGNORE 按受影响行数判断，冲突不再走 save()+catch
            //（IDENTITY 下冲突会标记 rollback-only，catch 后提交仍抛 UnexpectedRollbackException）
            int inserted = noteLikeRepository.insertIfAbsent(noteId, userId);
            if (inserted > 0) {
                noteRepository.adjustLikes(noteId, 1);
                // 【新功能】笔记被赞 +1（仅新点赞生效一次，自己赞自己不发放）
                if (note.getUserId() != null && !note.getUserId().equals(userId)) {
                    userService.addPoints(note.getUserId(), 1);
                }
            }
        }

        // adjustLikes 已 clearAutomatically，重新读取最新计数
        Note fresh = noteRepository.findById(noteId)
                .orElseThrow(() -> new IllegalArgumentException("游记不存在"));
        Map<String, Object> result = new HashMap<>();
        result.put("likes", fresh.getLikes());
        result.put("isLiked", !alreadyLiked);
        return result;
    }

    /**
     * 检查当前用户是否已点赞某篇游记
     */
    public boolean isLikedByUser(Long noteId, Long userId) {
        if (userId == null) return false;
        return noteLikeRepository.existsByNoteIdAndUserId(noteId, userId);
    }
}
