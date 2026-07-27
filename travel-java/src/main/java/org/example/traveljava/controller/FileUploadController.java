package org.example.traveljava.controller;

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
        ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".svg", ".ico"
    );
    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024;
    private static final long MAX_VIDEO_SIZE = 1024L * 1024 * 1024;

    private final Path uploadDir;

    public FileUploadController() {
        this.uploadDir = Paths.get(System.getProperty("user.dir"), "uploads").toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            log.error("创建上传目录失败", e);
        }
    }

    /**
     * 文件上传
     */
    @PostMapping("/api/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.fail("请选择文件");
        }

        String contentType = file.getContentType();
        long size = file.getSize();

        // 先通过 MIME 类型判断，再通过扩展名兜底（部分环境 MIME 可能为 application/octet-stream）
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf(".")).toLowerCase();
        }

        boolean isImage = IMAGE_TYPES.contains(contentType) || IMAGE_EXTENSIONS.contains(ext);
        boolean isVideo = VIDEO_TYPES.contains(contentType) || VIDEO_EXTENSIONS.contains(ext);

        if (!isImage && !isVideo) {
            return Result.fail("不支持的文件类型：" + contentType + "，扩展名：" + ext);
        }
        // 如果 MIME 和扩展名冲突（如图片 MIME 但视频扩展名），优先信任 MIME
        if (IMAGE_TYPES.contains(contentType) && VIDEO_EXTENSIONS.contains(ext)) {
            isVideo = false;
            isImage = true;
        }
        if (VIDEO_TYPES.contains(contentType) && IMAGE_EXTENSIONS.contains(ext)) {
            isImage = false;
            isVideo = true;
        }
        if (isImage && size > MAX_IMAGE_SIZE) {
            return Result.fail("图片大小不能超过10MB");
        }
        if (isVideo && size > MAX_VIDEO_SIZE) {
            return Result.fail("视频大小不能超过1GB");
        }

        try {
            String newName = UUID.randomUUID().toString() + ext;

            Path targetPath = uploadDir.resolve(newName);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

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

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filePath.getFileName() + "\"")
                    .body(resource);

        } catch (IOException e) {
            log.error("文件访问失败：{}", filename, e);
            return ResponseEntity.notFound().build();
        }
    }
}
