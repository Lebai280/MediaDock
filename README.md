# MediaDock

Android 影音播放器：连接 WebDAV（OpenList / AList）浏览并播放视频，带海报墙媒体库（TMDB 刮削）。

> **接手这个项目请先读 [WORKLOG.md](WORKLOG.md)** —— 那里有完整的现状、硬约束、代码地图、路线图和坑清单。
> 本文件只放最短的上手信息。

## 构建

SDK / JDK / Gradle 全在 E 盘，路径已写进 `gradle.properties`（`org.gradle.java.home`）和
`local.properties`（`sdk.dir`）。**`local.properties` 不要提交。**

```bash
cd /e/code/MediaDock
export JAVA_HOME="E:\code\tools\jdk21"
export GRADLE_USER_HOME="E:\code\.gradle"
./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

## 安装到手机

```bash
E:/code/tools/android-sdk/platform-tools/adb.exe install -r \
  app/build/outputs/apk/debug/app-debug.apk
```

或直接把 APK 拷到手机点击安装。

## 环境位置（本机）

| | |
|---|---|
| Android SDK | `E:\code\tools\android-sdk` |
| JDK 21 | `E:\code\tools\jdk21` |
| Gradle 9.8.1 | `E:\code\tools\gradle-9.8.1` |
| Gradle 缓存 | `E:\code\.gradle` |

Gradle 分发包走腾讯镜像（`mirrors.cloud.tencent.com/gradle`）—— `services.gradle.org`
会 307 跳到 github，本机下不动。

## 版本

AGP 9.4.1 · Gradle 9.8.1 · Kotlin 2.4.21 · KSP 2.3.12 · Compose BOM 2026.09.00 ·
Media3 1.11.1 · Room 2.8.5 · OkHttp 5.5.0 · Coil3 3.6.3
compileSdk 37.2 / targetSdk 36 / minSdk 26

版本集中在 `gradle/libs.versions.toml`。

> ⚠️ AGP 9 自带 Kotlin 支持，**不要**再加 `org.jetbrains.kotlin.android` 插件。
> 详见 [WORKLOG.md](WORKLOG.md) 的坑清单。

## 进度

Stage 0（构建链）与 Stage 1（添加数据源 + WebDAV 连通性测试，已真机验证）完成。
详见 [WORKLOG.md](WORKLOG.md)。
