package org.example.traveljava.controller;

import org.example.traveljava.entity.TripTemplate;
import org.example.traveljava.service.TripTemplateService;
import org.example.traveljava.util.AuthUtils;
import org.example.traveljava.util.JwtUtil;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 【新功能】行程模板市场（/api/template）。
 * - GET  /api/template/market  模板列表（匿名可看）
 * - GET  /api/template/{id}    模板详情（匿名可看）
 * - POST /api/template/{id}/instantiate 实例化为自己的行程（登录）
 * - POST /api/template         创建模板（管理员）
 * - DELETE /api/template/{id}  下架模板（管理员）
 */
@RestController
@RequestMapping("/api/template")
@io.swagger.v3.oas.annotations.tags.Tag(name = "行程模板")
public class TripTemplateController {

    private static final Logger log = LoggerFactory.getLogger(TripTemplateController.class);

    private final TripTemplateService templateService;
    private final JwtUtil jwtUtil;

    public TripTemplateController(TripTemplateService templateService, JwtUtil jwtUtil) {
        this.templateService = templateService;
        this.jwtUtil = jwtUtil;
    }

    /** 模板市场（分页 + 关键字搜索） */
    @GetMapping("/market")
    public Result<Map<String, Object>> market(@RequestParam(required = false) String keyword,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "12") int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        Page<TripTemplate> result = templateService.market(keyword, PageRequest.of(Math.max(page, 0), safeSize));
        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getContent());
        data.put("total", result.getTotalElements());
        data.put("page", result.getNumber());
        data.put("size", result.getSize());
        return Result.ok(data);
    }

    /** 模板详情 */
    @GetMapping("/{id}")
    public Result<TripTemplate> detail(@PathVariable Long id) {
        try {
            return Result.ok(templateService.detail(id));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    /** 实例化模板（登录用户，下载数 +1） */
    @PostMapping("/{id}/instantiate")
    public Result<Map<String, Object>> instantiate(@RequestHeader("Authorization") String authHeader,
                                                   @PathVariable Long id) {
        Long userId = AuthUtils.requireUserId(authHeader, jwtUtil);
        try {
            return Result.ok(templateService.instantiate(userId, id));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("实例化模板失败: templateId={}", id, e);
            return Result.fail("实例化失败，请稍后重试");
        }
    }

    /** 创建模板（管理员） */
    @PostMapping
    public Result<TripTemplate> create(@RequestHeader("Authorization") String authHeader,
                                       @RequestBody TripTemplate template) {
        Long adminId = AuthUtils.requireAdmin(authHeader, jwtUtil);
        if (template.getName() == null || template.getName().isBlank()) {
            return Result.fail("模板名称不能为空");
        }
        if (template.getDestination() == null || template.getDestination().isBlank()) {
            return Result.fail("目的地不能为空");
        }
        if (template.getPlanJson() == null || template.getPlanJson().isBlank()) {
            return Result.fail("模板行程内容不能为空");
        }
        try {
            return Result.ok(templateService.create(adminId, template));
        } catch (Exception e) {
            log.error("创建模板失败", e);
            return Result.fail("创建失败，请稍后重试");
        }
    }

    /** 下架模板（管理员，软删除 + 审计） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@RequestHeader("Authorization") String authHeader,
                               @PathVariable Long id) {
        Long adminId = AuthUtils.requireAdmin(authHeader, jwtUtil);
        try {
            templateService.delete(adminId, id);
            return Result.ok(null);
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("删除模板失败: templateId={}", id, e);
            return Result.fail("删除失败，请稍后重试");
        }
    }
}
