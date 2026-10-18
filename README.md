# MediaDock

Android 媒体播放与媒体库应用。通过 WebDAV（OpenList / AList）浏览并播放视频，提供基于 TMDB 元数据的海报墙、收藏、片单、播放历史与字幕能力。

## 文档

| 文档 | 内容 |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | 长期架构、数据模型、迁移策略与分阶段路线图 |
| [WORKLOG.md](WORKLOG.md) | 实现进展、已验证结论、问题记录与待办 |
| [NEXT_SESSION.md](NEXT_SESSION.md) | 当前迭代交接与下一步 |

开发前应先阅读 `WORKLOG.md` 中的强制约束、代码地图与问题记录。

## 构建

SDK、JDK 与 Gradle 路径通过 `gradle.properties`（`org.gradle.java.home`）和 `local.properties`（`sdk.dir`）配置。`local.properties` 属于本机配置，不得提交。

```bash
cd /e/code/MediaDock
export JAVA_HOME="E:\code\tools\jdk21"
export GRADLE_USER_HOME="E:\code\.gradle"
./gradlew :app:assembleDebug
```

构建产物：`app/build/outputs/apk/debug/app-debug.apk`。

## 安装

```bash
E:/code/tools/android-sdk/platform-tools/adb.exe install -r \
  app/build/outputs/apk/debug/app-debug.apk
```

也可将 APK 复制到设备后安装。

## 本机开发环境

| 组件 | 路径 |
|---|---|
| Android SDK | `E:\code\tools\android-sdk` |
| JDK 21 | `E:\code\tools\jdk21` |
| Gradle 9.8.1 | `E:\code\tools\gradle-9.8.1` |
| Gradle 缓存 | `E:\code\.gradle` |

Gradle 分发包通过腾讯镜像（`mirrors.cloud.tencent.com/gradle`）获取；官方源在目标网络环境下不可用。

## 版本

AGP 9.4.1 · Gradle 9.8.1 · Kotlin 2.4.21 · KSP 2.3.12 · Compose BOM 2026.09.00 ·
Media3 1.11.1 · Room 2.8.5 · OkHttp 5.5.0 · Coil3 3.6.3
compileSdk 37.2 / targetSdk 36 / minSdk 26

版本统一维护在 `gradle/libs.versions.toml`。

> AGP 9 已内置 Kotlin 支持，不得重复应用 `org.jetbrains.kotlin.android` 插件。
> 相关约束见 [WORKLOG.md](WORKLOG.md) 的问题记录。

## 进度

8 个功能阶段（构建链、数据源、浏览、播放、媒体库、TMDB 刮削、播放体验、字幕与设置）已完成，并通过真机逐屏验证；界面按 VidHub 版式与产品配色完成改造。

当前版本 `0.5.7`（versionCode 20），已发布；应用内「设置 → 关于 → 检查更新」可用。

> 构建 release 变体需要签名材料：密码配置在 `local.properties`（已 gitignore），密钥库位于仓库之外。缺少签名材料时仅可构建 debug 变体。签名材料丢失后无法继续发布同一应用的更新。

## 仓库

`https://github.com/Lebai280/MediaDock`（私有，分支 `master`）。

> 仓库托管于 GitHub；禁止将运维信息（WebDAV 地址、服务器地址、SSH 信息、账号）写入任何文件。
> `.gitignore` 已排除 `local.properties`、`*.keystore` 与 `*.apk`。

## 文档与文案规范

1. 文档使用正式书面语，不使用口语、网络用语、表情符号与感叹句式。
2. 技术结论应注明验证方式与日期；未验证内容标记为待验证。
3. 界面文案仅保留操作与状态所需信息：不复述用户已经可见的状态，不使用解释性长句；空状态使用最短标签。
4. 安全、迁移与数据完整性相关约束统一使用“必须”“不得”表述。
5. 代码变更应同步更新 `WORKLOG.md`；架构或决策变更应同步更新 `ARCHITECTURE.md`。
