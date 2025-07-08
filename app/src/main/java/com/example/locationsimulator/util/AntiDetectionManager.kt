package com.example.locationsimulator.util

import android.content.Context
import android.location.Location
import android.util.Log
import kotlin.math.*
import kotlin.random.Random

/**
 * 反检测管理器
 * 实现各种反检测机制，提高模拟定位在企业应用中的成功率
 * 特别针对百度地图、高德地图、钉钉等应用的反篡改检测
 */
object AntiDetectionManager {
    private const val TAG = "AntiDetection"

    // 持久化定位状态
    private var isPersistentMode = false
    private var persistentHandler: android.os.Handler? = null
    private var lastSetLocation: Pair<Double, Double>? = null
    
    // 位置历史记录
    private val locationHistory = mutableListOf<LocationRecord>()
    private var lastLocationTime = 0L
    
    data class LocationRecord(
        val latitude: Double,
        val longitude: Double,
        val timestamp: Long,
        val accuracy: Float,
        val speed: Float,
        val bearing: Float
    )
    
    /**
     * 创建具有反检测特性的位置对象
     */
    fun createAntiDetectionLocation(
        provider: String,
        lat: Double,
        lng: Double,
        previousLocation: Location? = null
    ): Location {
        val currentTime = System.currentTimeMillis()
        
        // 计算真实的时间间隔
        val timeDelta = if (lastLocationTime > 0) {
            (currentTime - lastLocationTime) / 1000.0 // 秒
        } else {
            1.0
        }
        
        // 添加微小的随机偏移，避免完全精确的坐标
        val latOffset = Random.nextDouble(-0.000001, 0.000001)
        val lngOffset = Random.nextDouble(-0.000001, 0.000001)
        
        val adjustedLat = lat + latOffset
        val adjustedLng = lng + lngOffset
        
        // 计算合理的速度和方向
        val (speed, bearing) = calculateRealisticMovement(
            adjustedLat, adjustedLng, previousLocation, timeDelta
        )
        
        // 计算动态精度
        val accuracy = calculateDynamicAccuracy(provider, speed)
        
        val location = Location(provider).apply {
            latitude = adjustedLat
            longitude = adjustedLng
            time = currentTime
            elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
            
            // 设置动态精度
            this.accuracy = accuracy
            
            // 设置合理的速度和方向
            this.speed = speed
            this.bearing = bearing
            
            // 设置海拔（添加随机变化）
            altitude = 50.0 + Random.nextDouble(-5.0, 5.0)
            
            // Android 8.0+ 的额外精度信息
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                verticalAccuracyMeters = accuracy * 1.5f
                speedAccuracyMetersPerSecond = speed * 0.1f + 0.1f
                bearingAccuracyDegrees = if (speed > 1.0f) 5.0f else 15.0f
            }
        }
        
        // 记录位置历史
        recordLocation(adjustedLat, adjustedLng, currentTime, accuracy, speed, bearing)
        lastLocationTime = currentTime
        
        Log.d(TAG, "创建反检测位置: ($adjustedLat, $adjustedLng), 精度: $accuracy, 速度: $speed")
        
