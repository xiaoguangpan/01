package com.example.locationsimulator

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import android.app.AppOpsManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.baidu.mapapi.map.*
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.core.SearchResult
import com.baidu.mapapi.search.geocode.*
import com.baidu.mapapi.search.sug.*
import com.baidu.location.*
import com.example.locationsimulator.data.SuggestionItem
import com.example.locationsimulator.ui.theme.LocationSimulatorTheme
import com.example.locationsimulator.util.CoordinateConverter
import com.example.locationsimulator.util.MockLocationManager
import com.example.locationsimulator.util.SHA1Util
import com.example.locationsimulator.util.Constants
import com.example.locationsimulator.util.UnifiedMockLocationManager
import com.example.locationsimulator.util.MockLocationResult
import com.example.locationsimulator.util.MockLocationStrategy
import com.example.locationsimulator.util.SetupInstruction
import com.example.locationsimulator.util.AntiDetectionMockLocationManager
import com.example.locationsimulator.util.ShizukuStatus
import com.example.locationsimulator.util.ShizukuStatusMonitor
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import rikka.shizuku.Shizuku

// region ViewModel
enum class InputMode { ADDRESS, COORDINATE }

// 收藏位置数据类
data class FavoriteLocation(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis()
)

class MainViewModel(val application: android.app.Application) : ViewModel() {
    var isSimulating by mutableStateOf(false)
        private set

    // 按钮文本状态
    val buttonText: String
        get() = if (isSimulating) "停止模拟定位" else "开始模拟定位"

    // 按钮颜色状态
    val buttonColor: androidx.compose.ui.graphics.Color
        get() = if (isSimulating) Constants.Colors.Error else Constants.Colors.Primary
    private var _inputMode by mutableStateOf(InputMode.ADDRESS)
    val inputMode: InputMode get() = _inputMode

    // Address Mode State
    var addressQuery by mutableStateOf("")
        private set
    var suggestions by mutableStateOf<List<SuggestionItem>>(emptyList())
        private set
    var selectedSuggestion by mutableStateOf<SuggestionItem?>(null)
        private set

    // 当前搜索城市
    var currentSearchCity by mutableStateOf("北京")
        private set

    // 常用城市列表
    val popularCities = listOf(
        "北京", "上海", "广州", "深圳", "杭州", "南京", "武汉", "成都",
        "重庆", "天津", "西安", "苏州", "长沙", "沈阳", "青岛", "郑州",
        "大连", "东莞", "宁波", "厦门", "福州", "无锡", "合肥", "昆明",
        "哈尔滨", "济南", "佛山", "长春", "温州", "石家庄", "南宁", "常州"
    )

    // Coordinate Mode State
    var coordinateInput by mutableStateOf("")
        private set

    var currentLatitude by mutableStateOf(39.915) // 默认北京纬度 (BD09坐标系，用于地图显示)
        private set

    var currentLongitude by mutableStateOf(116.404) // 默认北京经度 (BD09坐标系，用于地图显示)
        private set

    // Favorites State
    var favoriteLocations by mutableStateOf<List<FavoriteLocation>>(emptyList())
        private set

    var showFavoritesDialog by mutableStateOf(false)
        private set

    // 用于模拟定位的WGS84坐标
    private var simulationLatitude: Double = 39.915
    private var simulationLongitude: Double = 116.404

    var statusMessage by mutableStateOf<String?>(null)
        private set

    // 调试信息
    var debugMessages by mutableStateOf<List<String>>(emptyList())
        private set

    var isDebugExpanded by mutableStateOf(false)
        private set

    var isDebugPanelVisible by mutableStateOf(true)
        private set

    // 5次点击切换调试面板
    private var debugPanelClickCount = 0
    private var lastDebugPanelClickTime = 0L

    private var addressTabClickCount = 0
    private var lastAddressTabClickTime = 0L

    // Shizuku增强模式
    var isShizukuEnhancedModeEnabled by mutableStateOf(false)
        private set
    private var shizukuClickCount = 0
    private var lastShizukuClickTime = 0L

    fun addDebugMessage(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val newMessage = "[$timestamp] $message"
        debugMessages = debugMessages + newMessage // 保留全部调试信息
        Log.d("LocationViewModel", newMessage)
    }

    fun toggleDebugExpanded() {
        isDebugExpanded = !isDebugExpanded
    }

