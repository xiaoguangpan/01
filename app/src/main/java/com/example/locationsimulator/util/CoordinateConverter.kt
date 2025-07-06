package com.example.locationsimulator.util

import kotlin.math.*

/**
 * A utility for converting coordinates between different systems.
 * Handles conversion from BD-09 (Baidu) to WGS-84 (GPS).
 */
object CoordinateConverter {

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
}
