import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.lovelev.mediadock.core.data"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // 仓库/查询层对外暴露实体与网络类型。
    api(project(":core:model"))
    api(project(":core:database"))
    api(project(":core:network"))
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
