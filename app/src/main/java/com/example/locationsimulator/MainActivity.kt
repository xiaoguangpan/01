package com.example.locationsimulator

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

// region ViewModel
enum class InputMode { ADDRESS, COORDINATE }

class MainViewModel(private val application: android.app.Application) : ViewModel() {
    var isSimulating by mutableStateOf(false)
        private set
    private var _inputMode by mutableStateOf(InputMode.ADDRESS)
    val inputMode: InputMode get() = _inputMode

    // Address Mode State
    var addressQuery by mutableStateOf("")
        private set
    var suggestions by mutableStateOf<List<SuggestionItem>>(emptyList())
        private set
    var selectedSuggestion by mutableStateOf<SuggestionItem?>(null)
        private set

    // Coordinate Mode State
    var coordinateInput by mutableStateOf("")
        private set

    var currentLatitude by mutableStateOf(39.915) // 默认北京纬度
        private set

    var currentLongitude by mutableStateOf(116.404) // 默认北京经度
        private set

    var statusMessage by mutableStateOf<String?>(null)
        private set

    // 调试信息
    var debugMessages by mutableStateOf<List<String>>(emptyList())
        private set

    var isDebugExpanded by mutableStateOf(false)
        private set

    var isDebugPanelVisible by mutableStateOf(true)
        private set

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
            addDebugMessage("开始搜索地址建议...")
            fetchSuggestions(query)
        } else {
            suggestions = emptyList()
            addDebugMessage("清空地址建议列表")
        }
    }

    fun onCoordinateInputChange(input: String) {
        coordinateInput = input
    }

    fun selectSuggestion(suggestion: SuggestionItem) {
        selectedSuggestion = suggestion
        addressQuery = suggestion.name
        suggestions = emptyList()
    }

    fun setInputMode(mode: InputMode) {
        _inputMode = mode
        statusMessage = null
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
        // 应用启动时自动获取当前位置
        getCurrentLocation(application)
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

            // 等待一下确保SDK完全初始化
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                initSuggestionSearch()
            }, 500)

        } catch (e: Exception) {
            addDebugMessage("❌ 服务初始化异常: ${e.message}")
        }
    }

    private fun initSuggestionSearch() {
        try {
            addDebugMessage("🔍 初始化地址建议搜索...")

            // 创建建议搜索实例
            mSuggestionSearch = SuggestionSearch.newInstance()

            if (mSuggestionSearch == null) {
                addDebugMessage("❌ SuggestionSearch创建失败")
                return
            }

            addDebugMessage("✅ SuggestionSearch创建成功")

            // 设置搜索结果监听器
            setupSuggestionSearchListener()

        } catch (e: Exception) {
            addDebugMessage("❌ SuggestionSearch初始化失败: ${e.message}")
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
                    return
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
                        addressQuery = address
                        currentLatitude = location.latitude
                        currentLongitude = location.longitude
                        statusMessage = "定位成功：$address"
                        Log.d("LocationViewModel", "GPS location: $address")
                    }
                    BDLocation.TypeNetWorkLocation -> {
                        // 网络定位成功
                        val address = location.addrStr ?: "未知地址"
                        addressQuery = address
                        currentLatitude = location.latitude
                        currentLongitude = location.longitude
                        statusMessage = "定位成功：$address"
                        Log.d("LocationViewModel", "Network location: $address")
                    }
                    BDLocation.TypeOffLineLocation -> {
                        // 离线定位成功
                        val address = location.addrStr ?: "未知地址"
                        addressQuery = address
                        currentLatitude = location.latitude
                        currentLongitude = location.longitude
                        statusMessage = "离线定位成功：$address"
                        Log.d("LocationViewModel", "Offline location: $address")
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

    private fun performSuggestionSearch(query: String) {
        try {
            // 使用最简单的搜索选项
            val option = SuggestionSearchOption().apply {
                keyword(query)
                // 不设置城市限制，搜索全国范围
            }

            addDebugMessage("📡 发送搜索请求到百度服务器...")
            mSuggestionSearch?.requestSuggestion(option)
            addDebugMessage("✅ 搜索请求已发送，等待服务器响应...")
            Log.d("LocationViewModel", "Suggestion request sent successfully for: $query")

        } catch (e: Exception) {
            addDebugMessage("❌ 发送搜索请求失败: ${e.message}")
            Log.e("LocationViewModel", "Error sending suggestion request: ${e.message}")
            suggestions = emptyList()
        }
    }

    fun startSimulation(context: Context) {
        addDebugMessage("开始模拟定位...")
        statusMessage = "正在处理..."

        if (inputMode == InputMode.ADDRESS) {
            // 地址模式：使用百度SDK地理编码
            addDebugMessage("使用地址模式: '$addressQuery'")
            if (addressQuery.isBlank()) {
                statusMessage = "请输入地址"
                addDebugMessage("地址为空，停止处理")
                return
            }

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
                        addDebugMessage("📍 目标坐标: WGS84($lngWgs, $latWgs)")

                        try {
                            MockLocationManager.start(context, latWgs, lngWgs)

                            // 更新当前坐标为模拟位置
                            currentLatitude = latWgs
                            currentLongitude = lngWgs
                            addDebugMessage("✅ 系统级模拟定位启动成功")
                            addDebugMessage("📱 已覆盖所有定位提供者 (GPS/网络/被动)")
                            addDebugMessage("🎯 当前坐标已更新: ($lngWgs, $latWgs)")

                            isSimulating = true
                            statusMessage = "模拟成功: $addressQuery"
                        } catch (e: Exception) {
                            addDebugMessage("❌ 模拟定位启动失败: ${e.message}")
                            statusMessage = "模拟失败: ${e.message}"
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
                addDebugMessage("📍 目标坐标: WGS84($lngWgs, $latWgs)")
                Log.d("LocationViewModel", "Starting comprehensive mock location: lng=$lngWgs, lat=$latWgs")

                try {
                    MockLocationManager.start(context, latWgs, lngWgs)

                    // 更新当前坐标为模拟位置
                    currentLatitude = latWgs
                    currentLongitude = lngWgs
                    addDebugMessage("✅ 系统级模拟定位启动成功")
                    addDebugMessage("📱 已覆盖所有定位提供者 (GPS/网络/被动)")
                    addDebugMessage("🎯 当前坐标已更新: ($lngWgs, $latWgs)")

                    isSimulating = true
                    statusMessage = "模拟成功: $coordinateInput"
                } catch (e: Exception) {
                    addDebugMessage("❌ 模拟定位启动失败: ${e.message}")
                    statusMessage = "模拟失败: ${e.message}"
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
            MockLocationManager.stop(context)
            isSimulating = false
            statusMessage = null
            addressQuery = ""
            coordinateInput = ""
            selectedSuggestion = null
            suggestions = emptyList()
            addDebugMessage("✅ 所有模拟定位提供者已停止")
            addDebugMessage("🔄 系统定位已恢复正常")
        } catch (e: Exception) {
            addDebugMessage("❌ 停止模拟定位失败: ${e.message}")
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

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
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
        .background(Color(0xFF1F2937))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {

            // 调试信息面板
            DebugPanel(viewModel)
            Spacer(Modifier.height(12.dp))

            StatusCheck(viewModel)
            Spacer(Modifier.height(12.dp))
            Controls(viewModel, onStartClick = { viewModel.startSimulation(context) })
            Spacer(Modifier.height(12.dp))
            BaiduMapView(modifier = Modifier.weight(1f), isSimulating = false, viewModel = viewModel)
        }
    }
}

@Composable
fun SimulatingScreen(address: String, onStopClick: () -> Unit, viewModel: MainViewModel) {
    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF1F2937))) {
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

@Composable
fun StatusCheck(viewModel: MainViewModel) {
    val context = LocalContext.current

    // 使用 remember 和 mutableStateOf 来实现状态更新
    var isDeveloperModeEnabled by remember { mutableStateOf(false) }
    var isMockLocationAppSet by remember { mutableStateOf(false) }

    // 使用 LaunchedEffect 来检查状态（只在状态变化时输出调试信息）
    LaunchedEffect(Unit) {
        // 初始检查
        var lastDeveloperMode = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) != 0
        } catch (e: Exception) {
            false
        }
        var lastMockLocationApp = MockLocationManager.isCurrentAppSelectedAsMockLocationApp(context)

        isDeveloperModeEnabled = lastDeveloperMode
        isMockLocationAppSet = lastMockLocationApp

        // 初始状态输出
        viewModel.addDebugMessage("📱 初始状态检查 - 开发者模式: ${if (lastDeveloperMode) "已开启" else "未开启"}")
        viewModel.addDebugMessage("📱 初始状态检查 - 模拟定位应用: ${if (lastMockLocationApp) "已设置" else "未设置"}")

        // 每3秒检查一次，但只在状态变化时输出调试信息
        while (true) {
            delay(3000)

            val currentDeveloperMode = try {
                Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) != 0
            } catch (e: Exception) {
                false
            }
            val currentMockLocationApp = MockLocationManager.isCurrentAppSelectedAsMockLocationApp(context)

            // 只在状态变化时输出调试信息
            if (currentDeveloperMode != lastDeveloperMode) {
                viewModel.addDebugMessage("🔄 开发者模式状态变化: ${if (currentDeveloperMode) "已开启" else "未开启"}")
                lastDeveloperMode = currentDeveloperMode
                isDeveloperModeEnabled = currentDeveloperMode
            }

            if (currentMockLocationApp != lastMockLocationApp) {
                viewModel.addDebugMessage("🔄 模拟定位应用状态变化: ${if (currentMockLocationApp) "已设置" else "未设置"}")
                lastMockLocationApp = currentMockLocationApp
                isMockLocationAppSet = currentMockLocationApp
            }
        }
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(16.dp)
    ) {
        StatusRow(
            title = "开发者模式",
            status = if (isDeveloperModeEnabled) "已开启" else "未开启",
            statusColor = if (isDeveloperModeEnabled) Color(0xFF4CAF50) else Color(0xFFF44336),
            onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            }
        )
        Divider(color = Color.White.copy(alpha = 0.2f), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
        StatusRow(
            title = "模拟定位应用",
            status = if (isMockLocationAppSet) "已设置" else "未设置",
            statusColor = if (isMockLocationAppSet) Color(0xFF4CAF50) else Color(0xFFFB8C00),
            onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            }
        )
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
fun Controls(viewModel: MainViewModel, onStartClick: () -> Unit) {
    val isAddressMode = viewModel.inputMode == InputMode.ADDRESS
    Column {
        TabRow(
            selectedTabIndex = viewModel.inputMode.ordinal,
            containerColor = Color.White.copy(alpha = 0.1f),
            contentColor = Color.White,
            modifier = Modifier.clip(RoundedCornerShape(8.dp))
        ) {
            Tab(selected = isAddressMode, onClick = {
                viewModel.setInputMode(InputMode.ADDRESS)
                viewModel.onAddressTabClick()
            }, text = { Text("地址输入") })
            Tab(selected = !isAddressMode, onClick = { viewModel.setInputMode(InputMode.COORDINATE) }, text = { Text("坐标输入") })
        }
        Spacer(Modifier.height(16.dp))

        if (isAddressMode) {
            AddressInputWithSuggestions(viewModel)
            Spacer(Modifier.height(8.dp))
            // 获取当前位置按钮
            val context = LocalContext.current
            OutlinedButton(
                onClick = { viewModel.getCurrentLocation(context) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.foundation.BorderStroke(1.dp, Color.White).brush),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("获取当前位置", fontSize = 14.sp)
            }
        } else {
            OutlinedTextField(
                value = viewModel.coordinateInput,
                onValueChange = { viewModel.onCoordinateInputChange(it) },
                label = { Text("经度,纬度") },
                placeholder = { Text("例如: 116.404,39.915") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = textFieldColors()
            )
        }

        viewModel.statusMessage?.let {
            Text(it, color = Color.Yellow, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onStartClick,
            enabled = (isAddressMode && viewModel.addressQuery.isNotBlank()) || (!isAddressMode && viewModel.coordinateInput.isNotBlank()),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("开始模拟", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AddressInputWithSuggestions(viewModel: MainViewModel) {
    Column {
        OutlinedTextField(
            value = viewModel.addressQuery,
            onValueChange = { viewModel.onAddressQueryChange(it) },
            label = { Text("输入目标地址") },
            placeholder = { Text("例如：北京天安门") },
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors()
        )

        if (viewModel.suggestions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2D3748), shape = RoundedCornerShape(8.dp))
                    .heightIn(max = 200.dp)
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

    // 监听坐标变化
    val currentLat = viewModel?.currentLatitude ?: 39.915
    val currentLng = viewModel?.currentLongitude ?: 116.404

    AndroidView(
        factory = { mapView },
        modifier = modifier.clip(RoundedCornerShape(16.dp))
    ) { view ->
        if (!isInitialized) {
            view.map.apply {
                // 启用定位图层
                isMyLocationEnabled = true

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
            }
        }

        // 清除之前的覆盖物
        view.map.clear()

        // 添加当前位置标注
        val currentLocation = LatLng(currentLat, currentLng)
        val markerOptions = MarkerOptions()
            .position(currentLocation)
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            .title(if (isSimulating) "模拟位置" else "当前位置")

        view.map.addOverlay(markerOptions)

        // 更新地图位置并添加动画
        val mapStatus = MapStatus.Builder()
            .target(currentLocation)
            .zoom(16f)
            .build()

        view.map.animateMapStatus(MapStatusUpdateFactory.newMapStatus(mapStatus))
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
