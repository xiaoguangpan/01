package com.example.locationsimulator.util

/**
 * 模拟定位结果封装类
 */
sealed class MockLocationResult {
    /**
     * 成功结果
     */
    data class Success(val strategy: MockLocationStrategy) : MockLocationResult()
    
    /**
     * 失败结果
     */
    data class Failure(val error: String) : MockLocationResult()
}

/**
 * 模拟定位策略枚举
 */
enum class MockLocationStrategy {
    NONE,           // 未启动
    STANDARD        // 标准模式
}
