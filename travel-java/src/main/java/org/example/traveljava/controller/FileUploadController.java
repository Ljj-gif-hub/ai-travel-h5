package org.example.traveljava.controller;

import org.example.traveljava.annotation.RateLimit;
import org.example.traveljava.util.AuthUtils;
import org.example.traveljava.util.JwtUtil;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "系统")
public class FileUploadController {

    private static final Logger log = LoggerFactory.getLogger(FileUploadController.class);
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp");
    private static final Set<String> VIDEO_TYPES = Set.of(
        "video/mp4", "video/webm", "video/quicktime", "video/x-msvideo",
        "video/x-matroska", "video/ogg", "video/3gpp", "video/mpeg",
        "video/avi", "video/msvideo", "video/mp2t"
    );
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
        ".mp4", ".webm", ".mov", ".avi", ".mkv", ".ogv", ".ogg",
        ".3gp", ".3gpp", ".mpeg", ".mpg", ".ts", ".wmv", ".flv"
    );
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
        ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp"
    );
    /** MIME → 落盘标准扩展名（落盘扩展名一律由服务端决定，绝不沿用客户端可控的原始扩展名，防存储型 XSS） */
    private static final Map<String, String> MIME_TO_EXT = Map.of(
        "image/jpeg", ".jpg",
        "image/png", ".png",
        "image/gif", ".gif",
        "image/webp", ".webp",
        "image/bmp", ".bmp",
        "video/mp4", ".mp4",
        "video/webm", ".webm",
        "video/quicktime", ".mov"
    );
    /** 可执行/脚本类类型，即使被伪装成图片 MIME 也直接拒绝 */
    private static final Set<String> DANGEROUS_TYPES = Set.of(
        "text/html", "image/svg+xml", "application/xhtml+xml",
        "application/xml", "text/xml", "application/javascript", "text/javascript"
    );
    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024;
    // L-UPLOAD-2 修复：视频上限与框架 multipart.max-file-size（50MB，内存安全考量）对齐。
    // 此前声明 1GB 永远不可达——50MB 以上请求在进入控制器前就被 Spring 拒绝（MaxUploadSizeExceededException 统一兜底），
    // 保留 1GB 会让前端按错误上限提示、控制器自身的大小校验形同虚设。
    private static final long MAX_VIDEO_SIZE = 200L * 1024 * 1024;

    private final Path uploadDir;
    private final JwtUtil jwtUtil;

    public FileUploadController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
        this.uploadDir = Paths.get(System.getProperty("user.dir"), "uploads").toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            log.error("创建上传目录失败", e);
        }
    }

    /**
     * 文件上传（需登录）
     */
    @PostMapping("/api/upload")
    @RateLimit(max = 30, duration = 60, key = "file_upload")
    public Result<Map<String, Object>> upload(@RequestHeader("Authorization") String authHeader,
                                              @RequestParam("file") MultipartFile file) {
        AuthUtils.requireUserId(authHeader, jwtUtil);
        if (file == null || file.isEmpty()) {
            return Result.fail("请选择文件");
        }

        String contentType = file.getContentType();
        long size = file.getSize();

        // 直接拒绝可执行/脚本类类型（即使客户端把 MIME 伪装成图片）
        if (contentType != null && DANGEROUS_TYPES.contains(contentType.toLowerCase())) {
            return Result.fail("不支持的文件类型");
        }

        // 先通过 MIME 类型判断，再通过扩展名兜底（部分环境 MIME 可能为 application/octet-stream）
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf(".")).toLowerCase();
        }

        // UPLOAD-1 修复：part 无 Content-Type 时 getContentType() 返回 null，Set.of/Map.of 不允许 null
        boolean isImage = contentType != null && (IMAGE_TYPES.contains(contentType) || IMAGE_EXTENSIONS.contains(ext));
        boolean isVideo = contentType != null && (VIDEO_TYPES.contains(contentType) || VIDEO_EXTENSIONS.contains(ext));

        if (!isImage && !isVideo) {
            return Result.fail("不支持的文件类型：" + contentType + "，扩展名：" + ext);
        }
        // 如果 MIME 和扩展名冲突（如图片 MIME 但视频扩展名），优先信任 MIME
        if (contentType != null && IMAGE_TYPES.contains(contentType) && VIDEO_EXTENSIONS.contains(ext)) {
            isVideo = false;
            isImage = true;
        }
        if (contentType != null && VIDEO_TYPES.contains(contentType) && IMAGE_EXTENSIONS.contains(ext)) {
            isImage = false;
            isVideo = true;
        }
        if (isImage && size > MAX_IMAGE_SIZE) {
            return Result.fail("图片大小不能超过10MB");
        }
        if (isVideo && size > MAX_VIDEO_SIZE) {
            return Result.fail("视频大小不能超过200MB");
        }

        try {
            // 落盘扩展名由服务端按校验后的类型决定，绝不沿用客户端可控的原始扩展名
            // UPLOAD-1 修复：contentType 为 null 时 getOrDefault 传入 null 会对 Map.of 抛 NPE
            String storedExt = contentType != null ? MIME_TO_EXT.getOrDefault(contentType, isImage ? ".jpg" : ".mp4")
                    : (isImage ? ".jpg" : ".mp4");
            String newName = UUID.randomUUID().toString() + storedExt;

            Path targetPath = uploadDir.resolve(newName);
            // CTRL-5 修复：try-with-resources 关闭 MultipartFile 输入流，避免句柄泄漏
            try (var in = file.getInputStream()) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            String fileUrl = "/uploads/" + newName;

            Map<String, Object> result = new HashMap<>();
            result.put("url", fileUrl);
            result.put("type", isImage ? "image" : "video");
            result.put("size", size);
            result.put("name", originalName);

            log.info("文件上传成功：{} → {}", originalName, fileUrl);
            return Result.ok(result);

        } catch (IOException e) {
            log.error("文件上传失败", e);
            return Result.fail("文件上传失败，请重试");
        }
    }

    /**
     * 文件访问 — 走 /api/files/ 路径，与 /api 代理同通道，无需额外配置
     */
    @GetMapping("/api/files/{filename}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        try {
            Path filePath = uploadDir.resolve(filename).normalize();
            // 防止路径穿越攻击
            if (!filePath.startsWith(uploadDir)) {
                return ResponseEntity.notFound().build();
            }
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(filePath);
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            // SVG 等可在浏览器执行的类型强制附件下载，避免存储型 XSS（同源 inline 执行内嵌脚本）
            boolean dangerousInline = "image/svg+xml".equalsIgnoreCase(contentType)
                    || "text/html".equalsIgnoreCase(contentType)
                    || "application/xhtml+xml".equalsIgnoreCase(contentType);
            String disposition = dangerousInline ? "attachment" : "inline";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + filePath.getFileName() + "\"")
                    .body(resource);

        } catch (IOException e) {
            log.error("文件访问失败：{}", filename, e);
            return ResponseEntity.notFound().build();
        }
    }
}
