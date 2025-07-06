# **PRD：安卓虚拟定位模拟器 (AI开发者版)**

| 版本 | 日期       | 作者 | 变更描述                   |
| :--- | :--------- | :--- | :------------------------- |
| 2.0  | 2025-07-06 | Cline | 全面重构以适应国内环境和AI开发 |

---

## 1. 项目概述

### 1.1. 项目目标
开发一款界面简洁、交互直接的安卓虚拟定位应用。应用需在国内网络环境下稳定运行，并为AI辅助开发提供清晰、可执行的技术指令。

### 1.2. 核心用户
*   **应用开发者与测试人员:** 需要在国内地图坐标系下测试应用定位功能。
*   **普通用户:** 希望在国内主流地图服务（如滴滴、美团）中模拟定位。

## 2. 功能需求 (面向AI实现)

### 2.1. 核心功能模块

#### 2.2.1. 安卓环境自检模块
*   **功能描述:** 应用启动时，必须自动检测运行环境是否满足模拟定位的基本要求。
*   **技术指令:**
    1.  **检测开发者模式:** 调用安卓系统API，判断 `Settings.Secure.DEVELOPMENT_SETTINGS_ENABLED` 是否为 `true`。
    2.  **检测模拟定位应用:** 检查当前应用的包名是否与系统设置中“选择模拟位置信息应用”的包名一致。
    3.  **UI反馈:** 在界面上明确展示“开发者模式”和“模拟定位应用”的状态（例如：已开启/未设置）。
    4.  **快捷跳转:** 如果状态不正常，提供一个按钮，触发 `Intent` 跳转到对应的系统设置页面 (`Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS`)。

#### 2.2.2. 地址解析与坐标获取模块
*   **功能描述:** 用户输入中文地址，应用通过调用百度地图API将其转换为可用的坐标。
*   **技术指令:**
    1.  **API选型:** 使用**百度地图Web服务API - 地理编码服务**。
    2.  **接口地址:** `http://api.map.baidu.com/geocoding/v3/`
    3.  **请求参数:**
        *   `address`: 用户输入的地址字符串 (例如: "北京市海淀区上地十街10号")。
        *   `output`: `json`。
        *   `ak`: `YOUR_DEVELOPER_AK` (需要开发者自行在百度地图开放平台申请)。
    4.  **数据解析:** 从返回的JSON中提取 `result.location.lng` (经度) 和 `result.location.lat` (纬度)。

#### 2.2.3. 坐标系转换模块 (关键步骤)
*   **功能描述:** 将从百度API获取的BD-09坐标，转换为安卓系统`LocationManager`能够识别的坐标系。
*   **技术背景:** 百度地图使用自定义的BD-09坐标系，而安卓原生API通常使用WGS-84标准。直接使用BD-09坐标进行模拟会导致位置偏差。
*   **技术指令:**
    1.  寻找一个可靠的开源坐标转换库或算法（例如在GitHub上搜索 "BD09 to WGS84"）。
    2.  实现一个函数 `convertBd09ToWgs84(lng, lat)`。
    3.  在调用模拟定位API之前，必须先对从百度获取的坐标执行此转换。

#### 2.2.4. 模拟定位控制与反馈模块
*   **功能描述:** 控制模拟定位的开始和停止，并通过地图提供实时视觉反馈。
*   **技术指令:**
    1.  **启动模拟:**
        *   获取用户输入的地址，调用地址解析模块，然后调用坐标转换模块。
        *   使用 `android.location.LocationManager` 的 `addTestProvider` 和 `setTestProviderLocation` 方法设置模拟位置。
        *   传入转换后的WGS-84坐标。
    2.  **停止模拟:** 调用 `removeTestProvider` 方法。
    3.  **视觉反馈:**
        *   在首页集成一个**百度地图Android SDK**的MapView。
        *   MapView的核心作用是**显示设备当前位置**。
        *   当模拟启动后，MapView上代表“我的位置”的蓝点会从真实位置“跳”到模拟位置，从而为用户提供直观的成功反馈。

### 2.3. AI开发流程建议
```
1. **Setup Project:**
   - Create Android Project (Kotlin, Jetpack Compose).
   - Integrate Baidu Maps Android SDK.
   - Add network request library (e.g., OkHttp, Retrofit).

2. **Implement UI:**
   - Build UI based on `prototype.html`.
   - Create components for status check, address input, map view, and control buttons.

3. **Implement Modules (in order):**
   - **Environment Check Module:** Implement the logic for checking settings and navigating the user.
   - **Geocoding Module:**
     - Implement the network request to Baidu Geocoding API.
     - Parse the JSON response.
   - **Coordinate Conversion Module:**
     - Integrate or write the BD-09 to WGS-84 conversion logic.
   - **Location Simulation Module:**
     - Implement the `LocationManager` logic for starting and stopping the mock location.

4. **Connect Logic to UI:**
   - Wire up the "Start" button to trigger the full sequence: Geocoding -> Conversion -> Simulation.
   - Update the UI to reflect the "Simulating" state.
   - Ensure the MapView correctly displays the location updates.
