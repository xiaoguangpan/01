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
// import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
// 暂时注释掉百度地图相关导入
// import com.baidu.mapapi.map.*
// import com.baidu.mapapi.model.LatLng
import com.example.locationsimulator.network.BaiduApiService
import com.example.locationsimulator.network.RetrofitClient
import com.example.locationsimulator.network.SnCalculator
import com.example.locationsimulator.network.SuggestionResult
import com.example.locationsimulator.ui.theme.LocationSimulatorTheme
import com.example.locationsimulator.util.CoordinateConverter
import com.example.locationsimulator.util.MockLocationManager
import kotlinx.coroutines.launch

// region ViewModel
enum class InputMode { ADDRESS, COORDINATE }

class MainViewModel : ViewModel() {
    var isSimulating by mutableStateOf(false)
        private set
    var inputMode by mutableStateOf(InputMode.ADDRESS)
        private set

    // Address Mode State
    var addressQuery by mutableStateOf("")
        private set
    var suggestions by mutableStateOf<List<SuggestionResult>>(emptyList())
        private set
    var selectedSuggestion by mutableStateOf<SuggestionResult?>(null)
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

    fun selectSuggestion(suggestion: SuggestionResult) {
        selectedSuggestion = suggestion
        addressQuery = suggestion.name
        suggestions = emptyList()
    }

    fun setInputMode(mode: InputMode) {
        inputMode = mode
        statusMessage = null
    }

    private fun fetchSuggestions(query: String) {
        viewModelScope.launch {
            try {
                val ak = BuildConfig.BAIDU_MAP_AK
                val sk = BuildConfig.BAIDU_MAP_SK
                // A common practice is to use a default region, e.g., "全国" (nationwide)
                val region = "全国"
                val sn = SnCalculator.calculateSn(ak, sk, query, region)
                val response = RetrofitClient.instance.getSuggestions(query, region, ak, sn)
                if (response.status == 0 && response.result != null) {
                    suggestions = response.result
                } else {
                    Log.e("ViewModel", "Suggestion API Error: ${response.message}")
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Suggestion Network Exception", e)
            }
        }
    }

    fun startSimulation(context: Context) {
        statusMessage = "正在处理..."
        var targetLat: Double? = null
        var targetLng: Double? = null
        var displayAddress = ""

        if (inputMode == InputMode.ADDRESS) {
            val location = selectedSuggestion?.location
            if (location == null) {
                statusMessage = "请先从列表中选择一个有效的地址"
                return
            }
            targetLat = location.lat
            targetLng = location.lng
            displayAddress = selectedSuggestion?.name ?: "未知地址"
        } else { // COORDINATE mode
            val parts = coordinateInput.split(',', '，').map { it.trim() }
            if (parts.size != 2) {
                statusMessage = "坐标格式不正确，请使用 '经度,纬度' 格式"
                return
            }
            targetLng = parts[0].toDoubleOrNull()
            targetLat = parts[1].toDoubleOrNull()
            if (targetLat == null || targetLng == null) {
                statusMessage = "经纬度必须是数字"
                return
            }
            displayAddress = coordinateInput
        }

        val (lngWgs, latWgs) = CoordinateConverter.bd09ToWgs84(targetLng, targetLat)
        MockLocationManager.start(context, latWgs, lngWgs)
        isSimulating = true
        statusMessage = "模拟成功: $displayAddress"
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
}
// endregion

// region UI Composables
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LocationSimulatorTheme {
                val viewModel: MainViewModel = viewModel()
                if (viewModel.isSimulating) {
                    SimulatingScreen(
                        address = if (viewModel.inputMode == InputMode.ADDRESS) viewModel.selectedSuggestion?.name ?: "未知" else viewModel.coordinateInput,
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
            Spacer(Modifier.height(24.dp))
            Controls(viewModel, onStartClick = { viewModel.startSimulation(context) })
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
            // 暂时注释掉地图视图
            // BaiduMapView(modifier = Modifier.weight(1f).padding(vertical = 16.dp), isSimulating = true)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Gray.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "地图视图\n(暂时禁用)",
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
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
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(16.dp)
    ) {
        StatusRow("开发者模式", "已开启", Color(0xFF4CAF50), onClick = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        })
        Divider(color = Color.White.copy(alpha = 0.2f), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
        StatusRow("模拟定位应用", "未设置", Color(0xFFFB8C00), onClick = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        })
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
            enabled = (isAddressMode && viewModel.selectedSuggestion != null) || (!isAddressMode && viewModel.coordinateInput.isNotBlank()),
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
    Box {
        OutlinedTextField(
            value = viewModel.addressQuery,
            onValueChange = { viewModel.onAddressQueryChange(it) },
            label = { Text("输入目标地址") },
            placeholder = { Text("例如：北京天安门") },
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors()
        )
        if (viewModel.suggestions.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .padding(top = 64.dp)
                    .fillMaxWidth()
                    .background(Color(0xFF2D3748), shape = RoundedCornerShape(8.dp))
                    .heightIn(max = 200.dp)
            ) {
                items(viewModel.suggestions) { suggestion ->
                    Text(
                        text = "${suggestion.name} (${suggestion.city}${suggestion.district})",
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectSuggestion(suggestion) }
                            .padding(16.dp)
                    )
                }
            }
        }
    }
}

// 暂时注释掉百度地图视图
/*
@Composable
fun BaiduMapView(modifier: Modifier = Modifier, isSimulating: Boolean) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }

    AndroidView(
        factory = { mapView },
        modifier = modifier.clip(RoundedCornerShape(16.dp))
    ) { view ->
        view.map.isMyLocationEnabled = true
        // Further map configuration can be done here
    }
}
*/

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
        MainScreen(viewModel = MainViewModel())
    }
}
// endregion
