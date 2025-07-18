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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.MyLocationData
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.map.MapStatusUpdateFactory
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
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
        var statusMessage by remember { mutableStateOf("") }
        var mapView by remember { mutableStateOf<MapView?>(null) }
        var baiduMap by remember { mutableStateOf<BaiduMap?>(null) }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 标题
            Text(
                text = "定位模拟器",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // 简化说明
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "📱 简化版定位模拟器",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "• 仅支持标准模拟定位\n• 成功率约30-60%\n• 需配合飞行模式等手动操作",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 地图显示
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(bottom = 16.dp)
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
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 地图生命周期管理
            DisposableEffect(mapView) {
                onDispose {
                    mapView?.onDestroy()
                }
            }

            // 坐标输入
            OutlinedTextField(
                value = coordinateInput,
                onValueChange = { coordinateInput = it },
                label = { Text("输入坐标 (经度,纬度)") },
                placeholder = { Text("113.781601,22.739863") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
            
            // 操作按钮行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 开始/停止模拟按钮
                Button(
                    onClick = {
                        if (isSimulating) {
                            stopMockLocation()
                            isSimulating = false
                            statusMessage = "模拟定位已停止"
                        } else {
                            val result = startMockLocation(coordinateInput)
                            isSimulating = result
                            statusMessage = if (result) {
                                // 更新地图显示
                                updateMapLocation(coordinateInput, baiduMap)
                                "模拟定位已启动"
                            } else {
                                "启动失败，请检查设置"
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSimulating) 
                            MaterialTheme.colorScheme.error 
                        else 
                            MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (isSimulating) "停止模拟" else "开始模拟")
                }
                
                // 收藏当前位置按钮
                OutlinedButton(
                    onClick = {
                        showAddFavoriteDialog(coordinateInput)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("收藏位置")
                }
            }
            
            // 收藏列表按钮
            OutlinedButton(
                onClick = {
                    val intent = Intent(this@SimplifiedMainActivity, FavoriteLocationsActivity::class.java)
                    favoriteListLauncher.launch(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text("📍 查看收藏位置")
            }
            
            // 状态显示
            if (statusMessage.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = statusMessage,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 使用建议
            UsageTipsCard()
        }
    }
    
    @Composable
    fun UsageTipsCard() {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "💡 使用建议",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                val tips = SimplifiedMockLocationManager.getUsageTips()
                tips.forEach { tip ->
                    if (tip.isNotEmpty()) {
                        Text(
                            text = tip,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
    
    private fun startMockLocation(coordinateInput: String): Boolean {
        return try {
            val parts = coordinateInput.split(",")
            if (parts.size != 2) {
                Toast.makeText(this, "坐标格式错误", Toast.LENGTH_SHORT).show()
                return false
            }
            
            val longitude = parts[0].trim().toDouble()
            val latitude = parts[1].trim().toDouble()
            
            val result = SimplifiedMockLocationManager.start(this, latitude, longitude)
            when (result) {
                is MockLocationResult.Success -> {
                    Toast.makeText(this, "模拟定位启动成功", Toast.LENGTH_SHORT).show()
                    true
                }
                is MockLocationResult.Failure -> {
                    Toast.makeText(this, result.error, Toast.LENGTH_LONG).show()
                    false
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "坐标解析失败: ${e.message}", Toast.LENGTH_SHORT).show()
            false
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
    
    private fun showAddFavoriteDialog(coordinateInput: String) {
        try {
            val parts = coordinateInput.split(",")
            if (parts.size != 2) {
                Toast.makeText(this, "坐标格式错误", Toast.LENGTH_SHORT).show()
                return
            }
            
            val longitude = parts[0].trim().toDouble()
            val latitude = parts[1].trim().toDouble()
            
            // 这里应该显示一个对话框让用户输入收藏名称
            // 为了简化，直接使用默认名称
            val defaultName = "位置 ${System.currentTimeMillis() % 10000}"
            val address = "经度: $longitude, 纬度: $latitude"
            
            lifecycleScope.launch {
                favoriteRepository.addFavorite(defaultName, latitude, longitude, address)
                Toast.makeText(this@SimplifiedMainActivity, "已添加到收藏", Toast.LENGTH_SHORT).show()
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
                    // 创建位置数据
                    val locData = MyLocationData.Builder()
                        .accuracy(0f)
                        .direction(0f)
                        .latitude(latitude)
                        .longitude(longitude)
                        .build()

                    // 设置定位数据
                    map.setMyLocationData(locData)

                    // 移动地图到指定位置
                    val latLng = LatLng(latitude, longitude)
                    val mapStatus = MapStatusUpdateFactory.newLatLng(latLng)
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
