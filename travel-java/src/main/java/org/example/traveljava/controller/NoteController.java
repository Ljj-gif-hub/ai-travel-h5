package org.example.traveljava.controller;

import org.example.traveljava.entity.Note;
import org.example.traveljava.entity.NoteLike;
import org.example.traveljava.entity.User;
import org.example.traveljava.repository.NoteLikeRepository;
import org.example.traveljava.repository.UserRepository;
import org.example.traveljava.service.NoteService;
import org.example.traveljava.util.AuthUtils;
import org.example.traveljava.util.JwtUtil;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notes")
@io.swagger.v3.oas.annotations.tags.Tag(name = "社交")
public class NoteController {

    private static final Logger log = LoggerFactory.getLogger(NoteController.class);

    private final NoteService noteService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final NoteLikeRepository noteLikeRepository;

    public NoteController(NoteService noteService, JwtUtil jwtUtil, UserRepository userRepository,
                          NoteLikeRepository noteLikeRepository) {
        this.noteService = noteService;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.noteLikeRepository = noteLikeRepository;
    }

    /**
     * 社区发现页：分页获取所有用户已发布的游记（无需登录也可浏览）
     * 附带作者信息（昵称、头像、userId，供前端关注使用）和当前用户是否已点赞
     * 批量查询作者与点赞，消除 N+1
     */
    @GetMapping
    public Result<Map<String, Object>> getAllNotes(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            final Long currentUserId = resolveOptionalUserId(authHeader);
            if (page < 1) page = 1;
            if (size < 1) size = 10;
            if (size > 50) size = 50;

            Page<Note> notesPage = noteService.getAllPublishedNotes(page, size);
            List<Note> notes = notesPage.getContent();
            long total = notesPage.getTotalElements();

            // 批量作者信息
            List<Long> authorIds = notes.stream().map(Note::getUserId).distinct().toList();
            Map<Long, User> authorMap = authorIds.isEmpty() ? Map.of()
                    : userRepository.findAllById(authorIds).stream()
                            .collect(Collectors.toMap(User::getId, u -> u));

            // 批量点赞状态
            List<Long> noteIds = notes.stream().map(Note::getId).toList();
            Set<Long> likedNoteIds = currentUserId == null || noteIds.isEmpty() ? Set.of()
                    : noteLikeRepository.findByNoteIdInAndUserId(noteIds, currentUserId).stream()
                            .map(NoteLike::getNoteId)
                            .collect(Collectors.toSet());

            List<Map<String, Object>> list = notes.stream().map(note -> {
                Map<String, Object> item = new HashMap<>();
                item.put("id", note.getId());
                item.put("userId", note.getUserId()); // 作者 ID（前端关注用）
                item.put("title", note.getTitle());
                item.put("content", note.getContent());
                item.put("cover", note.getCover());
                if (note.getTags() != null && !note.getTags().isEmpty()) {
                    item.put("tags", Arrays.asList(note.getTags().split(",")));
                } else {
                    item.put("tags", Collections.emptyList());
                }
                item.put("views", note.getViews());
                item.put("likes", note.getLikes());
                item.put("comments", note.getComments());
                item.put("date", note.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                User author = authorMap.get(note.getUserId());
                if (author != null) {
                    item.put("authorName", author.getNickname() != null ? author.getNickname() : author.getUsername());
                    item.put("authorAvatar", author.getAvatar());
                }
                item.put("isLiked", currentUserId != null && likedNoteIds.contains(note.getId()));
                return item;
            }).toList();

            Map<String, Object> result = new HashMap<>();
            result.put("list", list);
            result.put("total", total);
            result.put("page", page);
            result.put("size", size);
            result.put("hasMore", (long) page * size < total);
            return Result.ok(result);
        } catch (Exception e) {
            log.error("获取游记列表失败", e);
            return Result.fail("获取游记列表失败");
        }
    }

