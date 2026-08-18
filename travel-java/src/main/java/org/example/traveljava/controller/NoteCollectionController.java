package org.example.traveljava.controller;

import org.example.traveljava.annotation.RateLimit;
import org.example.traveljava.entity.NoteCollection;
import org.example.traveljava.service.NoteCollectionService;
import org.example.traveljava.util.AuthUtils;
import org.example.traveljava.util.JwtUtil;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 【新功能】游记收藏夹接口。
 * - POST   /api/collection              创建
 * - PUT    /api/collection/{id}         编辑（本人）
 * - DELETE /api/collection/{id}         删除（本人）
 * - POST   /api/collection/{id}/notes       添加笔记（body: {noteId}，去重）
 * - DELETE /api/collection/{id}/notes/{noteId} 移除笔记
 * - GET    /api/collection/mine         我的收藏夹
 * - GET    /api/collection/public       公开收藏夹（keyword/page/size）
 * - GET    /api/collection/{id}         详情（附笔记摘要；私有仅本人可见）
 */
@RestController
@RequestMapping("/api/collection")
@io.swagger.v3.oas.annotations.tags.Tag(name = "收藏夹")
public class NoteCollectionController {

    private static final Logger log = LoggerFactory.getLogger(NoteCollectionController.class);

    private final NoteCollectionService collectionService;
    private final JwtUtil jwtUtil;

    public NoteCollectionController(NoteCollectionService collectionService, JwtUtil jwtUtil) {
        this.collectionService = collectionService;
        this.jwtUtil = jwtUtil;
    }

    /** 创建收藏夹 */
    @PostMapping("")
    @RateLimit(max = 10, duration = 60, key = "collection")
    public Result<NoteCollection> create(@RequestHeader("Authorization") String authHeader,
                                         @RequestBody Map<String, Object> body) {
        Long userId = AuthUtils.requireUserId(authHeader, jwtUtil);
        try {
            String name = body.get("name") != null ? String.valueOf(body.get("name")) : null;
            String description = body.get("description") != null ? String.valueOf(body.get("description")) : null;
            Boolean isPublic = body.get("isPublic") != null && Boolean.parseBoolean(String.valueOf(body.get("isPublic")));
            return Result.ok(collectionService.create(userId, name, description, isPublic));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("创建收藏夹失败", e);
            return Result.fail("创建失败，请稍后重试");
        }
    }

    /** 编辑收藏夹 */
    @PutMapping("/{id}")
    public Result<NoteCollection> update(@RequestHeader("Authorization") String authHeader,
                                         @PathVariable Long id,
                                         @RequestBody Map<String, Object> body) {
        Long userId = AuthUtils.requireUserId(authHeader, jwtUtil);
        try {
            String name = body.get("name") != null ? String.valueOf(body.get("name")) : null;
            String description = body.get("description") != null ? String.valueOf(body.get("description")) : null;
            Boolean isPublic = body.containsKey("isPublic")
                    ? Boolean.parseBoolean(String.valueOf(body.get("isPublic"))) : null;
            return Result.ok(collectionService.update(userId, id, name, description, isPublic));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("编辑收藏夹失败: id={}", id, e);
            return Result.fail("编辑失败，请稍后重试");
        }
    }

    /** 删除收藏夹 */
    @DeleteMapping("/{id}")
    public Result<String> delete(@RequestHeader("Authorization") String authHeader,
                                 @PathVariable Long id) {
        Long userId = AuthUtils.requireUserId(authHeader, jwtUtil);
        try {
            collectionService.delete(userId, id);
            return Result.ok("删除成功");
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("删除收藏夹失败: id={}", id, e);
            return Result.fail("删除失败，请稍后重试");
        }
    }

    /** 添加笔记 */
    @PostMapping("/{id}/notes")
    @RateLimit(max = 20, duration = 60, key = "collection_note")
    public Result<Map<String, Object>> addNote(@RequestHeader("Authorization") String authHeader,
                                               @PathVariable Long id,
                                               @RequestBody Map<String, Object> body) {
        Long userId = AuthUtils.requireUserId(authHeader, jwtUtil);
        try {
            Long noteId = body.get("noteId") != null ? ((Number) body.get("noteId")).longValue() : null;
            return Result.ok(collectionService.addNote(userId, id, noteId));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("添加笔记失败: collectionId={}", id, e);
            return Result.fail("添加失败，请稍后重试");
        }
    }

    /** 移除笔记 */
    @DeleteMapping("/{id}/notes/{noteId}")
    public Result<Map<String, Object>> removeNote(@RequestHeader("Authorization") String authHeader,
                                                  @PathVariable Long id,
                                                  @PathVariable Long noteId) {
        Long userId = AuthUtils.requireUserId(authHeader, jwtUtil);
        try {
            return Result.ok(collectionService.removeNote(userId, id, noteId));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("移除笔记失败: collectionId={}, noteId={}", id, noteId, e);
            return Result.fail("移除失败，请稍后重试");
        }
    }

    /** 我的收藏夹 */
    @GetMapping("/mine")
    public Result<List<Map<String, Object>>> listMy(@RequestHeader("Authorization") String authHeader) {
        Long userId = AuthUtils.requireUserId(authHeader, jwtUtil);
        try {
            return Result.ok(collectionService.listMy(userId));
        } catch (Exception e) {
            log.error("查询收藏夹失败", e);
            return Result.fail("查询失败，请稍后重试");
        }
    }

    /** 公开收藏夹列表（keyword/page/size） */
    @GetMapping("/public")
    public Result<Map<String, Object>> publicList(@RequestParam(required = false) String keyword,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        try {
            int safeSize = Math.min(Math.max(size, 1), 100);
            Page<NoteCollection> result = collectionService.publicList(keyword,
                    PageRequest.of(Math.max(page, 0), safeSize));
            Map<String, Object> data = new HashMap<>();
            data.put("list", result.getContent());
            data.put("total", result.getTotalElements());
            data.put("page", result.getNumber());
            data.put("size", result.getSize());
            return Result.ok(data);
        } catch (Exception e) {
            log.error("查询公开收藏夹失败", e);
            return Result.fail("查询失败，请稍后重试");
        }
    }

    /** 收藏夹详情（附笔记摘要；私有仅本人可见，匿名可看公开） */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                              @PathVariable Long id) {
        Long userId = AuthUtils.optionalUserId(authHeader, jwtUtil);
        try {
            return Result.ok(collectionService.detail(userId, id));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("查询收藏夹详情失败: id={}", id, e);
            return Result.fail("查询失败，请稍后重试");
        }
    }
}
