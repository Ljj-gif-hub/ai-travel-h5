package org.example.traveljava.controller;

import org.example.traveljava.annotation.RateLimit;
import org.example.traveljava.service.VoiceToTextService;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 语音识别控制器
 * 提供语音上传并转换为文字的功能
 * 用于旅行规划中的语音输入场景（如语音搜索目的地、语音录入偏好等）
 */
@RestController
@RequestMapping("/api/voice")
public class VoiceController {

    private static final Logger log = LoggerFactory.getLogger(VoiceController.class);

    private final VoiceToTextService voiceToTextService;

    public VoiceController(VoiceToTextService voiceToTextService) {
        this.voiceToTextService = voiceToTextService;
    }

    /**
     * 语音转文字接口
     * 接收音频文件上传，返回识别后的文本内容
     *
     * @param file 音频文件（支持 wav、mp3、pcm 等格式）
     * @return 包含转写文本的Result
     */
    @PostMapping("/transcribe")
    @RateLimit(max = 10, duration = 60, key = "voice_transcribe")
    public Result<Map<String, Object>> transcribe(@RequestParam("file") MultipartFile file) {
        log.info("语音转文字请求：fileName={}, fileSize={}, contentType={}",
                file != null ? file.getOriginalFilename() : "null",
                file != null ? file.getSize() : 0,
                file != null ? file.getContentType() : "null");

        // 参数校验
        if (file == null || file.isEmpty()) {
            log.warn("语音转文字：上传文件为空");
            return Result.fail("请上传音频文件");
        }

        // 提取文件格式
        String originalFilename = file.getOriginalFilename();
        String format = "wav"; // 默认格式
        if (originalFilename != null && originalFilename.contains(".")) {
            format = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        }

        // 格式校验
        if (!isSupportedFormat(format)) {
            log.warn("不支持的音频格式：{}", format);
            return Result.fail("不支持的音频格式：" + format + "。支持的格式：wav, mp3, pcm, amr, flac");
        }

        try {
            // 读取音频字节数据
            byte[] audioData = file.getBytes();
            log.info("成功读取音频数据：{} bytes", audioData.length);

            // 校验音频数据有效性
            if (!voiceToTextService.validateAudioData(audioData, format)) {
                log.warn("音频数据校验失败：格式={}, 大小={}bytes", format, audioData.length);
                // 校验失败不阻断流程，仍尝试转写
            }

            // 执行语音转文字
            String text = voiceToTextService.transcribe(audioData, format);

            // 构建返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("text", text);
            result.put("format", format);
            result.put("fileSize", audioData.length);
            result.put("fileName", originalFilename);

            log.info("语音转文字完成：识别文本长度={}", text != null ? text.length() : 0);
            return Result.ok(result);

        } catch (IOException e) {
            log.error("读取音频文件失败", e);
            return Result.fail("读取音频文件失败，请重试");
        } catch (Exception e) {
            log.error("语音转文字处理异常", e);
            return Result.fail("语音识别处理失败，请稍后重试");
        }
    }

    /**
     * 语音服务健康检查接口
     * 用于前端检测语音服务是否可用
     */
    @GetMapping("/health")
    @RateLimit(max = 60, duration = 60, key = "voice_health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("service", "voice-to-text");
        result.put("status", "available");
        result.put("mode", "mock");
        result.put("supportedFormats", "wav, mp3, pcm, amr, flac");
        result.put("maxFileSize", "10MB");
        result.put("note", "当前为占位实现，需配置真实语音识别API密钥后提供完整功能");
        return Result.ok(result);
    }

    /**
     * 检查音频格式是否受支持
     */
    private boolean isSupportedFormat(String format) {
        if (format == null) {
            return false;
        }
        return switch (format.toLowerCase()) {
            case "wav", "mp3", "pcm", "amr", "flac", "m4a", "aac", "ogg" -> true;
            default -> false;
        };
    }
}