    fun onAddressTabClick() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAddressTabClickTime > 2000) {
            // 如果距离上次点击超过2秒，重置计数
            addressTabClickCount = 1
        } else {
            addressTabClickCount++
        }
        lastAddressTabClickTime = currentTime

        if (addressTabClickCount >= 5) {
            isDebugPanelVisible = !isDebugPanelVisible
            addressTabClickCount = 0
            addDebugMessage("🔧 调试面板${if (isDebugPanelVisible) "显示" else "隐藏"}")
        }
    }

    fun clearDebugMessages() {
        debugMessages = emptyList()
        addDebugMessage("调试信息已清除")
    }

    fun getDebugText(): String {
        return debugMessages.joinToString("\n")
    }

    // 5次点击切换调试面板
    fun handleDebugPanelToggle() {
        val currentTime = System.currentTimeMillis()

        // 如果距离上次点击超过3秒，重置计数
        if (currentTime - lastDebugPanelClickTime > 3000) {
            debugPanelClickCount = 0
        }

        debugPanelClickCount++
        lastDebugPanelClickTime = currentTime

        if (debugPanelClickCount >= 5) {
            isDebugPanelVisible = !isDebugPanelVisible
            debugPanelClickCount = 0
            addDebugMessage("🔧 调试面板${if (isDebugPanelVisible) "显示" else "隐藏"}")
        } else {
            addDebugMessage("🔢 调试面板切换: ${debugPanelClickCount}/5")
        }
    }

    // 5次点击切换Shizuku增强模式
    fun handleShizukuEnhancedModeToggle(context: Context? = null) {
        val currentTime = System.currentTimeMillis()

        // 如果距离上次点击超过3秒，重置计数
        if (currentTime - lastShizukuClickTime > 3000) {
            shizukuClickCount = 0
            addDebugMessage("🔄 Shizuku增强模式点击计数已重置")
        }

        shizukuClickCount++
        lastShizukuClickTime = currentTime

        addDebugMessage("🔢 Shizuku增强模式点击: ${shizukuClickCount}/5 (当前状态: ${if (isShizukuEnhancedModeEnabled) "已开启" else "已关闭"})")

        if (shizukuClickCount >= 5) {
            val previousState = isShizukuEnhancedModeEnabled

            // 如果要开启增强模式，先检查Shizuku状态
            if (!previousState) {
                // 要开启增强模式，先检查状态
                addDebugMessage("🔍 用户尝试开启Shizuku增强模式，开始状态检测...")
                val contextToUse = context ?: application

                // 检查Shizuku状态（等待Binder连接）
                addDebugMessage("🔍 检查Shizuku状态...")
                try {
                    // 检查Binder连接状态
                    val binder = rikka.shizuku.Shizuku.getBinder()
                    if (binder != null && binder.isBinderAlive) {
                        addDebugMessage("🔍 ✅ Shizuku Binder已连接且活跃")
                        try {
                            val version = rikka.shizuku.Shizuku.getVersion()
                            addDebugMessage("🔍 ✅ Shizuku版本: $version")
                            addDebugMessage("🔍 ✅ Shizuku已安装且正在运行")
                        } catch (e: Exception) {
                            addDebugMessage("🔍 ⚠️ Shizuku API调用失败: ${e.message}")
                        }
                    } else {
                        addDebugMessage("🔍 ⚠️ Shizuku Binder未连接或不活跃")
                        addDebugMessage("🔍 这可能说明Shizuku未运行或正在初始化中")
                    }
                } catch (e: Exception) {
                    addDebugMessage("🔍 ❌ Shizuku状态检查失败: ${e.javaClass.simpleName} - ${e.message}")
                }

                // 使用强制刷新，忽略缓存
                addDebugMessage("🔄 强制刷新Shizuku状态（忽略缓存）...")
                val shizukuStatus = ShizukuStatusMonitor.forceRefreshStatus()
                addDebugMessage("🔍 Shizuku状态检测完成: ${shizukuStatus.name} - ${shizukuStatus.message}")

                // 根据Shizuku状态决定增强模式状态
                when (shizukuStatus) {
                    ShizukuStatus.READY -> {
                        isShizukuEnhancedModeEnabled = true
                        addDebugMessage("🚀 ✅ Shizuku增强模式已开启 - Shizuku完全就绪")
                    }
                    ShizukuStatus.NOT_INSTALLED -> {
                        isShizukuEnhancedModeEnabled = false
                        addDebugMessage("🚀 ❌ 无法开启增强模式 - Shizuku未安装")
                    }
                    ShizukuStatus.NOT_RUNNING -> {
                        isShizukuEnhancedModeEnabled = false
                        addDebugMessage("🚀 ❌ 无法开启增强模式 - Shizuku未运行")
                    }
                    ShizukuStatus.NO_PERMISSION -> {
                        isShizukuEnhancedModeEnabled = false
                        addDebugMessage("🚀 ❌ 无法开启增强模式 - Shizuku未授权")
                    }
                    ShizukuStatus.ERROR -> {
                        isShizukuEnhancedModeEnabled = false
                        addDebugMessage("🚀 ❌ 无法开启增强模式 - Shizuku状态检测异常")
                    }
                }

                shizukuClickCount = 0

                // 立即显示状态提示
                checkAndShowShizukuStatus(contextToUse)
            } else {
                // 关闭增强模式
                isShizukuEnhancedModeEnabled = false
                shizukuClickCount = 0
                addDebugMessage("🚀 Shizuku增强模式已关闭，仅使用标准模拟定位")
            }
        }
    }

    // 检查和重新初始化SDK
    fun checkAndReinitSDK() {
        addDebugMessage("🔄 检查SDK状态并重新初始化...")

        // 输出SHA1配置信息
        val sha1 = SHA1Util.getAppSHA1(application)
        val securityCode = SHA1Util.generateBaiduSecurityCode(application)
        addDebugMessage("📋 当前应用SHA1: ${sha1?.take(20)}...")
        addDebugMessage("🔐 百度安全码: ${securityCode?.take(30)}...")

        // 重新初始化建议搜索
        try {
            mSuggestionSearch?.destroy()
            mSuggestionSearch = null
            addDebugMessage("🗑️ 旧的SuggestionSearch已清理")
        } catch (e: Exception) {
            addDebugMessage("⚠️ 清理旧SDK时出错: ${e.message}")
        }

        // 重新初始化
        initBaiduSDK()
        addDebugMessage("✅ SDK重新初始化完成")
    }

    fun onAddressQueryChange(query: String) {
        addressQuery = query
        selectedSuggestion = null // Clear selection when user types
        addDebugMessage("地址输入变化: '$query'")

        if (query.length > 1) {
            // 智能检测城市
            val detectedCity = detectCityFromQuery(query)
            if (detectedCity != null && detectedCity != currentSearchCity) {
                currentSearchCity = detectedCity
                addDebugMessage("🏙️ 智能检测到城市: $detectedCity")
            }

            addDebugMessage("开始搜索地址建议...")
            fetchSuggestions(query)
        } else {
            suggestions = emptyList()
            addDebugMessage("清空地址建议列表")
        }
    }

    fun updateSearchCity(city: String) {
        currentSearchCity = city
        addDebugMessage("🏙️ 切换搜索城市: $city")
        // 如果有当前查询，重新搜索
        if (addressQuery.isNotEmpty()) {
            fetchSuggestions(addressQuery)
        }
    }

    fun onCoordinateInputChange(input: String) {
        coordinateInput = input

        // 实时更新地图位置
        if (input.isNotBlank()) {
            try {
                val parts = input.split(',', '，').map { it.trim() }
                if (parts.size == 2) {
                    val targetLng = parts[0].toDoubleOrNull()
                    val targetLat = parts[1].toDoubleOrNull()

                    if (targetLat != null && targetLng != null) {
                        // 验证坐标范围
                        if (targetLat >= -90 && targetLat <= 90 && targetLng >= -180 && targetLng <= 180) {
                            // 更新地图位置（假设输入的是BD09坐标）
                            currentLatitude = targetLat
                            currentLongitude = targetLng
                            addDebugMessage("🗺️ 地图位置实时更新: BD09($targetLng, $targetLat)")
                        }
                    }
                }
            } catch (e: Exception) {
                // 忽略解析错误，用户可能还在输入
            }
        }
    }

    fun confirmCoordinateInput() {
        if (coordinateInput.isNotBlank()) {
            try {
                val parts = coordinateInput.split(',', '，').map { it.trim() }
                if (parts.size == 2) {
                    val targetLng = parts[0].toDoubleOrNull()
                    val targetLat = parts[1].toDoubleOrNull()

                    if (targetLat != null && targetLng != null) {
                        // 验证坐标范围
                        if (targetLat >= -90 && targetLat <= 90 && targetLng >= -180 && targetLng <= 180) {
                            // 更新地图位置并居中显示
                            currentLatitude = targetLat
                            currentLongitude = targetLng
                            addDebugMessage("🎯 确认坐标输入: BD09($targetLng, $targetLat)")
                            addDebugMessage("🗺️ 地图已居中到指定位置")

                            // 显示确认提示
                            statusMessage = "坐标已确认：($targetLng, $targetLat)"
                        } else {
                            statusMessage = "坐标超出有效范围"
                            addDebugMessage("❌ 坐标超出范围: 纬度=$targetLat, 经度=$targetLng")
                        }
                    } else {
                        statusMessage = "坐标格式错误，请输入数字"
                        addDebugMessage("❌ 坐标解析失败: 无法转换为数字")
                    }
                } else {
                    statusMessage = "坐标格式不正确，请使用 '经度,纬度' 格式"
                    addDebugMessage("❌ 坐标格式错误: 需要2个部分，实际${parts.size}个")
                }
            } catch (e: Exception) {
                statusMessage = "坐标解析失败: ${e.message}"
                addDebugMessage("❌ 坐标解析异常: ${e.message}")
            }
        }
    }

    // 收藏位置管理
    fun addToFavorites() {
        val currentLocation = when (inputMode) {
            InputMode.ADDRESS -> {
                if (addressQuery.isNotBlank() && selectedSuggestion?.location != null) {
                    FavoriteLocation(
                        name = addressQuery,
                        address = addressQuery,
                        latitude = selectedSuggestion!!.location!!.latitude,
                        longitude = selectedSuggestion!!.location!!.longitude
                    )
                } else null
            }
            InputMode.COORDINATE -> {
                if (coordinateInput.isNotBlank()) {
                    try {
                        val parts = coordinateInput.split(',', '，').map { it.trim() }
                        if (parts.size == 2) {
                            val lng = parts[0].toDouble()
                            val lat = parts[1].toDouble()
                            FavoriteLocation(
                                name = "坐标位置 ($lng, $lat)",
                                address = coordinateInput,
                                latitude = lat,
                                longitude = lng
                            )
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }
        }

        currentLocation?.let { location ->
            // 检查是否已存在相同位置
            val exists = favoriteLocations.any {
                kotlin.math.abs(it.latitude - location.latitude) < 0.0001 &&
                kotlin.math.abs(it.longitude - location.longitude) < 0.0001
            }

            if (!exists) {
                favoriteLocations = favoriteLocations + location
                saveFavoriteLocations() // 持久化保存
                addDebugMessage("⭐ 已添加到收藏: ${location.name}")
                statusMessage = "已添加到收藏"
            } else {
                addDebugMessage("⚠️ 位置已存在于收藏中")
                statusMessage = "位置已存在于收藏中"
            }
        }
    }

    // 重载方法：接受参数的addToFavorites
    fun addToFavorites(name: String, address: String, latitude: Double, longitude: Double) {
        val location = FavoriteLocation(
            name = name,
            address = address,
            latitude = latitude,
            longitude = longitude
        )

        // 检查是否已存在相同位置
        val exists = favoriteLocations.any {
            kotlin.math.abs(it.latitude - location.latitude) < 0.0001 &&
            kotlin.math.abs(it.longitude - location.longitude) < 0.0001
        }

        if (!exists) {
            favoriteLocations = favoriteLocations + location
            saveFavoriteLocations() // 持久化保存
            addDebugMessage("⭐ 已添加到收藏: ${location.name}")
            statusMessage = "已添加到收藏"
        } else {
            addDebugMessage("⚠️ 位置已存在于收藏中")
            statusMessage = "位置已存在于收藏中"
        }
    }

    fun removeFromFavorites(location: FavoriteLocation) {
        favoriteLocations = favoriteLocations.filter { it.id != location.id }
        saveFavoriteLocations() // 持久化保存
        addDebugMessage("🗑️ 已从收藏中移除: ${location.name}")
    }

    fun loadFavoriteLocation(location: FavoriteLocation) {
        // 设置输入模式和内容
        if (location.address.contains(",") || location.address.contains("，")) {
            // 坐标格式
            setInputMode(InputMode.COORDINATE)
            coordinateInput = location.address
        } else {
            // 地址格式
            setInputMode(InputMode.ADDRESS)
            addressQuery = location.address
            selectedSuggestion = SuggestionItem(
                name = location.name,
                location = LatLng(location.latitude, location.longitude),
                uid = null,
                city = null,
                district = null
            )
        }

        // 更新地图位置
        currentLatitude = location.latitude
        currentLongitude = location.longitude

        addDebugMessage("📍 已加载收藏位置: ${location.name}")
        statusMessage = "已加载收藏位置"

        // 关闭收藏对话框
        showFavoritesDialog = false
    }

    fun toggleFavoritesDialog() {
        showFavoritesDialog = !showFavoritesDialog
    }

    // 检查当前位置是否已收藏
    fun isCurrentLocationFavorited(): Boolean {
        val currentName = if (inputMode == InputMode.ADDRESS) {
            addressQuery.ifEmpty { "${currentSearchCity}市" }
        } else {
            coordinateInput.ifEmpty { "${currentLongitude},${currentLatitude}" }
        }

        return favoriteLocations.any { it.name == currentName || it.address == currentName }
    }

    // 切换当前位置的收藏状态
    fun toggleCurrentLocationFavorite() {
        val currentName = if (inputMode == InputMode.ADDRESS) {
            addressQuery.ifEmpty { "${currentSearchCity}市" }
        } else {
            coordinateInput.ifEmpty { "${currentLongitude},${currentLatitude}" }
        }

        val currentAddress = if (inputMode == InputMode.ADDRESS) {
            addressQuery.ifEmpty { "${currentSearchCity}市" }
        } else {
            coordinateInput
        }

        if (isCurrentLocationFavorited()) {
            // 移除收藏
            val toRemove = favoriteLocations.find { it.name == currentName || it.address == currentName }
            toRemove?.let { removeFromFavorites(it) }
        } else {
            // 添加收藏
            if (currentName.isNotEmpty()) {
                addToFavorites(currentName, currentAddress, currentLatitude, currentLongitude)
            }
        }
    }

    // 收藏位置持久化
    private fun saveFavoriteLocations() {
        try {
            val sharedPrefs = application.getSharedPreferences("favorite_locations", Context.MODE_PRIVATE)
            val editor = sharedPrefs.edit()

            val jsonArray = favoriteLocations.map { location ->
                """{"id":"${location.id}","name":"${location.name}","address":"${location.address}","latitude":${location.latitude},"longitude":${location.longitude},"timestamp":${location.timestamp}}"""
            }.joinToString(",", "[", "]")

            editor.putString("locations", jsonArray)
            editor.apply()

            addDebugMessage("💾 收藏位置已保存: ${favoriteLocations.size}个")
        } catch (e: Exception) {
            addDebugMessage("❌ 保存收藏位置失败: ${e.message}")
        }
    }

    private fun loadFavoriteLocations() {
        try {
            val sharedPrefs = application.getSharedPreferences("favorite_locations", Context.MODE_PRIVATE)
            val jsonString = sharedPrefs.getString("locations", "[]") ?: "[]"

            if (jsonString != "[]") {
                // 简单的JSON解析（避免引入额外依赖）
                val locations = mutableListOf<FavoriteLocation>()
                val items = jsonString.removeSurrounding("[", "]").split("},")

                items.forEach { item ->
                    val cleanItem = if (item.endsWith("}")) item else "$item}"
                    try {
                        val id = cleanItem.substringAfter("\"id\":\"").substringBefore("\"")
                        val name = cleanItem.substringAfter("\"name\":\"").substringBefore("\"")
                        val address = cleanItem.substringAfter("\"address\":\"").substringBefore("\"")
                        val latitude = cleanItem.substringAfter("\"latitude\":").substringBefore(",").toDouble()
                        val longitude = cleanItem.substringAfter("\"longitude\":").substringBefore(",").toDouble()
                        val timestamp = cleanItem.substringAfter("\"timestamp\":").substringBefore("}").toLong()

                        locations.add(FavoriteLocation(id, name, address, latitude, longitude, timestamp))
                    } catch (e: Exception) {
                        addDebugMessage("⚠️ 解析收藏位置失败: ${e.message}")
                    }
                }

                favoriteLocations = locations
                addDebugMessage("📂 已加载收藏位置: ${favoriteLocations.size}个")
            }
        } catch (e: Exception) {
            addDebugMessage("❌ 加载收藏位置失败: ${e.message}")
        }
    }

    fun selectSuggestion(suggestion: SuggestionItem) {
        selectedSuggestion = suggestion
        addressQuery = suggestion.name
        suggestions = emptyList()

        // 如果建议包含坐标信息，更新地图位置
        suggestion.location?.let { location ->
            currentLatitude = location.latitude
            currentLongitude = location.longitude
            addDebugMessage("🗺️ 地图位置已更新: BD09(${location.longitude}, ${location.latitude})")
            addDebugMessage("📍 选择地址: ${suggestion.name}")
            addDebugMessage("🎯 建议坐标精度: 经度=${location.longitude}, 纬度=${location.latitude}")
        }
    }

    fun setInputMode(mode: InputMode) {
        _inputMode = mode
        statusMessage = null
    }

    // 更新地址查询
    fun updateAddressQuery(query: String) {
        addressQuery = query

        // 如果查询不为空，尝试获取建议
        if (query.isNotBlank()) {
            fetchSuggestions(query)
        } else {
            suggestions = emptyList()
        }
    }

    // 更新坐标输入
    fun updateCoordinateInput(input: String) {
        coordinateInput = input
    }

    // 解析并更新坐标 - 实时地图更新
    fun parseAndUpdateCoordinates(input: String) {
        if (input.isBlank()) return

        try {
            val parts = input.split(",")
            if (parts.size == 2) {
                val longitude = parts[0].trim().toDoubleOrNull()
                val latitude = parts[1].trim().toDoubleOrNull()

                if (longitude != null && latitude != null) {
                    // 假设输入的是BD09坐标（百度地图坐标系），直接使用
                    currentLatitude = latitude
                    currentLongitude = longitude

                    addDebugMessage("🗺️ 坐标已更新: BD09($longitude, $latitude) - 直接使用百度坐标系")
                }
            }
        } catch (e: Exception) {
            addDebugMessage("⚠️ 坐标解析失败: ${e.message}")
        }
    }

    // 百度SDK实例
    private var mSuggestionSearch: SuggestionSearch? = null
    private var mGeoCoder: GeoCoder? = null
    private var mLocationClient: LocationClient? = null

    init {
        addDebugMessage("🚀 Location Simulator 启动")
        addDebugMessage("📱 系统级全局模拟定位工具")
        addDebugMessage("🎯 支持覆盖所有应用的定位信息")
        addDebugMessage("📍 包括百度地图、高德地图、微信、钉钉等")
        addDebugMessage("⚠️ 如遇PERMISSION_UNFINISHED错误，请检查百度开发者平台SHA1配置")
        addDebugMessage("📋 包名: com.example.locationsimulator")
        initBaiduSDK()
        // 启动时自动获取当前位置
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            addDebugMessage("🌍 自动获取当前位置...")
            getCurrentLocation(application)
        }, 2000) // 延迟2秒确保SDK初始化完成
        addDebugMessage("💡 正在获取当前位置，也可手动输入地址或坐标")

        // 加载收藏位置
        loadFavoriteLocations()
    }

    private fun initBaiduSDK() {
        addDebugMessage("🔧 初始化百度地图服务...")

        try {
            // SDK已在Application中初始化，这里只需要初始化具体服务

            // 检查API Key配置
            val apiKey = application.packageManager.getApplicationInfo(
                application.packageName,
                android.content.pm.PackageManager.GET_META_DATA
            ).metaData?.getString("com.baidu.lbsapi.API_KEY")

            if (apiKey.isNullOrEmpty()) {
                addDebugMessage("❌ 百度API Key未设置或为空")
                return
            } else {
                addDebugMessage("✅ 百度API Key已配置: ${apiKey.take(10)}...")
            }

            // 检查SHA1配置
            checkSHA1Configuration()

            // 先测试SDK状态
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                testSDKInitialization()
            }, 1000)

            // 等待更长时间确保SDK完全初始化
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                addDebugMessage("🔄 开始延迟初始化搜索服务...")
                initSuggestionSearch()
            }, 3000) // 增加到3秒

        } catch (e: Exception) {
            addDebugMessage("❌ 服务初始化异常: ${e.message}")
        }
    }

    // 检查SHA1配置
    private fun checkSHA1Configuration() {
        try {
            addDebugMessage("🔐 检查SHA1安全码配置...")

            val sha1 = SHA1Util.getAppSHA1(application)
            val packageName = application.packageName
            val securityCode = SHA1Util.generateBaiduSecurityCode(application)

            addDebugMessage("📋 当前包名: $packageName")
            addDebugMessage("🔧 当前SHA1: $sha1")
            addDebugMessage("🔐 百度安全码: $securityCode")

            addDebugMessage("💡 百度开发者平台配置:")
            addDebugMessage("   1. 访问: https://lbsyun.baidu.com/apiconsole/key")
            addDebugMessage("   2. 找到你的应用")
            addDebugMessage("   3. 在Android SDK安全码中填入: $securityCode")
            addDebugMessage("⚠️ 注意: Debug和Release版本的SHA1可能不同")

        } catch (e: Exception) {
            addDebugMessage("❌ SHA1检查失败: ${e.message}")
        }
    }

    // 测试SDK是否正确初始化
    private fun testSDKInitialization() {
        try {
            addDebugMessage("🧪 测试SDK初始化状态...")

            // 尝试创建一个简单的搜索实例来测试
            val testSearch = SuggestionSearch.newInstance()
            if (testSearch != null) {
                addDebugMessage("✅ SDK初始化正常，可以创建搜索实例")

                // 真正的权限测试：尝试发起一个搜索请求
                addDebugMessage("🔍 测试搜索权限...")
                testSearch.setOnGetSuggestionResultListener { result ->
                    if (result?.error == SearchResult.ERRORNO.PERMISSION_UNFINISHED) {
                        addDebugMessage("❌ 权限测试失败：PERMISSION_UNFINISHED")
                        addDebugMessage("💡 这说明SHA1安全码配置有问题！")
                    } else {
                        addDebugMessage("✅ 权限测试通过")
                    }
                }

                // 发起测试搜索
                val testOption = SuggestionSearchOption()
                    .keyword("测试")
                    .city("北京")
                testSearch.requestSuggestion(testOption)

                // 延迟销毁
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    testSearch.destroy()
                }, 2000)
            } else {
                addDebugMessage("❌ SDK初始化异常，无法创建搜索实例")
            }
        } catch (e: Exception) {
            addDebugMessage("❌ SDK测试失败: ${e.message}")
        }
    }

    private fun initSuggestionSearch() {
        try {
            addDebugMessage("🔍 初始化地址建议搜索...")

            // 确保在主线程中创建
            if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    initSuggestionSearch()
                }
                return
            }

            // 创建建议搜索实例
            mSuggestionSearch = SuggestionSearch.newInstance()

            if (mSuggestionSearch == null) {
                addDebugMessage("❌ SuggestionSearch创建失败，可能SDK未完全初始化")
                // 重试一次
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    addDebugMessage("🔄 重试创建SuggestionSearch...")
                    initSuggestionSearch()
                }, 1000)
                return
            }

            addDebugMessage("✅ SuggestionSearch创建成功")

            // 设置搜索结果监听器
            setupSuggestionSearchListener()

        } catch (e: Exception) {
            addDebugMessage("❌ SuggestionSearch初始化失败: ${e.message}")
            Log.e("LocationViewModel", "SuggestionSearch initialization failed", e)
        }
    }

    private fun setupSuggestionSearchListener() {

        mSuggestionSearch?.setOnGetSuggestionResultListener(object : OnGetSuggestionResultListener {
            override fun onGetSuggestionResult(result: SuggestionResult?) {
                // 确保在主线程中处理结果
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    addDebugMessage("📡 收到地址建议搜索结果")
                    Log.d("LocationViewModel", "Received suggestion result: $result")

                    if (result == null) {
                        addDebugMessage("❌ 搜索结果为空")
                        Log.e("LocationViewModel", "Suggestion result is null")
                        suggestions = emptyList()
                        return@post
                    }

                    if (result.error != SearchResult.ERRORNO.NO_ERROR) {
                        val errorMsg = when (result.error) {
                            SearchResult.ERRORNO.PERMISSION_UNFINISHED -> {
                                "权限未完成初始化 - 请检查API Key和SHA1安全码配置"
                            }
                            SearchResult.ERRORNO.NETWORK_ERROR -> "网络错误 - 请检查网络连接"
                            SearchResult.ERRORNO.KEY_ERROR -> "API Key错误 - 请检查Key是否正确"
                            else -> "未知错误: ${result.error}"
                        }
                        addDebugMessage("❌ 搜索失败: $errorMsg")
                        addDebugMessage("💡 提示: 请确保在百度开发者平台正确配置了SHA1安全码")
                        Log.e("LocationViewModel", "Suggestion search failed with error: ${result.error}")

                        suggestions = emptyList()
                        return@post
                    }

                // 使用getAllSuggestions()获取建议列表
                val allSuggestions = result.allSuggestions
                addDebugMessage("获取到${allSuggestions?.size ?: 0}个建议")
                Log.d("LocationViewModel", "All suggestions count: ${allSuggestions?.size ?: 0}")

                if (allSuggestions == null || allSuggestions.isEmpty()) {
                    addDebugMessage("没有找到地址建议")
                    Log.d("LocationViewModel", "No suggestions found")
                    suggestions = emptyList()
                    return@post
                }

                val suggestionItems = allSuggestions.mapNotNull { info ->
                    Log.d("LocationViewModel", "Processing suggestion: key=${info.key}, pt=${info.pt}")
                    addDebugMessage("处理建议: ${info.key}")
                    // 包含所有建议，不仅仅是有坐标的
                    if (info.key != null) {
                        SuggestionItem(
                            name = info.key,
                            location = info.pt, // 可能为null
                            uid = info.uid,
                            city = info.city,
                            district = info.district
                        )
                    } else {
                        null
                    }
                }

                suggestions = suggestionItems
                addDebugMessage("建议列表更新完成，共${suggestionItems.size}项")
                Log.d("LocationViewModel", "Final suggestions count: ${suggestionItems.size}")
                }
            }
        })

        // 初始化地理编码
        mGeoCoder = GeoCoder.newInstance()

        // 初始化定位客户端
        initLocationClient()
    }

    private fun initLocationClient() {
        addDebugMessage("开始初始化定位客户端...")
        try {
            // 设置隐私合规
            LocationClient.setAgreePrivacy(true)
            addDebugMessage("已设置隐私合规同意")

            mLocationClient = LocationClient(application)
            addDebugMessage("LocationClient创建成功")

            // 配置定位参数
            val option = LocationClientOption().apply {
                locationMode = LocationClientOption.LocationMode.Hight_Accuracy // 高精度模式
                setCoorType("bd09ll") // 百度坐标系
                setScanSpan(0) // 单次定位
                setIsNeedAddress(true) // 需要地址信息
                setIsNeedLocationDescribe(true) // 需要位置描述
                setNeedDeviceDirect(false) // 不需要设备方向
                setLocationNotify(false) // 不需要位置提醒
                setIgnoreKillProcess(true) // 忽略kill进程
                setIsNeedLocationDescribe(true) // 需要位置描述
                setIsNeedAltitude(false) // 不需要海拔
                setOpenGps(true) // 打开GPS
            }

            mLocationClient?.locOption = option
            addDebugMessage("定位参数配置完成")
            Log.d("LocationViewModel", "LocationClient initialized successfully")
        } catch (e: Exception) {
            addDebugMessage("定位客户端初始化失败: ${e.message}")
            Log.e("LocationViewModel", "Failed to initialize LocationClient: ${e.message}")
            mLocationClient = null
        }
    }

    fun getCurrentLocation(context: Context) {
        addDebugMessage("开始获取当前位置...")

        // 检查定位权限
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            statusMessage = "需要定位权限，请在设置中授予定位权限"
            addDebugMessage("定位权限检查失败")
            return
        }
        addDebugMessage("定位权限检查通过")

        if (mLocationClient == null) {
            statusMessage = "定位服务初始化失败，正在重新初始化..."
            addDebugMessage("定位客户端为空，重新初始化...")
            initLocationClient()
            if (mLocationClient == null) {
                statusMessage = "定位服务不可用"
                addDebugMessage("定位客户端重新初始化失败")
                return
            }
        }

        statusMessage = "正在获取当前位置..."
        addDebugMessage("发起定位请求...")
        Log.d("LocationViewModel", "Starting location request")

        // 设置定位监听器
        mLocationClient?.registerLocationListener(object : BDAbstractLocationListener() {
            override fun onReceiveLocation(location: BDLocation?) {
                Log.d("LocationViewModel", "Received location: $location")

                if (location == null) {
                    statusMessage = "定位失败：未获取到位置信息"
                    return
                }

                when (location.locType) {
                    BDLocation.TypeGpsLocation -> {
                        // GPS定位成功
                        val address = location.addrStr ?: "未知地址"
                        val city = location.city ?: "北京"
                        // 不自动填充地址输入框，保持空白便于用户输入
                        // addressQuery = address
                        currentLatitude = location.latitude
                        currentLongitude = location.longitude
                        // 更新搜索城市为当前定位城市
                        currentSearchCity = city.removeSuffix("市")
                        statusMessage = "定位成功：$address"
                        addDebugMessage("🏙️ 定位城市已更新: ${currentSearchCity}")
                        Log.d("LocationViewModel", "GPS location: $address, city: $city")
                    }
                    BDLocation.TypeNetWorkLocation -> {
                        // 网络定位成功
                        val address = location.addrStr ?: "未知地址"
                        val city = location.city ?: "北京"
                        // 不自动填充地址输入框，保持空白便于用户输入
                        // addressQuery = address
                        currentLatitude = location.latitude
                        currentLongitude = location.longitude
                        // 更新搜索城市为当前定位城市
                        currentSearchCity = city.removeSuffix("市")
                        statusMessage = "定位成功：$address"
                        addDebugMessage("🏙️ 定位城市已更新: ${currentSearchCity}")
                        Log.d("LocationViewModel", "Network location: $address, city: $city")
                    }
                    BDLocation.TypeOffLineLocation -> {
                        // 离线定位成功
                        val address = location.addrStr ?: "未知地址"
                        val city = location.city ?: "北京"
                        // 不自动填充地址输入框，保持空白便于用户输入
                        // addressQuery = address
                        currentLatitude = location.latitude
                        currentLongitude = location.longitude
                        // 更新搜索城市为当前定位城市
                        currentSearchCity = city.removeSuffix("市")
                        statusMessage = "离线定位成功：$address"
                        addDebugMessage("🏙️ 定位城市已更新: ${currentSearchCity}")
                        Log.d("LocationViewModel", "Offline location: $address, city: $city")
                    }
                    else -> {
                        // 定位失败
                        val errorMsg = when (location.locType) {
                            BDLocation.TypeServerError -> "服务端网络定位失败"
                            BDLocation.TypeNetWorkException -> "网络不通导致定位失败"
                            BDLocation.TypeCriteriaException -> "无法获取有效定位依据"
                            else -> "定位失败，错误码：${location.locType}"
                        }
                        statusMessage = errorMsg
                        Log.e("LocationViewModel", "Location failed: $errorMsg")
                    }
                }

                // 停止定位
                mLocationClient?.stop()
            }
        })

        // 开始定位
        mLocationClient?.start()
    }

    private fun fetchSuggestions(query: String) {
        addDebugMessage("🔍 发起地址建议搜索: '$query'")
        Log.d("LocationViewModel", "Fetching suggestions for: $query")

        try {
            if (mSuggestionSearch == null) {
                addDebugMessage("⚠️ SuggestionSearch未初始化，重新初始化...")
                initSuggestionSearch()

                // 延迟执行搜索，等待初始化完成
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (mSuggestionSearch != null) {
                        performSuggestionSearch(query)
                    } else {
                        addDebugMessage("❌ 重新初始化后仍然失败")
                    }
                }, 1000)
                return
            }

            performSuggestionSearch(query)

        } catch (e: Exception) {
            addDebugMessage("❌ 地址建议搜索异常: ${e.message}")
            Log.e("LocationViewModel", "Error fetching suggestions: ${e.message}")
            suggestions = emptyList()
        }
    }

    // 智能检测查询中的城市信息
    private fun detectCityFromQuery(query: String): String? {
        val trimmedQuery = query.trim()

        // 检查是否以城市名开头
        for (city in popularCities) {
            if (trimmedQuery.startsWith(city)) {
                return city
            }
        }

        // 检查是否包含"市"字的城市
        val cityPattern = Regex("([\\u4e00-\\u9fa5]+市)")
        val match = cityPattern.find(trimmedQuery)
        if (match != null) {
            val cityWithShi = match.value
            val cityName = cityWithShi.removeSuffix("市")
            // 检查是否在常用城市列表中
            if (popularCities.contains(cityName)) {
                return cityName
            }
            // 如果不在列表中，返回带"市"的完整名称
            return cityWithShi
        }

        return null
    }

    private fun performSuggestionSearch(query: String) {
        try {
            // 检查查询字符串是否有效
            if (query.isBlank()) {
                addDebugMessage("❌ 搜索关键词为空")
                return
            }

            // 创建搜索选项，根据百度官方文档，使用链式调用
            val option = SuggestionSearchOption()
                .keyword(query.trim()) // 设置关键词并去除空格
                .city(currentSearchCity) // city为必填项，使用当前选择的城市

            addDebugMessage("📡 发送搜索请求到百度服务器...")
            addDebugMessage("🔍 搜索关键词: '$query', 搜索城市: $currentSearchCity")

            mSuggestionSearch?.requestSuggestion(option)
            addDebugMessage("✅ 搜索请求已发送，等待服务器响应...")
            Log.d("LocationViewModel", "Suggestion request sent successfully for: $query")

        } catch (e: Exception) {
            addDebugMessage("❌ 发送搜索请求失败: ${e.message}")
            Log.e("LocationViewModel", "Error sending suggestion request: ${e.message}")
            suggestions = emptyList()
        }
    }

    fun toggleSimulation(context: Context) {
        addDebugMessage("🔘 按钮点击 - 当前状态: ${if (isSimulating) "模拟中" else "未模拟"}")
        addDebugMessage("📝 输入模式: ${if (inputMode == InputMode.ADDRESS) "地址模式" else "坐标模式"}")
        addDebugMessage("📍 当前输入: ${if (inputMode == InputMode.ADDRESS) addressQuery else coordinateInput}")

        if (isSimulating) {
            addDebugMessage("🛑 准备停止模拟定位...")
            stopSimulation(context)
        } else {
            addDebugMessage("🚀 准备开始模拟定位...")
            startSimulation(context)
        }
    }

    private fun startSimulation(context: Context) {
        addDebugMessage("开始模拟定位...")
        statusMessage = "正在处理..."

        if (inputMode == InputMode.ADDRESS) {
            // 地址模式：优先使用已选择建议的坐标，避免重复地理编码
            addDebugMessage("使用地址模式: '$addressQuery'")
            if (addressQuery.isBlank()) {
                statusMessage = "请输入地址"
                addDebugMessage("地址为空，停止处理")
                return
            }

            // 🎯 关键修复：检查是否已有选择的建议坐标
            selectedSuggestion?.location?.let { location ->
                addDebugMessage("🎯 使用已选择建议的坐标，避免重复地理编码")
                addDebugMessage("🏷️ 选择的地址: ${selectedSuggestion?.name}")
                addDebugMessage("📍 建议坐标: BD09(${location.longitude}, ${location.latitude})")
                addDebugMessage("🔧 坐标来源: 地址搜索建议API")

                // 直接使用建议的坐标进行模拟定位
                val (wgsLng, wgsLat) = CoordinateConverter.bd09ToWgs84(location.longitude, location.latitude)
                addDebugMessage("🌍 转换为WGS84坐标: ($wgsLng, $wgsLat)")
                addDebugMessage("🎯 坐标传递链路: 建议选择 → 直接使用 → 模拟定位")

                // 使用统一模拟定位管理器
                val result = UnifiedMockLocationManager.start(context, wgsLat, wgsLng, isShizukuEnhancedModeEnabled)

                when (result) {
                    is MockLocationResult.Success -> {
                        // 保存模拟定位的WGS84坐标
                        simulationLatitude = wgsLat
                        simulationLongitude = wgsLng

                        isSimulating = true
                        val addressName = selectedSuggestion?.name ?: "选定位置"
                        statusMessage = "模拟定位成功！策略：${result.strategy.displayName}，位置：$addressName"
                        addDebugMessage("✅ 模拟定位启动成功 - 策略: ${result.strategy.displayName}")
                        addDebugMessage("📱 最终GPS坐标: WGS84($wgsLng, $wgsLat)")
                        addDebugMessage("🎉 位置一致性保证: 选择位置 = 模拟位置")

                        // 显示Toast提示
                        android.widget.Toast.makeText(
                            context,
                            "模拟定位成功！策略：${result.strategy.displayName}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }

                    is MockLocationResult.Failure -> {
                        statusMessage = "模拟失败: ${result.status.message}"
                        addDebugMessage("❌ 模拟定位启动失败: ${result.status.message}")

                        // 只有在Shizuku相关问题时才显示Shizuku状态对话框
                        if (isShizukuEnhancedModeEnabled && isShizukuRelatedFailure(result.status)) {
                            checkAndShowShizukuStatus(context)
                        }

                        addDebugMessage("📋 设置说明:")
                        result.instructions.forEach { instruction ->
                            addDebugMessage("  • ${instruction.title}: ${instruction.description}")
                        }

                        // 显示设置说明
                        showSetupInstructions(context, result.instructions)
                    }
                }
                return
            }

            // 如果没有建议坐标，才使用地理编码API
            addDebugMessage("⚠️ 没有建议坐标，使用地理编码API进行地址解析")
            addDebugMessage("🔧 坐标来源: 地理编码API（可能与建议不同）")

            // 设置地理编码监听器
            mGeoCoder?.setOnGetGeoCodeResultListener(object : OnGetGeoCoderResultListener {
                override fun onGetGeoCodeResult(result: GeoCodeResult?) {
                    Log.d("LocationViewModel", "Geocode result: $result")

                    if (result == null) {
                        statusMessage = "地址解析失败：无返回结果"
                        Log.e("LocationViewModel", "Geocode result is null")
                        return
                    }

                    if (result.error != SearchResult.ERRORNO.NO_ERROR) {
                        val errorMsg = when (result.error) {
                            SearchResult.ERRORNO.AMBIGUOUS_KEYWORD -> "地址信息不明确，请提供更详细的地址"
                            SearchResult.ERRORNO.AMBIGUOUS_ROURE_ADDR -> "路线地址不明确"
                            SearchResult.ERRORNO.NOT_SUPPORT_BUS -> "不支持公交路线"
                            SearchResult.ERRORNO.RESULT_NOT_FOUND -> "未找到相关地址"
                            SearchResult.ERRORNO.PERMISSION_UNFINISHED -> "权限验证未完成"
                            SearchResult.ERRORNO.KEY_ERROR -> "API密钥错误"
                            SearchResult.ERRORNO.NETWORK_ERROR -> "网络连接失败"
                            else -> "地址解析失败，错误码：${result.error}"
                        }
                        statusMessage = errorMsg
                        Log.e("LocationViewModel", "Geocode failed: ${result.error}")
                        return
                    }

                    val location = result.location
                    if (location != null) {
                        Log.d("LocationViewModel", "Geocode success: lng=${location.longitude}, lat=${location.latitude}")
                        val (lngWgs, latWgs) = CoordinateConverter.bd09ToWgs84(location.longitude, location.latitude)

                        addDebugMessage("🚀 启动全面系统级模拟定位...")
                        addDebugMessage("📍 地址: $addressQuery")
                        addDebugMessage("🔧 坐标来源: 地理编码API")
                        addDebugMessage("⚠️ 注意: 地理编码可能与建议坐标不同")
                        addDebugMessage("📍 地理编码坐标: BD09(${location.longitude}, ${location.latitude})")
                        addDebugMessage("🌍 转换为WGS84坐标: ($lngWgs, $latWgs)")
                        addDebugMessage("🎯 坐标传递链路: 地理编码API → 坐标转换 → 模拟定位")

                        // 使用统一模拟定位管理器
                        val result = UnifiedMockLocationManager.start(context, latWgs, lngWgs, isShizukuEnhancedModeEnabled)

                        when (result) {
                            is MockLocationResult.Success -> {
                                // 保存模拟定位的WGS84坐标
                                simulationLatitude = latWgs
                                simulationLongitude = lngWgs

                                // 保持地图显示坐标为BD09坐标系（不变）
                                currentLatitude = location.latitude
                                currentLongitude = location.longitude

                                addDebugMessage("✅ 模拟定位启动成功 - 策略: ${result.strategy.displayName}")
                                addDebugMessage("📱 已覆盖所有定位提供者 (GPS/网络/被动)")
                                addDebugMessage("🎯 地图坐标保持: BD09(${location.longitude}, ${location.latitude})")
                                addDebugMessage("📱 最终GPS坐标: WGS84($lngWgs, $latWgs)")
                                addDebugMessage("⚠️ 警告: 使用地理编码API，位置可能与建议不同")

                                isSimulating = true
                                statusMessage = "模拟定位成功！策略：${result.strategy.displayName}，位置：$addressQuery"

                                // 显示Toast提示
                                android.widget.Toast.makeText(
                                    context,
                                    "模拟定位成功！策略：${result.strategy.displayName}",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }

                            is MockLocationResult.Failure -> {
                                addDebugMessage("❌ 模拟定位启动失败: ${result.status.message}")

                                // 只有在Shizuku相关问题时才显示Shizuku状态对话框
                                if (isShizukuEnhancedModeEnabled && isShizukuRelatedFailure(result.status)) {
                                    checkAndShowShizukuStatus(context)
                                }

                                addDebugMessage("📋 设置说明:")
                                result.instructions.forEach { instruction ->
                                    addDebugMessage("  • ${instruction.title}: ${instruction.description}")
                                }
                                statusMessage = "模拟失败: ${result.status.message}"

                                // 显示设置说明
                                showSetupInstructions(context, result.instructions)
                            }
                        }
                    } else {
                        statusMessage = "无法获取坐标信息"
                        Log.e("LocationViewModel", "Location is null in geocode result")
                    }
                }

                override fun onGetReverseGeoCodeResult(result: ReverseGeoCodeResult?) {
                    // 不需要逆地理编码
                }
            })

            // 发起地理编码请求
            try {
                Log.d("LocationViewModel", "Starting geocode for address: $addressQuery")
                mGeoCoder?.geocode(GeoCodeOption()
                    .city("全国")
                    .address(addressQuery))
            } catch (e: Exception) {
                Log.e("LocationViewModel", "Error starting geocode: ${e.message}")
                statusMessage = "地址解析启动失败: ${e.message}"
            }

        } else {
            // 坐标模式：直接使用输入的坐标
            addDebugMessage("使用坐标模式: '$coordinateInput'")
            Log.d("LocationViewModel", "Processing coordinate input: $coordinateInput")

            try {
                val parts = coordinateInput.split(',', '，').map { it.trim() }
                addDebugMessage("坐标分割结果: ${parts.size}个部分")

                if (parts.size != 2) {
                    statusMessage = "坐标格式不正确，请使用 '经度,纬度' 格式"
                    addDebugMessage("坐标格式错误: 需要2个部分，实际${parts.size}个")
                    return
                }

                val targetLng = parts[0].toDoubleOrNull()
                val targetLat = parts[1].toDoubleOrNull()
                addDebugMessage("坐标解析: 经度=$targetLng, 纬度=$targetLat")

                if (targetLat == null || targetLng == null) {
                    statusMessage = "经纬度必须是数字"
                    addDebugMessage("坐标解析失败: 无法转换为数字")
                    return
                }

                // 验证坐标范围
                if (targetLat < -90 || targetLat > 90) {
                    statusMessage = "纬度必须在-90到90之间"
                    addDebugMessage("纬度超出范围: $targetLat")
                    return
                }
                if (targetLng < -180 || targetLng > 180) {
                    statusMessage = "经度必须在-180到180之间"
                    addDebugMessage("经度超出范围: $targetLng")
                    return
                }

                addDebugMessage("开始坐标转换...")
                Log.d("LocationViewModel", "Converting coordinates: lng=$targetLng, lat=$targetLat")
                val (lngWgs, latWgs) = CoordinateConverter.bd09ToWgs84(targetLng, targetLat)
                addDebugMessage("坐标转换完成: WGS84($lngWgs, $latWgs)")

                addDebugMessage("🚀 启动全面系统级模拟定位...")
                addDebugMessage("📍 输入坐标: BD09($targetLng, $targetLat)")
                addDebugMessage("📍 模拟坐标: WGS84($lngWgs, $latWgs)")
                Log.d("LocationViewModel", "Starting comprehensive mock location: lng=$lngWgs, lat=$latWgs")

                // 使用统一模拟定位管理器
                val result = UnifiedMockLocationManager.start(context, latWgs, lngWgs, isShizukuEnhancedModeEnabled)

                when (result) {
                    is MockLocationResult.Success -> {
                        // 保存模拟定位的WGS84坐标
                        simulationLatitude = latWgs
                        simulationLongitude = lngWgs

                        // 保持地图显示坐标为BD09坐标系（用户输入的坐标）
                        currentLatitude = targetLat
                        currentLongitude = targetLng

                        addDebugMessage("✅ 模拟定位启动成功 - 策略: ${result.strategy.displayName}")
                        addDebugMessage("📱 已覆盖所有定位提供者 (GPS/网络/被动)")
                        addDebugMessage("🎯 地图坐标保持: BD09($targetLng, $targetLat)")
                        addDebugMessage("🎯 模拟坐标设置: WGS84($lngWgs, $latWgs)")

                        isSimulating = true
                        statusMessage = "模拟定位成功！策略：${result.strategy.displayName}，坐标：WGS84($lngWgs, $latWgs)"

                        // 显示Toast提示
                        android.widget.Toast.makeText(
                            context,
                            "模拟定位成功！策略：${result.strategy.displayName}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }

                    is MockLocationResult.Failure -> {
                        addDebugMessage("❌ 模拟定位启动失败: ${result.status.message}")

                        // 只有在Shizuku相关问题时才显示Shizuku状态对话框
                        if (isShizukuEnhancedModeEnabled && isShizukuRelatedFailure(result.status)) {
                            checkAndShowShizukuStatus(context)
                        }

                        addDebugMessage("📋 设置说明:")
                        result.instructions.forEach { instruction ->
                            addDebugMessage("  • ${instruction.title}: ${instruction.description}")
                        }
                        statusMessage = "模拟失败: ${result.status.message}"

                        // 显示设置说明
                        showSetupInstructions(context, result.instructions)
                    }
                }

            } catch (e: Exception) {
                Log.e("LocationViewModel", "Error processing coordinates: ${e.message}")
                statusMessage = "坐标处理失败: ${e.message}"
            }
        }
    }

    fun stopSimulation(context: Context) {
        addDebugMessage("🛑 停止系统级模拟定位...")
        try {
            UnifiedMockLocationManager.stop(context)
            isSimulating = false
            statusMessage = null
            // 保留地址和坐标输入，不清空
            // addressQuery = ""
            // coordinateInput = ""
            selectedSuggestion = null
            suggestions = emptyList()
            addDebugMessage("✅ 所有模拟定位提供者已停止")
            addDebugMessage("🔄 系统定位已恢复正常")
            addDebugMessage("💾 地址输入已保留，便于重新启动")
        } catch (e: Exception) {
            addDebugMessage("❌ 停止模拟定位失败: ${e.message}")
        }
    }

    /**
     * 显示设置说明对话框
     */
    private fun showSetupInstructions(context: Context, instructions: List<SetupInstruction>) {
        if (instructions.isEmpty()) return

        // 在调试面板中显示详细说明
        addDebugMessage("📋 模拟定位设置说明:")
        instructions.forEach { instruction ->
            addDebugMessage("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            addDebugMessage("🔧 ${instruction.title}")
            addDebugMessage("📝 ${instruction.description}")
            if (instruction.action != null) {
                addDebugMessage("💡 如需要可手动前往系统设置页面进行配置")
            }
        }
        addDebugMessage("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        // 不自动执行操作，避免强制跳转到系统设置页面
        // 让用户根据调试信息手动决定是否需要进行系统设置
        addDebugMessage("💡 提示：应用不会自动跳转到系统设置，请根据上述说明手动检查配置")
    }

    /**
     * 判断失败是否与Shizuku相关
     */
    private fun isShizukuRelatedFailure(status: MockLocationStatus): Boolean {
        return when (status) {
            MockLocationStatus.MOCK_APP_NOT_SELECTED,
            MockLocationStatus.DEVELOPER_OPTIONS_DISABLED,
            MockLocationStatus.NO_PERMISSION,
            MockLocationStatus.LOCATION_SERVICE_UNAVAILABLE -> false
            MockLocationStatus.READY -> true // 如果状态是READY但仍然失败，可能是Shizuku问题
        }
    }

    /**
     * 检查并显示Shizuku状态详细信息，并弹出用户友好的状态提示
     */
    private fun checkAndShowShizukuStatus(context: Context) {
        addDebugMessage("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        addDebugMessage("🔧 增强模式状态检查:")
        addDebugMessage("📱 增强模式状态: ${if (isShizukuEnhancedModeEnabled) "已开启" else "已关闭"}")

        // 强制刷新Shizuku状态（忽略缓存）
        addDebugMessage("🔄 强制刷新Shizuku状态...")
        val shizukuStatus = ShizukuStatusMonitor.forceRefreshStatus()
        addDebugMessage("📦 Shizuku状态: ${shizukuStatus.name} - ${shizukuStatus.message}")

        // 弹出明确的状态提示
        showShizukuStatusDialog(context, shizukuStatus)

        when (shizukuStatus) {
            ShizukuStatus.NOT_INSTALLED -> {
                addDebugMessage("💡 建议: 安装Shizuku应用以使用增强功能")
                addDebugMessage("💡 或者: 关闭增强模式使用标准模拟定位功能")
            }
            ShizukuStatus.NOT_RUNNING -> {
                addDebugMessage("💡 建议: 启动Shizuku应用并开启服务")
                addDebugMessage("💡 步骤: 打开Shizuku → 点击启动按钮 → 重新尝试模拟定位")
            }
            ShizukuStatus.NO_PERMISSION -> {
                addDebugMessage("💡 建议: 在Shizuku中授权本应用")
                addDebugMessage("💡 步骤: 打开Shizuku → 应用管理 → 找到本应用 → 授权")
            }
            ShizukuStatus.ERROR -> {
                addDebugMessage("💡 建议: 检查Shizuku安装和权限状态")
            }
            ShizukuStatus.READY -> {
                addDebugMessage("💡 Shizuku状态正常，增强模式已就绪")
                addDebugMessage("💡 可以使用Shizuku增强模式进行模拟定位")
            }
        }
        addDebugMessage("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    /**
     * 显示Shizuku状态对话框
     */
    private fun showShizukuStatusDialog(context: Context, status: ShizukuStatus) {
        addDebugMessage("🔔 准备显示Shizuku状态对话框...")
        addDebugMessage("🔔 Context类型: ${context.javaClass.simpleName}")
        addDebugMessage("🔔 Shizuku状态: ${status.name}")

        val (title, message, actionText) = when (status) {
            ShizukuStatus.NOT_INSTALLED -> Triple(
                "Shizuku未安装",
                "未检测到Shizuku应用。要使用增强模式功能，请先安装Shizuku应用。\n\n增强模式已自动关闭，您仍可使用标准模拟定位功能。",
                "去下载"
            )
            ShizukuStatus.NOT_RUNNING -> Triple(
                "Shizuku未启动",
                "Shizuku已安装但未启动。要使用增强模式功能，请先开启Shizuku服务。\n\n增强模式已自动关闭，您仍可使用标准模拟定位功能。",
                "去启动"
            )
            ShizukuStatus.NO_PERMISSION -> Triple(
                "需要授权",
                "Shizuku已安装且运行，但需要授权本应用才能使用增强功能。\n\n增强模式已自动关闭，您仍可使用标准模拟定位功能。",
                "去授权"
            )
            ShizukuStatus.READY -> Triple(
                "增强模式就绪",
                "🎉 Shizuku增强模式已成功开启！\n\n现在可以使用系统级模拟定位功能，具有更强的兼容性和反检测能力。",
                "确定"
            )
            ShizukuStatus.ERROR -> Triple(
                "检测异常",
                "Shizuku状态检测出现异常，请检查Shizuku安装和权限状态。\n\n增强模式已自动关闭，您仍可使用标准模拟定位功能。",
                "确定"
            )
        }

        // 使用Handler在主线程中显示对话框
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                // 尝试获取正确的Activity Context
                val activityContext = when {
                    context is android.app.Activity -> {
                        addDebugMessage("🔔 使用传入的Activity Context")
                        context
                    }
                    context is android.content.ContextWrapper -> {
                        addDebugMessage("🔔 尝试从ContextWrapper获取Activity")
                        var baseContext = context.baseContext
                        while (baseContext is android.content.ContextWrapper && baseContext !is android.app.Activity) {
                            baseContext = baseContext.baseContext
                        }
                        baseContext as? android.app.Activity
                    }
                    else -> {
                        addDebugMessage("🔔 Context类型不匹配，尝试获取Application Context")
                        null
                    }
                }

                if (activityContext != null && !activityContext.isFinishing && !activityContext.isDestroyed) {
                    addDebugMessage("🔔 ✅ 成功获取有效Activity Context，显示对话框: $title")

                    android.app.AlertDialog.Builder(activityContext)
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton(actionText) { _, _ ->
                            when (status) {
                                ShizukuStatus.NOT_INSTALLED -> {
                                    // 尝试打开应用商店或提供下载链接
                                    try {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                                        intent.data = android.net.Uri.parse("https://github.com/RikkaApps/Shizuku/releases")
                                        activityContext.startActivity(intent)
                                        addDebugMessage("📱 已打开Shizuku下载页面")
                                    } catch (e: Exception) {
                                        addDebugMessage("❌ 无法打开下载页面: ${e.message}")
                                    }
                                }
                                ShizukuStatus.NOT_RUNNING, ShizukuStatus.NO_PERMISSION -> {
                                    // 尝试打开Shizuku应用
                                    try {
                                        val intent = activityContext.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                        if (intent != null) {
                                            activityContext.startActivity(intent)
                                            addDebugMessage("📱 已打开Shizuku应用")
                                        } else {
                                            addDebugMessage("❌ 无法找到Shizuku应用")
                                        }
                                    } catch (e: Exception) {
                                        addDebugMessage("❌ 打开Shizuku应用失败: ${e.message}")
                                    }
                                }
                                else -> {
                                    addDebugMessage("✅ 用户确认Shizuku状态")
                                }
                            }
                        }
                        .setNegativeButton("取消") { dialog, _ ->
                            addDebugMessage("❌ 用户取消Shizuku操作")
                            dialog.dismiss()
                        }
                        .setCancelable(true)
                        .show()
                } else {
                    val reason = when {
                        activityContext == null -> "无法获取有效的Activity Context"
                        activityContext.isFinishing -> "Activity正在结束"
                        activityContext.isDestroyed -> "Activity已被销毁"
                        else -> "未知原因"
                    }
                    addDebugMessage("❌ 无法显示对话框: $reason")
                    addDebugMessage("💡 状态信息已记录在调试日志中，请查看上方的状态检测结果")
                }
            } catch (e: Exception) {
                addDebugMessage("❌ 显示对话框异常: ${e.message}")
                addDebugMessage("💡 状态信息已记录在调试日志中，请查看上方的状态检测结果")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mSuggestionSearch?.destroy()
        mGeoCoder?.destroy()
        mLocationClient?.stop()
        mLocationClient = null
    }
}

class MainViewModelFactory(private val application: android.app.Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
// endregion

// region UI Composables
class MainActivity : ComponentActivity() {

    // Shizuku监听器引用
    private var binderReceivedListener: rikka.shizuku.Shizuku.OnBinderReceivedListener? = null
    private var binderDeadListener: rikka.shizuku.Shizuku.OnBinderDeadListener? = null
    private var permissionResultListener: rikka.shizuku.Shizuku.OnRequestPermissionResultListener? = null

    /**
     * 初始化Shizuku连接
     */
    private fun initializeShizuku(viewModel: MainViewModel) {
        try {
            viewModel.addDebugMessage("🔧 开始初始化Shizuku连接...")

            // 检查Shizuku类是否可用
            try {
                val shizukuClass = rikka.shizuku.Shizuku::class.java
                viewModel.addDebugMessage("🔧 ✅ Shizuku类加载成功")
            } catch (e: Exception) {
                viewModel.addDebugMessage("🔧 ❌ Shizuku类加载失败: ${e.message}")
                return
            }

            // 添加Shizuku Binder接收监听器
            binderReceivedListener = object : rikka.shizuku.Shizuku.OnBinderReceivedListener {
                override fun onBinderReceived() {
                    viewModel.addDebugMessage("🔧 ✅ Shizuku Binder连接成功")
                    // 连接成功后，可以尝试检测状态
                    try {
                        val version = rikka.shizuku.Shizuku.getVersion()
                        viewModel.addDebugMessage("🔧 ✅ Shizuku版本: $version")
                    } catch (e: Exception) {
                        viewModel.addDebugMessage("🔧 ⚠️ Shizuku连接后版本检测失败: ${e.message}")
                    }
                }
            }

            // 添加Binder死亡监听器
            binderDeadListener = object : rikka.shizuku.Shizuku.OnBinderDeadListener {
                override fun onBinderDead() {
                    viewModel.addDebugMessage("🔧 ⚠️ Shizuku Binder连接断开")
                }
            }

            // 添加权限结果监听器
            permissionResultListener = object : rikka.shizuku.Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    viewModel.addDebugMessage("🔧 📋 Shizuku权限结果: requestCode=$requestCode, grantResult=$grantResult")
                    if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        viewModel.addDebugMessage("🔧 ✅ Shizuku权限授权成功！")
                        viewModel.addDebugMessage("🔧 🔄 增强模式现在可用，无需重新点击5次")

                        // 权限授权成功后，自动更新增强模式状态
                        if (!isShizukuEnhancedModeEnabled) {
                            isShizukuEnhancedModeEnabled = true
                            viewModel.addDebugMessage("🔧 ✅ 增强模式已自动开启")
                        }
                    } else {
                        viewModel.addDebugMessage("🔧 ❌ Shizuku权限授权被拒绝")
                    }
                }
            }

            // 注册监听器（安全方式）
            try {
                binderReceivedListener?.let { rikka.shizuku.Shizuku.addBinderReceivedListener(it) }
                binderDeadListener?.let { rikka.shizuku.Shizuku.addBinderDeadListener(it) }
                permissionResultListener?.let { rikka.shizuku.Shizuku.addRequestPermissionResultListener(it) }
                viewModel.addDebugMessage("🔧 ✅ Shizuku监听器注册完成（包括权限监听器）")
            } catch (e: Exception) {
                viewModel.addDebugMessage("🔧 ❌ Shizuku监听器注册失败: ${e.message}")
                return
            }

            // 检查是否已经有Binder连接（安全方式）
            try {
                val binder = rikka.shizuku.Shizuku.getBinder()
                if (binder != null) {
                    viewModel.addDebugMessage("🔧 ✅ Shizuku Binder已存在，连接正常")
                    try {
                        val version = rikka.shizuku.Shizuku.getVersion()
                        viewModel.addDebugMessage("🔧 ✅ 当前Shizuku版本: $version")
                    } catch (e: Exception) {
                        viewModel.addDebugMessage("🔧 ⚠️ Shizuku版本检测失败: ${e.message}")
                    }
                } else {
                    viewModel.addDebugMessage("🔧 ⚠️ Shizuku Binder尚未连接，等待连接...")
                }
            } catch (e: Exception) {
                viewModel.addDebugMessage("🔧 ⚠️ Shizuku Binder检查失败: ${e.message}")
            }

        } catch (e: Exception) {
            viewModel.addDebugMessage("🔧 ❌ Shizuku初始化失败: ${e.javaClass.simpleName} - ${e.message}")
        }
    }

    companion object {
        private val LOCATION_PERMISSION_REQUEST_CODE = Constants.RequestCodes.LOCATION_PERMISSION
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查并请求定位权限
        checkAndRequestLocationPermission()

        // 检查包查询权限（用于检测Shizuku）
        checkQueryAllPackagesPermission()
    }

    override fun onDestroy() {
        super.onDestroy()

        // 清理Shizuku监听器
        try {
            binderReceivedListener?.let { rikka.shizuku.Shizuku.removeBinderReceivedListener(it) }
            binderDeadListener?.let { rikka.shizuku.Shizuku.removeBinderDeadListener(it) }
            permissionResultListener?.let { rikka.shizuku.Shizuku.removeRequestPermissionResultListener(it) }

            binderReceivedListener = null
            binderDeadListener = null
            permissionResultListener = null
        } catch (e: Exception) {
            // 忽略清理异常
        }
    }

    override fun onResume() {
        super.onResume()

        // 每次回到应用时重新检查权限状态，以便及时更新Shizuku检测结果
        checkQueryAllPackagesPermissionStatus()

        setContent {
            LocationSimulatorTheme {
                val viewModel: MainViewModel = viewModel(
                    factory = MainViewModelFactory(application)
                )

                // 在viewModel初始化后立即初始化Shizuku连接
                LaunchedEffect(Unit) {
                    initializeShizuku(viewModel)
                }

                if (viewModel.isSimulating) {
                    SimulatingScreen(
                        address = if (viewModel.inputMode == InputMode.ADDRESS) viewModel.addressQuery else viewModel.coordinateInput,
                        onStopClick = { viewModel.stopSimulation(this) },
                        viewModel = viewModel
                    )
                } else {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }

    private fun checkAndRequestLocationPermission() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun checkQueryAllPackagesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 需要检查QUERY_ALL_PACKAGES权限
            try {
                val hasPermission = checkSelfPermission(Manifest.permission.QUERY_ALL_PACKAGES) == PackageManager.PERMISSION_GRANTED
                Log.d("MainActivity", "QUERY_ALL_PACKAGES权限状态: ${if (hasPermission) "已授予" else "未授予"}")

                if (!hasPermission) {
                    // 显示权限说明并引导用户到设置页面
                    showQueryAllPackagesPermissionDialog()
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "检查QUERY_ALL_PACKAGES权限失败: ${e.message}")
            }
        } else {
            Log.d("MainActivity", "Android 11以下版本，无需QUERY_ALL_PACKAGES权限")
        }
    }

    private fun showQueryAllPackagesPermissionDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle("权限说明")
            .setMessage("为了检测Shizuku应用的安装状态，需要授予\"查询所有应用包\"权限。\n\n请在应用设置中找到\"权限\"→\"查询所有应用包\"并开启。")
            .setPositiveButton("去设置") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("MainActivity", "无法打开应用设置页面", e)
                }
            }
            .setNegativeButton("稍后") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun checkQueryAllPackagesPermissionStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val hasPermission = checkSelfPermission(Manifest.permission.QUERY_ALL_PACKAGES) == PackageManager.PERMISSION_GRANTED
                Log.d("MainActivity", "权限状态检查: QUERY_ALL_PACKAGES = ${if (hasPermission) "已授予" else "未授予"}")

                if (hasPermission) {
                    Log.d("MainActivity", "权限已授予，触发Shizuku状态刷新")
                    // 权限已授予，可以触发Shizuku状态刷新
                    // 这里可以通过ViewModel触发状态更新
                } else {
                    Log.d("MainActivity", "权限仍未授予，将使用备选检测方案")
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "检查权限状态失败: ${e.message}")
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                // 权限获取成功，通知ViewModel获取当前位置
                Log.d("MainActivity", "Location permissions granted, getting current location")
                // 这里可以通过Intent或其他方式通知ViewModel
            } else {
                // 权限被拒绝，显示说明
                Log.w("MainActivity", "Location permissions denied")
                android.widget.Toast.makeText(
                    this,
                    "定位权限被拒绝，将使用默认位置（北京）",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    Box(modifier = Modifier
        .fillMaxSize()
        .background(Constants.Colors.Background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Constants.Dimensions.PADDING_XLARGE.dp)
        ) {

            // 调试信息面板
            DebugPanel(viewModel)
            Spacer(Modifier.height(Constants.Dimensions.PADDING_LARGE.dp))

            // 优化后的状态栏
            OptimizedStatusBar(viewModel)
            Spacer(Modifier.height(Constants.Dimensions.PADDING_LARGE.dp))

            // 重新设计的输入控件区域
            RedesignedInputSection(viewModel)
            Spacer(Modifier.height(Constants.Dimensions.PADDING_LARGE.dp))

            // 当前位置显示 - 5次点击切换调试面板
            CurrentLocationDisplay(viewModel)
            Spacer(Modifier.height(Constants.Dimensions.PADDING_LARGE.dp))

            // 地图区域
            BaiduMapView(modifier = Modifier.weight(1f), isSimulating = false, viewModel = viewModel)

            Spacer(Modifier.height(Constants.Dimensions.PADDING_LARGE.dp))
            // 修复后的主操作按钮
            MainActionButton(viewModel, context)
        }

        // 收藏对话框
        FavoritesDialog(viewModel)
    }
}

@Composable
fun SimulatingScreen(address: String, onStopClick: () -> Unit, viewModel: MainViewModel) {
    Box(modifier = Modifier
        .fillMaxSize()
        .background(Constants.Colors.Background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SimulatingStatus(address)
            BaiduMapView(modifier = Modifier.weight(1f).padding(vertical = 16.dp), isSimulating = true, viewModel = viewModel)
            Button(
                onClick = onStopClick,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("停止模拟", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun Header() {
    Text(
        "虚拟定位模拟器",
        color = Color.White,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun DebugPanel(viewModel: MainViewModel) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager

    if (viewModel.isDebugPanelVisible && viewModel.debugMessages.isNotEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(
                    max = if (viewModel.isDebugExpanded) 300.dp else 120.dp
                ),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF374151))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // 标题栏和操作按钮 - 改为垂直布局
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "🔧 调试信息 (${viewModel.debugMessages.size})",
                        color = Color.Yellow,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 操作按钮 - 水平排列，紧凑布局
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 展开/收起按钮
                        TextButton(
                            onClick = { viewModel.toggleDebugExpanded() },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.Cyan),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (viewModel.isDebugExpanded) "收起" else "展开",
                                fontSize = 11.sp
                            )
                        }

                        // 复制按钮
                        TextButton(
                            onClick = {
                                val clipData = ClipData.newPlainText("调试信息", viewModel.getDebugText())
                                clipboardManager.setPrimaryClip(clipData)
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.Green),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("复制", fontSize = 11.sp)
                        }

                        // 清除按钮
                        TextButton(
                            onClick = { viewModel.clearDebugMessages() },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.Red),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("清除", fontSize = 11.sp)
                        }

                        // 重新初始化SDK按钮
                        TextButton(
                            onClick = { viewModel.checkAndReinitSDK() },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.Magenta),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("重置", fontSize = 11.sp)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    reverseLayout = true // 最新消息在顶部
                ) {
                    items(viewModel.debugMessages.reversed()) { message ->
                        Text(
                            text = message,
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 1.dp),
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}

// 辅助函数：检查模拟定位应用状态
private fun checkMockLocationAppStatus(context: Context): Boolean {
    return try {
        // 方法1：检查Settings.Secure中的模拟定位应用设置
        val mockLocationApp = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ALLOW_MOCK_LOCATION
        )

        // 方法2：使用AppOpsManager检查权限
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val result = appOpsManager.checkOp(
            AppOpsManager.OPSTR_MOCK_LOCATION,
            android.os.Process.myUid(),
            context.packageName
        )

        // 方法3：检查系统设置中的选择应用
        val selectedApp = try {
            Settings.Secure.getString(context.contentResolver, "mock_location_app")
        } catch (e: Exception) {
            null
        }

        // 如果任一方法检测到应用被选择，则返回true
        val isSelected = result == AppOpsManager.MODE_ALLOWED ||
                        context.packageName == selectedApp ||
                        mockLocationApp == "1"

        Log.d("MockLocationCheck", "AppOps result: $result, Selected app: $selectedApp, Package: ${context.packageName}, Final result: $isSelected")

        isSelected
    } catch (e: Exception) {
        Log.e("MockLocationCheck", "Error checking mock location status: ${e.message}", e)
        false
    }
}

// 优化后的状态栏 - 网格布局
@Composable
fun OptimizedStatusBar(viewModel: MainViewModel) {
    val context = LocalContext.current

    // 使用 remember 和 mutableStateOf 来实现状态更新
    var isDeveloperModeEnabled by remember { mutableStateOf(false) }
    var isShizukuAvailable by remember { mutableStateOf(false) }

    // 初始状态检查
    LaunchedEffect(Unit) {
        isDeveloperModeEnabled = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) != 0
        } catch (e: Exception) {
            false
        }
        isShizukuAvailable = try { Shizuku.pingBinder() } catch (e: Exception) { false }

        // 定期更新状态
        var lastDeveloperMode = isDeveloperModeEnabled
        var lastShizukuAvailable = isShizukuAvailable

        while (true) {
            delay(3000)

            val currentDeveloperMode = try {
                Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) != 0
            } catch (e: Exception) {
                false
            }
            val currentShizukuAvailable = try { Shizuku.pingBinder() } catch (e: Exception) { false }

            // 只在状态变化时输出调试信息
            if (currentDeveloperMode != lastDeveloperMode) {
                viewModel.addDebugMessage("🔄 开发者模式状态变化: ${if (currentDeveloperMode) "已开启" else "未开启"}")
                lastDeveloperMode = currentDeveloperMode
                isDeveloperModeEnabled = currentDeveloperMode
            }

            if (currentShizukuAvailable != lastShizukuAvailable) {
                viewModel.addDebugMessage("🔄 Shizuku状态变化: ${if (currentShizukuAvailable) "可用" else "不可用"}")
                lastShizukuAvailable = currentShizukuAvailable
                isShizukuAvailable = currentShizukuAvailable
            }
        }
    }

    // 网格布局状态栏
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Constants.Colors.Surface),
        shape = RoundedCornerShape(Constants.Dimensions.CORNER_RADIUS_MEDIUM.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Constants.Dimensions.PADDING_MEDIUM.dp),
            horizontalArrangement = Arrangement.spacedBy(Constants.Dimensions.PADDING_SMALL.dp)
        ) {
            // 开发者模式状态 - 添加点击跳转功能
            StatusItem(
                label = "开发者模式",
                value = if (isDeveloperModeEnabled) {
                    // 使用状态变量实现实时更新
                    var mockLocationAppStatus by remember { mutableStateOf(false) }

                    // 使用LaunchedEffect实现状态轮询
                    LaunchedEffect(isDeveloperModeEnabled) {
                        while (isDeveloperModeEnabled) {
                            val currentStatus = checkMockLocationAppStatus(context)
                            if (currentStatus != mockLocationAppStatus) {
                                mockLocationAppStatus = currentStatus
                                viewModel.addDebugMessage("🔄 模拟定位应用状态变化: ${if (currentStatus) "已选择" else "未选择"}")
                            }
                            delay(2000) // 每2秒检查一次
                        }
                    }

                    if (mockLocationAppStatus) "已开启 (已选择)" else "已开启 (未选择)"
                } else "未开启",
                isPositive = isDeveloperModeEnabled,
                modifier = Modifier.weight(1f),
                onClick = {
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        viewModel.addDebugMessage("❌ 无法打开开发者选项设置: ${e.message}")
                    }
                }
            )

            // Shizuku状态 - 使用统一的状态监控，避免重复检测
            val shizukuStatus by remember { mutableStateOf(UnifiedMockLocationManager.getShizukuStatus()) }

            StatusItem(
                label = "增强模式",
                value = if (viewModel.isShizukuEnhancedModeEnabled) {
                    "已开启"
                } else {
                    "未开启"
                },
                isPositive = viewModel.isShizukuEnhancedModeEnabled,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.handleShizukuEnhancedModeToggle(context) },
                isEnhanced = false  // 不使用背景色变化
            )
        }
    }
}

