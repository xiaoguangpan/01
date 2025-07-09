package com.example.locationsimulator

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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

    var isDebugPanelVisible by mutableStateOf(false)
        private set

    // 5次点击切换调试面板
    private var debugPanelClickCount = 0
    private var lastDebugPanelClickTime = 0L

    private var addressTabClickCount = 0
    private var lastAddressTabClickTime = 0L

    fun addDebugMessage(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val newMessage = "[$timestamp] $message"
        debugMessages = (debugMessages + newMessage).takeLast(20) // 保留最新20条
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
                    // 假设输入的是WGS84坐标，转换为BD09用于地图显示
                    val bd09Result = CoordinateConverter.wgs84ToBd09(latitude, longitude)
                    currentLatitude = bd09Result.latitude
                    currentLongitude = bd09Result.longitude

                    addDebugMessage("🗺️ 坐标已更新: WGS84($longitude, $latitude) -> BD09(${bd09Result.longitude}, ${bd09Result.latitude})")
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
        // 移除自动获取当前位置，让用户手动输入
        // android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        //     addDebugMessage("🌍 自动获取当前位置...")
        //     getCurrentLocation(application)
        // }, 2000) // 延迟2秒确保SDK初始化完成
        addDebugMessage("💡 请手动输入地址或坐标开始使用")

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
                        addressQuery = address
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
                        addressQuery = address
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
                        addressQuery = address
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
        if (isSimulating) {
            stopSimulation(context)
        } else {
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
                val result = UnifiedMockLocationManager.start(context, wgsLat, wgsLng)

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
                        val result = UnifiedMockLocationManager.start(context, latWgs, lngWgs)

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
                val result = UnifiedMockLocationManager.start(context, latWgs, lngWgs)

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
                addDebugMessage("💡 点击下方按钮可直接跳转到设置页面")
            }
        }
        addDebugMessage("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        // 自动执行第一个可执行的操作
        instructions.firstOrNull { it.action != null }?.action?.invoke()
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

    companion object {
        private val LOCATION_PERMISSION_REQUEST_CODE = Constants.RequestCodes.LOCATION_PERMISSION
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查并请求定位权限
        checkAndRequestLocationPermission()

        setContent {
            LocationSimulatorTheme {
                val viewModel: MainViewModel = viewModel(
                    factory = MainViewModelFactory(application)
                )
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (!allGranted) {
                // 权限被拒绝，可以显示说明或引导用户到设置页面
                Log.w("MainActivity", "Location permissions denied")
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
            // 地图区域
            BaiduMapView(modifier = Modifier.weight(1f), isSimulating = false, viewModel = viewModel)

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
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val result = appOpsManager.checkOp(
            AppOpsManager.OPSTR_MOCK_LOCATION,
            android.os.Process.myUid(),
            context.packageName
        )
        result == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
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
                    val isMockLocationApp = remember(isDeveloperModeEnabled) {
                        if (isDeveloperModeEnabled) {
                            checkMockLocationAppStatus(context)
                        } else {
                            false
                        }
                    }
                    if (isMockLocationApp) "已开启 (已选择)" else "已开启 (未选择)"
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

            // Shizuku状态
            val shizukuStatus = remember { UnifiedMockLocationManager.getShizukuStatus() }
            StatusItem(
                label = "Shizuku",
                value = when (shizukuStatus.status) {
                    ShizukuStatus.READY -> "已就绪"
                    ShizukuStatus.NO_PERMISSION -> "需授权"
                    ShizukuStatus.NOT_RUNNING -> "未运行"
                    ShizukuStatus.NOT_INSTALLED -> "未安装"
                    ShizukuStatus.ERROR -> "错误"
                },
                isPositive = shizukuStatus.status == ShizukuStatus.READY,
                modifier = Modifier.weight(1f)
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
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier.clickable { onClick() }
            } else {
                Modifier
            }
        ),
        colors = CardDefaults.cardColors(containerColor = Constants.Colors.Surface),
        shape = RoundedCornerShape(Constants.Dimensions.CORNER_RADIUS_SMALL.dp)
    ) {
        Column(
            modifier = Modifier.padding(Constants.Dimensions.PADDING_SMALL.dp)
        ) {
            Text(
                text = label,
                color = Constants.Colors.OnSurfaceVariant,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = if (isPositive) Constants.Colors.Success else Constants.Colors.Error,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
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
        // 初始检查
        var lastDeveloperMode = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) != 0
        } catch (e: Exception) {
            false
        }
        var lastShizukuAvailable = try { Shizuku.pingBinder() } catch (e: Exception) { false }

        isDeveloperModeEnabled = lastDeveloperMode
        isShizukuAvailable = lastShizukuAvailable

        // 初始状态输出
        viewModel.addDebugMessage("📱 初始状态检查 - 开发者模式: ${if (lastDeveloperMode) "已开启" else "未开启"}")
        viewModel.addDebugMessage("📱 初始状态检查 - Shizuku: ${if (lastShizukuAvailable) "可用" else "不可用"}")

        // 每3秒检查一次，但只在状态变化时输出调试信息
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
                    ShizukuStatus.READY -> "就绪"
                    ShizukuStatus.NO_PERMISSION -> "需授权"
                    ShizukuStatus.NOT_RUNNING -> "未运行"
                    ShizukuStatus.NOT_INSTALLED -> "未安装"
                    ShizukuStatus.ERROR -> "错误"
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
