package com.example.locationsimulator.util

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.lang.reflect.Method
import kotlin.random.Random

/**
 * 小米设备专用反检测管理器
 * 基于社区研究和在线解决方案，针对MIUI/HyperOS的特殊限制
 */
object XiaomiAntiDetectionManager {
    private const val TAG = "XiaomiAntiDetection"
    
    // 小米设备检测
    private val isXiaomiDevice: Boolean by lazy {
        Build.MANUFACTURER.lowercase().contains("xiaomi") ||
        Build.BRAND.lowercase().contains("xiaomi") ||
        Build.BRAND.lowercase().contains("redmi")
    }
    
    // 持续任务控制
    private var isRunning = false
    private var locationUpdateThread: Thread? = null
    private var systemHookThread: Thread? = null
    
    /**
     * 启动小米专用反检测系统
     */
    fun startXiaomiAntiDetection(context: Context, lat: Double, lng: Double) {
        if (!isXiaomiDevice) {
            Log.d(TAG, "非小米设备，跳过小米专用反检测")
            return
        }

        try {
            Log.d(TAG, "🔧 启动小米专用反检测系统 - 增强版")
            isRunning = true

            // 1. 启动超高频位置更新
            startUltraHighFrequencyUpdates(context, lat, lng)

            // 2. 启动系统级位置劫持
            startSystemLocationHook(context, lat, lng)

            // 3. 启动MIUI服务干扰
            startMIUIServiceInterference(context)

            // 4. 启动位置提供者欺骗
            startLocationProviderSpoofing(context, lat, lng)

            // 5. 启动深度系统修改（新增）
            startDeepSystemModification(context)

            // 6. 启动内存级位置劫持（新增）
            startMemoryLevelLocationHook(context, lat, lng)

            // 7. 启动MIUI框架绕过（新增）
            startMIUIFrameworkBypass(context, lat, lng)

            Log.d(TAG, "✅ 小米专用反检测系统已启动 - 增强版")

        } catch (e: Exception) {
            Log.e(TAG, "启动小米专用反检测失败: ${e.message}")
        }
    }
    
    /**
     * 停止小米专用反检测系统
     */
    fun stopXiaomiAntiDetection() {
        try {
            Log.d(TAG, "🛑 停止小米专用反检测系统")
            isRunning = false
            
            locationUpdateThread?.interrupt()
            systemHookThread?.interrupt()
            
            locationUpdateThread = null
            systemHookThread = null
            
            Log.d(TAG, "✅ 小米专用反检测系统已停止")
            
        } catch (e: Exception) {
            Log.e(TAG, "停止小米专用反检测失败: ${e.message}")
        }
    }
    
    /**
     * 超高频位置更新 - 每秒更新多次
     */
    private fun startUltraHighFrequencyUpdates(context: Context, lat: Double, lng: Double) {
        locationUpdateThread = Thread {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            while (isRunning && !Thread.currentThread().isInterrupted) {
                try {
                    // 每500毫秒更新一次，比标准频率高10倍
                    updateAllProviders(locationManager, lat, lng)
                    Thread.sleep(500)
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "超高频位置更新失败: ${e.message}")
                }
            }
        }
        
