#!/bin/bash

# 获取release版本的SHA1指纹
# 用于GitHub Actions构建时输出SHA1信息

echo "=== 获取Release版本SHA1指纹 ==="

# 检查keystore文件是否存在
if [ ! -f "app/release.keystore" ]; then
    echo "❌ release.keystore文件不存在"
    echo "💡 使用debug keystore获取SHA1..."
    
    # 使用debug keystore
    KEYSTORE_PATH="$HOME/.android/debug.keystore"
    KEYSTORE_PASSWORD="android"
    KEY_ALIAS="androiddebugkey"
    
    if [ ! -f "$KEYSTORE_PATH" ]; then
        echo "❌ debug.keystore也不存在"
        exit 1
    fi
else
    # 使用release keystore
    KEYSTORE_PATH="app/release.keystore"
    KEYSTORE_PASSWORD="${KEYSTORE_PASSWORD:-android}"
    KEY_ALIAS="${KEY_ALIAS:-key0}"
fi

echo "📁 Keystore路径: $KEYSTORE_PATH"
echo "🔑 Key别名: $KEY_ALIAS"

# 获取SHA1
SHA1=$(keytool -list -v -keystore "$KEYSTORE_PATH" -alias "$KEY_ALIAS" -storepass "$KEYSTORE_PASSWORD" -keypass "$KEYSTORE_PASSWORD" 2>/dev/null | grep "SHA1:" | cut -d' ' -f3)

if [ -z "$SHA1" ]; then
    echo "❌ 无法获取SHA1指纹"
    exit 1
fi

echo "✅ SHA1指纹: $SHA1"

# 生成百度地图安全码
PACKAGE_NAME="com.example.locationsimulator"
APP_NAME="Location Simulator"
SECURITY_CODE="$SHA1;$PACKAGE_NAME;$APP_NAME"

echo ""
echo "=== 百度地图配置信息 ==="
echo "📦 包名: $PACKAGE_NAME"
echo "🏷️ 应用名: $APP_NAME"
echo "🔐 安全码: $SECURITY_CODE"
echo ""
echo "📋 请将上述安全码配置到百度开发者平台："
echo "   1. 登录 https://lbsyun.baidu.com/apiconsole/key"
echo "   2. 找到对应的AK应用"
echo "   3. 在'安全码'字段填入: $SECURITY_CODE"
echo ""

# 输出到GitHub Actions环境变量
if [ "$GITHUB_ACTIONS" = "true" ]; then
    echo "RELEASE_SHA1=$SHA1" >> $GITHUB_OUTPUT
    echo "BAIDU_SECURITY_CODE=$SECURITY_CODE" >> $GITHUB_OUTPUT
fi
