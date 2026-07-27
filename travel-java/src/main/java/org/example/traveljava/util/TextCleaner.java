package org.example.traveljava.util;

import java.util.regex.Pattern;

/**
 * AI 流式输出文本清洗工具
 * 清除大模型内部结构化分割符号、控制字符、重复空格
 */
public final class TextCleaner {

    private TextCleaner() {}

    // AI 模型常见的结构化占位符
    private static final Pattern DIRTY_MARKERS = Pattern.compile(
        "[\\|△▲▼▽◆◇▪▫•◾★☆✧✦→←↑↓↔⇒⇐⇑⇓├└│─━═▬►◄]");

    // 不可见控制字符（保留 \n）
    private static final Pattern CONTROL_CHARS = Pattern.compile(
        "[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    // 法语/特殊 Unicode 片段中夹杂的占位标记
    private static final Pattern FRENCH_ARTIFACTS = Pattern.compile(
        "[\\\\u0000-\\\\u001F]");

    // 连续 3 个以上空格
    private static final Pattern MULTI_SPACE = Pattern.compile(" {3,}");

    // 连续 3 个以上换行
    private static final Pattern MULTI_NEWLINE = Pattern.compile("\\n{4,}");

    /**
     * 清洗 AI 流式输出的单个分片
     * @param raw 原始分片文本
     * @return 清洗后的干净文本；全空时返回空串
     */
    public static String cleanChunk(String raw) {
        if (raw == null || raw.isEmpty()) return "";

        String cleaned = raw;

        // 1. 移除不可见控制字符（保留换行 \n）
        cleaned = CONTROL_CHARS.matcher(cleaned).replaceAll("");

        // 2. 移除 AI 结构化分割符号
        cleaned = DIRTY_MARKERS.matcher(cleaned).replaceAll("");

        // 3. 移除 Unicode 转义残留
        cleaned = FRENCH_ARTIFACTS.matcher(cleaned).replaceAll("");

        // 4. 压缩连续空格
        cleaned = MULTI_SPACE.matcher(cleaned).replaceAll(" ");

        // 5. 压缩连续空行
        cleaned = MULTI_NEWLINE.matcher(cleaned).replaceAll("\n\n\n");

        // 6. 首尾去空格
        cleaned = cleaned.trim();

        return cleaned;
    }

    /**
     * 判断清洗后是否还有有效内容
     */
    public static boolean hasContent(String cleaned) {
        return cleaned != null && !cleaned.isBlank();
    }
}
