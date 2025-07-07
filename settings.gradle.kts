pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 尝试使用阿里云镜像
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        // JitPack仓库
        maven { url = uri("https://jitpack.io") }
        // 百度官方仓库（备用）
        maven {
            url = uri("https://developer.baidu.com/map/sdk/maven/")
            isAllowInsecureProtocol = true
        }
    }
}

rootProject.name = "LocationSimulator"
include(":app")
