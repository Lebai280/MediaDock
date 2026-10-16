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
    alias(libs.plugins.ksp)
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
        versionCode = 10
        versionName = "0.3.7"
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
        release {
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
            // minify 暂不开：R8 会裁掉 Room / kotlinx-serialization 依赖的反射。
            // 要开的话必须同时补 keep 规则并跑一轮真机验证，属于独立的一步（见 WORKLOG）。
            isMinifyEnabled = false
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

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.serialization.json)

    // 文件名解析是纯逻辑，能在本机 JVM 上跑测试 —— 别把它的验证推给真机
    testImplementation(libs.junit)
}
