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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material3.*
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
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.MyLocationData
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.search.geocode.GeoCoder
import com.baidu.mapapi.search.geocode.GeoCodeOption
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener
import com.baidu.mapapi.search.geocode.GeoCodeResult
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult
import androidx.lifecycle.lifecycleScope
import com.example.locationsimulator.repository.FavoriteLocationRepository
import com.example.locationsimulator.ui.theme.LocationSimulatorTheme
import com.example.locationsimulator.util.SimplifiedMockLocationManager
import com.example.locationsimulator.util.MockLocationResult
import kotlinx.coroutines.launch

/**
 * 简化的主界面 - 移除Shizuku相关功能，专注核心功能
 */
class SimplifiedMainActivity : ComponentActivity() {
    
    private lateinit var favoriteRepository: FavoriteLocationRepository
    
    // 收藏列表界面启动器
    private val favoriteListLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                val latitude = data.getDoubleExtra("latitude", 0.0)
                val longitude = data.getDoubleExtra("longitude", 0.0)
                val address = data.getStringExtra("address") ?: ""
                val startSimulation = data.getBooleanExtra("start_simulation", false)
                
                if (startSimulation) {
                    // 从收藏列表返回，启动模拟定位
                    startMockLocationFromFavorite(latitude, longitude, address)
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶部标题栏
                TopAppBar(
                    title = { Text("定位模拟器") },
                    actions = {
                        IconButton(onClick = { showHelp = true }) {
                            Icon(Icons.Default.Info, contentDescription = "帮助")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            
                // 主要地图显示区域
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    AndroidView(
                        factory = { context ->
                            MapView(context).apply {
                                mapView = this
                                baiduMap = map.apply {
                                    // 启用定位图层
                                    isMyLocationEnabled = true
                                    // 设置地图类型
                                    mapType = BaiduMap.MAP_TYPE_NORMAL
                                    // 设置缩放级别
                                    setMapStatus(MapStatusUpdateFactory.zoomTo(15f))
                                    // 启用缩放控件和指南针
                                    try {
                                        // 百度地图的UI设置
                                        showZoomControls(true)
                                    } catch (e: Exception) {
                                        Log.w("SimplifiedMainActivity", "设置地图UI控件失败: ${e.message}")
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // 状态显示
                    if (statusMessage.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSimulating)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Text(
                                text = statusMessage,
                                modifier = Modifier.padding(12.dp),
                                color = if (isSimulating)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // 地图生命周期管理
                DisposableEffect(mapView) {
                    onDispose {
                        mapView?.onDestroy()
                    }
                }

                // 底部输入和操作区域
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // 地址输入框
                        OutlinedTextField(
                            value = addressInput,
                            onValueChange = {
                                addressInput = it
                                if (it.text.length > 2) {
                                    // 触发地址搜索建议
                                    searchAddressSuggestions(it.text) { suggestions ->
                                        addressSuggestions = suggestions
                                        showAddressSuggestions = suggestions.isNotEmpty()
                                    }
                                } else {
                                    showAddressSuggestions = false
                                }
                            },
                            label = { Text("输入地址") },
                            placeholder = { Text("例如：深圳市南山区") },
                            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        )

                        // 坐标输入框
                        OutlinedTextField(
                            value = coordinateInput,
                            onValueChange = { coordinateInput = it },
                            label = { Text("或输入坐标 (经度,纬度)") },
                            placeholder = { Text("113.781601,22.739863") },
                            leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        )
            
                        // 操作按钮区域
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 开始/停止模拟按钮
                            Button(
                                onClick = {
                                    val inputToUse = if (addressInput.text.isNotEmpty()) {
                                        // 如果有地址输入，先转换为坐标
                                        geocodeAddress(addressInput.text) { lat, lng ->
                                            if (lat != 0.0 && lng != 0.0) {
                                                coordinateInput = "$lng,$lat"
                                                performMockLocation(coordinateInput, baiduMap) { success, message ->
                                                    isSimulating = success
                                                    statusMessage = message
                                                }
                                            }
                                        }
                                        return@Button
                                    } else {
                                        coordinateInput
                                    }

                                    if (isSimulating) {
                                        stopMockLocation()
                                        isSimulating = false
                                        statusMessage = "模拟定位已停止"
                                    } else {
                                        performMockLocation(inputToUse, baiduMap) { success, message ->
                                            isSimulating = success
                                            statusMessage = message
                                        }
                                    }
                                },
                                modifier = Modifier.weight(2f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSimulating)
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    if (isSimulating) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isSimulating) "停止模拟" else "开始模拟")
                            }

                            // 收藏位置按钮
                            OutlinedButton(
                                onClick = {
                                    val inputToUse = if (addressInput.text.isNotEmpty()) {
                                        addressInput.text
                                    } else {
                                        coordinateInput
                                    }
                                    showAddFavoriteDialog(inputToUse)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FavoriteBorder, contentDescription = null, modifier = Modifier.size(16.dp))
                            }

                            // 收藏列表按钮
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(this@SimplifiedMainActivity, FavoriteLocationsActivity::class.java)
                                    favoriteListLauncher.launch(intent)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FormatListBulleted, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            
            // 地址建议下拉列表
            if (showAddressSuggestions && addressSuggestions.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                                        addressInput = TextFieldValue(suggestion)
                                        showAddressSuggestions = false
                                        // 自动搜索并更新地图
                                        geocodeAddress(suggestion) { lat, lng ->
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
                            text = "1. 在地址框输入目标地址，或在坐标框输入经纬度\n" +
                                    "2. 点击\"开始模拟\"启动位置模拟\n" +
                                    "3. 可收藏常用位置便于快速使用\n\n",
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
                                    "• 成功率约30-60%，需要多次尝试\n\n",
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

            val result = SimplifiedMockLocationManager.start(this, latitude, longitude)
            when (result) {
                is MockLocationResult.Success -> {
                    updateMapLocation(coordinateInput, baiduMap)
                    callback(true, "模拟定位已启动")
                }
                is MockLocationResult.Failure -> {
                    callback(false, result.error)
                }
            }
        } catch (e: Exception) {
            callback(false, "坐标解析失败: ${e.message}")
        }
    }

    /**
     * 地址搜索建议
     */
    private fun searchAddressSuggestions(query: String, callback: (List<String>) -> Unit) {
        // 简单的地址建议，实际项目中可以调用百度地图API
        val suggestions = mutableListOf<String>()

        // 常见地址建议
        val commonPlaces = listOf(
            "深圳市南山区",
            "深圳市福田区",
            "深圳市罗湖区",
            "广州市天河区",
            "北京市朝阳区",
            "上海市浦东新区"
        )

        commonPlaces.forEach { place ->
            if (place.contains(query, ignoreCase = true)) {
                suggestions.add(place)
            }
        }

        callback(suggestions)
    }

    /**
     * 地址转坐标
     */
    private fun geocodeAddress(address: String, callback: (Double, Double) -> Unit) {
        try {
            val geoCoder = GeoCoder.newInstance()
            geoCoder.setOnGetGeoCodeResultListener(object : OnGetGeoCoderResultListener {
                override fun onGetGeoCodeResult(result: GeoCodeResult?) {
                    if (result?.location != null) {
                        callback(result.location.latitude, result.location.longitude)
                    } else {
                        callback(0.0, 0.0)
                    }
                    geoCoder.destroy()
                }

                override fun onGetReverseGeoCodeResult(result: ReverseGeoCodeResult?) {
                    // 不需要实现
                }
            })

            geoCoder.geocode(GeoCodeOption().city("深圳").address(address))
        } catch (e: Exception) {
            Log.e("SimplifiedMainActivity", "地址转坐标失败: ${e.message}")
            callback(0.0, 0.0)
        }
    }
    
    private fun startMockLocationFromFavorite(latitude: Double, longitude: Double, address: String) {
        val result = SimplifiedMockLocationManager.start(this, latitude, longitude)
        when (result) {
            is MockLocationResult.Success -> {
                Toast.makeText(this, "已启动模拟定位: $address", Toast.LENGTH_SHORT).show()
            }
            is MockLocationResult.Failure -> {
                Toast.makeText(this, result.error, Toast.LENGTH_LONG).show()
            }
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

    override fun onResume() {
        super.onResume()
        // 地图生命周期管理会在Compose中处理
    }

    override fun onPause() {
        super.onPause()
        // 地图生命周期管理会在Compose中处理
    }
}
