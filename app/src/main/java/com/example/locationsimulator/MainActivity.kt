package com.example.locationsimulator

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.launch

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

    var statusMessage by mutableStateOf<String?>(null)
        private set

    fun onAddressQueryChange(query: String) {
        addressQuery = query
        selectedSuggestion = null // Clear selection when user types
        if (query.length > 1) {
            fetchSuggestions(query)
        } else {
            suggestions = emptyList()
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
        initBaiduSDK()
        // 应用启动时自动获取当前位置
        getCurrentLocation(application)
    }

    private fun initBaiduSDK() {
        // 初始化建议搜索
        mSuggestionSearch = SuggestionSearch.newInstance()
        mSuggestionSearch?.setOnGetSuggestionResultListener(object : OnGetSuggestionResultListener {
            override fun onGetSuggestionResult(result: SuggestionResult?) {
                if (result == null || result.error != SearchResult.ERRORNO.NO_ERROR) {
                    Log.e("ViewModel", "Suggestion search failed: ${result?.error}")
                    suggestions = emptyList()
                    return
                }

                // 使用getAllSuggestions()获取建议列表
                val allSuggestions = result.allSuggestions
                if (allSuggestions == null || allSuggestions.isEmpty()) {
                    suggestions = emptyList()
                    return
                }

                val suggestionItems = allSuggestions.mapNotNull { info ->
                    // 过滤掉没有坐标信息的建议（如纯文字联想）
                    if (info.key != null && info.pt != null) {
                        SuggestionItem(
                            name = info.key,
                            location = info.pt,
                            uid = info.uid,
                            city = info.city,
                            district = info.district
                        )
                    } else {
                        null
                    }
                }

                suggestions = suggestionItems
                Log.d("ViewModel", "Got ${suggestionItems.size} suggestions")
            }
        })

        // 初始化地理编码
        mGeoCoder = GeoCoder.newInstance()

        // 初始化定位客户端
        initLocationClient()
    }

    private fun initLocationClient() {
        try {
            mLocationClient = LocationClient(application)

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
            Log.d("LocationViewModel", "LocationClient initialized successfully")
        } catch (e: Exception) {
            Log.e("LocationViewModel", "Failed to initialize LocationClient: ${e.message}")
            mLocationClient = null
        }
    }

    fun getCurrentLocation(context: Context) {
        if (mLocationClient == null) {
            statusMessage = "定位服务初始化失败"
            return
        }

        statusMessage = "正在获取当前位置..."
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
                        statusMessage = "定位成功：$address"
                        Log.d("LocationViewModel", "GPS location: $address")
                    }
                    BDLocation.TypeNetWorkLocation -> {
                        // 网络定位成功
                        val address = location.addrStr ?: "未知地址"
                        addressQuery = address
                        statusMessage = "定位成功：$address"
                        Log.d("LocationViewModel", "Network location: $address")
                    }
                    BDLocation.TypeOffLineLocation -> {
                        // 离线定位成功
                        val address = location.addrStr ?: "未知地址"
                        addressQuery = address
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
        Log.d("LocationViewModel", "Fetching suggestions for: $query")
        try {
            mSuggestionSearch?.requestSuggestion(
                SuggestionSearchOption()
                    .city("全国")
                    .keyword(query)
            )
            Log.d("LocationViewModel", "Suggestion request sent successfully")
        } catch (e: Exception) {
            Log.e("LocationViewModel", "Error fetching suggestions: ${e.message}")
            suggestions = emptyList()
        }
    }

    fun startSimulation(context: Context) {
        statusMessage = "正在处理..."

        if (inputMode == InputMode.ADDRESS) {
            // 地址模式：使用百度SDK地理编码
            if (addressQuery.isBlank()) {
                statusMessage = "请输入地址"
                return
            }

            // 设置地理编码监听器
            mGeoCoder?.setOnGetGeoCodeResultListener(object : OnGetGeoCoderResultListener {
                override fun onGetGeoCodeResult(result: GeoCodeResult?) {
                    if (result == null || result.error != SearchResult.ERRORNO.NO_ERROR) {
                        statusMessage = "地址解析失败，请检查地址是否正确"
                        return
                    }

                    val location = result.location
                    if (location != null) {
                        val (lngWgs, latWgs) = CoordinateConverter.bd09ToWgs84(location.longitude, location.latitude)
                        MockLocationManager.start(context, latWgs, lngWgs)
                        isSimulating = true
                        statusMessage = "模拟成功: $addressQuery"
                    } else {
                        statusMessage = "无法获取坐标信息"
                    }
                }

                override fun onGetReverseGeoCodeResult(result: ReverseGeoCodeResult?) {
                    // 不需要逆地理编码
                }
            })

            // 发起地理编码请求
            mGeoCoder?.geocode(GeoCodeOption()
                .city("全国")
                .address(addressQuery))

        } else {
            // 坐标模式：直接使用输入的坐标
            Log.d("LocationViewModel", "Processing coordinate input: $coordinateInput")

            try {
                val parts = coordinateInput.split(',', '，').map { it.trim() }
                if (parts.size != 2) {
                    statusMessage = "坐标格式不正确，请使用 '经度,纬度' 格式"
                    return
                }

                val targetLng = parts[0].toDoubleOrNull()
                val targetLat = parts[1].toDoubleOrNull()

                if (targetLat == null || targetLng == null) {
                    statusMessage = "经纬度必须是数字"
                    return
                }

                // 验证坐标范围
                if (targetLat < -90 || targetLat > 90) {
                    statusMessage = "纬度必须在-90到90之间"
                    return
                }
                if (targetLng < -180 || targetLng > 180) {
                    statusMessage = "经度必须在-180到180之间"
                    return
                }

                Log.d("LocationViewModel", "Converting coordinates: lng=$targetLng, lat=$targetLat")
                val (lngWgs, latWgs) = CoordinateConverter.bd09ToWgs84(targetLng, targetLat)

                Log.d("LocationViewModel", "Starting mock location: lng=$lngWgs, lat=$latWgs")
                MockLocationManager.start(context, latWgs, lngWgs)
                isSimulating = true
                statusMessage = "模拟成功: $coordinateInput"

            } catch (e: Exception) {
                Log.e("LocationViewModel", "Error processing coordinates: ${e.message}")
                statusMessage = "坐标处理失败: ${e.message}"
            }
        }
    }

    fun stopSimulation(context: Context) {
        MockLocationManager.stop(context)
        isSimulating = false
        statusMessage = null
        addressQuery = ""
        coordinateInput = ""
        selectedSuggestion = null
        suggestions = emptyList()
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LocationSimulatorTheme {
                val viewModel: MainViewModel = viewModel(
                    factory = MainViewModelFactory(application)
                )
                if (viewModel.isSimulating) {
                    SimulatingScreen(
                        address = if (viewModel.inputMode == InputMode.ADDRESS) viewModel.addressQuery else viewModel.coordinateInput,
                        onStopClick = { viewModel.stopSimulation(this) }
                    )
                } else {
                    MainScreen(viewModel = viewModel)
                }
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
            Header()
            Spacer(Modifier.height(16.dp))
            StatusCheck()
            Spacer(Modifier.height(16.dp))
            Controls(viewModel, onStartClick = { viewModel.startSimulation(context) })
            Spacer(Modifier.height(16.dp))
            BaiduMapView(modifier = Modifier.weight(1f), isSimulating = false)
        }
    }
}

