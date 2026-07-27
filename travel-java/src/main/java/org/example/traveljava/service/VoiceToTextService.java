package org.example.traveljava.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 语音转文字服务
 * 提供将音频数据转换为文本的能力
 * 当前为占位实现，后续可接入真实语音识别API（如百度语音、讯飞等）
 */
@Service
public class VoiceToTextService {

    private static final Logger log = LoggerFactory.getLogger(VoiceToTextService.class);

    /**
     * 支持的最大音频数据大小：10MB
     */
    private static final int MAX_AUDIO_SIZE_BYTES = 10 * 1024 * 1024;

    /**
     * 将音频数据转写为文字
     * 当前为模拟实现，返回固定提示文本
     * 生产环境可对接以下语音识别API：
     * - 百度AI语音识别：https://ai.baidu.com/tech/speech
     * - 科大讯飞语音识别：https://www.xfyun.cn/services/voicedictation
     * - 阿里云语音识别：https://ai.aliyun.com/nls
     *
     * @param audioData 音频字节数组
     * @param format    音频格式（如 "wav"、"mp3"、"pcm"）
     * @return 识别出的文本内容
     */
    public String transcribe(byte[] audioData, String format) {
        log.info("收到语音转文字请求：format={}, 数据大小={} bytes",
                format, audioData != null ? audioData.length : 0);

        // 参数校验
        if (audioData == null || audioData.length == 0) {
            log.warn("音频数据为空");
            return "音频数据为空，请重新录制。";
        }

        if (audioData.length > MAX_AUDIO_SIZE_BYTES) {
            log.warn("音频数据过大：{} bytes，超过限制{} bytes",
                    audioData.length, MAX_AUDIO_SIZE_BYTES);
            return "音频文件过大，请缩短录音时长后重试。";
        }

        if (format == null || format.trim().isEmpty()) {
            log.warn("未指定音频格式");
            return "未指定音频格式，请选择有效的音频格式（wav/mp3/pcm）。";
        }

        // ============================================================
        // 占位实现：返回提示信息
        // 真实实现时，此处应：
        // 1. 将音频数据写入临时文件
        // 2. 调用语音识别API
        // 3. 解析API返回结果
        // 4. 返回识别文本
        // ============================================================
        String formatLower = format.toLowerCase().trim();
        log.info("语音转文字为占位实现，音频格式={}, 大小={}KB",
                formatLower, audioData.length / 1024);

        // 模拟：根据音频大小返回不同的提示
        if (audioData.length < 1024) {
            // 极小文件，可能是测试
            return "语音识别服务暂未配置真实的API密钥。"
                    + "请前往系统设置中配置语音识别服务商（百度/讯飞/阿里云）的API凭证。"
                    + "当前收到测试音频：" + audioData.length + " bytes。";
        }

        // 返回友好的占位提示
        return "语音识别服务暂未接入。收到音频数据："
                + String.format("%.1f", audioData.length / 1024.0) + "KB，"
                + "格式：" + formatLower + "。"
                + "请配置语音识别API密钥后重试。"
                + "支持的语音服务商：百度AI、科大讯飞、阿里云NLS。";
    }

    /**
     * 检查音频数据的基本有效性
     * 验证音频头部魔数（如WAV文件的RIFF头、MP3的同步字等）
     *
     * @param audioData 音频字节数组
     * @param format    音频格式
     * @return true表示音频数据格式正确
     */
    public boolean validateAudioData(byte[] audioData, String format) {
        if (audioData == null || audioData.length < 4 || format == null) {
            return false;
        }

        String formatLower = format.toLowerCase().trim();

        // 简单魔数校验
        switch (formatLower) {
            case "wav":
                // WAV 文件以 "RIFF" 开头
                return audioData[0] == 'R' && audioData[1] == 'I'
                        && audioData[2] == 'F' && audioData[3] == 'F';
            case "mp3":
                // MP3 文件以同步字 0xFF 0xFB（或 0xFF 0xFA、0xFF 0xF3 等）开头
                return (audioData[0] & 0xFF) == 0xFF
                        && ((audioData[1] & 0xE0) == 0xE0);
            case "pcm":
                // PCM 原始数据无头部标识，仅检查大小
                return audioData.length > 44;
            default:
                // 未知格式，仅检查非空
                log.debug("未识别的音频格式：{}，跳过魔数校验", formatLower);
                return audioData.length > 0;
        }
    }
}