        return location
    }
    
    /**
     * 计算真实的移动特征
     */
    private fun calculateRealisticMovement(
        lat: Double,
        lng: Double,
        previousLocation: Location?,
        timeDelta: Double
    ): Pair<Float, Float> {
        
        if (previousLocation == null || timeDelta <= 0) {
            // 静止状态
            return Pair(0f, 0f)
        }
        
        // 计算距离（米）
        val distance = calculateDistance(
            previousLocation.latitude, previousLocation.longitude,
            lat, lng
        )
        
        // 计算速度（米/秒）
        val speed = (distance / timeDelta).toFloat()
        
        // 计算方向
        val bearing = calculateBearing(
            previousLocation.latitude, previousLocation.longitude,
            lat, lng
        ).toFloat()
        
        // 限制最大速度（避免异常的瞬移）
        val maxSpeed = 50f // 50 m/s ≈ 180 km/h
        val adjustedSpeed = minOf(speed, maxSpeed)
        
        return Pair(adjustedSpeed, bearing)
    }
    
    /**
     * 计算两点间距离（米）
     */
    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6371000.0 // 地球半径（米）
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }
    
    /**
     * 计算方向角（度）
     */
    private fun calculateBearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLng = Math.toRadians(lng2 - lng1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        
        val y = sin(dLng) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLng)
        
        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360) % 360
    }
    
    /**
     * 计算动态精度
     */
    private fun calculateDynamicAccuracy(provider: String, speed: Float): Float {
        val baseAccuracy = when (provider) {
            "gps" -> 3.0f
            "network" -> 10.0f
            "passive" -> 15.0f
            "fused" -> 5.0f
            else -> 8.0f
        }
        
        // 速度越快，精度越低
        val speedFactor = 1.0f + speed * 0.1f
        
        // 添加随机变化
        val randomFactor = Random.nextFloat() * 0.5f + 0.75f // 0.75 - 1.25
        
        return baseAccuracy * speedFactor * randomFactor
    }
    
    /**
     * 记录位置历史
     */
    private fun recordLocation(
        lat: Double,
        lng: Double,
        timestamp: Long,
        accuracy: Float,
        speed: Float,
        bearing: Float
    ) {
        val record = LocationRecord(lat, lng, timestamp, accuracy, speed, bearing)
        locationHistory.add(record)
        
        // 保留最近100个位置记录
        if (locationHistory.size > 100) {
            locationHistory.removeAt(0)
        }
    }
    
    /**
     * 检查位置变化是否合理
     */
    fun isLocationChangeRealistic(newLat: Double, newLng: Double): Boolean {
        if (locationHistory.isEmpty()) return true
        
        val lastLocation = locationHistory.last()
        val timeDelta = (System.currentTimeMillis() - lastLocation.timestamp) / 1000.0
        
        if (timeDelta <= 0) return false
        
        val distance = calculateDistance(
            lastLocation.latitude, lastLocation.longitude,
            newLat, newLng
        )
        
        val speed = distance / timeDelta
        
        // 检查速度是否合理（最大200 km/h）
        val maxReasonableSpeed = 55.6 // 200 km/h in m/s
        
        return speed <= maxReasonableSpeed
    }
    
    /**
     * 获取位置历史统计
     */
    fun getLocationStats(): String {
        if (locationHistory.isEmpty()) {
            return "位置历史: 无记录"
        }
        
        val avgAccuracy = locationHistory.map { it.accuracy }.average()
        val avgSpeed = locationHistory.map { it.speed }.average()
        val totalDistance = calculateTotalDistance()
        
        return """
            位置历史统计:
            - 记录数量: ${locationHistory.size}
            - 平均精度: ${"%.1f".format(avgAccuracy)}m
            - 平均速度: ${"%.1f".format(avgSpeed)}m/s
            - 总移动距离: ${"%.1f".format(totalDistance)}m
        """.trimIndent()
    }
    
    /**
     * 计算总移动距离
     */
    private fun calculateTotalDistance(): Double {
        if (locationHistory.size < 2) return 0.0
        
        var totalDistance = 0.0
        for (i in 1 until locationHistory.size) {
            val prev = locationHistory[i - 1]
            val curr = locationHistory[i]
            totalDistance += calculateDistance(
                prev.latitude, prev.longitude,
                curr.latitude, curr.longitude
            )
        }
        
        return totalDistance
    }
    
    /**
     * 启动持久化模拟定位模式
     * 针对百度地图、高德地图等应用的反检测
     */
    fun startPersistentMockLocation(context: Context, lat: Double, lng: Double) {
        try {
            Log.d(TAG, "启动持久化模拟定位模式")
            isPersistentMode = true
            lastSetLocation = Pair(lat, lng)

            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager

            // 创建持久化处理器
            persistentHandler = android.os.Handler(android.os.Looper.getMainLooper())

            // 启动高频率位置更新
            startHighFrequencyLocationUpdates(locationManager, lat, lng)

            // 启动位置监控和恢复机制
            startLocationMonitoring(context, locationManager, lat, lng)

            Log.d(TAG, "持久化模拟定位模式已启动")

        } catch (e: Exception) {
            Log.e(TAG, "启动持久化模拟定位失败: ${e.message}")
        }
    }

    /**
     * 停止持久化模拟定位模式
     */
    fun stopPersistentMockLocation() {
        try {
            Log.d(TAG, "停止持久化模拟定位模式")
            isPersistentMode = false
            persistentHandler?.removeCallbacksAndMessages(null)
            persistentHandler = null
            lastSetLocation = null
            Log.d(TAG, "持久化模拟定位模式已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止持久化模拟定位失败: ${e.message}")
        }
    }

    /**
     * 高频率位置更新 - 防止应用检测到位置变化
     */
    private fun startHighFrequencyLocationUpdates(
        locationManager: android.location.LocationManager,
        lat: Double,
        lng: Double
    ) {
        val updateRunnable = object : Runnable {
            override fun run() {
                if (isPersistentMode) {
                    try {
                        // 为每个提供者设置位置，使用微小的随机偏移
                        listOf(
                            android.location.LocationManager.GPS_PROVIDER,
                            android.location.LocationManager.NETWORK_PROVIDER,
                            android.location.LocationManager.PASSIVE_PROVIDER,
                            "fused"
                        ).forEach { provider ->
                            try {
                                val location = createPersistentLocation(provider, lat, lng)
                                locationManager.setTestProviderLocation(provider, location)
                            } catch (e: Exception) {
                                // 忽略单个提供者错误
                            }
                        }

                        // 每5秒更新一次，保持位置活跃
                        persistentHandler?.postDelayed(this, 5000)

                    } catch (e: Exception) {
                        Log.e(TAG, "高频率位置更新失败: ${e.message}")
                    }
                }
            }
        }

        persistentHandler?.post(updateRunnable)
        Log.d(TAG, "高频率位置更新已启动")
    }

    /**
     * 位置监控和恢复机制 - 检测并对抗应用的位置重置
     */
    private fun startLocationMonitoring(
        context: Context,
        locationManager: android.location.LocationManager,
        targetLat: Double,
        targetLng: Double
    ) {
        val monitorRunnable = object : Runnable {
            override fun run() {
                if (isPersistentMode) {
                    try {
                        // 检查当前位置是否被重置
                        val currentLocation = getCurrentMockLocation(locationManager)
                        if (currentLocation != null) {
                            val distance = calculateDistance(
                                currentLocation.latitude, currentLocation.longitude,
                                targetLat, targetLng
                            )

                            // 如果位置偏差超过100米，说明可能被应用重置了
                            if (distance > 100) {
                                Log.w(TAG, "检测到位置被重置，距离目标位置${distance}米，正在恢复...")

                                // 立即恢复目标位置
                                restoreTargetLocation(locationManager, targetLat, targetLng)
                            }
                        }

                        // 每10秒检查一次
                        persistentHandler?.postDelayed(this, 10000)

                    } catch (e: Exception) {
                        Log.e(TAG, "位置监控失败: ${e.message}")
                    }
                }
            }
        }

        persistentHandler?.postDelayed(monitorRunnable, 10000)
        Log.d(TAG, "位置监控已启动")
    }

    /**
     * 创建持久化位置对象
     */
    private fun createPersistentLocation(provider: String, lat: Double, lng: Double): android.location.Location {
        val currentTime = System.currentTimeMillis()

        // 添加微小的随机偏移，避免完全相同的坐标
        val latOffset = kotlin.random.Random.nextDouble(-0.0000005, 0.0000005)
        val lngOffset = kotlin.random.Random.nextDouble(-0.0000005, 0.0000005)

        return android.location.Location(provider).apply {
            latitude = lat + latOffset
            longitude = lng + lngOffset
            time = currentTime
            elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()

            // 设置真实的精度值
            accuracy = when (provider) {
                android.location.LocationManager.GPS_PROVIDER -> kotlin.random.Random.nextFloat() * 2f + 2f // 2-4米
                android.location.LocationManager.NETWORK_PROVIDER -> kotlin.random.Random.nextFloat() * 5f + 8f // 8-13米
                "fused" -> kotlin.random.Random.nextFloat() * 3f + 3f // 3-6米
                else -> kotlin.random.Random.nextFloat() * 4f + 6f // 6-10米
            }

            // 设置其他参数
            speed = 0.0f
            bearing = 0.0f
            altitude = 50.0 + kotlin.random.Random.nextDouble(-2.0, 2.0)

            // Android 8.0+ 的额外参数
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                verticalAccuracyMeters = accuracy * 1.5f
                speedAccuracyMetersPerSecond = 0.1f
                bearingAccuracyDegrees = 15.0f
            }
        }
    }

    /**
     * 获取当前模拟位置
     */
    private fun getCurrentMockLocation(locationManager: android.location.LocationManager): android.location.Location? {
        return try {
            // 尝试从GPS提供者获取最后位置
            locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 恢复目标位置
     */
    private fun restoreTargetLocation(
        locationManager: android.location.LocationManager,
        lat: Double,
        lng: Double
    ) {
        try {
            listOf(
                android.location.LocationManager.GPS_PROVIDER,
                android.location.LocationManager.NETWORK_PROVIDER,
                android.location.LocationManager.PASSIVE_PROVIDER,
                "fused"
            ).forEach { provider ->
                try {
                    val location = createPersistentLocation(provider, lat, lng)
                    locationManager.setTestProviderLocation(provider, location)
                } catch (e: Exception) {
                    // 忽略单个提供者错误
                }
            }
            Log.d(TAG, "目标位置已恢复")
        } catch (e: Exception) {
            Log.e(TAG, "恢复目标位置失败: ${e.message}")
        }
    }

    /**
     * 检查是否处于持久化模式
     */
    fun isPersistentModeActive(): Boolean {
        return isPersistentMode
    }

    /**
     * 清除位置历史
     */
    fun clearLocationHistory() {
        locationHistory.clear()
        lastLocationTime = 0L
        stopPersistentMockLocation()
        Log.d(TAG, "位置历史已清除")
    }
}
