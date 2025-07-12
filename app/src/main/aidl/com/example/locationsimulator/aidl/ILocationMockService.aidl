package com.example.locationsimulator.aidl;

/**
 * AIDL接口用于Shizuku UserService位置模拟
 */
interface ILocationMockService {
    /**
     * 开始模拟位置
     * @param latitude 纬度
     * @param longitude 经度
     * @return 是否成功
     */
    boolean startMockLocation(double latitude, double longitude);
    
    /**
     * 停止模拟位置
     * @return 是否成功
     */
    boolean stopMockLocation();
    
    /**
     * 检查是否正在运行
     * @return 是否正在运行
     */
    boolean isRunning();
}
