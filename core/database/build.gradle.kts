import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.lovelev.mediadock.data.local"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        // MigrationTestHelper 从 androidTest assets 读导出的 schema。
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    // v8/v9 schema 是迁移测试与评审的基线。
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Room 类型出现在公开 API（MediaDatabase 继承 RoomDatabase），必须 api 暴露。
    api(project(":core:model"))
    api(libs.room.runtime)
    api(libs.room.ktx)
    ksp(libs.room.compiler)
    // S1 的兼容 VIEW 测试在 androidTest 里定义了独立的 Room 库。
    kspAndroidTest(libs.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
}
