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

    // ---- HTML 存储安全（防存储型 XSS）：剥离脚本/事件属性，保留基础富文本 ----

    private static final Pattern SCRIPT_BLOCK = Pattern.compile("(?is)<script[^>]*>.*?</script>");
    private static final Pattern IFRAME_BLOCK = Pattern.compile("(?is)<iframe[^>]*>.*?</iframe>");
    private static final Pattern EMBED_BLOCK = Pattern.compile("(?is)<embed[^>]*>.*?</embed>");
    private static final Pattern OBJECT_BLOCK = Pattern.compile("(?is)<object[^>]*>.*?</object>");
    private static final Pattern SVG_BLOCK = Pattern.compile("(?is)<svg[^>]*>.*?</svg>");
    private static final Pattern STYLE_BLOCK = Pattern.compile("(?is)<style[^>]*>.*?</style>");
    private static final Pattern JAVASCRIPT_PROTO = Pattern.compile("(?i)javascript:");
    private static final Pattern EVENT_HANDLER = Pattern.compile("(?i)on\\w+\\s*=\\s*[\"']?[^\"'>]*[\"']?");

    /**
     * 用户提交的富文本存储前清洗，剥离脚本/事件/危险协议。
     * 与前端 filterXss 保持同一黑名单口径，作为 XSS 的纵深防御。
     */
    public static String sanitizeHtml(String html) {
        if (html == null) {
            return null;
        }
        String s = html;
        s = SCRIPT_BLOCK.matcher(s).replaceAll("");
        s = IFRAME_BLOCK.matcher(s).replaceAll("");
        s = EMBED_BLOCK.matcher(s).replaceAll("");
        s = OBJECT_BLOCK.matcher(s).replaceAll("");
        s = SVG_BLOCK.matcher(s).replaceAll("");
        s = STYLE_BLOCK.matcher(s).replaceAll("");
        s = JAVASCRIPT_PROTO.matcher(s).replaceAll("");
        s = EVENT_HANDLER.matcher(s).replaceAll("");
        return s;
    }
}
