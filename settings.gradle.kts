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
        // 阿里云镜像
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        // JitPack仓库
        maven { url = uri("https://jitpack.io") }
        // 百度地图SDK仓库 - 使用HTTP协议
        maven {
            url = uri("http://developer.baidu.com/map/sdk/maven/")
            isAllowInsecureProtocol = true
        }
        // 备用：华为镜像
        maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
    }
}

rootProject.name = "LocationSimulator"
include(":app")
