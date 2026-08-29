package org.example.traveljava.util;

import java.util.Set;

/**
 * 坐标系转换工具类
 *
 * 支持三种常用坐标系之间的转换：
 * - WGS-84：GPS 原始坐标，国际标准（Google Earth、OSM 使用）
 * - GCJ-02：国测局坐标（火星坐标），高德、腾讯、Google 中国使用
 * - BD-09：百度坐标系，百度地图专用（在 GCJ-02 基础上二次加密）
 *
 * 转换公式参考：
 * - WGS-84 ↔ GCJ-02：基于国测局公开的偏移算法
 * - GCJ-02 ↔ BD-09：百度官方公布的转换公式
 */
public final class CoordinateUtil {

    private static final double PI = Math.PI;
    private static final double X_PI = PI * 3000.0 / 180.0;
    /** WGS-84 椭球长半轴 */
    private static final double A = 6378245.0;
    /** 扁率 */
    private static final double EE = 0.00669342162296594323;

    private CoordinateUtil() {
        // 工具类，禁止实例化
    }

    // ==================== WGS-84 ↔ GCJ-02 ====================

    /**
     * WGS-84 转 GCJ-02（火星坐标）
     * @param wgsLat WGS-84 纬度
     * @param wgsLng WGS-84 经度
     * @return [gcjLat, gcjLng]
     */
    public static double[] wgs84ToGcj02(double wgsLat, double wgsLng) {
        if (outOfChina(wgsLat, wgsLng)) {
            return new double[]{wgsLat, wgsLng};
        }
        double dLat = transformLat(wgsLng - 105.0, wgsLat - 35.0);
        double dLng = transformLng(wgsLng - 105.0, wgsLat - 35.0);
        double radLat = wgsLat / 180.0 * PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI);
        dLng = (dLng * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI);
        return new double[]{wgsLat + dLat, wgsLng + dLng};
    }

    /**
     * GCJ-02 转 WGS-84
     * @param gcjLat GCJ-02 纬度
     * @param gcjLng GCJ-02 经度
     * @return [wgsLat, wgsLng]
     */
    public static double[] gcj02ToWgs84(double gcjLat, double gcjLng) {
        if (outOfChina(gcjLat, gcjLng)) {
            return new double[]{gcjLat, gcjLng};
        }
        double[] delta = wgs84ToGcj02(gcjLat, gcjLng);
        return new double[]{gcjLat * 2 - delta[0], gcjLng * 2 - delta[1]};
    }

    // ==================== GCJ-02 ↔ BD-09 ====================

    /**
     * GCJ-02 转 BD-09（百度坐标）
     * @param gcjLat GCJ-02 纬度
     * @param gcjLng GCJ-02 经度
     * @return [bdLat, bdLng]
     */
    public static double[] gcj02ToBd09(double gcjLat, double gcjLng) {
        double z = Math.sqrt(gcjLng * gcjLng + gcjLat * gcjLat) + 0.00002 * Math.sin(gcjLat * X_PI);
        double theta = Math.atan2(gcjLat, gcjLng) + 0.000003 * Math.cos(gcjLng * X_PI);
        return new double[]{z * Math.sin(theta) + 0.006, z * Math.cos(theta) + 0.0065};
    }

    /**
     * BD-09 转 GCJ-02（火星坐标）
     * @param bdLat BD-09 纬度
     * @param bdLng BD-09 经度
     * @return [gcjLat, gcjLng]
     */
    public static double[] bd09ToGcj02(double bdLat, double bdLng) {
        double x = bdLng - 0.0065;
        double y = bdLat - 0.006;
        double z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * X_PI);
        double theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * X_PI);
        return new double[]{z * Math.sin(theta), z * Math.cos(theta)};
    }

    // ==================== WGS-84 ↔ BD-09 (组合变换) ====================

    /**
     * WGS-84 转 BD-09
     */
    public static double[] wgs84ToBd09(double wgsLat, double wgsLng) {
        double[] gcj = wgs84ToGcj02(wgsLat, wgsLng);
        return gcj02ToBd09(gcj[0], gcj[1]);
    }

    /**
     * BD-09 转 WGS-84
     */
    public static double[] bd09ToWgs84(double bdLat, double bdLng) {
        double[] gcj = bd09ToGcj02(bdLat, bdLng);
        return gcj02ToWgs84(gcj[0], gcj[1]);
    }

    // ==================== 便捷方法 ====================

    private static final Set<String> SUPPORTED_COORDS = Set.of("wgs84", "gcj02", "bd09");

    /**
     * 将坐标转换为目标坐标系
     * @param lat 源纬度
     * @param lng 源经度
     * @param from 源坐标系 ("wgs84" / "gcj02" / "bd09")
     * @param to   目标坐标系 ("wgs84" / "gcj02" / "bd09")
     * @return [targetLat, targetLng]
     * @throws IllegalArgumentException 参数为空 / 坐标系不支持 / 经纬度越界时抛出（不再静默当作 gcj02）
     */
    public static double[] convert(double lat, double lng, String from, String to) {
        if (from == null || to == null || from.trim().isEmpty() || to.trim().isEmpty()) {
            throw new IllegalArgumentException("坐标系参数不能为空");
        }
        String f = from.trim().toLowerCase();
        String t = to.trim().toLowerCase();
        if (!SUPPORTED_COORDS.contains(f) || !SUPPORTED_COORDS.contains(t)) {
            throw new IllegalArgumentException("不支持的坐标系: " + from + " → " + to);
        }
        if (Double.isNaN(lat) || Double.isNaN(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("经纬度超出合法范围: lat=" + lat + ", lng=" + lng);
        }

        if (f.equals(t)) {
            return new double[]{lat, lng};
        }

        // 统一先转到 GCJ-02
        double gcjLat, gcjLng;
        switch (f) {
            case "wgs84":
                double[] gcj = wgs84ToGcj02(lat, lng);
                gcjLat = gcj[0]; gcjLng = gcj[1];
                break;
            case "bd09":
                double[] gcj2 = bd09ToGcj02(lat, lng);
                gcjLat = gcj2[0]; gcjLng = gcj2[1];
                break;
            default: // gcj02
                gcjLat = lat; gcjLng = lng;
        }

        // 从 GCJ-02 转到目标
        switch (t) {
            case "wgs84":
                return gcj02ToWgs84(gcjLat, gcjLng);
            case "bd09":
                return gcj02ToBd09(gcjLat, gcjLng);
            default: // gcj02
                return new double[]{gcjLat, gcjLng};
        }
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 判断经纬度是否在中国境外（境外无需偏移）
     */
    private static boolean outOfChina(double lat, double lng) {
        return !isInChina(lat, lng);
    }

    /**
     * 判坐标是否在中国境内（境外无需火星偏移）
     */
    public static boolean isInChina(double lat, double lng) {
        return lng >= 72.004 && lng <= 137.8347 && lat >= 0.8293 && lat <= 55.8271;
    }

    /**
     * 两点球面距离（米，haversine），用于城市 bbox 校验
     */
    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * PI) + 320.0 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLng(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0;
        return ret;
    }
}
