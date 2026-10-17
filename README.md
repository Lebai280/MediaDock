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

8 个功能阶段（构建链 / 数据源 / 浏览 / 播放 / 媒体库 / TMDB 刮削 / 播放体验 / 字幕与设置）
**全部完成并逐屏真机验证过**，界面也已按 VidHub 版式与用户站点配色改造。

当前版本 `0.5.4`（versionCode 17），站点上已发 0.5.4，
应用内「设置 → 关于 → 检查更新」可用。

> ⚠️ **构建 release 包需要 keystore**：密码在 `local.properties`（已 gitignore），
> 密钥库在仓库外 `E:\code\keystore\`。**没有它的机器只能 `assembleDebug`。**
> 丢了 keystore 就再也无法更新这个 App —— 详见 [WORKLOG.md](WORKLOG.md) 的「换成正式签名」一节。

详见 [WORKLOG.md](WORKLOG.md) —— 现状、硬约束、代码地图、路线图、坑清单（编号到 69）。

## 仓库

`https://github.com/Lebai280/MediaDock`（私有，分支 `master`）。

> ⚠️ 本仓库会上 GitHub，**运维细节（WebDAV 地址 / 服务器 / SSH / 账号）不要写进任何文件**。
> `.gitignore` 已排除 `local.properties` / `*.keystore` / `*.apk`。
