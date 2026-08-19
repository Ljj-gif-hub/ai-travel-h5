package org.example.traveljava.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.traveljava.entity.Note;
import org.example.traveljava.entity.NoteCollection;
import org.example.traveljava.repository.NoteCollectionRepository;
import org.example.traveljava.repository.NoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 【新功能】游记收藏夹：
 * - CRUD（仅本人可改删）
 * - 添加/移除笔记（去重，noteIds 以 JSON 数组存 LONGTEXT）
 * - 公开列表（可关键字搜索）+ 详情（附笔记摘要，过滤被隐藏/未发布的笔记）
 */
@Service
public class NoteCollectionService {

    private static final Logger log = LoggerFactory.getLogger(NoteCollectionService.class);

    private final NoteCollectionRepository collectionRepository;
    private final NoteRepository noteRepository;
    private final ObjectMapper objectMapper;

    public NoteCollectionService(NoteCollectionRepository collectionRepository,
                                 NoteRepository noteRepository,
                                 ObjectMapper objectMapper) {
        this.collectionRepository = collectionRepository;
        this.noteRepository = noteRepository;
        this.objectMapper = objectMapper;
    }

    /** 创建收藏夹 */
    @Transactional
    public NoteCollection create(Long userId, String name, String description, Boolean isPublic) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("收藏夹名称不能为空");
        }
        if (name.trim().length() > 100) {
            throw new IllegalArgumentException("收藏夹名称过长");
        }
        NoteCollection collection = new NoteCollection();
        collection.setUserId(userId);
        collection.setName(name.trim());
        collection.setDescription(description != null && description.length() > 500
                ? description.substring(0, 500) : description);
        collection.setIsPublic(Boolean.TRUE.equals(isPublic));
        return collectionRepository.save(collection);
    }

    /** 编辑收藏夹（仅本人） */
    @Transactional
    public NoteCollection update(Long userId, Long id, String name, String description, Boolean isPublic) {
        NoteCollection collection = getOwned(userId, id);
        if (name != null && !name.isBlank()) {
            if (name.trim().length() > 100) {
                throw new IllegalArgumentException("收藏夹名称过长");
            }
            collection.setName(name.trim());
        }
        if (description != null) {
            collection.setDescription(description.length() > 500 ? description.substring(0, 500) : description);
        }
        if (isPublic != null) {
            collection.setIsPublic(isPublic);
        }
        return collectionRepository.save(collection);
    }

    /** 删除收藏夹（仅本人） */
    @Transactional
    public void delete(Long userId, Long id) {
        NoteCollection collection = getOwned(userId, id);
        collectionRepository.delete(collection);
    }

    /** 添加笔记（去重；封面为空时取第一篇笔记封面） */
    @Transactional
    public Map<String, Object> addNote(Long userId, Long collectionId, Long noteId) {
        // COLL-1 修复：用悲观锁序列化并发添加，防止 noteIds 读-改-写丢失更新
        NoteCollection collection = getOwnedForUpdate(userId, collectionId);
        if (noteId == null) {
            throw new IllegalArgumentException("笔记 id 不能为空");
        }
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new IllegalArgumentException("笔记不存在"));
        if (Boolean.TRUE.equals(note.getHidden()) || !"published".equals(note.getStatus())) {
            throw new IllegalArgumentException("该笔记已下架，无法收藏");
        }

        List<Long> ids = parseIds(collection.getNoteIds());
        boolean added = false;
        if (!ids.contains(noteId)) {
            ids.add(noteId);
            added = true;
            if (collection.getCoverImage() == null || collection.getCoverImage().isBlank()) {
                collection.setCoverImage(note.getCover());
            }
            collection.setNoteIds(toJson(ids));
            collectionRepository.save(collection);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("added", added);
        result.put("count", ids.size());
        return result;
    }

    /** 移除笔记（去重后保存） */
    @Transactional
    public Map<String, Object> removeNote(Long userId, Long collectionId, Long noteId) {
        // COLL-1 修复：用悲观锁序列化并发移除
        NoteCollection collection = getOwnedForUpdate(userId, collectionId);
        if (noteId == null) {
            throw new IllegalArgumentException("笔记 id 不能为空");
        }
        List<Long> ids = parseIds(collection.getNoteIds());
        boolean removed = ids.remove(noteId);
        if (removed) {
            collection.setNoteIds(toJson(ids));
            collectionRepository.save(collection);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("removed", removed);
        result.put("count", ids.size());
        return result;
    }

    /** 我的收藏夹列表（含笔记数） */
    public List<Map<String, Object>> listMy(Long userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (NoteCollection c : collectionRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            out.add(brief(c));
        }
        return out;
    }

    /** 公开收藏夹列表（可关键字搜索） */
    public Page<NoteCollection> publicList(String keyword, Pageable pageable) {
        if (keyword != null && !keyword.isBlank()) {
            return collectionRepository
                    .findByIsPublicTrueAndNameContainingIgnoreCaseOrderByCreatedAtDesc(keyword.trim(), pageable);
        }
        return collectionRepository.findByIsPublicTrueOrderByCreatedAtDesc(pageable);
    }

    /** 收藏夹详情：公开的任何人可看，私有的仅本人；附笔记摘要 */
    public Map<String, Object> detail(Long userId, Long id) {
        NoteCollection collection = collectionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("收藏夹不存在"));
        boolean isOwner = userId != null && userId.equals(collection.getUserId());
        if (!Boolean.TRUE.equals(collection.getIsPublic()) && !isOwner) {
            throw new IllegalArgumentException("收藏夹不存在");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("id", collection.getId());
        data.put("userId", collection.getUserId());
        data.put("name", collection.getName());
        data.put("description", collection.getDescription());
        data.put("coverImage", collection.getCoverImage());
        data.put("isPublic", collection.getIsPublic());
        data.put("isOwner", isOwner);
        data.put("createdAt", collection.getCreatedAt());

        // 笔记摘要：过滤已删除/被举报隐藏/未发布的笔记
        List<Map<String, Object>> notes = new ArrayList<>();
        List<Long> ids = parseIds(collection.getNoteIds());
        if (!ids.isEmpty()) {
            for (Note note : noteRepository.findByIdIn(ids)) {
                if (Boolean.TRUE.equals(note.getHidden()) || !"published".equals(note.getStatus())) {
                    continue;
                }
                Map<String, Object> summary = new HashMap<>();
                summary.put("id", note.getId());
                summary.put("title", note.getTitle());
                summary.put("cover", note.getCover());
                summary.put("tags", note.getTags());
                summary.put("likes", note.getLikes());
                summary.put("views", note.getViews());
                summary.put("createdAt", note.getCreatedAt());
                notes.add(summary);
            }
        }
        data.put("notes", notes);
        data.put("noteCount", notes.size());
        return data;
    }

    /** 获取本人收藏夹（校验归属） */
    private NoteCollection getOwned(Long userId, Long id) {
        NoteCollection collection = collectionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("收藏夹不存在"));
        if (userId == null || !userId.equals(collection.getUserId())) {
            throw new IllegalArgumentException("无权操作该收藏夹");
        }
        return collection;
    }

    /** COLL-1 修复：悲观锁版本的 getOwned，用于 addNote/removeNote 防并发丢失更新 */
    private NoteCollection getOwnedForUpdate(Long userId, Long id) {
        NoteCollection collection = collectionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("收藏夹不存在"));
        if (userId == null || !userId.equals(collection.getUserId())) {
            throw new IllegalArgumentException("无权操作该收藏夹");
        }
        return collection;
    }

    /** 解析 noteIds JSON 数组 */
    private List<Long> parseIds(String noteIds) {
        if (noteIds == null || noteIds.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<Long> ids = objectMapper.readValue(noteIds, new TypeReference<List<Long>>() {});
            return ids != null ? new ArrayList<>(ids) : new ArrayList<>();
        } catch (Exception e) {
            log.warn("收藏夹 noteIds 解析失败: {}", noteIds);
            return new ArrayList<>();
        }
    }

    /** 序列化 noteIds JSON（写入失败仅可能为序列化异常，转为运行时异常） */
    private String toJson(List<Long> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("收藏数据序列化失败", e);
        }
    }

    /** 列表项摘要 */
    private Map<String, Object> brief(NoteCollection c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("description", c.getDescription());
        m.put("coverImage", c.getCoverImage());
        m.put("isPublic", c.getIsPublic());
        m.put("noteCount", parseIds(c.getNoteIds()).size());
        m.put("createdAt", c.getCreatedAt());
        return m;
    }
}
