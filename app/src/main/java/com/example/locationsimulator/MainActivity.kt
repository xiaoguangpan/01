package com.example.locationsimulator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.locationsimulator.ui.theme.LocationSimulatorTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.locationsimulator.network.RetrofitClient
import com.example.locationsimulator.network.SnCalculator
import com.example.locationsimulator.util.CoordinateConverter
import com.example.locationsimulator.util.MockLocationManager
import kotlinx.coroutines.launch
import android.util.Log
import android.content.Context
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

enum class InputMode {
    ADDRESS, COORDINATE
}

class MainViewModel : ViewModel() {
    var isSimulating by mutableStateOf(false)
        private set
    var inputMode by mutableStateOf(InputMode.ADDRESS)
        private set
    var address by mutableStateOf("")
        private set
    var latitude by mutableStateOf("")
        private set
    var longitude by mutableStateOf("")
        private set
    var statusMessage by mutableStateOf<String?>(null)
        private set

    fun onAddressChange(newAddress: String) { address = newAddress }
    fun onLatitudeChange(newLat: String) { latitude = newLat }
    fun onLongitudeChange(newLng: String) { longitude = newLng }
    fun setInputMode(mode: InputMode) { inputMode = mode }

    fun startSimulation(context: Context) {
        statusMessage = "正在处理..."
        viewModelScope.launch {
            if (inputMode == InputMode.ADDRESS) {
                if (address.isBlank()) {
                    statusMessage = "地址不能为空"
                    return@launch
                }
                try {
                    val ak = BuildConfig.BAIDU_MAP_AK
                    val sk = BuildConfig.BAIDU_MAP_SK
                    val sn = SnCalculator.calculateSn(ak, sk, address)
                    val response = RetrofitClient.instance.geocode(address, ak, sn)
                    if (response.status == 0 && response.result != null) {
                        val (lngWgs, latWgs) = CoordinateConverter.bd09ToWgs84(response.result.location.lng, response.result.location.lat)
                        MockLocationManager.start(context, latWgs, lngWgs)
                        isSimulating = true
                        statusMessage = "模拟成功: ${response.result.location.lat}, ${response.result.location.lng}"
                        Log.d("MainViewModel", "Geocoding Success, Mocking at: $latWgs, $lngWgs")
                    } else {
                        statusMessage = "地址解析失败: ${response.status}"
                        Log.e("MainViewModel", "Geocoding Error: status=${response.status}")
                    }
                } catch (e: Exception) {
                    statusMessage = "网络异常"
                    Log.e("MainViewModel", "Network Exception", e)
                }
            } else { // COORDINATE mode
                val lat = latitude.toDoubleOrNull()
                val lng = longitude.toDoubleOrNull()
                if (lat == null || lng == null) {
                    statusMessage = "经纬度格式不正确"
                    return@launch
                }
                val (lngWgs, latWgs) = CoordinateConverter.bd09ToWgs84(lng, lat)
                MockLocationManager.start(context, latWgs, lngWgs)
                isSimulating = true
                statusMessage = "模拟成功: $lat, $lng"
            }
        }
    }

    fun stopSimulation(context: Context) {
        MockLocationManager.stop(context)
        isSimulating = false
        statusMessage = null
    }
}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LocationSimulatorTheme {
                val viewModel: MainViewModel = viewModel()
                val context = this
                if (viewModel.isSimulating) {
                    SimulatingScreen(
                        address = if (viewModel.inputMode == InputMode.ADDRESS) viewModel.address else "${viewModel.latitude}, ${viewModel.longitude}",
                        onStopClick = { viewModel.stopSimulation(context) }
                    )
                } else {
                    MainScreen(viewModel = viewModel, onStartClick = { viewModel.startSimulation(context) })
                }
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel, onStartClick: () -> Unit) {
    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF1F2937))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Header()
            Spacer(modifier = Modifier.height(16.dp))
            StatusCheck()
            Spacer(modifier = Modifier.weight(1f))
            Controls(viewModel, onStartClick)
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
            Spacer(modifier = Modifier.height(16.dp))
            SimulatingStatus(address)
            
            // Map placeholder area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text("地图预览区域", color = Color.Gray)
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
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(16.dp)
    ) {
        StatusRow("开发者模式", "已开启", Color(0xFF4CAF50))
        Divider(color = Color.White.copy(alpha = 0.2f), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
        StatusRow("模拟定位应用", "未设置", Color(0xFFFB8C00))
    }
}

@Composable
fun StatusRow(title: String, status: String, statusColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontSize = 16.sp)
        Text(status, color = statusColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
        Spacer(modifier = Modifier.height(4.dp))
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
        Spacer(modifier = Modifier.height(16.dp))

        if (isAddressMode) {
            OutlinedTextField(
                value = viewModel.address,
                onValueChange = { viewModel.onAddressChange(it) },
                label = { Text("输入目标地址") },
                placeholder = { Text("例如：北京市海淀区上地十街10号") },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors()
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = viewModel.latitude,
                    onValueChange = { viewModel.onLatitudeChange(it) },
                    label = { Text("纬度 (Lat)") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = textFieldColors()
                )
                OutlinedTextField(
                    value = viewModel.longitude,
                    onValueChange = { viewModel.onLongitudeChange(it) },
                    label = { Text("经度 (Lng)") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = textFieldColors()
                )
            }
        }

        viewModel.statusMessage?.let {
            Text(it, color = Color.Yellow, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onStartClick,
            enabled = (isAddressMode && viewModel.address.isNotBlank()) || (!isAddressMode && viewModel.latitude.isNotBlank() && viewModel.longitude.isNotBlank()),
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

@Preview(showBackground = true)
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
        MainScreen(viewModel = MainViewModel(), onStartClick = {})
    }
}

@Preview(showBackground = true)
@Composable
fun SimulatingPreview() {
    LocationSimulatorTheme {
        SimulatingScreen(address = "北京市海淀区上地十街10号", onStopClick = {})
    }
}
