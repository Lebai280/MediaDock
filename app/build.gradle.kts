import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// release 签名配置来自 local.properties（**该文件已 gitignore，不进仓库**）。
// keystore 本身放在仓库外：E:\code\keystore\mediadock-release.jks（同目录有备份说明）。
// 换机器编译：把 keystore 和 local.properties 里那 4 行一起带过去。
val signingProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKey = signingProps.getProperty("mediadock.storeFile") != null

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // TMDB 的响应解析用 kotlinx-serialization。**这个插件必须 apply**：
    // 少了它 @Serializable 不会生成 serializer，编译照样过，一跑就崩在
    // "Serializer for class 'SearchResponse' is not found"。
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.lovelev.mediadock"
    // 依赖（navigation-compose 2.10.2 / coil3 / compose 1.12.1 / core 1.19.1）
    // 的 AAR 元数据要求 compileSdk >= 37，平台装的是 android-37.2
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "com.lovelev.mediadock"
        minSdk = 26
        targetSdk = 36
        versionCode = 23
        versionName = "0.8.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // 只有本机配了 keystore 才建这个签名配置；别人 clone 下来没 local.properties
        // 也能正常跑 assembleDebug / 单测，只是 assembleRelease 出的是未签名包。
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(signingProps.getProperty("mediadock.storeFile"))
                storePassword = signingProps.getProperty("mediadock.storePassword")
                keyAlias = signingProps.getProperty("mediadock.keyAlias")
                keyPassword = signingProps.getProperty("mediadock.keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // 与正式签名包并存，避免 instrumentation 测试覆盖真机上的 release 安装。
            // -PverifySamePackage 时用正式包名 + 正式签名构建可调试包，仅用于只读核对真机库。
            val verifySamePackage = project.hasProperty("verifySamePackage")
            applicationIdSuffix = if (verifySamePackage) "" else ".debug"
            versionNameSuffix = "-debug"
            if (verifySamePackage && hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
            // R8：体积与启动都明显受益。Room / kotlinx-serialization / Media3 的 consumer
            // 规则已经足够；开之前必须真机跑一遍播放、刮削、字幕与详情（见 WORKLOG 问题 74）。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.documentfile)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)

    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:data"))

    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.serialization.json)

    // 文件名解析是纯逻辑，能在本机 JVM 上跑测试 —— 别把它的验证推给真机
    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // S3 spike dependency (SMB); DLNA uses an in-house SSDP/SOAP controller.
    androidTestImplementation(libs.smbj)
}
