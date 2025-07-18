package com.example.locationsimulator

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.MyLocationData
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.MapStatus
import com.baidu.mapapi.search.geocode.GeoCoder
import com.baidu.mapapi.search.geocode.GeoCodeOption
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener
import com.baidu.mapapi.search.geocode.GeoCodeResult
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult
import com.baidu.location.BDLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import androidx.lifecycle.lifecycleScope
import com.example.locationsimulator.repository.FavoriteLocationRepository
import com.example.locationsimulator.ui.theme.LocationSimulatorTheme
import com.example.locationsimulator.util.SimplifiedMockLocationManager
import com.example.locationsimulator.util.MockLocationResult
import com.example.locationsimulator.util.CoordinateConverter
import kotlinx.coroutines.launch

/**
 * 简化的主界面 - 移除Shizuku相关功能，专注核心功能
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class SimplifiedMainActivity : ComponentActivity() {
    
    private lateinit var favoriteRepository: FavoriteLocationRepository
    private val searchHistory = mutableListOf<String>()
    private var locationClient: LocationClient? = null

    // Activity Result Launcher for favorites
    private val favoriteListLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                val latitude = data.getDoubleExtra("latitude", 0.0)
                val longitude = data.getDoubleExtra("longitude", 0.0)
                val address = data.getStringExtra("address") ?: ""

                if (latitude != 0.0 && longitude != 0.0) {
                    startMockLocationFromFavorite(latitude, longitude, address)
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // 初始化收藏仓库
            favoriteRepository = FavoriteLocationRepository(this)

            setContent {
                LocationSimulatorTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainScreen()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SimplifiedMainActivity", "onCreate失败: ${e.message}", e)
            finish()
        }
    }
    
    @Composable
    fun MainScreen() {
        val context = LocalContext.current
        var isSimulating by remember { mutableStateOf(false) }
        var coordinateInput by remember { mutableStateOf("113.781601,22.739863") }
        var addressInput by remember { mutableStateOf(TextFieldValue("")) }
        var showAddressSuggestions by remember { mutableStateOf(false) }
        var addressSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
        var statusMessage by remember { mutableStateOf("") }
        var mapView by remember { mutableStateOf<MapView?>(null) }
        var baiduMap by remember { mutableStateOf<BaiduMap?>(null) }
        var showHelp by remember { mutableStateOf(false) }
        var showFavoritesList by remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxSize()) {
            // 全屏地图显示区域
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                AndroidView(
                    factory = { context ->
                        MapView(context).apply {
                            mapView = this
                            baiduMap = map.apply {
                                try {
                                    // 启用定位图层
                                    isMyLocationEnabled = true
                                    // 设置地图类型为卫星图（深色主题）
                                    mapType = BaiduMap.MAP_TYPE_SATELLITE
                                    // 设置缩放级别
                                    setMapStatus(MapStatusUpdateFactory.zoomTo(15f))
                                    // 启用缩放控件和指南针
                                    showZoomControls(false) // 隐藏默认控件，使用自定义UI
                                    // 设置地图UI设置
                                    uiSettings.apply {
                                        isCompassEnabled = false // 使用自定义指南针
                                        isScrollGesturesEnabled = true
                                        isZoomGesturesEnabled = true
                                        isRotateGesturesEnabled = true
                                        isOverlookingGesturesEnabled = true
                                    }
                                    Log.d("SimplifiedMainActivity", "地图初始化成功 - 深色主题")
                                } catch (e: Exception) {
                                    Log.e("SimplifiedMainActivity", "地图初始化失败: ${e.message}", e)
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { mapView ->
                        // 地图更新逻辑
                        try {
                            mapView.onResume()
                        } catch (e: Exception) {
                            Log.e("SimplifiedMainActivity", "地图更新失败: ${e.message}", e)
                        }
                    }
                )

                // 右侧悬浮控件组
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 帮助按钮
                    FloatingActionButton(
                        onClick = { showHelp = true },
                        modifier = Modifier.size(48.dp),
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "帮助",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 指南针按钮
                    FloatingActionButton(
                        onClick = {
                            // 重置地图方向
                            baiduMap?.let { map ->
                                val currentStatus = map.mapStatus
                                val mapStatus = MapStatusUpdateFactory.newMapStatus(
                                    MapStatus.Builder()
                                        .target(currentStatus.target)
                                        .zoom(currentStatus.zoom)
                                        .rotate(0f)
                                        .overlook(currentStatus.overlook)
                                        .build()
                                )
                                map.animateMapStatus(mapStatus)
                            }
                        },
                        modifier = Modifier.size(48.dp),
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Icon(
                            Icons.Default.Navigation,
                            contentDescription = "指南针",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 放大按钮
                    FloatingActionButton(
                        onClick = {
                            baiduMap?.let { map ->
                                val currentZoom = map.mapStatus.zoom
                                val mapStatus = MapStatusUpdateFactory.zoomTo(currentZoom + 1)
                                map.animateMapStatus(mapStatus)
                            }
                        },
                        modifier = Modifier.size(40.dp),
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "放大",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // 缩小按钮
                    FloatingActionButton(
                        onClick = {
                            baiduMap?.let { map ->
                                val currentZoom = map.mapStatus.zoom
                                val mapStatus = MapStatusUpdateFactory.zoomTo(currentZoom - 1)
                                map.animateMapStatus(mapStatus)
                            }
                        },
                        modifier = Modifier.size(40.dp),
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "缩小",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // 状态显示 - 顶部中央
                if (statusMessage.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isSimulating -> Color(0xFFE53E3E).copy(alpha = 0.95f) // 红色背景表示正在模拟
                                statusMessage.contains("成功") -> Color(0xFF4CAF50).copy(alpha = 0.95f) // 绿色成功
                                statusMessage.contains("失败") -> Color(0xFFFF9800).copy(alpha = 0.95f) // 橙色警告
                                else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                            }
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 状态指示点
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = if (isSimulating) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                            )

                            if (isSimulating) {
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Text(
                                text = statusMessage,
                                color = when {
                                    isSimulating -> Color.White
                                    statusMessage.contains("成功") -> Color.White
                                    statusMessage.contains("失败") -> Color.White
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // 地图生命周期管理
            DisposableEffect(mapView) {
                onDispose {
                    try {
                        mapView?.onDestroy()
                    } catch (e: Exception) {
                        Log.e("SimplifiedMainActivity", "地图销毁失败: ${e.message}", e)
                    }
                }
            }

            // 底部搜索和操作区域 - 悬浮在地图上
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // 统一输入框 - 自动识别地址或坐标
                    OutlinedTextField(
                        value = addressInput,
                        onValueChange = {
                            addressInput = it
                            // 自动判断输入类型并触发相应搜索
                            val inputText = it.text.trim()
                            if (inputText.length > 2) {
                                if (isCoordinateInput(inputText)) {
                                    // 坐标输入，直接更新地图
                                    coordinateInput = inputText
                                    updateMapLocation(inputText, baiduMap)
                                    showAddressSuggestions = false
                                } else {
                                    // 地址输入，触发搜索建议
                                    searchAddressSuggestions(inputText) { suggestions ->
                                        addressSuggestions = suggestions
                                        showAddressSuggestions = suggestions.isNotEmpty()
                                    }
                                }
                            } else {
                                showAddressSuggestions = false
                            }
                        },
                        label = { Text("搜索地址或输入坐标") },
                        placeholder = { Text("深圳市南山区 或 113.781601,22.739863") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = Color(0xFF6B7280)
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    // 获取当前位置
                                    getCurrentLocation { lat, lng ->
                                        if (lat != 0.0 && lng != 0.0) {
                                            addressInput = TextFieldValue("当前位置")
                                            coordinateInput = "$lng,$lat"
                                            updateMapLocation(coordinateInput, baiduMap)
                                            statusMessage = "已获取当前位置"
                                        } else {
                                            statusMessage = "获取当前位置失败"
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.MyLocation,
                                    contentDescription = "获取当前位置",
                                    tint = Color(0xFF2196F3)
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(12.dp)
                    )


            
                    // 操作按钮区域
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 开始/停止模拟按钮
                        Button(
                            onClick = {
                                val inputText = addressInput.text.trim()
                                if (inputText.isEmpty()) {
                                    statusMessage = "请输入地址或坐标"
                                    return@Button
                                }

                                if (isSimulating) {
                                    stopMockLocation()
                                    isSimulating = false
                                    statusMessage = "模拟定位已停止"
                                } else {
                                    if (isCoordinateInput(inputText)) {
                                        // 坐标输入
                                        coordinateInput = inputText
                                        performMockLocation(inputText, baiduMap) { success, message ->
                                            isSimulating = success
                                            statusMessage = message
                                        }
                                    } else {
                                        // 地址输入，先转换为坐标
                                        geocodeAddress(inputText) { lat, lng ->
                                            if (lat != 0.0 && lng != 0.0) {
                                                coordinateInput = "$lng,$lat"
                                                performMockLocation(coordinateInput, baiduMap) { success, message ->
                                                    isSimulating = success
                                                    statusMessage = message
                                                }
                                            } else {
                                                statusMessage = "地址解析失败"
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(2f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSimulating)
                                    Color(0xFFE53E3E) // 红色停止按钮
                                else
                                    Color(0xFF2196F3) // 蓝色开始按钮
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                if (isSimulating) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (isSimulating) "停止模拟" else "开始模拟",
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // 收藏位置按钮
                        OutlinedButton(
                            onClick = {
                                val inputText = addressInput.text.trim()
                                if (inputText.isNotEmpty()) {
                                    showAddFavoriteDialog(inputText)
                                } else {
                                    statusMessage = "请先输入地址或坐标"
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF6B7280)
                            )
                        }

                        // 收藏列表按钮
                        OutlinedButton(
                            onClick = { showFavoritesList = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                Icons.Default.List,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF6B7280)
                            )
                        }
                    }
                }
            }
            
            // 地址建议下拉列表 - 悬浮在底部卡片上方
            if (showAddressSuggestions && addressSuggestions.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 200.dp), // 在底部卡片上方显示
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
                    )
                ) {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 200.dp)
                    ) {
                        items(addressSuggestions) { suggestion ->
                            Text(
                                text = suggestion,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val cleanSuggestion = suggestion.replace("🕒 ", "")
                                        addressInput = TextFieldValue(cleanSuggestion)
                                        showAddressSuggestions = false

                                        // 添加到搜索历史
                                        addToSearchHistory(cleanSuggestion)

                                        // 自动搜索并更新地图
                                        geocodeAddress(cleanSuggestion) { lat, lng ->
                                            if (lat != 0.0 && lng != 0.0) {
                                                coordinateInput = "$lng,$lat"
                                                updateMapLocation(coordinateInput, baiduMap)
                                            }
                                        }
                                    }
                                    .padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (suggestion != addressSuggestions.last()) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            // 帮助对话框
            if (showHelp) {
                HelpDialog(
                    onDismiss = { showHelp = false },
                    onOpenDeveloperOptions = {
                        SimplifiedMockLocationManager.openDeveloperOptions(this@SimplifiedMainActivity)
                    }
                )
            }
        }
    }
    
    @Composable
    fun HelpDialog(
        onDismiss: () -> Unit,
        onOpenDeveloperOptions: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("使用帮助") },
            text = {
                LazyColumn {
                    item {
                        Text(
                            text = "📱 使用说明",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "1. 在搜索框输入目标地址或坐标（经度,纬度）\n" +
                                    "2. 点击\"开始模拟\"启动位置模拟\n" +
                                    "3. 可收藏常用位置便于快速使用\n" +
                                    "4. 支持百度坐标系，会自动转换为GPS坐标\n\n",
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Text(
                            text = "🗺️ 坐标获取",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "• 百度地图坐标获取工具：\n" +
                                    "  https://lbs.baidu.com/maptool/getpoint\n" +
                                    "• 在地图上点击获取准确的百度坐标\n" +
                                    "• 格式：经度,纬度 (如：113.781601,22.739863)\n" +
                                    "• 应用会自动转换坐标系确保定位准确\n\n",
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Text(
                            text = "⚙️ 设置要求",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "• 需要开启开发者模式\n" +
                                    "• 需要选择本应用为模拟位置应用\n" +
                                    "• 建议配合飞行模式使用提高成功率\n\n",
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Text(
                            text = "💡 使用技巧",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "• 钉钉打卡：开启飞行模式3秒→关闭→立即打开钉钉\n" +
                                    "• 高德地图：关闭WiFi→飞行模式3秒→关闭→重启高德\n" +
                                    "• 百度地图：由于坐标系转换，定位更加准确\n" +
                                    "• 成功率约30-60%，需要多次尝试\n" +
                                    "• 坐标系已自动转换，无需手动处理\n\n",
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Text(
                            text = "🔧 快速设置",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = onOpenDeveloperOptions) {
                        Text("开发者选项")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("关闭")
                    }
                }
            }
        )
    }
    
    /**
     * 判断输入是否为坐标格式
     */
    private fun isCoordinateInput(input: String): Boolean {
        return try {
            val parts = input.split(",")
            if (parts.size != 2) return false

            val longitude = parts[0].trim().toDouble()
            val latitude = parts[1].trim().toDouble()

            // 检查坐标范围是否合理
            val isValid = longitude in -180.0..180.0 && latitude in -90.0..90.0

            if (isValid) {
                Log.d("SimplifiedMainActivity", "✅ 识别为坐标输入: $longitude, $latitude")
            }

            isValid
        } catch (e: Exception) {
            Log.d("SimplifiedMainActivity", "❌ 非坐标格式: $input")
            false
        }
    }

    /**
     * 执行模拟定位
     */
    private fun performMockLocation(
        coordinateInput: String,
        baiduMap: BaiduMap?,
        callback: (Boolean, String) -> Unit
    ) {
        try {
            val parts = coordinateInput.split(",")
            if (parts.size != 2) {
                callback(false, "坐标格式错误")
                return
            }

            val longitude = parts[0].trim().toDouble()
            val latitude = parts[1].trim().toDouble()

            Log.d("SimplifiedMainActivity", "🗺️ 原始坐标 (BD09LL): $latitude, $longitude")

            // 将百度坐标系(BD09LL)转换为GPS坐标系(WGS84)用于模拟定位
            val wgs84Coords = CoordinateConverter.bd09ToWgs84(longitude, latitude)
            val wgs84Lng = wgs84Coords.first
            val wgs84Lat = wgs84Coords.second

            Log.d("SimplifiedMainActivity", "📍 转换后坐标 (WGS84): $wgs84Lat, $wgs84Lng")

            // 使用WGS84坐标进行模拟定位
            val result = SimplifiedMockLocationManager.start(this, wgs84Lat, wgs84Lng)
            when (result) {
                is MockLocationResult.Success -> {
                    // 地图显示仍使用百度坐标系
                    updateMapLocation(coordinateInput, baiduMap)
                    callback(true, "模拟定位已启动 (坐标已转换)")
                }
                is MockLocationResult.Failure -> {
                    callback(false, result.error)
                }
            }
        } catch (e: Exception) {
            Log.e("SimplifiedMainActivity", "坐标转换失败: ${e.message}", e)
            callback(false, "坐标解析失败: ${e.message}")
        }
    }

    /**
     * 地址搜索建议 - 包含搜索历史
     */
    private fun searchAddressSuggestions(query: String, callback: (List<String>) -> Unit) {
        val suggestions = mutableListOf<String>()

        // 优先显示搜索历史中匹配的项目
        searchHistory.forEach { historyItem ->
            if (historyItem.contains(query, ignoreCase = true) && !suggestions.contains(historyItem)) {
                suggestions.add("🕒 $historyItem")
            }
        }

        // 常见地址建议
        val commonPlaces = listOf(
            "深圳市南山区",
            "深圳市福田区",
            "深圳市罗湖区",
            "深圳市宝安区",
            "广州市天河区",
            "广州市越秀区",
            "北京市朝阳区",
            "北京市海淀区",
            "上海市浦东新区",
            "上海市黄浦区"
        )

        commonPlaces.forEach { place ->
            if (place.contains(query, ignoreCase = true) && !suggestions.any { it.endsWith(place) }) {
                suggestions.add(place)
            }
        }

        // 限制建议数量
        callback(suggestions.take(8))
    }

    /**
     * 添加到搜索历史
     */
    private fun addToSearchHistory(query: String) {
        val cleanQuery = query.replace("🕒 ", "")
        if (cleanQuery.isNotBlank() && !searchHistory.contains(cleanQuery)) {
            searchHistory.add(0, cleanQuery)
            // 限制历史记录数量
            if (searchHistory.size > 10) {
                searchHistory.removeAt(searchHistory.size - 1)
            }
            Log.d("SimplifiedMainActivity", "添加搜索历史: $cleanQuery")
        }
    }

    /**
     * 地址转坐标 - 百度地址解析返回BD09LL坐标系
     */
    private fun geocodeAddress(address: String, callback: (Double, Double) -> Unit) {
        try {
            val geoCoder = GeoCoder.newInstance()
            geoCoder.setOnGetGeoCodeResultListener(object : OnGetGeoCoderResultListener {
                override fun onGetGeoCodeResult(result: GeoCodeResult?) {
                    if (result?.location != null) {
                        val bdLat = result.location.latitude
                        val bdLng = result.location.longitude

                        Log.d("SimplifiedMainActivity", "🗺️ 地址解析结果 (BD09LL): $address -> $bdLat, $bdLng")

                        // 百度地址解析返回的是BD09LL坐标系，直接返回
                        // 注意：这里返回的是百度坐标系，用于地图显示
                        // 实际模拟定位时会在performMockLocation中转换为WGS84
                        callback(bdLat, bdLng)
                    } else {
                        Log.w("SimplifiedMainActivity", "地址解析失败: $address")
                        callback(0.0, 0.0)
                    }
                    geoCoder.destroy()
                }

                override fun onGetReverseGeoCodeResult(result: ReverseGeoCodeResult?) {
                    // 不需要实现
                }
            })

            // 使用深圳作为默认城市进行地址解析
            geoCoder.geocode(GeoCodeOption().city("深圳").address(address))
        } catch (e: Exception) {
            Log.e("SimplifiedMainActivity", "地址转坐标失败: ${e.message}", e)
            callback(0.0, 0.0)
        }
    }
    
    private fun startMockLocationFromFavorite(latitude: Double, longitude: Double, address: String) {
        try {
            Log.d("SimplifiedMainActivity", "🗺️ 收藏位置坐标 (BD09LL): $latitude, $longitude")

            // 将百度坐标系转换为GPS坐标系用于模拟定位
            val wgs84Coords = CoordinateConverter.bd09ToWgs84(longitude, latitude)
            val wgs84Lng = wgs84Coords.first
            val wgs84Lat = wgs84Coords.second

            Log.d("SimplifiedMainActivity", "📍 转换后坐标 (WGS84): $wgs84Lat, $wgs84Lng")

            val result = SimplifiedMockLocationManager.start(this, wgs84Lat, wgs84Lng)
            when (result) {
                is MockLocationResult.Success -> {
                    Toast.makeText(this, "已启动模拟定位: $address (坐标已转换)", Toast.LENGTH_SHORT).show()
                }
                is MockLocationResult.Failure -> {
                    Toast.makeText(this, result.error, Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e("SimplifiedMainActivity", "收藏位置坐标转换失败: ${e.message}", e)
            Toast.makeText(this, "坐标转换失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun stopMockLocation() {
        SimplifiedMockLocationManager.stop(this)
        Toast.makeText(this, "模拟定位已停止", Toast.LENGTH_SHORT).show()
    }
    
    private fun showAddFavoriteDialog(input: String) {
        try {
            // 判断输入是地址还是坐标
            val isCoordinate = input.contains(",") && input.split(",").size == 2

            if (isCoordinate) {
                // 坐标输入
                val parts = input.split(",")
                val longitude = parts[0].trim().toDouble()
                val latitude = parts[1].trim().toDouble()

                val defaultName = "位置 ${System.currentTimeMillis() % 10000}"
                val address = "经度: $longitude, 纬度: $latitude"

                lifecycleScope.launch {
                    favoriteRepository.addFavorite(defaultName, latitude, longitude, address)
                    Toast.makeText(this@SimplifiedMainActivity, "已添加到收藏", Toast.LENGTH_SHORT).show()
                }
            } else {
                // 地址输入，先转换为坐标
                geocodeAddress(input) { lat, lng ->
                    if (lat != 0.0 && lng != 0.0) {
                        lifecycleScope.launch {
                            favoriteRepository.addFavorite(input, lat, lng, input)
                            Toast.makeText(this@SimplifiedMainActivity, "已添加到收藏", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@SimplifiedMainActivity, "地址解析失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "添加收藏失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 更新地图位置显示
     */
    private fun updateMapLocation(coordinateInput: String, baiduMap: BaiduMap?) {
        try {
            val parts = coordinateInput.split(",")
            if (parts.size == 2) {
                val longitude = parts[0].trim().toDouble()
                val latitude = parts[1].trim().toDouble()

                baiduMap?.let { map ->
                    // 清除之前的标记
                    map.clear()

                    // 创建位置数据
                    val locData = MyLocationData.Builder()
                        .accuracy(0f)
                        .direction(0f)
                        .latitude(latitude)
                        .longitude(longitude)
                        .build()

                    // 设置定位数据
                    map.setMyLocationData(locData)

                    // 添加标记点
                    val latLng = LatLng(latitude, longitude)
                    try {
                        val marker = MarkerOptions()
                            .position(latLng)
                            .title("模拟位置")
                        map.addOverlay(marker)
                    } catch (e: Exception) {
                        Log.w("SimplifiedMainActivity", "添加地图标记失败: ${e.message}")
                    }

                    // 移动地图到指定位置
                    val mapStatus = MapStatusUpdateFactory.newLatLngZoom(latLng, 16f)
                    map.animateMapStatus(mapStatus)
                }
            }
        } catch (e: Exception) {
            Log.e("SimplifiedMainActivity", "更新地图位置失败: ${e.message}")
        }
    }

    /**
     * 获取当前位置
     */
    private fun getCurrentLocation(callback: (Double, Double) -> Unit) {
        try {
            if (locationClient == null) {
                locationClient = LocationClient(applicationContext)
            }

            val option = LocationClientOption().apply {
                locationMode = LocationClientOption.LocationMode.Hight_Accuracy
                setCoorType("bd09ll") // 百度坐标系
                setScanSpan(0) // 单次定位
                setIsNeedAddress(false)
                setIsNeedLocationDescribe(false)
                setNeedDeviceDirect(false)
                setLocationNotify(false)
                setIgnoreKillProcess(true)
                setIsNeedLocationPoiList(false)
                SetIgnoreCacheException(false)
                setIsNeedAltitude(false)
                setEnableSimulateGps(false)
            }

            locationClient?.locOption = option

            val locationListener = object : BDLocationListener {
                override fun onReceiveLocation(location: BDLocation?) {
                    locationClient?.stop()

                    if (location != null && location.locType <= 161) {
                        val latitude = location.latitude
                        val longitude = location.longitude
                        Log.d("SimplifiedMainActivity", "📍 获取当前位置成功: $latitude, $longitude")
                        callback(latitude, longitude)
                    } else {
                        Log.w("SimplifiedMainActivity", "获取当前位置失败: ${location?.locType}")
                        callback(0.0, 0.0)
                    }
                }
            }

            locationClient?.registerLocationListener(locationListener)
            locationClient?.start()

        } catch (e: Exception) {
            Log.e("SimplifiedMainActivity", "获取当前位置异常: ${e.message}", e)
            callback(0.0, 0.0)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            // 地图生命周期管理会在Compose中处理
            Log.d("SimplifiedMainActivity", "Activity onResume")
        } catch (e: Exception) {
            Log.e("SimplifiedMainActivity", "onResume失败: ${e.message}", e)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            // 地图生命周期管理会在Compose中处理
            Log.d("SimplifiedMainActivity", "Activity onPause")
        } catch (e: Exception) {
            Log.e("SimplifiedMainActivity", "onPause失败: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        try {
            // 清理LocationClient
            locationClient?.stop()
            locationClient = null

            super.onDestroy()
            Log.d("SimplifiedMainActivity", "Activity onDestroy")
        } catch (e: Exception) {
            Log.e("SimplifiedMainActivity", "onDestroy失败: ${e.message}", e)
        }
    }
}