@Composable
fun SimulatingScreen(address: String, onStopClick: () -> Unit) {
    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF1F2937))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Header()
            Spacer(Modifier.height(16.dp))
            SimulatingStatus(address)
            BaiduMapView(modifier = Modifier.weight(1f).padding(vertical = 16.dp), isSimulating = true)
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
fun StatusCheck() {
    val context = LocalContext.current

    // 检测开发者模式状态
    val isDeveloperModeEnabled = remember {
        try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) != 0
        } catch (e: Exception) {
            false
        }
    }

    // 检测模拟定位应用状态
    val isMockLocationAppSet = remember {
        MockLocationManager.isCurrentAppSelectedAsMockLocationApp(context)
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
            Tab(selected = isAddressMode, onClick = { viewModel.setInputMode(InputMode.ADDRESS) }, text = { Text("地址输入") })
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
fun BaiduMapView(modifier: Modifier = Modifier, isSimulating: Boolean) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }

    AndroidView(
        factory = { mapView },
        modifier = modifier.clip(RoundedCornerShape(16.dp))
    ) { view ->
        view.map.apply {
            // 启用定位图层
            isMyLocationEnabled = true

            // 设置地图类型为卫星图（更暗的效果）
            mapType = BaiduMap.MAP_TYPE_SATELLITE

            // 获取UI设置并配置
            val uiSettings = uiSettings
            // 启用缩放手势
            uiSettings.setZoomGesturesEnabled(true)
            // 启用指南针
            uiSettings.setCompassEnabled(true)
            // 启用平移手势
            uiSettings.setScrollGesturesEnabled(true)
            // 启用旋转手势
            uiSettings.setRotateGesturesEnabled(true)

            // 隐藏百度logo（如果可能）
            try {
                view.showZoomControls(false)
            } catch (e: Exception) {
                // 忽略错误
            }

            // 设置缩放级别和默认位置（北京）
            setMapStatus(MapStatusUpdateFactory.newMapStatus(
                MapStatus.Builder()
                    .target(LatLng(39.915, 116.404)) // 北京坐标
                    .zoom(15f)
                    .build()
            ))
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