@Composable
fun StatusItem(
    label: String,
    value: String,
    isPositive: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    isEnhanced: Boolean = false
) {
    Card(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier.clickable { onClick() }
            } else {
                Modifier
            }
        ),
        colors = CardDefaults.cardColors(
            containerColor = Constants.Colors.Surface  // 统一使用灰色背景
        ),
        shape = RoundedCornerShape(Constants.Dimensions.CORNER_RADIUS_SMALL.dp)
    ) {
        Column(
            modifier = Modifier.padding(Constants.Dimensions.PADDING_SMALL.dp)
        ) {
            Text(
                text = label,
                color = Constants.Colors.OnSurfaceVariant,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = if (isPositive) Constants.Colors.Primary else Constants.Colors.OnSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = if (isPositive) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

// 重新设计的输入控件区域
@Composable
fun RedesignedInputSection(viewModel: MainViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Constants.Colors.Surface),
        shape = RoundedCornerShape(Constants.Dimensions.CORNER_RADIUS_MEDIUM.dp)
    ) {
        Column(
            modifier = Modifier.padding(Constants.Dimensions.PADDING_LARGE.dp)
        ) {
            // 选项卡和收藏按钮的协调布局
            TabAndFavoritesRow(viewModel)

            Spacer(modifier = Modifier.height(Constants.Dimensions.PADDING_LARGE.dp))

            // 输入字段
            when (viewModel.inputMode) {
                InputMode.ADDRESS -> AddressInputField(viewModel)
                InputMode.COORDINATE -> CoordinateInputField(viewModel)
            }
        }
    }
}

// 选项卡和收藏按钮的协调布局
@Composable
fun TabAndFavoritesRow(viewModel: MainViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Constants.Dimensions.PADDING_MEDIUM.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 选项卡容器
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(containerColor = Constants.Colors.Surface),
            shape = RoundedCornerShape(Constants.Dimensions.CORNER_RADIUS_SMALL.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                TabButton(
                    text = "地址输入",
                    isSelected = viewModel.inputMode == InputMode.ADDRESS,
                    onClick = { viewModel.setInputMode(InputMode.ADDRESS) },
                    modifier = Modifier.weight(1f)
                )
                TabButton(
                    text = "坐标输入",
                    isSelected = viewModel.inputMode == InputMode.COORDINATE,
                    onClick = { viewModel.setInputMode(InputMode.COORDINATE) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 收藏按钮组
        Row(
            horizontalArrangement = Arrangement.spacedBy(Constants.Dimensions.PADDING_SMALL.dp)
        ) {
            // 添加收藏按钮 - 带视觉反馈
            FavoriteButton(
                isFavorited = viewModel.isCurrentLocationFavorited(),
                onClick = { viewModel.toggleCurrentLocationFavorite() }
            )

            // 查看收藏按钮
            IconButton(
                onClick = { viewModel.toggleFavoritesDialog() },
                modifier = Modifier.size(Constants.Dimensions.ICON_BUTTON_SIZE.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Constants.Colors.Surface),
                    shape = RoundedCornerShape(Constants.Dimensions.CORNER_RADIUS_SMALL.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "★",
                            color = Constants.Colors.Warning,
                            fontSize = 20.sp // 统一图标尺寸
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(Constants.Dimensions.ICON_BUTTON_SIZE.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Constants.Colors.SurfaceVariant else Color.Transparent,
            contentColor = if (isSelected) Color.White else Constants.Colors.OnSurfaceVariant
        ),
        shape = RoundedCornerShape(Constants.Dimensions.CORNER_RADIUS_SMALL.dp),
        elevation = null
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun FavoriteButton(
    isFavorited: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(Constants.Dimensions.ICON_BUTTON_SIZE.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Constants.Colors.Surface),
            shape = RoundedCornerShape(Constants.Dimensions.CORNER_RADIUS_SMALL.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isFavorited) "♥" else "♡",
                    color = if (isFavorited) Constants.Colors.Favorite else Constants.Colors.OnSurface,
                    fontSize = 20.sp // 统一图标尺寸
                )
            }
        }
    }
}

// 地址输入字段
@Composable
fun AddressInputField(viewModel: MainViewModel) {
    OutlinedTextField(
        value = viewModel.addressQuery,
        onValueChange = { viewModel.updateAddressQuery(it) },
        placeholder = {
            Text(
                text = "输入地址，如：北京市朝阳区",
                color = Constants.Colors.OnSurfaceDisabled
            )
        },
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Constants.Colors.Primary,
            unfocusedBorderColor = Color(0x33FFFFFF),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Constants.Colors.Primary,
            focusedContainerColor = Constants.Colors.Surface,
            unfocusedContainerColor = Constants.Colors.Surface
        ),
        shape = RoundedCornerShape(Constants.Dimensions.CORNER_RADIUS_SMALL.dp),
        singleLine = true
    )

    // 地址建议列表
    if (viewModel.suggestions.isNotEmpty()) {
        Spacer(modifier = Modifier.height(Constants.Dimensions.PADDING_SMALL.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Constants.Colors.Surface,
                    shape = RoundedCornerShape(Constants.Dimensions.CORNER_RADIUS_SMALL.dp)
                )
                .heightIn(max = 120.dp)
        ) {
            items(viewModel.suggestions) { suggestion ->
                val displayText = if (suggestion.city != null && suggestion.district != null) {
                    "${suggestion.name} (${suggestion.city}${suggestion.district})"
                } else {
                    suggestion.name
                }

                Text(
                    text = displayText,
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectSuggestion(suggestion) }
                        .padding(Constants.Dimensions.PADDING_LARGE.dp)
                )

                if (suggestion != viewModel.suggestions.last()) {
                    Divider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
                }
            }
        }
    }
}

// 坐标输入字段 - 移除确认按钮，保持实时更新
@Composable
fun CoordinateInputField(viewModel: MainViewModel) {
    Column {
        OutlinedTextField(
            value = viewModel.coordinateInput,
            onValueChange = {
                viewModel.updateCoordinateInput(it)
                // 实时更新地图位置
                viewModel.parseAndUpdateCoordinates(it)
            },
            placeholder = {
                Text(
                    text = "116.404,39.915",
                    color = Constants.Colors.OnSurfaceDisabled
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Constants.Colors.Primary,
                unfocusedBorderColor = Color(0x33FFFFFF),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Constants.Colors.Primary,
                focusedContainerColor = Constants.Colors.Surface,
                unfocusedContainerColor = Constants.Colors.Surface
            ),
            shape = RoundedCornerShape(Constants.Dimensions.CORNER_RADIUS_SMALL.dp),
            singleLine = true
        )

        // 获取坐标按钮 - 重新设计位置
        Spacer(modifier = Modifier.height(Constants.Dimensions.PADDING_MEDIUM.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "需要坐标？",
                color = Constants.Colors.OnSurfaceVariant,
                fontSize = 12.sp
            )

            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.map.baidu.com/lbsapi/getpoint/"))
                    viewModel.application.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Constants.Colors.Primary
                ),
                border = BorderStroke(1.dp, Color(0x4DFFFFFF)),
                shape = RoundedCornerShape(Constants.Dimensions.CORNER_RADIUS_TINY.dp),
                modifier = Modifier.height(Constants.Dimensions.SMALL_BUTTON_HEIGHT.dp)
            ) {
                Text(
                    text = "📍 获取坐标",
                    fontSize = 12.sp
                )
            }
        }
    }
}

// 当前位置显示
@Composable
fun CurrentLocationDisplay(viewModel: MainViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.handleDebugPanelToggle() },
        colors = CardDefaults.cardColors(containerColor = Constants.Colors.Surface),
        shape = RoundedCornerShape(Constants.Dimensions.CORNER_RADIUS_SMALL.dp)
    ) {
        Row(
            modifier = Modifier.padding(Constants.Dimensions.PADDING_MEDIUM.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📍",
                color = Constants.Colors.Warning,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.width(Constants.Dimensions.PADDING_SMALL.dp))
            Text(
                text = "当前位置: ${if (viewModel.addressQuery.isNotEmpty()) viewModel.addressQuery else "${viewModel.currentSearchCity}市"}",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun StatusCheck(viewModel: MainViewModel) {
    val context = LocalContext.current

    // 使用 remember 和 mutableStateOf 来实现状态更新
    var isDeveloperModeEnabled by remember { mutableStateOf(false) }
    var isShizukuAvailable by remember { mutableStateOf(false) }

    // 使用 LaunchedEffect 来检查状态（只在状态变化时输出调试信息）
    LaunchedEffect(Unit) {
        // 初始检查 - 只检查开发者模式
        var lastDeveloperMode = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) != 0
        } catch (e: Exception) {
            false
        }

        isDeveloperModeEnabled = lastDeveloperMode

        // 初始状态输出
        viewModel.addDebugMessage("📱 初始状态检查 - 开发者模式: ${if (lastDeveloperMode) "已开启" else "未开启"}")
        viewModel.addDebugMessage("📱 Shizuku状态由统一监控管理")

        // 每3秒检查一次，但只在状态变化时输出调试信息
        while (true) {
            delay(3000)

            val currentDeveloperMode = try {
                Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) != 0
            } catch (e: Exception) {
                false
            }
            // 只检查开发者模式，Shizuku状态由统一监控处理
            // 只在状态变化时输出调试信息
            if (currentDeveloperMode != lastDeveloperMode) {
                viewModel.addDebugMessage("🔄 开发者模式状态变化: ${if (currentDeveloperMode) "已开启" else "未开启"}")
                lastDeveloperMode = currentDeveloperMode
                isDeveloperModeEnabled = currentDeveloperMode
            }
        }
    }

    // 紧凑的状态栏 - 缩小尺寸
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 开发者模式状态 - 缩小
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .clickable {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "开发者模式: ",
                color = Color.White,
                fontSize = 12.sp
            )
            val context = LocalContext.current
            val isMockLocationApp = remember(isDeveloperModeEnabled) {
                if (isDeveloperModeEnabled) {
                    checkMockLocationAppStatus(context)
                } else {
                    false
                }
            }

            Text(
                text = if (isDeveloperModeEnabled) {
                    if (isMockLocationApp) {
                        "已开启 (已选择为模拟定位应用)"
                    } else {
                        "已开启 (未选择为模拟定位应用)"
                    }
                } else {
                    "未开启"
                },
                color = if (isDeveloperModeEnabled) Color(0xFF4CAF50) else Color(0xFFF44336),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Shizuku状态 - 显示详细信息
        val shizukuStatus = remember { UnifiedMockLocationManager.getShizukuStatus() }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .clickable {
                    try {
                        context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let {
                            context.startActivity(it)
                        } ?: run {
                            viewModel.addDebugMessage("📋 Shizuku详细状态:")
                            viewModel.addDebugMessage(shizukuStatus.getStatusText())
                        }
                    } catch (e: Exception) {
                        viewModel.addDebugMessage("无法打开Shizuku: ${e.message}")
                    }
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Shizuku: ",
                color = Color.White,
                fontSize = 12.sp
            )
            Text(
                text = when (shizukuStatus.status) {
                    ShizukuStatus.READY -> "已安装 (已开启)"
                    ShizukuStatus.NO_PERMISSION -> "已安装 (需授权)"
                    ShizukuStatus.NOT_RUNNING -> "已安装 (未开启)"
                    ShizukuStatus.NOT_INSTALLED -> "未安装"
                    ShizukuStatus.ERROR -> "检测错误"
                },
                color = when (shizukuStatus.status) {
                    ShizukuStatus.READY -> Constants.Colors.Success
                    ShizukuStatus.NO_PERMISSION -> Constants.Colors.Warning
                    ShizukuStatus.NOT_RUNNING -> Constants.Colors.Warning
                    ShizukuStatus.NOT_INSTALLED -> Constants.Colors.Error
                    ShizukuStatus.ERROR -> Constants.Colors.Error
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun StatusRow(title: String, status: String, statusColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontSize = 16.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(status, color = statusColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            // Icon for navigation hint can be added here
        }
    }
}

@Composable
fun SimulatingStatus(address: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("正在模拟位置", color = Color.Gray, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        Text(address, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun InputControls(viewModel: MainViewModel) {
    val isAddressMode = viewModel.inputMode == InputMode.ADDRESS
    Column {
        // 收藏和输入模式切换行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabRow(
                selectedTabIndex = viewModel.inputMode.ordinal,
                containerColor = Color.White.copy(alpha = 0.1f),
                contentColor = Color.White,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                Tab(selected = isAddressMode, onClick = {
                    viewModel.setInputMode(InputMode.ADDRESS)
                    viewModel.onAddressTabClick()
                }, text = { Text("地址输入") })
                Tab(selected = !isAddressMode, onClick = { viewModel.setInputMode(InputMode.COORDINATE) }, text = { Text("坐标输入") })
            }

            Spacer(Modifier.width(8.dp))

            // 收藏按钮
            Row {
                // 添加到收藏按钮
                IconButton(
                    onClick = { viewModel.addToFavorites() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = "添加收藏",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 查看收藏按钮
                IconButton(
                    onClick = { viewModel.toggleFavoritesDialog() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "查看收藏",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (isAddressMode) {
            AddressInputWithSuggestions(viewModel)
        } else {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = viewModel.coordinateInput,
                        onValueChange = { viewModel.onCoordinateInputChange(it) },
                        label = { Text("经度,纬度") },
                        placeholder = { Text("116.404,39.915") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = textFieldColors()
                    )

                    Spacer(Modifier.width(8.dp))

                    // 确认按钮
                    Button(
                        onClick = { viewModel.confirmCoordinateInput() },
                        modifier = Modifier.height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Constants.Colors.Primary
                        ),
                        enabled = viewModel.coordinateInput.isNotBlank()
                    ) {
                        Text("确认", color = Color.White)
                    }
                }

                // 坐标获取链接按钮 - 紧凑布局
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "需要坐标？",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )

                    TextButton(
                        onClick = {
                            // 在浏览器中打开百度坐标拾取器
                            val context = viewModel.application
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                data = android.net.Uri.parse("https://api.map.baidu.com/lbsapi/getpoint/")
                                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF007AFF)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "获取坐标",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("获取坐标", fontSize = 12.sp)
                    }
                }
            }
        }

        viewModel.statusMessage?.let {
            Text(it, color = Color.Yellow, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

// 修复后的主操作按钮
@Composable
fun MainActionButton(viewModel: MainViewModel, context: Context) {
    val isAddressMode = viewModel.inputMode == InputMode.ADDRESS
    val hasValidInput = (isAddressMode && viewModel.addressQuery.isNotBlank()) ||
                       (!isAddressMode && viewModel.coordinateInput.isNotBlank())

    val buttonEnabled = hasValidInput && !viewModel.isSimulating
    val buttonText = if (viewModel.isSimulating) "停止模拟定位" else "开始模拟定位"

    Button(
        onClick = { viewModel.toggleSimulation(context) },
        enabled = buttonEnabled || viewModel.isSimulating, // 停止按钮始终可点击
        shape = RoundedCornerShape(Constants.Dimensions.CORNER_RADIUS_LARGE.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                viewModel.isSimulating -> Constants.Colors.Error
                buttonEnabled -> Constants.Colors.Primary
                else -> Constants.Colors.Disabled
            },
            contentColor = when {
                viewModel.isSimulating -> Color.White
                buttonEnabled -> Color.White
                else -> Constants.Colors.OnDisabled
            },
            disabledContainerColor = Constants.Colors.Disabled,
            disabledContentColor = Constants.Colors.OnDisabled
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(Constants.Dimensions.BUTTON_HEIGHT.dp),
        elevation = if (buttonEnabled || viewModel.isSimulating) {
            ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        } else {
            ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        }
    ) {
        Text(
            text = buttonText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AddressInputWithSuggestions(viewModel: MainViewModel) {
    var showCityDropdown by remember { mutableStateOf(false) }

    Column {
        // 简化的地址输入框 - 隐藏城市选择器
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 城市选择器部分 - 隐藏但保留逻辑
            Box(modifier = Modifier.size(0.dp)) { // 设置为0大小来隐藏
                TextButton(
                    onClick = { showCityDropdown = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${viewModel.currentSearchCity} ▼",
                        fontSize = 14.sp
                    )
                }

                DropdownMenu(
                    expanded = showCityDropdown,
                    onDismissRequest = { showCityDropdown = false },
                    modifier = Modifier
                        .background(Color(0xFF2D3748))
                        .heightIn(max = 300.dp)
                ) {
                    viewModel.popularCities.forEach { city ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    city,
                                    color = if (city == viewModel.currentSearchCity) Color.Yellow else Color.White
                                )
                            },
                            onClick = {
                                viewModel.updateSearchCity(city)
                                showCityDropdown = false
                            }
                        )
                    }
                }
            }

            // 地址输入框部分 - 占满整个宽度
            BasicTextField(
                value = viewModel.addressQuery,
                onValueChange = { viewModel.onAddressQueryChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 16.sp
                ),
                decorationBox = { innerTextField ->
                    if (viewModel.addressQuery.isEmpty()) {
                        Text(
                            text = "输入目标地址",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 16.sp
                        )
                    }
                    innerTextField()
                }
            )
        }

        if (viewModel.suggestions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2D3748), shape = RoundedCornerShape(8.dp))
                    .heightIn(max = 120.dp) // 限制高度，不遮挡地图
            ) {
                items(viewModel.suggestions) { suggestion ->
                    val displayText = if (suggestion.city != null && suggestion.district != null) {
                        "${suggestion.name} (${suggestion.city}${suggestion.district})"
                    } else {
                        suggestion.name
                    }

                    Text(
                        text = displayText,
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectSuggestion(suggestion) }
                            .padding(16.dp)
                    )

                    if (suggestion != viewModel.suggestions.last()) {
                        Divider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun BaiduMapView(modifier: Modifier = Modifier, isSimulating: Boolean, viewModel: MainViewModel? = null) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    var isInitialized by remember { mutableStateOf(false) }

    // 获取当前坐标（无论是否在模拟状态都使用viewModel中的坐标）
    val currentLat = viewModel?.currentLatitude ?: 39.915
    val currentLng = viewModel?.currentLongitude ?: 116.404

    // 监听位置变化，确保地图实时更新
    LaunchedEffect(currentLat, currentLng, isSimulating) {
        if (isInitialized) {
            mapView.map?.let { baiduMap ->
                // 清除之前的覆盖物
                baiduMap.clear()

                // 添加位置标注
                val currentLocation = LatLng(currentLat, currentLng)
                val markerOptions = MarkerOptions()
                    .position(currentLocation)
                    .icon(BitmapDescriptorFactory.fromResource(
                        if (isSimulating) android.R.drawable.ic_menu_compass
                        else android.R.drawable.ic_menu_mylocation
                    ))
                    .title(if (isSimulating) "模拟位置" else "当前位置")

                baiduMap.addOverlay(markerOptions)

                // 更新地图位置并添加动画
                val mapStatus = MapStatus.Builder()
                    .target(currentLocation)
                    .zoom(16f)
                    .build()

                baiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(mapStatus))
            }
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.clip(RoundedCornerShape(16.dp))
    ) { view ->
        if (!isInitialized) {
            view.map.apply {
                // 在模拟状态下禁用系统定位图层，避免冲突
                isMyLocationEnabled = !isSimulating

                // 设置地图类型为卫星图（更暗的效果）
                mapType = BaiduMap.MAP_TYPE_SATELLITE

                // 获取UI设置并配置
                val uiSettings = uiSettings
                uiSettings.setZoomGesturesEnabled(true)
                uiSettings.setCompassEnabled(true)
                uiSettings.setScrollGesturesEnabled(true)
                uiSettings.setRotateGesturesEnabled(true)

                // 隐藏百度logo
                try {
                    view.showZoomControls(false)
                } catch (e: Exception) {
                    // 忽略错误
                }

                isInitialized = true

                // 初始化时设置地图位置
                val currentLocation = LatLng(currentLat, currentLng)
                val markerOptions = MarkerOptions()
                    .position(currentLocation)
                    .icon(BitmapDescriptorFactory.fromResource(
                        if (isSimulating) android.R.drawable.ic_menu_compass
                        else android.R.drawable.ic_menu_mylocation
                    ))
                    .title(if (isSimulating) "模拟位置" else "当前位置")

                addOverlay(markerOptions)

                // 设置地图位置
                val mapStatus = MapStatus.Builder()
                    .target(currentLocation)
                    .zoom(16f)
                    .build()

                animateMapStatus(MapStatusUpdateFactory.newMapStatus(mapStatus))
            }
        }
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color.White,
    unfocusedBorderColor = Color.Gray,
    focusedLabelColor = Color.White,
    unfocusedLabelColor = Color.Gray,
    cursorColor = Color.White,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White
)

@Composable
fun FavoritesDialog(viewModel: MainViewModel) {
    val context = LocalContext.current
    if (viewModel.showFavoritesDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.toggleFavoritesDialog() },
            title = {
                Text(
                    text = "收藏位置",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    if (viewModel.favoriteLocations.isEmpty()) {
                        item {
                            Text(
                                text = "暂无收藏位置",
                                color = Color.Gray,
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        items(viewModel.favoriteLocations) { location ->
                            FavoriteLocationItem(
                                location = location,
                                onLoad = { viewModel.loadFavoriteLocation(location) },
                                onDelete = { viewModel.removeFromFavorites(location) },
                                onQuickStart = {
                                    viewModel.loadFavoriteLocation(location)
                                    // 快速启动模拟定位
                                    viewModel.toggleSimulation(context)
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.toggleFavoritesDialog() }
                ) {
                    Text("关闭", color = Constants.Colors.Primary)
                }
            },
            containerColor = Color(0xFF2D2D2D),
            textContentColor = Color.White
        )
    }
}

@Composable
fun FavoriteLocationItem(
    location: FavoriteLocation,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
    onQuickStart: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onLoad() },
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = location.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = location.address,
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Text(
                    text = "${location.longitude}, ${location.latitude}",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }

            Row {
                // 快速启动按钮
                IconButton(
                    onClick = onQuickStart,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "快速启动",
                        tint = Color.Green,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // 删除按钮
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = Color.Red,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    LocationSimulatorTheme {
        // Preview中使用模拟的Application
        val mockApp = androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
        MainScreen(viewModel = MainViewModel(mockApp))
    }
}
// endregion
