package com.example.locationsimulator.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.*
import kotlin.random.Random

/**
 * 传感器模拟管理器
 * 模拟加速度计、陀螺仪等传感器数据，提高模拟定位的真实性
 */
object SensorSimulationManager {
    private const val TAG = "SensorSimulation"
    
    private var sensorManager: SensorManager? = null
    private var isSimulating = false
    
    // 模拟的传感器数据
    private var simulatedAccelerometer = FloatArray(3) { 0f }
    private var simulatedGyroscope = FloatArray(3) { 0f }
    private var simulatedMagnetometer = FloatArray(3) { 0f }
    
    // 基础传感器值（静止状态）
    private val baseAccelerometer = floatArrayOf(0f, 0f, 9.8f) // 重力加速度
    private val baseMagnetometer = floatArrayOf(0f, 60f, -40f) // 地磁场强度
    
    /**
     * 开始传感器数据模拟
     */
    fun startSensorSimulation(context: Context, lat: Double, lng: Double) {
        try {
            Log.d(TAG, "开始传感器数据模拟")
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            isSimulating = true
            
            // 根据位置计算基础传感器值
            calculateLocationBasedSensorValues(lat, lng)
            
            // 开始模拟传感器数据变化
            startSensorDataGeneration()
            
            Log.d(TAG, "传感器模拟启动成功")
        } catch (e: Exception) {
            Log.e(TAG, "传感器模拟启动失败: ${e.message}")
        }
    }
    
    /**
     * 停止传感器数据模拟
     */
    fun stopSensorSimulation() {
        try {
            Log.d(TAG, "停止传感器数据模拟")
            isSimulating = false
            sensorManager = null
            Log.d(TAG, "传感器模拟已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止传感器模拟失败: ${e.message}")
        }
    }
    
    /**
     * 根据地理位置计算基础传感器值
     */
    private fun calculateLocationBasedSensorValues(lat: Double, lng: Double) {
        // 根据纬度调整重力加速度（地球不是完美球体）
        val gravityVariation = 9.78 + 0.052 * sin(Math.toRadians(2 * lat))
        baseAccelerometer[2] = gravityVariation.toFloat()
        
        // 根据经纬度计算地磁场方向
        val magneticDeclination = calculateMagneticDeclination(lat, lng)
        baseMagnetometer[0] = (60 * cos(Math.toRadians(magneticDeclination))).toFloat()
        baseMagnetometer[1] = (60 * sin(Math.toRadians(magneticDeclination))).toFloat()
        
        Log.d(TAG, "基于位置($lat, $lng)计算传感器基础值")
        Log.d(TAG, "重力加速度: ${baseAccelerometer[2]}")
        Log.d(TAG, "磁场偏角: $magneticDeclination°")
    }
    
    /**
     * 简化的磁偏角计算
     */
    private fun calculateMagneticDeclination(lat: Double, lng: Double): Double {
        // 简化的磁偏角计算公式（实际应用中应使用WMM模型）
        return lng * 0.1 + lat * 0.05
    }
    
    /**
     * 开始生成传感器数据
     */
    private fun startSensorDataGeneration() {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val updateRunnable = object : Runnable {
            override fun run() {
                if (isSimulating) {
                    generateRealisticSensorData()
                    handler.postDelayed(this, 50) // 20Hz更新频率
                }
            }
        }
        handler.post(updateRunnable)
    }
    
    /**
     * 生成真实的传感器数据
     */
    private fun generateRealisticSensorData() {
        val currentTime = System.currentTimeMillis()
        
        // 生成加速度计数据（模拟微小的设备震动）
        simulatedAccelerometer[0] = baseAccelerometer[0] + Random.nextFloat() * 0.1f - 0.05f
        simulatedAccelerometer[1] = baseAccelerometer[1] + Random.nextFloat() * 0.1f - 0.05f
        simulatedAccelerometer[2] = baseAccelerometer[2] + Random.nextFloat() * 0.05f - 0.025f
        
        // 生成陀螺仪数据（模拟微小的旋转）
        simulatedGyroscope[0] = Random.nextFloat() * 0.02f - 0.01f
        simulatedGyroscope[1] = Random.nextFloat() * 0.02f - 0.01f
        simulatedGyroscope[2] = Random.nextFloat() * 0.01f - 0.005f
        
        // 生成磁力计数据（模拟环境磁场变化）
        simulatedMagnetometer[0] = baseMagnetometer[0] + Random.nextFloat() * 2f - 1f
        simulatedMagnetometer[1] = baseMagnetometer[1] + Random.nextFloat() * 2f - 1f
        simulatedMagnetometer[2] = baseMagnetometer[2] + Random.nextFloat() * 1f - 0.5f
    }
    
    /**
     * 模拟移动状态的传感器数据
     */
    fun simulateMovementSensors(speed: Float, bearing: Float) {
        if (!isSimulating) return
        
        try {
            // 根据速度和方向调整加速度计数据
            val acceleration = speed * 0.1f // 简化的加速度计算
            simulatedAccelerometer[0] += acceleration * cos(Math.toRadians(bearing.toDouble())).toFloat()
            simulatedAccelerometer[1] += acceleration * sin(Math.toRadians(bearing.toDouble())).toFloat()
            
            // 根据转向调整陀螺仪数据
            simulatedGyroscope[2] += bearing * 0.001f
            
            Log.d(TAG, "模拟移动传感器数据: 速度=$speed, 方向=$bearing°")
        } catch (e: Exception) {
            Log.e(TAG, "模拟移动传感器数据失败: ${e.message}")
        }
    }
    
    /**
     * 获取当前模拟的传感器数据
     */
    fun getCurrentSensorData(): Map<String, FloatArray> {
        return mapOf(
            "accelerometer" to simulatedAccelerometer.clone(),
            "gyroscope" to simulatedGyroscope.clone(),
            "magnetometer" to simulatedMagnetometer.clone()
        )
    }
    
    /**
     * 检查设备传感器可用性
     */
    fun checkSensorAvailability(context: Context): Map<String, Boolean> {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        
        return mapOf(
            "accelerometer" to (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null),
            "gyroscope" to (sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null),
            "magnetometer" to (sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null),
            "gravity" to (sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) != null),
            "rotation_vector" to (sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null)
        )
    }
    
    /**
     * 获取传感器模拟状态信息
     */
    fun getSimulationStatus(): String {
        return if (isSimulating) {
            """
            传感器模拟状态: 运行中
            加速度计: [${simulatedAccelerometer.joinToString(", ") { "%.3f".format(it) }}]
            陀螺仪: [${simulatedGyroscope.joinToString(", ") { "%.3f".format(it) }}]
            磁力计: [${simulatedMagnetometer.joinToString(", ") { "%.1f".format(it) }}]
            """.trimIndent()
        } else {
            "传感器模拟状态: 未运行"
        }
    }
}