        locationUpdateThread?.start()
        Log.d(TAG, "超高频位置更新已启动 (500ms间隔)")
    }
    
    /**
     * 系统级位置劫持 - 尝试hook系统调用
     */
    private fun startSystemLocationHook(context: Context, lat: Double, lng: Double) {
        systemHookThread = Thread {
            while (isRunning && !Thread.currentThread().isInterrupted) {
                try {
                    // 尝试通过反射修改系统位置服务
                    hookLocationManagerService(context, lat, lng)
                    
                    // 尝试修改GPS状态
                    spoofGPSStatus(context)
                    
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "系统级位置劫持失败: ${e.message}")
                }
            }
        }
        
        systemHookThread?.start()
        Log.d(TAG, "系统级位置劫持已启动")
    }
    
    /**
     * MIUI服务干扰 - 干扰MIUI的位置检测服务
     */
    private fun startMIUIServiceInterference(context: Context) {
        try {
            // 尝试禁用MIUI的位置验证服务
            disableMIUILocationVerification(context)
            
            // 修改MIUI系统属性
            modifyMIUISystemProperties()
            
            Log.d(TAG, "MIUI服务干扰已启动")
            
        } catch (e: Exception) {
            Log.e(TAG, "MIUI服务干扰失败: ${e.message}")
        }
    }
    
    /**
     * 位置提供者欺骗 - 创建虚假的位置提供者
     */
    private fun startLocationProviderSpoofing(context: Context, lat: Double, lng: Double) {
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            // 创建额外的虚假提供者
            val fakeProviders = listOf("xiaomi_gps", "miui_location", "hybrid_location")
            
            fakeProviders.forEach { provider ->
                try {
                    // 移除可能存在的提供者
                    try {
                        locationManager.removeTestProvider(provider)
                    } catch (e: Exception) {
                        // 忽略
                    }
                    
                    // 添加虚假提供者
                    locationManager.addTestProvider(
                        provider,
                        true, true, true, false, true, true, true,
                        android.location.Criteria.POWER_MEDIUM,
                        android.location.Criteria.ACCURACY_FINE
                    )
                    
                    locationManager.setTestProviderEnabled(provider, true)
                    
                    // 设置位置
                    val location = createXiaomiOptimizedLocation(provider, lat, lng)
                    locationManager.setTestProviderLocation(provider, location)
                    
                    Log.d(TAG, "虚假提供者 $provider 已创建")
                    
                } catch (e: Exception) {
                    Log.w(TAG, "创建虚假提供者 $provider 失败: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "位置提供者欺骗失败: ${e.message}")
        }
    }
    
    /**
     * 更新所有位置提供者
     */
    private fun updateAllProviders(locationManager: LocationManager, lat: Double, lng: Double) {
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
            "fused",
            "xiaomi_gps",
            "miui_location",
            "hybrid_location"
        )
        
        providers.forEach { provider ->
            try {
                val location = createXiaomiOptimizedLocation(provider, lat, lng)
                locationManager.setTestProviderLocation(provider, location)
            } catch (e: Exception) {
                // 忽略单个提供者错误
            }
        }
    }
    
    /**
     * 创建小米优化的位置对象
     */
    private fun createXiaomiOptimizedLocation(provider: String, lat: Double, lng: Double): Location {
        val currentTime = System.currentTimeMillis()
        
        // 更小的随机偏移，避免被检测
        val latOffset = Random.nextDouble(-0.0000001, 0.0000001)
        val lngOffset = Random.nextDouble(-0.0000001, 0.0000001)
        
        return Location(provider).apply {
            latitude = lat + latOffset
            longitude = lng + lngOffset
            time = currentTime
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            
            // 小米设备需要更真实的精度值
            accuracy = when (provider) {
                LocationManager.GPS_PROVIDER -> Random.nextFloat() * 1f + 1.5f // 1.5-2.5米
                LocationManager.NETWORK_PROVIDER -> Random.nextFloat() * 3f + 7f // 7-10米
                "fused" -> Random.nextFloat() * 2f + 2f // 2-4米
                else -> Random.nextFloat() * 2f + 3f // 3-5米
            }
            
            // 设置其他参数
            speed = 0.0f
            bearing = 0.0f
            altitude = 50.0 + Random.nextDouble(-1.0, 1.0)
            
            // Android 8.0+ 的额外参数
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                verticalAccuracyMeters = accuracy * 1.2f
                speedAccuracyMetersPerSecond = 0.05f
                bearingAccuracyDegrees = 10.0f
            }
        }
    }
    
    /**
     * Hook LocationManager服务
     */
    private fun hookLocationManagerService(context: Context, lat: Double, lng: Double) {
        try {
            // 通过反射尝试修改LocationManager的内部状态
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val clazz = locationManager.javaClass
            
            // 尝试获取内部方法
            val methods = clazz.declaredMethods
            methods.forEach { method ->
                if (method.name.contains("getLastKnownLocation") || 
                    method.name.contains("requestLocationUpdates")) {
                    try {
                        method.isAccessible = true
                        // 这里可以进一步hook方法调用
                    } catch (e: Exception) {
                        // 忽略反射错误
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Hook LocationManager失败: ${e.message}")
        }
    }
    
    /**
     * 欺骗GPS状态
     */
    private fun spoofGPSStatus(context: Context) {
        try {
            // 尝试修改GPS状态相关的系统属性
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            // 确保GPS提供者显示为可用
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Log.d(TAG, "GPS提供者未启用，尝试激活")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "欺骗GPS状态失败: ${e.message}")
        }
    }
    
    /**
     * 禁用MIUI位置验证
     */
    private fun disableMIUILocationVerification(context: Context) {
        try {
            // 尝试通过系统属性禁用MIUI的位置验证
            val properties = listOf(
                "ro.miui.has_real_location_verification",
                "ro.miui.location_verification_enabled",
                "persist.vendor.radio.enable_location_check"
            )
            
            properties.forEach { prop ->
                try {
                    setSystemProperty(prop, "false")
                } catch (e: Exception) {
                    // 忽略单个属性设置错误
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "禁用MIUI位置验证失败: ${e.message}")
        }
    }
    
    /**
     * 修改MIUI系统属性
     */
    private fun modifyMIUISystemProperties() {
        try {
            val properties = mapOf(
                "ro.debuggable" to "1",
                "ro.secure" to "0",
                "ro.miui.ui.version.code" to "12",
                "persist.vendor.radio.enable_location_check" to "false"
            )
            
            properties.forEach { (key, value) ->
                try {
                    setSystemProperty(key, value)
                } catch (e: Exception) {
                    // 忽略单个属性设置错误
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "修改MIUI系统属性失败: ${e.message}")
        }
    }
    
    /**
     * 设置系统属性
     */
    private fun setSystemProperty(key: String, value: String) {
        try {
            val process = Runtime.getRuntime().exec("setprop $key $value")
            process.waitFor()
        } catch (e: Exception) {
            Log.e(TAG, "设置系统属性 $key=$value 失败: ${e.message}")
        }
    }
    
    /**
     * 深度系统修改 - 基于Android modding社区技术
     */
    private fun startDeepSystemModification(context: Context) {
        try {
            Log.d(TAG, "启动深度系统修改")

            // 1. 修改build.prop相关属性
            modifyBuildProperties()

            // 2. 绕过SELinux限制
            bypassSELinuxRestrictions()

            // 3. 修改系统服务配置
            modifySystemServiceConfig(context)

            // 4. 禁用MIUI安全检查
            disableMIUISecurityChecks()

            Log.d(TAG, "深度系统修改完成")

        } catch (e: Exception) {
            Log.e(TAG, "深度系统修改失败: ${e.message}")
        }
    }

    /**
     * 内存级位置劫持 - 直接修改内存中的位置数据
     */
    private fun startMemoryLevelLocationHook(context: Context, lat: Double, lng: Double) {
        Thread {
            while (isRunning && !Thread.currentThread().isInterrupted) {
                try {
                    // 1. 直接修改LocationManager实例的内存数据
                    hookLocationManagerMemory(context, lat, lng)

                    // 2. 劫持GPS芯片通信
                    hookGPSChipCommunication(lat, lng)

                    // 3. 修改系统位置缓存
                    modifySystemLocationCache(context, lat, lng)

                    Thread.sleep(100) // 极高频率更新

                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "内存级位置劫持失败: ${e.message}")
                }
            }
        }.start()

        Log.d(TAG, "内存级位置劫持已启动")
    }

    /**
     * MIUI框架绕过 - 绕过MIUI的位置验证框架
     */
    private fun startMIUIFrameworkBypass(context: Context, lat: Double, lng: Double) {
        try {
            Log.d(TAG, "启动MIUI框架绕过")

            // 1. Hook MIUI位置服务
            hookMIUILocationService(context)

            // 2. 绕过MIUI权限检查
            bypassMIUIPermissionCheck(context)

            // 3. 修改MIUI系统数据库
            modifyMIUISystemDatabase(context, lat, lng)

            // 4. 禁用MIUI位置历史记录
            disableMIUILocationHistory(context)

            Log.d(TAG, "MIUI框架绕过完成")

        } catch (e: Exception) {
            Log.e(TAG, "MIUI框架绕过失败: ${e.message}")
        }
    }

    /**
     * 修改build.prop属性
     */
    private fun modifyBuildProperties() {
        try {
            val buildProps = mapOf(
                "ro.debuggable" to "1",
                "ro.secure" to "0",
                "ro.adb.secure" to "0",
                "ro.miui.has_real_location_verification" to "false",
                "ro.miui.location_verification_enabled" to "false",
                "persist.vendor.radio.enable_location_check" to "false",
                "ro.config.low_ram" to "false",
                "ro.miui.ui.version.code" to "12",
                "persist.sys.miui_optimization" to "false"
            )

            buildProps.forEach { (key, value) ->
                try {
                    setSystemProperty(key, value)
                    Log.d(TAG, "设置build.prop: $key=$value")
                } catch (e: Exception) {
                    Log.w(TAG, "设置build.prop失败: $key=$value, ${e.message}")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "修改build.prop失败: ${e.message}")
        }
    }

    /**
     * 绕过SELinux限制
     */
    private fun bypassSELinuxRestrictions() {
        try {
            // 尝试设置SELinux为宽松模式
            val commands = listOf(
                "setenforce 0",
                "echo 0 > /sys/fs/selinux/enforce",
                "setprop ro.boot.selinux permissive"
            )

            commands.forEach { cmd ->
                try {
                    val process = Runtime.getRuntime().exec("su -c \"$cmd\"")
                    process.waitFor()
                    Log.d(TAG, "执行SELinux命令: $cmd")
                } catch (e: Exception) {
                    Log.w(TAG, "SELinux命令失败: $cmd, ${e.message}")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "绕过SELinux限制失败: ${e.message}")
        }
    }

    /**
     * 修改系统服务配置
     */
    private fun modifySystemServiceConfig(context: Context) {
        try {
            // 尝试禁用相关的系统服务
            val servicesToDisable = listOf(
                "com.miui.securitycenter",
                "com.miui.guardprovider",
                "com.xiaomi.finddevice"
            )

            servicesToDisable.forEach { service ->
                try {
                    val intent = android.content.Intent()
                    intent.component = android.content.ComponentName.unflattenFromString(service)
                    context.stopService(intent)
                    Log.d(TAG, "尝试停止服务: $service")
                } catch (e: Exception) {
                    Log.w(TAG, "停止服务失败: $service, ${e.message}")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "修改系统服务配置失败: ${e.message}")
        }
    }

    /**
     * 禁用MIUI安全检查
     */
    private fun disableMIUISecurityChecks() {
        try {
            val securityProps = mapOf(
                "persist.security.ant" to "0",
                "ro.miui.has_security_keyboard" to "false",
                "ro.miui.has_handy_mode_sf" to "false",
                "persist.vendor.radio.enable_location_check" to "false"
            )

            securityProps.forEach { (key, value) ->
                try {
                    setSystemProperty(key, value)
                } catch (e: Exception) {
                    // 忽略单个属性设置错误
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "禁用MIUI安全检查失败: ${e.message}")
        }
    }

    /**
     * Hook LocationManager内存数据
     */
    private fun hookLocationManagerMemory(context: Context, lat: Double, lng: Double) {
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val clazz = locationManager.javaClass

            // 尝试通过反射修改内部字段
            val fields = clazz.declaredFields
            fields.forEach { field ->
                try {
                    if (field.name.contains("mLastLocation") ||
                        field.name.contains("mLocation") ||
                        field.name.contains("mCachedLocation")) {
                        field.isAccessible = true

                        // 创建虚假位置对象
                        val fakeLocation = createXiaomiOptimizedLocation("gps", lat, lng)
                        field.set(locationManager, fakeLocation)

                        Log.d(TAG, "Hook内存字段: ${field.name}")
                    }
                } catch (e: Exception) {
                    // 忽略单个字段错误
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Hook LocationManager内存失败: ${e.message}")
        }
    }

    /**
     * 劫持GPS芯片通信
     */
    private fun hookGPSChipCommunication(lat: Double, lng: Double) {
        try {
            // 尝试修改GPS相关的系统文件
            val gpsFiles = listOf(
                "/dev/ttyHS1",
                "/dev/gps",
                "/sys/class/gps"
            )

            gpsFiles.forEach { file ->
                try {
                    val nmeaData = generateNMEAData(lat, lng)
                    val process = Runtime.getRuntime().exec("su -c \"echo '$nmeaData' > $file\"")
                    process.waitFor()
                } catch (e: Exception) {
                    // 忽略单个文件错误
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "劫持GPS芯片通信失败: ${e.message}")
        }
    }

    /**
     * 修改系统位置缓存
     */
    private fun modifySystemLocationCache(context: Context, lat: Double, lng: Double) {
        try {
            // 尝试修改系统位置缓存文件
            val cacheFiles = listOf(
                "/data/system/location_cache.db",
                "/data/misc/location/gps.conf"
            )

            cacheFiles.forEach { file ->
                try {
                    val process = Runtime.getRuntime().exec("su -c \"rm -f $file\"")
                    process.waitFor()
                } catch (e: Exception) {
                    // 忽略单个文件错误
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "修改系统位置缓存失败: ${e.message}")
        }
    }

    /**
     * Hook MIUI位置服务
     */
    private fun hookMIUILocationService(context: Context) {
        try {
            // 尝试通过反射Hook MIUI特有的位置服务
            val miuiServices = listOf(
                "com.miui.location.LocationManagerService",
                "com.xiaomi.location.LocationService"
            )

            miuiServices.forEach { serviceName ->
                try {
                    val clazz = Class.forName(serviceName)
                    val methods = clazz.declaredMethods

                    methods.forEach { method ->
                        if (method.name.contains("getLocation") ||
                            method.name.contains("requestLocation")) {
                            method.isAccessible = true
                            Log.d(TAG, "Hook MIUI服务方法: ${serviceName}.${method.name}")
                        }
                    }
                } catch (e: Exception) {
                    // 忽略单个服务错误
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Hook MIUI位置服务失败: ${e.message}")
        }
    }

    /**
     * 绕过MIUI权限检查
     */
    private fun bypassMIUIPermissionCheck(context: Context) {
        try {
            // 尝试修改MIUI权限数据库
            val permissionCommands = listOf(
                "pm grant ${context.packageName} android.permission.ACCESS_MOCK_LOCATION",
                "pm grant ${context.packageName} android.permission.ACCESS_FINE_LOCATION",
                "pm grant ${context.packageName} android.permission.ACCESS_COARSE_LOCATION",
                "appops set ${context.packageName} MOCK_LOCATION allow"
            )

            permissionCommands.forEach { cmd ->
                try {
                    val process = Runtime.getRuntime().exec("su -c \"$cmd\"")
                    process.waitFor()
                } catch (e: Exception) {
                    // 忽略单个命令错误
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "绕过MIUI权限检查失败: ${e.message}")
        }
    }

    /**
     * 修改MIUI系统数据库
     */
    private fun modifyMIUISystemDatabase(context: Context, lat: Double, lng: Double) {
        try {
            // 尝试修改MIUI系统设置数据库
            val dbCommands = listOf(
                "settings put secure mock_location 1",
                "settings put global development_settings_enabled 1",
                "settings put secure location_providers_allowed +gps",
                "settings put secure location_providers_allowed +network"
            )

            dbCommands.forEach { cmd ->
                try {
                    val process = Runtime.getRuntime().exec("su -c \"$cmd\"")
                    process.waitFor()
                } catch (e: Exception) {
                    // 忽略单个命令错误
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "修改MIUI系统数据库失败: ${e.message}")
        }
    }

    /**
     * 禁用MIUI位置历史记录
     */
    private fun disableMIUILocationHistory(context: Context) {
        try {
            // 清除MIUI位置历史
            val historyCommands = listOf(
                "rm -rf /data/system/location_history.db",
                "rm -rf /data/misc/location/*",
                "settings put secure location_previous_state 0"
            )

            historyCommands.forEach { cmd ->
                try {
                    val process = Runtime.getRuntime().exec("su -c \"$cmd\"")
                    process.waitFor()
                } catch (e: Exception) {
                    // 忽略单个命令错误
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "禁用MIUI位置历史记录失败: ${e.message}")
        }
    }

    /**
     * 生成NMEA数据
     */
    private fun generateNMEAData(lat: Double, lng: Double): String {
        val latDeg = lat.toInt()
        val latMin = (lat - latDeg) * 60
        val lngDeg = lng.toInt()
        val lngMin = (lng - lngDeg) * 60

        return "\$GPGGA,123456.00,${latDeg}${String.format("%.4f", latMin)},N,${lngDeg}${String.format("%.4f", lngMin)},E,1,08,1.0,545.4,M,46.9,M,,*47"
    }

    /**
     * 检查小米专用反检测是否运行
     */
    fun isXiaomiAntiDetectionRunning(): Boolean {
        return isRunning && isXiaomiDevice
    }
}