    @GetMapping("/my")
    public Result<List<Map<String, Object>>> getMyNotes(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            List<Note> notes = noteService.getNotes(userId);

            List<Map<String, Object>> result = notes.stream().map(note -> {
                Map<String, Object> item = new HashMap<>();
                item.put("id", note.getId());
                item.put("title", note.getTitle());
                item.put("content", note.getContent());
                item.put("cover", note.getCover());
                if (note.getTags() != null && !note.getTags().isEmpty()) {
                    item.put("tags", Arrays.asList(note.getTags().split(",")));
                } else {
                    item.put("tags", Collections.emptyList());
                }
                item.put("views", note.getViews());
                item.put("likes", note.getLikes());
                item.put("comments", note.getComments());
                item.put("date", note.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                return item;
            }).toList();

            return Result.ok(result);
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (Exception e) {
            log.error("获取我的游记失败", e);
            return Result.fail("获取我的游记失败");
        }
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> getNoteDetail(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long currentUserId = jwtUtil.extractUserId(token);
            Note note = noteService.getNoteById(id);
            // 【修复】禁止未登录用户读取游记详情
            if (currentUserId == null) {
                return Result.fail("请先登录");
            }
            Note viewed = noteService.incrementViews(id);

            Map<String, Object> result = new HashMap<>();
            result.put("id", note.getId());
            result.put("userId", note.getUserId()); // 作者 ID（前端关注用）
            result.put("title", note.getTitle());
            result.put("content", note.getContent());
            result.put("cover", note.getCover());
            // tags 存为逗号分隔字符串，返回数组给前端
            if (note.getTags() != null && !note.getTags().isEmpty()) {
                result.put("tags", Arrays.asList(note.getTags().split(",")));
            } else {
                result.put("tags", Collections.emptyList());
            }
            result.put("views", viewed.getViews()); // 原子递增后的浏览量
            result.put("likes", note.getLikes());
            result.put("comments", note.getComments());
            result.put("date", note.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            // 作者信息
            userRepository.findById(note.getUserId()).ifPresent(author -> {
                result.put("authorName", author.getNickname() != null ? author.getNickname() : author.getUsername());
                result.put("authorAvatar", author.getAvatar());
            });
            // 当前用户是否点赞
            result.put("isLiked", noteService.isLikedByUser(note.getId(), currentUserId));

            return Result.ok(result);
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (IllegalArgumentException e) {
            log.warn("获取游记详情失败：{}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("获取游记详情异常", e);
            return Result.fail("获取游记详情失败");
        }
    }

    @PostMapping
    public Result<Map<String, Object>> createNote(@RequestHeader("Authorization") String authHeader, @RequestBody Map<String, Object> params) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            Note note = noteService.createNote(userId, params);

            Map<String, Object> result = new HashMap<>();
            result.put("id", note.getId());
            result.put("title", note.getTitle());
            result.put("content", note.getContent());
            result.put("cover", note.getCover());

            return Result.ok(result);
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (IllegalArgumentException e) {
            log.warn("创建游记失败：{}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("创建游记异常", e);
            return Result.fail("创建游记失败");
        }
    }

    @PutMapping("/{id}")
    public Result<Map<String, Object>> updateNote(@RequestHeader("Authorization") String authHeader,
                                                  @PathVariable Long id,
                                                  @RequestBody Map<String, Object> params) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            Note note = noteService.updateNote(userId, id, params);

            Map<String, Object> result = new HashMap<>();
            result.put("id", note.getId());
            result.put("title", note.getTitle());
            result.put("content", note.getContent());
            result.put("cover", note.getCover());

            return Result.ok(result);
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (IllegalArgumentException e) {
            log.warn("更新游记失败：{}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("更新游记异常", e);
            return Result.fail("更新游记失败");
        }
    }

    @DeleteMapping("/{id}")
    public Result<String> deleteNote(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            noteService.deleteNote(userId, id);
            return Result.ok("删除成功");
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (IllegalArgumentException e) {
            log.warn("删除游记失败：{}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("删除游记异常", e);
            return Result.fail("删除游记失败");
        }
    }

    @PostMapping("/{id}/like")
    public Result<Map<String, Object>> likeNote(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            Map<String, Object> result = noteService.toggleLike(id, userId);
            return Result.ok(result);
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (IllegalArgumentException e) {
            log.warn("点赞失败：{}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("点赞异常", e);
            return Result.fail("点赞失败");
        }
    }

    @GetMapping("/count")
    public Result<Map<String, Object>> getNoteCount(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            Long userId = jwtUtil.extractUserId(token);

            Map<String, Object> result = new HashMap<>();
            result.put("count", noteService.getNoteCount(userId));

            return Result.ok(result);
        } catch (AuthUtils.AuthException e) {
            throw e; // let GlobalExceptionHandler return 401
        } catch (Exception e) {
            log.error("获取游记数量失败", e);
            return Result.fail("获取游记数量失败");
        }
    }

    /**
     * 可选认证：从 Authorization 头解析用户ID，失败时返回 null（游客身份）
     */
    private Long resolveOptionalUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        try {
            String token = authHeader.replace("Bearer ", "");
            return jwtUtil.extractUserId(token);
        } catch (Exception e) {
            log.debug("可选认证失败: {}", e.getMessage());
            return null;
        }
    }
}
