package com.example.locationsimulator.util

import kotlin.math.*

/**
 * A utility for converting coordinates between different systems.
 * Handles conversion between BD-09 (Baidu), GCJ-02 (Mars), and WGS-84 (GPS).
 */
object CoordinateConverter {

    // 坐标系统枚举
    enum class CoordinateSystem {
        WGS84,  // GPS标准坐标系
        GCJ02,  // 火星坐标系（中国标准）
        BD09    // 百度坐标系
    }

    private const val X_PI = 3.14159265358979324 * 3000.0 / 180.0
    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323

    /**
     * Converts BD-09 coordinates to WGS-84 coordinates.
     * @param lngBaidu Baidu longitude.
     * @param latBaidu Baidu latitude.
     * @return A Pair containing WGS-84 longitude and latitude.
     */
    fun bd09ToWgs84(lngBaidu: Double, latBaidu: Double): Pair<Double, Double> {
        val (lngGcj, latGcj) = bd09ToGcj02(lngBaidu, latBaidu)
        return gcj02ToWgs84(lngGcj, latGcj)
    }

    /**
     * Converts WGS-84 coordinates to BD-09 coordinates.
     * @param lngWgs WGS-84 longitude.
     * @param latWgs WGS-84 latitude.
     * @return A LatLng object containing BD-09 coordinates.
     */
    fun wgs84ToBd09(latWgs: Double, lngWgs: Double): com.baidu.mapapi.model.LatLng {
        val (gcjLng, gcjLat) = wgs84ToGcj02(lngWgs, latWgs)
        val (bdLng, bdLat) = gcj02ToBd09(gcjLng, gcjLat)
        return com.baidu.mapapi.model.LatLng(bdLat, bdLng)
    }

    private fun bd09ToGcj02(lngBaidu: Double, latBaidu: Double): Pair<Double, Double> {
        val x = lngBaidu - 0.0065
        val y = latBaidu - 0.006
        val z = sqrt(x * x + y * y) - 0.00002 * sin(y * X_PI)
        val theta = atan2(y, x) - 0.000003 * cos(x * X_PI)
        val lngGcj = z * cos(theta)
        val latGcj = z * sin(theta)
        return Pair(lngGcj, latGcj)
    }

    private fun gcj02ToWgs84(lngGcj: Double, latGcj: Double): Pair<Double, Double> {
        if (outOfChina(lngGcj, latGcj)) {
            return Pair(lngGcj, latGcj)
        }
        var dLat = transformLat(lngGcj - 105.0, latGcj - 35.0)
        var dLng = transformLng(lngGcj - 105.0, latGcj - 35.0)
        val radLat = latGcj / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        dLng = (dLng * 180.0) / (A / sqrtMagic * cos(radLat) * PI)
        val latWgs = latGcj - dLat
        val lngWgs = lngGcj - dLng
        return Pair(lngWgs, latWgs)
    }

    private fun transformLat(lng: Double, lat: Double): Double {
        var ret = -100.0 + 2.0 * lng + 3.0 * lat + 0.2 * lat * lat + 0.1 * lng * lat + 0.2 * sqrt(abs(lng))
        ret += (20.0 * sin(6.0 * lng * PI) + 20.0 * sin(2.0 * lng * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(lat * PI) + 40.0 * sin(lat / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(lat / 12.0 * PI) + 320 * sin(lat * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLng(lng: Double, lat: Double): Double {
        var ret = 300.0 + lng + 2.0 * lat + 0.1 * lng * lng + 0.1 * lng * lat + 0.1 * sqrt(abs(lng))
        ret += (20.0 * sin(6.0 * lng * PI) + 20.0 * sin(2.0 * lng * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(lng * PI) + 40.0 * sin(lng / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(lng / 12.0 * PI) + 300.0 * sin(lng / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }

    private fun outOfChina(lng: Double, lat: Double): Boolean {
        return !(lng > 73.66 && lng < 135.05 && lat > 3.86 && lat < 53.55)
    }

    /**
     * 通用坐标转换方法
     */
    fun convertCoordinates(
        lng: Double,
        lat: Double,
        from: CoordinateSystem,
        to: CoordinateSystem
    ): Pair<Double, Double> {
        if (from == to) return Pair(lng, lat)

        return when (from to to) {
            CoordinateSystem.BD09 to CoordinateSystem.WGS84 -> bd09ToWgs84(lng, lat)
            CoordinateSystem.BD09 to CoordinateSystem.GCJ02 -> bd09ToGcj02(lng, lat)
            CoordinateSystem.GCJ02 to CoordinateSystem.WGS84 -> gcj02ToWgs84(lng, lat)
            CoordinateSystem.GCJ02 to CoordinateSystem.BD09 -> gcj02ToBd09(lng, lat)
            CoordinateSystem.WGS84 to CoordinateSystem.GCJ02 -> wgs84ToGcj02(lng, lat)
            CoordinateSystem.WGS84 to CoordinateSystem.BD09 -> {
                val (gcjLng, gcjLat) = wgs84ToGcj02(lng, lat)
                gcj02ToBd09(gcjLng, gcjLat)
            }
            else -> Pair(lng, lat)
        }
    }

    /**
     * GCJ-02 to BD-09 conversion
     */
    fun gcj02ToBd09(lngGcj: Double, latGcj: Double): Pair<Double, Double> {
        val z = sqrt(lngGcj * lngGcj + latGcj * latGcj) + 0.00002 * sin(latGcj * X_PI)
        val theta = atan2(latGcj, lngGcj) + 0.000003 * cos(lngGcj * X_PI)
        val lngBd = z * cos(theta) + 0.0065
        val latBd = z * sin(theta) + 0.006
        return Pair(lngBd, latBd)
    }

    /**
     * WGS-84 to GCJ-02 conversion
     */
    fun wgs84ToGcj02(lngWgs: Double, latWgs: Double): Pair<Double, Double> {
        if (outOfChina(lngWgs, latWgs)) {
            return Pair(lngWgs, latWgs)
        }
        var dLat = transformLat(lngWgs - 105.0, latWgs - 35.0)
        var dLng = transformLng(lngWgs - 105.0, latWgs - 35.0)
        val radLat = latWgs / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        dLng = (dLng * 180.0) / (A / sqrtMagic * cos(radLat) * PI)
        val latGcj = latWgs + dLat
        val lngGcj = lngWgs + dLng
        return Pair(lngGcj, latGcj)
    }

    /**
     * 检测坐标系统类型
     */
    fun detectCoordinateSystem(lng: Double, lat: Double): CoordinateSystem {
        // 简单的启发式检测
        return when {
            // 百度坐标系通常偏移较大
            abs(lng - 116.404) > 0.01 && abs(lat - 39.915) > 0.01 -> CoordinateSystem.BD09
            // 在中国境内且不是百度坐标系，可能是GCJ02
            !outOfChina(lng, lat) -> CoordinateSystem.GCJ02
            // 其他情况假设为WGS84
            else -> CoordinateSystem.WGS84
        }
    }

    /**
     * 获取坐标系统描述
     */
    fun getCoordinateSystemDescription(system: CoordinateSystem): String {
        return when (system) {
            CoordinateSystem.WGS84 -> "WGS-84 (GPS标准坐标系)"
            CoordinateSystem.GCJ02 -> "GCJ-02 (火星坐标系，中国标准)"
            CoordinateSystem.BD09 -> "BD-09 (百度坐标系)"
        }
    }
}
