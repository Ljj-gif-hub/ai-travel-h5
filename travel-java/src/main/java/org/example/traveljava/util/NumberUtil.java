package org.example.traveljava.util;

/**
 * CTRL-2 修复：Controller 层安全数值解析工具。
 * 旧代码直接 `((Number) body.get("x")).intValue()`——请求体传字符串 "3" 即抛 ClassCastException 500。
 * 统一改为：兼容 Number 与数字字符串，解析失败回退默认值，绝不抛 CCE。
 */
public final class NumberUtil {

    private NumberUtil() {
    }

    public static int toInt(Object value, int defaultValue) {
        if (value instanceof Number n) return n.intValue();
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static long toLong(Object value, long defaultValue) {
        if (value instanceof Number n) return n.longValue();
        if (value == null) return defaultValue;
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static double toDouble(Object value, double defaultValue) {
        if (value instanceof Number n) return n.doubleValue();
        if (value == null) return defaultValue;
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
