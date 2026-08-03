package org.example.traveljava.service;

import org.example.traveljava.entity.Note;
import org.example.traveljava.entity.NoteLike;
import org.example.traveljava.repository.NoteLikeRepository;
import org.example.traveljava.repository.NoteRepository;
import org.example.traveljava.util.TextCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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

    public NoteService(NoteRepository noteRepository, NoteLikeRepository noteLikeRepository) {
        this.noteRepository = noteRepository;
        this.noteLikeRepository = noteLikeRepository;
    }

    public List<Note> getNotes(Long userId) {
        return noteRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, "published");
    }

    /**
     * 【新增】社区发现页：获取所有用户已发布的游记
     */
    public List<Note> getAllPublishedNotes() {
        return noteRepository.findByStatusOrderByCreatedAtDesc("published");
    }

    /** 社区发现页：分页获取已发布游记（page 从 1 开始） */
    public Page<Note> getAllPublishedNotes(int page, int size) {
        return noteRepository.findByStatusOrderByCreatedAtDesc("published", PageRequest.of(page - 1, size));
    }

    public Note getNoteById(Long noteId) {
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new IllegalArgumentException("游记不存在"));
        // 软删除的游记不可再通过详情接口读取
        if ("deleted".equals(note.getStatus())) {
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
            // 取消点赞
            noteLikeRepository.deleteByNoteIdAndUserId(noteId, userId);
            note.setLikes(noteLikeRepository.countByNoteId(noteId));
            noteRepository.save(note);
        } else {
            // 点赞：并发双击时唯一约束兜底，冲突视为已点赞
            try {
                noteLikeRepository.save(new NoteLike(noteId, userId));
            } catch (DataIntegrityViolationException e) {
                log.debug("游记点赞并发冲突：noteId={}, userId={}", noteId, userId);
            }
            note.setLikes(noteLikeRepository.countByNoteId(noteId));
            noteRepository.save(note);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("likes", note.getLikes());
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
