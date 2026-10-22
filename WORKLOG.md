# MediaDock 开发记录

> 新会话从本文件开始阅读。决策、结果、问题与下一步均记录于此，不记录流水账。
> 最后更新：2026-10-20（已纳入 git，远程 `Lebai280/MediaDock`，**2026-10-20 起为公开仓库**）
>
> `NEXT_SESSION.md` 提供可直接使用的交接说明（含构建命令与下一步）。
> 本文件为细节归档。

### 会话工作目录

长期记忆按项目目录分桶（`~/.claude/projects/<slug>/memory/`）。本项目对应 `E--code`（即 `E:\code`）。在 `MediaDock` 子目录启动会话会创建 `E--code-MediaDock` 桶，导致既有记忆（包括协作偏好）无法加载。工具链与构建配置均位于项目文件内，与会话目录无关。

> **记录规范**：新增条目按「背景 / 变更 / 验证 / 备注」四段书写，使用正式书面语，
> 便于交接；历史条目在不改变技术事实的前提下统一为正式书面语。

---

## 0. 项目摘要

**MediaDock** 是自建 Android 影音播放器：配置 WebDAV 地址与账号后浏览目录并播放视频，同时提供基于 TMDB 数据的海报墙媒体库。数据源为自建 **OpenList WebDAV**。产品定位为替代 VidHub 的自有客户端。

> 运维细节（WebDAV 地址、服务器、SSH、账号）不得写入仓库。
> 需要时查阅长期记忆（`vps-proxy-node`）或服务器手册。

- 项目路径：`E:\code\MediaDock`
- 包名 / applicationId：`com.lovelev.mediadock`
- 当前版本：`0.5.7`（versionCode 20），已发布。

---

## 1. 当前状态

| 阶段 | 状态 |
|---|---|
| **Stage 0** 构建链打通 | 完成 |
| **Stage 1** 添加数据源 + WebDAV 连通性测试 | 完成，真机验证通过 |
| **Stage 2** 目录浏览 | 完成，真机验证通过 |
| **Stage 3** 播放 | 完成，真机验证通过 |
| **Stage 4** 媒体库 | 完成，**已完成真机验证**（扫描 92 个视频） |
| **Stage 5** 刮削（TMDB） | 完成，**已完成真机验证**（80/92 匹配，海报出图） |
| **Stage 6** 播放体验 | 完成，**已完成真机验证**（续播 00:24→01:19、旋屏不中断、后台播放） |
| **分季分集 + 详情页** | 完成，**详情页已完成真机验证**；分季聚合仅有单元测试（媒体库无剧集样本） |
| **Stage 7** 字幕与设置 | 完成，**设置页已在真机验证**；字幕功能因媒体库无字幕文件，尚未验证 |
| **界面改造**（VidHub 版式 + 用户站点配色） | 完成，**每一步均完成真机验证**；详情页重做成整页一体（海报完整不裁剪 + 取色延伸）|
| 播放页自绘控件 | **完成，真机验证**（触摸 / 手势 / 锁定 / 自动隐藏全部通过），见下 |

> **2026-10-15 在 MuMu 模拟器（Android 12 / SDK 32 / x86_64）上执行了一轮完整验证。**
> 详见下面「真机验证结果」一节 —— 发现 1 个严重缺陷（未应用 kotlinx-serialization 插件）
> 和 2 个仅在真机暴露的问题（横屏详情页背景图、MuMu 的点击注入）。

**Stage 1 的真机验证结果（2026-10-15，经截图确认）**：配置 WebDAV 地址与 OpenList 账号后显示 **「已连接，7 个条目」**，并正确列出中文目录名：

```
【Netflix纪录片100部】/
【新海诚】系列电影.双语特效字幕【共10部】/
奥斯卡最佳动画短片 1932-2024/
皮克斯经典动画电影合集（297G）/
```

→ PROPFIND、XML 命名空间解析、中文百分号编解码、Basic 鉴权全部正确。

**尚未验证**：终止应用后重新启动，凭据是否仍然有效（Keystore 解密链路）。

### 应用更名：MediaDock → Mooring（2026-10-20）

- **决定**：桌面显示名改为 **Mooring**（英文「系泊处」）。选它的理由：四个标准里没有短板——贴（Dock 是公共码头，Mooring 是把船系在自己的桩上）、好记、有诗意、重名少。
- **范围**：只改显示名与用户可见的品牌字样。`applicationId` 仍是 `com.lovelev.mediadock`，类名（`MediaDockApp`）、主题（`Theme.MediaDock`）、日志 TAG、模块与包结构全部不动。
- **改动点**：
  - `res/values/strings.xml` 的 `app_name` → Mooring（桌面与任务栏显示的就是它）
  - 数据源分享文本标题、播放诊断标题、各处剪贴板标签 → Mooring
  - 截图文件名前缀与相册目录 `Pictures/MediaDock` → `Pictures/Mooring`
- **验证**：`assembleRelease` 成功；`aapt2 dump badging` 显示 `application-label:'Mooring'`、`package: name='com.lovelev.mediadock'`；真机 `install -r` 成功（同包同签名，数据保留），再从设备拉回 `base.apk` 复核，标签仍是 Mooring，versionCode 33 / 0.13.4 未变。
- **副作用**：之后新截图进 `Pictures/Mooring`，旧截图仍留在 `Pictures/MediaDock`。
- **待办**：文档（README / WORKLOG 标题）与代码里的 `MediaDock` 字样暂未改；将来若连包名一起换，需手动迁移 WebDAV 凭据（applicationId 变了等于新应用）。

### Stage 2 的进展与验证（2026-10-15）

代码已完成、编译通过。**本机 adb 无设备，尚未装机验证过** —— 下面是"已经证明了的"和"还没证明的"。

已经用真实服务器（用户提供的账号，共 3 次**已鉴权** PROPFIND，全部 `207`，无 `401`）证明：

- 子目录 PROPFIND 返回 `自身的 href + 全部子项`，子项 href 是**带完整父路径**的绝对路径
  （`/dav/知乎 推荐…/1.河西走廊/`），不是相对被请求集合的短名。
- 空格编码成 `%20`（不是 `+`），全角括号/书名号按 UTF-8 百分号编码 → `decodePath` 的处理是对的。

还没证明（要真机进行验证才知道）：

- 列表渲染、面包屑、返回键、刷新、缓存是否符合预期。
- 普通**文件**条目是否带 `getcontentlength`（根目录与抽查的子目录里全是目录，
  没有文件样本）。真返回 null 时界面只是不显示大小，不会出错。

**最新 APK**：`E:\code\MediaDock\app\build\outputs\apk\debug\app-debug.apk`

### Stage 3 的进展（2026-10-15，同一会话）

代码完成、编译通过。**仍然没有设备，尚未完成真机验证。** 已经能证明的和不能证明的分开写：

已经证明（静态可查）：

- 打包后的 manifest 里 `PlaybackService` 带着 `foregroundServiceType=0x2`(mediaPlayback) +
  media3 要求的 intent-filter；`FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` /
  `POST_NOTIFICATIONS` 都在。（`WAKE_LOCK` 由 media3 自己并进来的，正常。）
- 整个 Stage 3 通过编译，没有警告。

还没证明（必须真机进行验证）：

- **播放能不能起来** —— 最大的不确定点是 `PlaybackService` 的 `android:exported="false"`，
  见问题 14。若失败，界面会显示「连接播放服务失败」（而非白屏），可按提示修正。
- 拖 ≥1GB MKV 进度条是否秒跳（服务器侧应看到 `206`）、旋屏是否不中断。
- 通知栏控制、锁屏控制。

> **2026-10-15 用户确认「可以正常播放」** —— 起播这条路通了，`exported="false"` 是对的
> （问题 14 那条回退不再适用）。进度条秒跳 / 旋屏 / 通知栏尚未单独验证。

### Stage 4 的进展（2026-10-15，同一会话）

代码完成、编译通过；KSP 确实生成了 `MediaDatabase_Impl.kt`（Room 处理器跑到了）。
**尚未完成真机验证。** 尚未验证证的是：扫描能不能跑完、海报墙长什么样、取消是否干净。

已知会让人意外的两点（都在界面上有交代，不是隐性问题）：

- **第一次扫描可能要几分钟**：顺序发 PROPFIND，一个目录一个请求，绝不并发
  （硬约束：不要高频 PROPFIND）。进度条会显示「已扫 N 个目录 · M 个视频」，随时可取消。
- **有上限**：目录 4000 / 深度 6 / 文件 20000（`LibraryRepository` 里的常量）。
  撞到上限会在头部下面出一行「达到扫描上限，结果可能不完整」。
  用户的库结构如果是「合集/电影名/文件」这种两级，4000 个目录够用；不够就改常量。

### 真机验证结果 · 三星 S21 Ultra（Android 15 / SDK 35 / arm64）

**模拟器测不出来的两条，在真机上是对的：**

```
ServiceRecord{... com.lovelev.mediadock/.player.PlaybackService}
  isForeground=true  types=0x00000002(=mediaPlayback)
  foregroundNoti=Notification(channel=default_channel_id ... category=transport)
android.permission.FOREGROUND_SERVICE: granted=true
android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK: granted=true
```
- 播放位置在真机上稳定前进（`MediaCodec` 硬解），全程 0 异常。
- 扫描 92 条、刮削 80/92、海报出图 —— 与模拟器结果一致。
- 用户手机的 Clash 让 TMDB 通；手机真实网络解析自己的 WebDAV 域名正常
  （PC 上那次解析失败是 PC 自己的 DNS 问题）。
- 老数据（凭据）跨版本升级后仍在 → Keystore 解密链路另外验证过了。

### 产品负责人追加的两项播放器行为（已实现并完成真机验证）

1. **进播放页自动横屏，离开还回系统**
   - 播放页 3200x1440（横屏）→ 返回后 1440x3200（竖屏）。
   - 用户手机锁着竖屏，所以用的是**硬性** `LANDSCAPE`（理由见问题 36）。
2. **离开播放页就暂停，但保留通知**
   - 返回后 `state=PAUSED(2)`，前台服务仍在（`isForeground=true`）→ 能从通知栏接着看。
   - 实现见问题 37（判据不能靠 `isChangingConfigurations`，也不能靠销毁时读路由）。

### 真机验证结果（2026-10-15，MuMu 模拟器 Android 12 / SDK 32 / x86_64）

设备：`emulator-5554`（MuMu 5.30，Android 12，x86_64，横屏 2560x1440 物理 / 1440x2560 逻辑）。

**实测通过的**：

| 项 | 证据 |
|---|---|
| 底部两 Tab | 浏览 / 媒体库 都渲染、都能切 |
| 扫描 | 头显示「共 92 个视频」 |
| 刮削 | 「80 条匹配上 · 12 条没匹配上」 |
| 海报墙出图 | TMDB 真海报正常显示（Coil 走通 `image.tmdb.org`） |
| 未匹配的占位 | 12 条没匹配上的显示解析出的中文片名 |
| 详情页 | 片名 /「1939 · 7.0」/ 播放按钮；底部 Tab 正确消失 |
| 播放 | `MediaCodec OMX.qcom.video.decoder.avc` 硬解；`position` 0→5.6s→8.6s 前进 |
| 后台播放 | 退出播放页后 `position` 继续前进（17.6→20.6→23.6s） |
| 媒体通知 | 日志里出现来自媒体会话的通知 |
| 进度记忆 + 续播 | 回详情页显示「继续播放 · 00:24」→ 一路涨到「01:19」 |
| 旋屏不中断 | 竖屏→横屏两次切换，进度 24→49→79 秒**全程没归零** |
| 倍速按钮 | 播放器页出现「1×」 |
| 设置页 | TMDB 凭据 / 保存 / 清空媒体库 / 退出登录 |
| 二次确认 | 点「清空媒体库」弹出确认，正文「海报墙会变空，需要重新扫描。播放进度会保留。」；「取消」不执行 |

**这轮抓到的 缺陷（都已修）**：见问题 33 / 34 / 35。

**还尚未验证的**（库里没对应数据，不是代码问题）：

- **同级目录字幕**：这个合集里没有 `.srt/.ass` 文件 → 没得测。
- **分季分集的「N 集」聚合**：库里 92 条都是独立短片（没有集数）→ 只能靠单测。
- **海报块那条 3dp 进度条**：缩放太小看不清；但它的数据通路已被详情页的
  「继续播放 · 01:19」证明（同一个 map、同一批 path）。
- **自动播放下一集**：要等一个片子放完；短片太短没等到。

### Stage 5 的进展（2026-10-15，同一会话）

TMDB 申请已过，key 到手（**只填进 App 的设置页，代码/仓库里一个字都没有**）。

这次比前几个 Stage 多验证到了一些东西 —— 凡是能在这台机器上证明的都证明了：

- **文件名解析器有单元测试**：`app/src/test/.../TitleParserTest.kt`，13 个用例
  **全部取自用户库里真实的文件名/目录名**（实际列举出来的，不是编的）。跑法见文末命令。
- **检索配对已用真实 API 验证**：拿解析器给出的检索词去打 TMDB，6 个真实样本全部命中正确条目
  **且都有海报**：
  `河西走廊`→河西走廊(2015)、`虫虫危机`→虫虫危机(1998)、`贝克汉姆`→贝克汉姆(2023)、
  `Up`→飞屋环游记(2009)、`你的名字`→你的名字。(2016)、`一级方程式`→一级方程式：疾速争胜(2023)。
- 打包前的 manifest / Room 生成物之类的照旧静态核过。

还没证明（要真机）：

- 海报墙是否真的显示出图（Coil 取 `image.tmdb.org` 能不能走通）。
- 刮削进度、取消、以及「凭据无效 → 去设置」那条路。

#### TMDB 访问需要代理

实测：直连 `api.themoviedb.org` 解析到 `face:b00c`（DNS 污染特征）并超时；
走 Clash（`127.0.0.1:7897`）立刻 `401`（= 连通，只是没带凭据）。

→ **手机上必须开着 VPN/代理**，否则刮削和海报都会失败（报「连不上 TMDB」）。
`api.themoviedb.org` 和 `image.tmdb.org` 两个域名都要能通。
这是环境约束，不是代码问题；App 侧无法绕过。

> 补充（同日）：自家那个 WebDAV 域名也出现过一次本机默认解析器超时，
> 但服务器本身是好的（绕过 DNS 直连 = 207 / 0.47s，阿里 DNS 和 DNSPod 都能解析出正确 IP）。
> 结论：**坏的是客户端解析那一步**，多半跟为了 TMDB 去调代理/DNS 有关。
> 自家的域名在 Clash 里应该走 `DOMAIN-SUFFIX,lovelev.eu.cc,DIRECT`（另外这条对播放也更快）。
>
> 又补充：用户后来在**模拟器**里跑，报 `failed to connect to api.themoviedb.org/31.13.83.2` ——
> `31.13.83.2` 是 Facebook 的 IP（DNS 污染），源地址 `10.0.2.15` 是模拟器 NAT。
> **模拟器要走宿主机的代理**：宿主机在模拟器里是 `10.0.2.2`，不是 `127.0.0.1`。
> 启动参数 `-http-proxy http://10.0.2.2:7897`，或在模拟器 WiFi（AndroidWifi）里手填代理；
> 前提是 Clash 开了「允许局域网连接」。

### Stage 6 的进展（2026-10-15，同一会话）

用户选了「播放体验」这一批：**进度记忆 + 倍速 + 自动播放下一集**。代码完成、编译通过、
单元测试仍全部通过。**尚未完成真机验证。**

- **进度**：单独一张 `playback_progress` 表（问题 23），播放中每 5 秒落一次、暂停时补一次，
  **写在 `PlaybackService` 里**（播放器住在服务里，退出播放页/锁屏时进度也得继续记）。
- **续播**：`setMediaItems(items, startIndex, startPositionMs)` 一次把队列和续播位置都设好。
  停在片尾 30 秒内不算续播点（否则每次都从片尾开始）。
- **倍速**：播放页右上角，`DropdownMenu` 选 0.5×–3×。**未实现持久化** ——
  速度是播放器的属性，同一会话换集不丢；重启 App 回到 1×。
- **自动下一集**：点文件时把**同目录的视频**一起当队列传下去（见问题 24 的队列指纹）。
- 海报块底部会出一条细进度条，一眼看出哪些看了一半。

**和原计划的偏差**：WORKLOG 原来写「详情页显示『继续播放 12:34』」，但详情页（`ui/detail/`）
尚未实现，因此将「继续播放」实现为**海报块的进度条 + 自动续播**。等详情页做了可以在那儿再显示一次。

### 分季分集 + 详情页（2026-10-15，同一会话）

代码完成、编译通过、**分组逻辑有 6 个单测**（`WallEntryTest`，用例覆盖了平铺电影合集、
同目录多集、只有一集、分季排序、两个剧不互相合并）。**尚未完成真机验证。**

- **海报墙按「剧」聚合**：识别出集数、且同一目录里 ≥2 集的条目聚合为一个条目，并标注「N 集」。
  规则与理由见问题 27 —— 关键是**不能简单按目录分组**。
- **剧的进度条 = 已看完集数 / 总集数**（看完 ≥92% 算一集）。
  一开始写的是取「最大进度」，但看完第 1 集就会显示满格、看着像整部看完了，改成比例。
- **详情页**（`ui/detail/`）：背景图 + 海报 + 片名/年份/评分/N 集 + 简介 +
  「继续播放 · 12:34」（= 最近看过的那一集）或「播放」+ 序号按钮「从头播放」+ 分集列表
  （分了好几季才打季标题；每集带一条细进度条，当前要继续的那一集加粗高亮）。
  点某集就从那一集开始播，队列仍是整部剧 → 继续自动下一集。
- **点海报不再直接播**，先进详情页（与 VidHub 一致，也是用户要的「点击进入后的样子」）。

**已知不覆盖**：同一部剧被拆到 `Season 1/`、`Season 2/` 两个**目录**时会显示成两块。
真遇到可以用 `tmdbId` 做二次合并（各集刮到的是同一部剧，`tmdbId` 相同）。

### Stage 7（字幕与设置，2026-10-15 同一会话）

代码完成、编译通过、**字幕配对有 7 个单测**（`MediaTypesTest`）。**尚未完成真机验证。**
全部 7 个 Stage 都写完了一轮，接下来是集中真机验证。

- **同级目录字幕**：播放时**发一次 PROPFIND** 拿所在目录，本地按文件名配对（见问题 29 的规则），
  再作为 `MediaItem.SubtitleConfiguration` 交给 ExoPlayer。为什么不在扫描时存库：
  从「浏览」Tab 直接播的文件可能压根没进过媒体库；一次用户触发的请求不算高频；
  也不会留下过期的字幕记录。**拿不到字幕不影响播放**（异常吞掉）。
- **内嵌字幕轨切换**：`PlayerView.setShowSubtitleButton(true)` + 播放器控件的轨选择。
- **设置页**：TMDB 凭据（保存按钮）、**清空媒体库**、**退出登录**，两个不可逆操作
  都有二次确认弹窗，且放在最下面彼此拉开（防误触）。清空媒体库**保留播放进度**
  （进度按 path 存，是用户数据不是缓存）。

**字幕的已知限制**：只认同级目录、按文件名前缀配对；ass/ssa/vtt/srt 四种扩展名。
外挂字幕需要在 MP4/MKV 之外单独发一次请求，第一次显示可能慢一拍。

---

## 2. 强制约束

### 401 / 429 不得自动重试

OpenList 的**登录失败锁定按来源 IP 计数**，而 nginx 反向代理使所有客户端显示为 `127.0.0.1` ——
**任一客户端密码错误可能导致所有 WebDAV 客户端被锁定**（电影与音乐服务均不可用）。

代码中已设置四道防线（修改时不得移除或绕过）：

1. OkHttp **不设置 `Authenticator`** —— 设置了它才会对 401 自动重发；不设置，401 原样返回。
2. `SourceViewModel` 里 `state is Running` 时按钮禁用，防止连续点击。
3. 收到 401/429 后置 `retryBlocked = true`，**按钮禁用，直至用户修改任一输入框**后解锁。
4. 仅在连通成功时保存凭据。

**不得对线上 WebDAV 执行试探请求**（包括未鉴权的 PROPFIND，同样计入一次登录失败）。
连通性测试应在应用内使用正确凭据执行。

其它约束：
- **不得高频执行 PROPFIND**：目录列表应缓存并显式刷新；启动时不得执行全量递归扫描；扫描必须由用户主动触发并可取消。
- 健康状态码 = `207 / 206 / 200`；`404` = 路径失效（不是网络错）；`429` = 已被锁定。
- **SDK、Gradle 缓存与工具链统一安装至 E 盘**（C 盘剩余空间约 34 GB，且 `AppData` 存在 EFS 加密问题）。

### 数据库变更必须使用真实迁移

`media_items` 是缓存（丢了重扫），但 `playback_progress` / `favorites` / `title_overrides` /
`title_details` 均属于**用户数据，丢失后无法恢复**。约束：

- 加表 / 加列写真迁移，进 `MediaDatabase.MIGRATIONS` 数组（容器整批注册，见问题 39）。
- 版本号改 `MediaDatabase.VERSION`（单一来源），**必须同时补一条迁移**，否则 `MigrationCoverageTest` 失败。
- 升级方向**不得启用破坏性回退**：缺失迁移会抛出异常，不得静默清库（问题 45）。
  动 `media_items` 结构时写一条显式命名迁移；不得启用破坏性回退，否则用户数据将一并丢失。

---

## 3. 已确定的技术决策

| 项 | 决定 | 备注 |
|---|---|---|
| 播放内核 | **Media3 / ExoPlayer** | 不采用 libVLC（产品决策） |
| 设备形态 | **只做手机 / 平板** | **不做** Android TV / 遥控器 |
| 刮削 | **TMDB API** | 由产品方提供 key（尚未提供） |
| 数据源 | OpenList / AList WebDAV | |
| WebDAV 客户端 | **在 OkHttp 上手写** | 不采用 `sardine-android`（仅发布至 JitPack，当前环境拉取不稳定） |
| DI | **手写 `AppContainer`** | 不引入 Hilt（减少注解处理器） |
| 凭据存储 | DataStore + **Android Keystore AES-GCM** | 密码加密落盘 |
| 界面 | Jetpack Compose + Material3 | 单一 `:app` 模块 |
| 首页结构 | **底部两个 Tab：浏览 / 媒体库**；播放页与设置全屏、不带 Tab | 2026-10-15 产品决策。媒体库入口原位于浏览页右上角，可发现性不足，已调整至底部导航 |
| 播放架构 | **MediaSessionService 持有播放器 + 界面用 MediaController 挂上去** | 屏幕旋转或退出播放页均不中断；通知栏可控 |
| 播放鉴权 | **逐次请求补 Authorization**，且只给数据源主机 | 见问题 12 |
| 要播的文件 | `AppContainer.playbackSelection`（内存） | **不进 nav 参数**（会进 Bundle） |
| 媒体 HTTP 客户端 | 与 PROPFIND 的**分开**：无 `callTimeout` + `Accept-Encoding: identity` | 见问题 10 / 11 |
| 媒体库数据源 | **Room 是海报墙的唯一数据源**；扫描仅负责写入数据库 | Stage 4 |
| 扫描 | 用户主动触发 · 顺序 BFS · 有界 · 可取消 · 扫完一次性事务替换 | 见问题 16 |
| 扫描器归属 | `LibraryRepository` 是**应用作用域**（不是 ViewModel） | 退出页面或屏幕旋转不会中断扫描；副作用是封锁状态不会自动解除，见问题 17 |
| 刮削 | **TMDB** | 用 search 端点一步到位（返回里就带 poster/backdrop/overview/rating），不再打详情接口 |
| TMDB 凭据 | 存 secure prefs（Keystore 加密），**绝不进 BuildConfig / 源码** | Bearer token 和 32 位 api_key 两种形式均支持，避免用户猜测 |
| 文件名解析 | `data/tmdb/TitleParser.kt`，**由单元测试覆盖** | 规则基于真实命名样本归纳，见问题 19 |
| 刮削并发 | 4 个一批（`chunked(4)` + `awaitAll`），不使用 Semaphore | 见问题 20 |
| 播放进度 | **单独一张表**，不挂在媒体条目上 | 媒体条目每次扫描整表替换，附加在媒体条目上会在扫描时被替换，见问题 23 |
| 进度落库位置 | 在 `PlaybackService` 里，播放中 5 秒一次 + 暂停时一次 | 播放器由服务持有，界面退出后仍持续记录 |
| 播放队列 | 同目录视频由发起方计算并写入 `PlaybackSelection.queue` | 有队列才有「自动下一集」，见问题 24 |
| 倍速 | 不持久化，重启后恢复为 1× | 只在同一会话内保持（播放器的属性） |
| 海报墙聚合 | **只对「识别出集数 + 同目录 ≥2 集」的条目聚合为剧集** | 不能按目录分组，见问题 27；纯函数已有单元测试 |
| 详情页入参 | `DetailSelection.paths`（内存），详情页再按 path 去 Room 查 | 不传 nav 参数（路径包含中文与 `/`，且会进入 Bundle） |
| 外挂字幕 | **播放时现查同级目录**（一次 PROPFIND + 文件名配对），不写入数据库 | 见 Stage 7 那节 / 问题 29 |
| 回到数据源页 | `popUpTo(navController.graph.id) { inclusive = true }` | 底部 Tab 会导致 `popUpTo(BROWSE)` 无法正确弹出，见问题 31 |

---

## 4. 环境与构建

### 构建链（本机环境）

| 东西 | 位置 |
|---|---|
| Android SDK | `E:\code\tools\android-sdk`（578 MB） |
| build-tools | 36.0.0 + 37.0.0 |
| platforms | android-36 + **android-37.2** |
| platform-tools | 37.0.1（adb 1.0.41） |
| JDK | `E:\code\tools\jdk21`（Temurin 21.0.12.1） |
| Gradle | `E:\code\tools\gradle-9.8.1` |
| GRADLE_USER_HOME | `E:\code\.gradle` |
| 下载缓存 | `E:\code\tools\_downloads` |

本机**没有全局 `JAVA_HOME` / `ANDROID_HOME`**，所以：
- `gradle.properties` 写死 `org.gradle.java.home=E:\code\tools\jdk21`
- `local.properties` 写死 `sdk.dir=E:\code\tools\android-sdk`（**不要提交这个文件**）

### 构建命令

```bash
cd /e/code/MediaDock
export JAVA_HOME="E:\code\tools\jdk21"
export GRADLE_USER_HOME="E:\code\.gradle"
./gradlew :app:assembleDebug
```

### 安装至设备

```bash
E:/code/tools/android-sdk/platform-tools/adb.exe install -r \
  E:/code/MediaDock/app/build/outputs/apk/debug/app-debug.apk
```

> **当前 adb 没有连接设备** —— 用户目前是手动拷 APK 安装的。需要读 logcat 排错时，
> 请用户插 USB 开「USB 调试」并在「传输文件」模式下连接。

### 版本锁定（均已实测验证）

AGP **9.4.1** · Gradle **9.8.1** · Kotlin **2.4.21** · KSP **2.3.12** · Compose BOM **2026.09.00**
Media3 **1.11.1** · Room **2.8.5** · OkHttp **5.5.0** · Coil3 **3.6.3** · kotlinx-serialization **1.11.0**
navigation-compose **2.10.2** · lifecycle **2.11.0** · activity-compose **1.13.0** · core-ktx **1.19.1**
datastore **1.2.1** · security-crypto **1.1.0**
compileSdk **37.2** / targetSdk **36** / minSdk **26**

全部集中在 `gradle/libs.versions.toml`，改版本只改那里。

---

## 5. 代码地图

`app/src/main/java/com/lovelev/mediadock/`

| 文件 | 作用 |
|---|---|
| `MediaDockApp.kt` | Application，持有 `AppContainer` |
| `MainActivity.kt` | `enableEdgeToEdge` + **底部两 Tab 的外壳**（`HomeScaffold`）+ `NavHost`（起始路由按本地凭据定） |
| `di/AppContainer.kt` | 手写 DI：`okHttpClient` / `webDavClient` / `credentials` |
| `data/webdav/WebDavClient.kt` | PROPFIND 请求；`basicAuth()` 生成 Basic 头。`propfind(collection, root, ...)` 要**同时**给被列集合与数据源根 |
| `data/webdav/MultistatusParser.kt` | 解析 `207 Multi-Status`；**只按 local name 匹配**命名空间前缀。条目 path 一律**相对数据源根**（见问题 8），并剔掉 Depth:1 返回的集合自身 |
| `data/webdav/WebDavUrl.kt` | 地址解析、补尾斜杠、`rootCollection(rawUrl, rootPath)` 求数据源根、`addPathSegments` 正确编码、不把 `+` 当空格的百分号解码 |
| `data/webdav/WebDavModels.kt` | `WebDavEntry`（path/name/isDirectory/size/lastModified/contentType） |
| `data/webdav/WebDavException.kt` | `Unauthorized` / `Locked` / `NotFound` / `Http` / `Network` / `Malformed`，每类带一句人话 |
| `data/prefs/SecureCredentialStore.kt` | DataStore 存地址/用户名/根路径；`CryptoBox`（内部）用 Keystore AES-GCM 加解密密码；**`clearSource()` 只清数据源**（退出登录不再误删 TMDB key，问题 46） |
| `ui/nav/Routes.kt` | 路由常量（`ADD_SOURCE` / `BROWSE`） |
| `ui/source/SourceViewModel.kt` | 表单状态 + `TestState` + `test()`（单次请求，失败封锁重试） |
| `ui/source/AddSourceScreen.kt` | 添加数据源界面；连通成功后出「进入浏览」按钮（`onConnected`） |
| `ui/browse/BrowserViewModel.kt` | 目录浏览状态机：进入/返回/刷新/重试、目录缓存、`inFlight` + `nav` 序号防重（见问题 9）、401/429 后 `blocked` 停止一切自动请求 |
| `ui/browse/BrowserScreen.kt` | 面包屑（可点跳祖先）+ 刷新 + 列表（目录带 `›`、文件带大小、空目录显示「空目录」）+ `BackHandler` 返回上级；**点目录进目录、点文件进播放页**。媒体库入口已挪到底部 Tab |
| `player/PlaybackService.kt` | `MediaSessionService`：持有 ExoPlayer + MediaSession；`sessionActivity` 指向 MainActivity；**进度计时器**；媒体数据源 = `DefaultDataSource` 包住 WebDAV 那层（本地 `file://` 字幕要靠它，问题 55） |
| `player/WebDavMediaDataSource.kt` | 媒体流 DataSource：`open()` 时补 Basic 头，且只给数据源主机；取不到凭据直接抛错 |
| `player/PlaybackSelection.kt` | 「要播什么」的内存传递：选中的文件 + 同目录队列（刻意不进 nav 参数） |
| `ui/player/PlayerViewModel.kt` | 由 path 拼出绝对地址（另外刷新 `credentials.cached`）+ 队列指纹 + 续播位置 |
| `ui/player/PlayerScreen.kt` | `MediaController` + `AndroidView(PlayerView)`；**自绘控件**（`useController=false`）：顶部返回/标题/字幕/倍速/锁定、中间大播放键、底部细进度条；左右半屏上下滑调亮度/音量；自动隐藏；`setMediaItems(队列, 起播位, 续播位)`；旋屏不重设；把 401/429/404 翻成人话。**触摸层结构见问题 53，不得修改回兄弟节点** |
| `data/local/MediaItemEntity.kt` | 库表：`path` 当主键（相对数据源根，天然唯一，还能直接喂回 `child()`）；含解析结果与刮削结果 |
| `data/local/MediaDao.kt` / `MediaDatabase.kt` | 只读 Flow + 整体替换 + 标记单条刮削结果。**`media_items` 是缓存**（丢表重扫只影响它）；进度 / 收藏 / 改名 / 详情是用户数据，只走真迁移。DB 现在 **v6**（迁移链 3→4→5→6，全在 `MIGRATIONS` 数组，见问题 39 / 45） |
| `data/local/PlaybackProgress.kt` | 进度表 + DAO。独立于媒体条目，见问题 23 |
| `data/repo/ProgressRepository.kt` | 存进度 / 查续播点（片尾 30 秒内不算）/ 给海报墙的 Flow |
| `data/MediaTypes.kt` | 「哪些扩展名算视频 / 算字幕」+ **字幕与视频的配对规则**的唯一定义。配对有单测（`MediaTypesTest`） |
| `data/repo/LibraryRepository.kt` | 扫描器 + 刮削器 + `ScanState`/`ScrapeState` + 库的 Flow。应用作用域，见问题 16 / 17；**`scanJob`/`scrapeJob` 各自独立 + `cancelScan`/`cancelScrape`/`cancelAll`**（问题 49） |
| `data/tmdb/TitleParser.kt` | 文件名 → 检索词/年份/季集。规则来自真实命名，见问题 19，改动必须同步改单测 |
| `data/tmdb/TmdbClient.kt` | `search/movie` / `search/tv` + 两种凭据 + 429 按 `Retry-After` 退避（见问题 21） |
| `ui/library/LibraryViewModel.kt` | 很薄：把仓库的三个 Flow 转给界面 + 扫描/刮削开关 |
| `ui/library/LibraryScreen.kt` | 海报墙（有海报用 Coil 取图，没有就用解析出的片名占位）+ 扫描/刮削/设置 + 进度；失败不抹掉已有内容；**按剧聚合，点块进详情页** |
| `ui/library/WallEntry.kt` | 海报墙聚合的**纯函数** + `orderedEpisodes()`。有单测（`WallEntryTest`），改动必须同步改 |
| `ui/detail/DetailScreen.kt` / `DetailViewModel.kt` / `DetailSelection.kt` | 详情页：整页一体 —— 海报按原比例完整显示 + 下半部分用海报取色延伸（`PosterTint`）；可展开简介 / 分集 / 演职人员 |
| `ui/detail/PosterTint.kt` | 取海报底部一条的平均色，供详情页做「颜色延伸」背景（见问题 52） |
| `ui/settings/SettingsScreen.kt` / `SettingsViewModel.kt` | TMDB 凭据 + 清空媒体库 + 退出登录（后两个带二次确认） |
| `ui/theme/Theme.kt` | 配色：**柠檬绿强调**（取用户站点），浅/深两套跟随系统；亮色只做色块、文字走 `tertiary`（问题 40） |
| `ui/HeartButton.kt` | 收藏心形：矢量图标 + 缩放动画 + 48dp 热区，支持传描边色（压图上要用浅色） |
| `ui/Formats.kt` | `formatSize` / `formatClock` / `formatDurationWords`，各处共用 |
| `ui/library/PosterGrid.kt` | 海报墙组件，媒体库页与收藏页共用；`WallEntry.paths()` 也在这 |
| `ui/favorites/FavoritesScreen.kt` / `FavoritesViewModel.kt` | 收藏 Tab（复用 PosterGrid，按收藏 path 过滤） |
| `ui/nav/PlayerOrientation.kt` | 播放页自动横屏、离开还回系统（**必须在导航外壳里做**，见问题 36） |
| `data/local/TitleOverride.kt` | 手工改的片名/年份，独立表；所有读媒体条目的地方都会合并它 |
| `data/local/TitleDetail.kt` | 类型/国家/时长/上映日期/演职人员，独立表；**打开详情页时才拉**并缓存 |
| `data/repo/DetailsRepository.kt` | 拉详情（已有就跳过，不重复请求）+ 解析演职人员 JSON |
| `data/subtitle/SubtitleProvider.kt` | **字幕源接口**（可切换）+ 结果/异常模型 + 语言名。加新源只写一个实现 |
| `data/subtitle/OpenSubtitlesProvider.kt` | OpenSubtitles.com REST：搜索用 Api-Key、下载用账号换 Bearer；406/429 当额度不重试 |
| `data/repo/SubtitleRepository.kt` | 字幕搜索 / 下载 / **统一落盘**到 `filesDir/subtitles/`；挂载一律走本地文件（问题 56） |
| `data/Countries.kt` | iso-3166-1 → 中文国名（TMDB 不本地化国家名） |
| `data/repo/FavoriteRepository.kt` | 收藏仓库 |
| `data/local/MediaDatabase.kt` | **`MIGRATIONS` 数组在这儿**，容器整批注册（问题 39）；版本号 `VERSION` 单一来源，迁移链由 `MigrationCoverageTest` 锁住（问题 45） |

资源：`res/values/{strings,themes}.xml`、`res/xml/network_security_config.xml`（放行明文以便局域网 http NAS）、
`res/mipmap-anydpi-v26/ic_launcher.xml` + `res/drawable/ic_launcher_{background,foreground}.xml`（纯矢量图标，无 PNG）

构建文件：`settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`、`gradle/libs.versions.toml`、
`gradle/wrapper/*`、`app/build.gradle.kts`、`app/proguard-rules.pro`、`.gitignore`

**计划中但尚未创建**的目录：（暂无 —— `domain/` 用不上）

> **版本管理（2026-10-16 起）**：项目已是 git 仓库，远程在
> **https://github.com/Lebai280/MediaDock**（**私有**），分支 `master`。
> `.gitignore` 已排除 `local.properties` / `*.keystore` / `*.apk` / `build/` / `.gradle/`。
>  本文件要上 GitHub，**运维细节（WebDAV 地址 / 服务器 / SSH / 账号）一律不写进来**（见文件开头）。
> 另注：`gradle.properties` 里写死了本机 JDK 路径（`org.gradle.java.home`），换机器编译要改。

---

## 6. 路线图（Stage 2 起）

**Stage 2 — 目录浏览** （代码完成，待真机验证）：`BrowserScreen`，文件夹优先排序、面包屑、返回栈。
目录切换用 `WebDavUrl.child(root, path, isCollection=true)`；条目 path 与地址重建见问题 8。
**点文件进播放页** 归 Stage 3 —— 现在文件行不可点（没有水波纹，不假装能点）。

**Stage 3 — 播放** （代码完成，待真机验证）：`PlaybackService`（MediaSessionService，持有 ExoPlayer）
+ `PlayerScreen`（`PlayerView` 塞进 `AndroidView`，通过 `MediaController` 挂到服务上）。
鉴权见问题 12；`callTimeout` 与 gzip 两个问题见 10 / 11。
**不要**写触碰 `Range` 的 interceptor —— Range 由 DataSource 依据 seek 位置自动发。
验收：拖 ≥1GB MKV 的进度条能秒跳（服务器侧应看到 `206`）；旋转屏幕不中断。
「旋转不中断」的实现要点是问题 13（不得无条件重设媒体项）。

**已知遗留（小的）**：播放页被 MainActivity 的 Scaffold 内边距框住了，视频没有真正全屏
（状态栏/导航栏那圈是黑的）。要顶到边得把内边距从 NavHost 下放到各个页面，涉及
AddSourceScreen / BrowserScreen 一起改 —— 属于打磨项，未实现。

**Stage 4 — 媒体库** （代码完成，待真机验证）：用户主动点「扫描」→ 顺序 BFS 递归 →
`MediaItemEntity` 入 Room → 海报墙（未刮削时用文件名占位）。
Room 是海报墙的唯一数据源。**不要在启动时自动全盘扫描**（见硬约束）。
入口在浏览页头部的「媒体库」按钮（设置页要等 Stage 7）。

**已知取舍**：

- 海报墙是**平铺**的：一个视频一块。按剧集/剧名归并属于 Stage 5 的活儿（那要先做文件名解析）。
- 视频扩展名收得比较宽（含 `avi`/`rmvb`/`wmv`）。**media3 对这些不一定解得了**，
  点开可能提示播放失败 —— 宁可让用户看见自己的文件，也不要偷偷藏起来。
- 媒体库**还没成为 App 的首页**（起始页仍是浏览页）。等 Stage 6「继续播放」做完再定更合适。

**Stage 5 — 刮削** （代码完成 + 解析器/检索已验证，待真机验证）：
文件名解析 → TMDB → 落库 poster/backdrop/overview/rating；
Coil 加载 `https://image.tmdb.org/t/p/w500{posterPath}`。
- API key 存 secure prefs，**不写进 BuildConfig**
- 4 并发 + 429 按 `Retry-After` 退避 （问题 21：该 429 与 WebDAV 的 429 语义不同）
- `scrapeState` 列（`UNSCRAPED|MATCHED|FAILED`）+ `scrapedAt`，避免重复请求
- 刮削只处理 `scrapeState != 'MATCHED'`，所以再点一次「刮削」= 重试上次没匹配上的

**有意留到后面的**：单条详情页（`ui/detail/`）还没有，点海报是直接播；
按剧集/剧名归并海报墙也未实现（现在是一个文件一块，剧集多了会很长）。
这两个都依赖 Stage 5 已经落库的 `tmdbId`/`season`/`episode`，做起来不难。

**Stage 6 — 进度记忆 + 播放体验** （代码完成，待真机验证）：
进度表 + 5 秒节流落库（在服务里）+ 暂停补记；`setMediaItems(items, startIndex, startPositionMs)`
一次设好队列与续播位；同目录视频作队列（自动下一集）；海报块细进度条；倍速菜单。
片尾 30 秒内不算续播点。**详情页未实现**，所以「继续播放」目前体现在海报进度条上。

**Stage 7 — 字幕与设置**：同级目录 `.srt/.ass` 作为可选字幕轨（`MediaItem.SubtitleConfiguration`）
+ 内嵌字幕轨切换；设置页含 TMDB key、清缓存、退出登录。

### 文件名解析规则（Stage 5 使用）

去扩展名 → 点/下划线转空格 → 去 `[...]` 与发布标签（1080p / x265 / HEVC / WEB-DL / BluRay / -GROUP）→
匹配集数 → 取片名。

**集数**（按优先级）：
1. `S(\d{1,2})E(\d{1,3})`（如 `S02E29`）
2. `EPxx` / `Exx`（如 `EP29`、`E29`）
3. `1x03` 写法
4. 文件名末尾的裸数字（如 `葬送的芙莉莲29.mp4`；`720` 等分辨率数值除外）
5. **仅当文件位于季目录**（`S01` / `Season 1` / `第1季`）时，文件名开头的 `NN.` / `NN-` / `NN_`
   也算集数（如 `S02/简日双语/29.简日双语.mp4`）。
   不在季目录时，开头的数字只当目录序号（`12.片名.2018.mkv` 不会被误判成第 12 集）。

**季号**：优先取文件名里的 `Season N`，否则从路径里的 `S01` / `Season 1` / `第N季` 目录补。

**片名**（按优先级）：
1. 方括号里的中文（`[虫虫危机]`），但 `【电影】/【动画】` 这类分类标签不算；
2. 文件名里能抠出的连续中文段；
3. 目录名里能抠出的连续中文段；
4. 洗掉标签后的英文；
5. 第一个词回退。

**已知命名限制（会按“名字问题”处理，不写特判）**：
- `第29集` 这种以“集”结尾的写法**不识别**为集数（只认末尾数字或 `EP/E/SxxExx`）。
- `第二季` 这种中文数字的目录**不识别**为季目录（只认 `S02` / `Season 2` / `第2季`）。
- 同一个目录（或同一个名为 `简日双语` 的发布标签目录）里放多部剧，会按目录合并成一部剧。
- 电影合集目录里以 `1.`、`2.` 开头的文件，开头数字不会被当成集数。


### 后台播放的 manifest 要求（Stage 3 使用）

`FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` 权限，service 标
`foregroundServiceType="mediaPlayback"`，API 33+ 还要运行时 `POST_NOTIFICATIONS` ——
否则 service 启动即抛异常。

---

## 7. 问题记录（均已解决）

### Android / AGP 9

1. **`services.gradle.org` 下不动** —— 它 307 跳到 github.com，本机被墙。
   用 `https://mirrors.cloud.tencent.com/gradle/`。wrapper 的 `distributionUrl` 已指向它。
2. **AGP 9 自带 Kotlin 支持，不能再挂 `org.jetbrains.kotlin.android`**。报错原文：
   *The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0*。
   删掉即可，但 **`org.jetbrains.kotlin.plugin.compose` 和 KSP 仍要留**。
3. **`kotlin { compilerOptions { jvmTarget } }` 块仍可用**（compose 插件提供该扩展），不用改。
4. **compileSdk 必须 ≥ 37** —— navigation-compose 2.10.2 / coil3 3.6.3 / compose 1.12.1 / core 1.19.1
   的 AAR 元数据都要求 37+，写 36 会在 `checkDebugAarMetadata` 一次性报 23 条错。
5. **Android 现在有「次版本」平台**，要写两行：
   ```kotlin
   compileSdk = 37
   compileSdkMinor = 2
   ```
   只写 `compileSdk = 37` 找不到平台（平台目录名就叫 `android-37.2`）。
6. **Compose 里 Kotlin 属性 setter 会和方法撞名** —— `var url by mutableStateOf("") ` + `fun setUrl(...)`
   会报 `Platform declaration clash`。ViewModel 里的写法是属性用 `private set`、方法叫 `editUrl()` 之类。
7. `gradlew` 由 Gradle 9.8.1 生成，`gradlew.bat` 需 CRLF（已是）。

### Stage 2 问题（已修复）

8. **条目 path 必须相对「数据源根」，不是相对「被列举的集合」** ——
   原 `MultistatusParser` 把 href 相对**请求的那个集合**去前缀。只列根目录时两者恰好相同，
   所以 Stage 1 没暴露；一旦浏览进子目录，条目 path 就只剩本级短名，再拼回地址会**丢掉父路径**
   （等于去请求另一个目录）。
   修法：`parse(input, collectionEncodedPath, rootEncodedPath)` —— 按 root 相对化得到 path，
   再用「collection 相对 root 的路径」把 Depth:1 必然返回的**集合自身**那一条剔掉。
   少了后一步，进任何子目录都会看到一个指向自己的幽灵文件夹。
   真实服务器已验证：子目录返回 `自身 + 全部子项`，子项 href 是**带完整父路径**的绝对路径。
9. **浏览页要防「慢请求覆盖新导航」** —— 用户点进 A（还没回来）又退回已缓存的父目录，
   A 的响应回来会把界面又拽回 A。用 `nav` 序号解决：每次界面切走 `nav++`，
   发请求时记下 token，回来时 `token == nav` 才允许改 `path` / `state`（缓存照存不误）。
   另用 `inFlight` 集合保证**同一目录不并发取两次**（连点 10 下 = 1 个请求）；
   401/429 则**不看序号、立即**置 `blocked` 停手。

### Stage 3 问题（已修复）

10. **给 PROPFIND 用的那个 OkHttp 客户端不能拿去放视频。** 它带了 `callTimeout(45s)`，
    而 `callTimeout` 是**整个请求**的总时限 —— 一个几 GB 的文件要连着读好几分钟，
    使用后会在短时间后中断，容易被误判为网络问题。
    → AppContainer 里另开了 `mediaOkHttpClient`，**不设 callTimeout**。
11. **媒体流必须 `Accept-Encoding: identity`。** OkHttp 默认加 `Accept-Encoding: gzip`
    并**透明解压**响应体，于是 `Content-Length` 和实际拿到的字节数对不上，
    字节区间（Range）随之失效 —— 正好就是「拖进度条」要用的东西。
    → 只在 `mediaOkHttpClient` 上失败一个只改这一个头的 interceptor。**Range 一个字没碰。**
12. **鉴权头要逐次请求补，不能写死在 DataSource 工厂里。** 凭据是从 DataStore 异步读的，
    写死在工厂就有「播放器已经 prepare、凭据还没读出来」的空窗，那一下发出去的是
    **未鉴权请求** —— 在 OpenList 上等于记一次登录失败，会连累所有人。
    → 改成 `open()` 时才从 `credentials.cached` 取；取不到**直接抛错**，绝不发出去。
    并且只在目标主机 == 数据源主机时才加头，密码不会发送到其他主机。
13. **旋屏不能让播放从头开始。** Activity 重建 → 新的 MediaController；如果
    `LaunchedEffect` 里无条件 `setMediaItem`，就会 reset 到 0。
    → 判 `currentMediaItem?.mediaId != mediaId` 才 set（mediaId 用的就是文件路径）。
    「旋转屏幕不中断」靠的就是这个判断。
14. **`PlaybackService` 的 `android:exported` 还没在真机上确认过。** 现在写 `false`
    （同 UID 绑定不受 exported 限制，不导出时其他组件无法连接该媒体会话）。
    **若点播放毫无反应 / 显示「连接播放服务失败」，第一件事就是把它改成 `true` 再试** ——
    密码外泄这一层已经由问题 12 的主机判断挡住了，所以改 true 是安全的。
15. `init { ... }` 块里**不能写 `return`**（Kotlin 不允许），要包一个私有函数再 return。

### Stage 4 问题（已修复）

16. **取消扫描后不能同时把半份结果写进去。** 第一版是「循环退出 → 写库」，而取消也是
    「循环退出」，于是取消会把自己当成扫描完成，用不完整的结果覆盖掉上一次的库。
    → 写库前显式判 `isActive`，取消就直接 return（库保持上一次的样子）。
17. **应用作用域的封锁不会自己消失。** 浏览页的 `blocked` 是靠 ViewModel 重建清掉的；
    `LibraryRepository` 活在进程里，重建不了 —— 用户重填密码后照样扫不了。
    → 加了 `reset()`，由导航层在「重新登录」那条路上显式调一次。
18. Room 的 KSP 产物在 `app/build/generated/ksp/debug/**kotlin**/`（不是 `java/`），
    Room 2.8 生成的是 Kotlin。想确认处理器真跑到了就找 `MediaDatabase_Impl.kt`。

### Stage 5 问题

19. **文件名解析的规则必须来自真实命名，不能凭想象。** 实际列举用户库之后发现三条：
    - 集数标记可能是 **`EP01`**，不一定是 `S01E01`（`河西走廊...2015.EP01.4K...`）。
    - **电影的片名常挂在目录上**：目录叫 `[虫虫危机].A.Bug's.Life.1998.BluRay...`，
      而里面的文件只有英文名 —— 只看文件名会漏掉中文名。
    - 中文库里**能抠出连续中文段就用它检索**（TMDB 的 zh-CN 对中文名匹配很好），
      比拿整串 `河西走廊 HeXi Corridor 2015` 去搜强得多。
    → 这三条都写进了 `TitleParser` 并有单测锁住。**改解析器必须同步改单测**，
    那是唯一能在没有真机时证明它没坏的东西。
20. **"4 并发信号量"用 `chunked(4)` + `awaitAll` 就够了**，不必引入 `Semaphore`：
    一批 4 个、批间是天然的顺序点，取消也在批间生效，代码少一半。
21. **TMDB 的 429 和 WebDAV 的 401/429 是完全相反的两回事，不得将规矩串了。**
    - WebDAV（OpenList）：429 = 账号被锁，**绝不能重试**，重试会连累所有人。
    - TMDB：429 = 限流，**按 `Retry-After` 退避重试才是对服务器友善的做法**。
    代码里两处都写了醒目注释，改的时候不得将这条规矩挪错地方。
22. 解析器要同时看**文件名和父目录名**。扫描时父目录名从 path 现算：
    `path.substringBeforeLast('/', "").substringAfterLast('/')`。

### Stage 6 问题

23. **进度绝不能挂在 `media_items` 上。** 每次扫描都是 `deleteAll + insertAll` 整表替换，
    进度写在里面会被每扫一次抹一遍。而且从浏览页直接播、没进过媒体库的文件也得有进度。
    → 单开 `playback_progress` 表（path 主键）。
24. **队列指纹必须和实际队列同源。** 播放页拿「队列指纹」判断控制器上挂的是不是同一批；
    第一版指纹直接取 `playbackSelection.queue`，而实际队列还做了「空则回退成单元素」，
    两者不同源 → 旋屏时会误判成「不是同一批」→ 重新 `setMediaItems` → **播放被拽回开头**，
    正好毁掉「旋转不中断」。→ 两者都从同一个 `queue` 字段来（见 `PlayerViewModel`）。
25. **`remember` 不能写在 `LazyVerticalGrid` 的内容 lambda 里** ——
    那是 `LazyGridScope`，不是 `@Composable` 上下文，直接编译报错。要提到 grid 外面。
26. 海报墙按目录分组要**先 `groupBy` 一次（O(n)）**，不得在每块海报里再 `filter` 整个库（O(n²)）。
    （现在换成了 `WallEntry.kt` 里的 `groupWall()`，一次性算好。）

### 分季分集问题

27. **海报墙绝不能「按目录分组」** —— 这是最危险的一条。用户的
    「豆瓣电影Top250【723GB】」这类合集目录里很可能**平铺着几百部电影**，
    按目录分会把它们并成一块；「皮克斯」那种一片一目录的结构又正好相反。
    真正的判据是**有没有识别出集数**：只有 `episode != null` 的条目才参与聚合成剧，
    而且同一个目录里要有 **≥2 集**（否则片名里碰巧匹配到 E 数字的单片会变成「只有 1 集的剧」）。
    → `ui/library/WallEntry.kt` 的 `groupWall()`，**有单测锁着**，改之前先看 `WallEntryTest`。
28. **Room 的 `IN (:paths)` 遇到空列表会生成 `IN ()`，那是 SQL 语法错。**
    详情页的入参来自内存（进程被杀就空了），所以 `LibraryRepository.observeByPaths()`
    在空列表时直接返回 `flowOf(emptyList())`，不得将空列表交给 Room。

### Stage 7 问题

29. **字幕配对必须卡在分隔符上。** 「字幕名是视频名的前缀」这个规则本身对，
    但不加边界就会把 `Ep10.srt` 认领给 `Ep1.mkv`。规则是：完全相等，
    或者「前缀之后紧跟着 `.` `-` `_` 空格」。见 `MediaTypes.matchesSubtitle()` + 单测。
30. **media3 默认不会自动选「没有语言标记」的字幕轨。** 同级目录的字幕正是这种，
    表现就是「明明有字幕却不显示」。要么给字幕设语言，要么在
    `DefaultTrackSelector` 上开 `setSelectUndeterminedTextLanguage(true)`（本项目选了后者）。
31. **底部 Tab + `popUpTo(BROWSE)` 是错的组合。** Tab 切换用 `saveState` 会把 BROWSE 从
    回退栈里弹掉，所以「退出登录/重新登录」时 `popUpTo(BROWSE)` 可能什么都没弹，
    后面会残留 Settings/Library 页。→ 改成 `popUpTo(navController.graph.id) { inclusive = true }`
    弹整个图。
32. **`PlayerView.setShowSubtitleButton(boolean)` 只能当方法调。** 它只有 setter 没有 getter，
    Kotlin 合成不出属性，写 `showSubtitleButton = true` 会报 Unresolved reference。
    （查证办法：`javap -classpath <media3-ui-api.jar> androidx.media3.ui.PlayerView`。）

### 真机验证阶段问题（均已修复）

33. **⭐ kotlinx-serialization 的编译器插件从来没被 apply。** `libs.versions.toml` 里
    声明了 `kotlin-serialization` 插件，但 `app/build.gradle.kts` 的 `plugins {}` 里没写
    `alias(libs.plugins.kotlin.serialization)`。后果：`@Serializable` 不生成 serializer，
    **编译期完全正常**，一跑就崩在
    `Serializer for class 'SearchResponse' is not found. Please ensure that class is marked
    as '@Serializable' and that the serialization compiler plugin is applied.`
    → 这就是「只在运行时才暴露」的典型。**教训：凡是编译器插件（serialization / kapt / ksp /
    compose），光有依赖不算数，必须检查 `plugins {}`。**
    验证办法：`find app/build -name "*\$\$serializer.class"` 看生成物有没有。
34. **详情页背景图在横屏下会把整页顶出去。** 第一版用
    `fillMaxWidth().aspectRatio(16f/9f)` —— 2560 宽的屏幕上算出 1440px 高，**正好占满整屏**，
    片名和播放按钮全跑到屏幕外（要滚动才看得到）。竖屏没这问题，横屏一眼就废。
    → 改成固定 `height(200.dp)` + `ContentScale.Crop`。
    （另外：该缺陷最初被误判为「详情页全黑 / 白屏」，实际上是背景图还在加载、又占满屏。）
35. **MuMu 上 `adb shell input tap` 会被当成滑动。** 注入的触摸有抖动，一 tap 就被 Compose
    判成拖动 → 海报墙滚动而不点进去。**用「同点按住」代替**：
    `adb shell input swipe X Y X Y 150`（起止同点 + 150ms），可靠命中。
    另外 `uiautomator dump` 给出的 bounds 是**物理坐标空间**（2560x1440），
    但 `wm size` 报的是逻辑尺寸（1440x2560）；E 盘 Git Bash 还会把 `/sdcard/x.xml`
    当 Windows 路径转换 → 要 `export MSYS_NO_PATHCONV=1`。

### 模拟器访问 TMDB 的配置方法（已验证）

```bash
# 让模拟器走宿主机的 Clash。10.0.2.2 = 宿主机的回环（不是 127.0.0.1！）
adb shell settings put global http_proxy 10.0.2.2:7897
# 撤销
adb shell settings put global http_proxy :0
```
OkHttp 默认读系统代理，所以设完刮削就能通。**前提是 Clash 允许来自本机回环的连接**
（只监听 127.0.0.1 也行，因为 10.0.2.2 映射的就是宿主机回环）。

### 界面改造问题

39. **⭐ 加迁移必须同时确认容器注册了它 —— 忘了就是静默清库。**
    加了 `title_overrides` 表（v4→v5）并写了 `MIGRATION_4_5`，但 `AppContainer` 里
    `.addMigrations()` 只注册了 `MIGRATION_3_4`，于是 4→5 没有可用迁移 →
    走了 `fallbackToDestructiveMigration` 回退 → **媒体库、播放进度、收藏全被清掉**。
    编译、单测、启动全都正常，只有「数据没了」这一个症状。
    → 修法不只是补注册，而是让这类错误不可能再犯：
    **迁移清单收进 `MediaDatabase.MIGRATIONS` 数组，容器 `.addMigrations(*MediaDatabase.MIGRATIONS)`
    整批注册**，以后加迁移只改数据库文件一处。
    （注意 companion 是顺序初始化的，数组要写在各个迁移定义**之后**。）
40. **M3 默认拿 `primary` 当文字色，亮色强调色一换就露馅。** 强调色改成亮柠檬绿后，
    浅色主题里 `TextButton`（扫描/刮削、刷新）、`NavigationBar` 的选中文字、
    「已连接」这类状态文字全都变成了亮绿 → 对比度 1.5:1，等于看不见。
    另外 `NavigationBar` 的默认容器/指示器用的是 `secondaryContainer`，
    不覆盖就是**淡紫色**，跟柠檬绿打架。
    → 规则：**亮色强调色只做色块，文字一律走 `tertiary`**；导航栏要显式给
    `containerColor` + `NavigationBarItemDefaults.colors(...)`。
    改完颜色**必须跑对比度脚本**，不得仅凭肉眼。
41. **Compose 的 `Modifier.blur` 只吃一个半径，做不了渐变模糊。**
    用户要「从中间往下虚化逐渐加深」，一刀切（上半清晰下半模糊）的接缝非常生硬。
    → 做法：**底层放一张重模糊的图，上层放同一张清晰的图，用竖直渐变把上层的
    不透明度从中间往下归零**（`graphicsLayer { compositingStrategy = Offscreen }` +
    `drawWithContent { drawRect(渐变, blendMode = DstIn) }`）。
    两层同尺寸同位置，看不出是两张图，视觉上就是渐进加深。
42. **不得使用 `♡` / `♥` 字形当图标** —— 用户明确指出「太扁、点击时大小会变」：
    那是两个不同字形，宽高都不一样，一点下去布局就跳。
    → 用矢量图标（Material 的 `Favorite` / `FavoriteBorder`），画布尺寸恒定，
    填充/描边只是颜色和绘制方式不同；再加一个 `Animatable` 缩放弹一下（主流做法）。
    点击热区要 ≥48dp，图标本身 30dp 就够。

### 类型与演职人员问题

43. **⭐ `init {}` 块必须写在它引用的属性之后。** Kotlin 按**声明顺序**初始化：
    将 `init { entries.first { ... } }` 插入到 `val entries = ...` **之前**，
    编译一切正常，一打开详情页就
    `NullPointerException: Attempt to invoke interface method 'Flow.collect' on a null object reference`。
    → 把 `init` 移到所有属性之后。**这类 缺陷 只在运行时出现，编译和单测都拦不住。**
44. 同时记：`StateFlow.stateIn(..., 初值)` 的初值是**立刻**发出的，
    所以 `flow.first()` 会拿到初值而不是第一个真实值。要等真实数据得用
    `first { it.isNotEmpty() }` 这种带条件的。

### 加固与详情页重做问题（2026-10-15 夜间）

45. **回退策略：宁可打不开，也不要静默清库。** `fallbackToDestructiveMigration(dropAllTables = true)`
    的语义是「找不到迁移路径就把**所有表**丢掉重建」—— `MIGRATIONS` 数组只防「忘了注册」，
    防不了「版本号 +1 却忘了写迁移」。改成 `...OnDowngrade(dropAllTables = false)`：升级方向缺迁移就
    **抛异常**（响亮），只有降级（手动装旧 APK）才允许破坏重建、且只丢结构变了的表。
    另加 `MigrationCoverageTest` 把「链断了」提前到单测阶段；版本号提成 `MediaDatabase.VERSION` 单一来源。
46. **`DataStore.edit { it.clear() }` 清的是整个 preferences 文件。** 退出登录原本调它，于是
    `tmdb_secret_enc` 一起没了 —— 而确认文案只说会清数据源，用户不知道为什么刮削忽然失效。
    改成 `clearSource()`（只 `remove` 数据源 4 个 key）。**改这里要同步看 `SettingsScreen` 里
    `Confirmable.Logout` 的文案。**
47. **`coroutineScope { batch.map { async { … } }.awaitAll() }` 里一条抛异常会连坐整批。**
    刮削批内任一条抛非鉴权的 `TmdbException`（网络抖动 / 非 200）都会取消同批、向上抛、整轮 Failed ——
    92 条跑到一半遇一次抖动就前功尽弃。改成单条 `try/catch` 把非鉴权异常降级成「这条没匹配上」，
    鉴权异常照旧抛出去中止整轮。
48. **`onDestroy` 里 `saveProgress()` 紧跟 `scope.cancel()` 会把这次写入一起取消。**
    `saveProgress` 只是把真正的库写入 `launch` 出去异步做，`cancel` 一来协程可能还没开始就被杀，
    退场时最后一段进度就丢了。**只 cancel ticker，不得 cancel 整个 scope。**
49. **扫描和刮削共用一个 `job` 字段会互相牵连。** 取消刮削会同时把扫描结果提示也抹成 Idle；
    刮削中点扫描又静默无反应。拆成 `scanJob` / `scrapeJob` + `cancelScan()` / `cancelScrape()` / `cancelAll()`。
    两者仍互斥（扫描整表替换 `media_items`，并发会把刮削中的结果换掉），互斥在界面上**置灰按钮**，
    避免点击无响应。
50. **不得将界面 StateFlow 的快照作为「当前是否收藏」的判断依据。** `favorites` 是 `WhileSubscribed` 的 StateFlow、
    初值是空集，`path !in favorites.value` 在订阅到位前恒为 true —— 翻转方向就反了。
    改成 `FavoriteRepository.toggle(path)` 先查库（DAO 加 `countOf`）再翻。
51. **横版背景图铺满竖屏会被放大到只剩一条。** 16:9（1.78）铺 1440×3200（0.45）要按高度铺满，
    宽度被撑到 ~5700px，只露 1440/5700 ≈ 25% —— 用户原话「放大后只能看到部分海报」。
    详情页顶部改用**海报按原比例（2:3）`Fit` 完整显示**（不裁剪）。
    `Fit` 而非 `Crop`：万一只有横版图也完整显示、上下空档露出页面底色，接得上。
52. **要「一体、不割裂」，就不得使用另一张图/另一种材质去铺下半部分** —— 用海报自己的颜色。
    做法：取海报**底部一条的平均色**当页面主色，大图下半部的压暗**渐变成这个色**（不是黑），
    图下面再用同一个色铺（略微渐暗）。这样图和内容是同一色调，接缝看不出来。
    取色单独下一张 `w92`（几 KB）；**亮度要压到 ≤0.30 再当底色**，否则白字读不清；
    取不到就退回中性深色。`ui/detail/PosterTint.kt`。
53. **Compose 自绘覆盖层压在 `PlayerView` 上、按钮收不到触摸 —— 是层的结构问题，不是 SurfaceView 的锅。**
    `AndroidView(PlayerView)` 上盖一层 Compose 控件，只要把「点空白显示/隐藏」挂在**铺满的父 `Box`**
    的 `pointerInput` 上、控件作为**子节点**，子节点就先拿到事件，按钮 / 进度条全正常。
    反例（上一轮失败的写法）：把全屏「点击切换」层和按钮做成**兄弟**、且让点击层**声明在后面**
    （= 画在上面、也先拿事件）—— 它会截胡所有触摸，按钮一次都收不到。**不得修改回兄弟结构。**
    （另外：`screencap` 抓 SurfaceView 上的视频帧经常是黑的，不得据此判断「播放坏了」——
    看日志里 `PlaybackState` 的 position 有没有在走。）
54. **同一个节点上挂两个并行 `pointerInput`（一个 `detectTapGestures`、一个 `detectVerticalDragGestures`）
   会互相抢事件。** 表现：上下滑调亮度/音量**又抖又不灵敏**，一次 700px 的滑动只生效了几个像素
    （日志显示累计行程只有 ~9px）。因为前一个手势检测器把事件流吞掉了大半，后一个只吃到末尾一小段。
    → 合成**一个** `pointerInput`，用 `awaitEachGesture { awaitFirstDown() … }` 手写循环同时判「点」
    和「滑」，并且**用累计位移**算调节量（拿单次事件的增量去加起始值只会原地抖）。
    同理：进度条要 `down.consume()`，否则父层也会跟着起手势，在进度条上拖会被误判成「点了一下」。
55. **本地（`file://`）字幕读不了 —— 因为媒体数据源被本项目那层只认 http(s) 的 factory 包住了。**
    `WebDavMediaDataSource` 底层是 OkHttpDataSource，只处理 http(s)；直接把它交给
    `DefaultMediaSourceFactory` 的话，`file://`（本地字幕）和 `content://` 都是死路。
    → 外面再包一层 `DefaultDataSource.Factory(context, 本项目的factory)`：本地/内容 URI 走它自己的
    数据源，其余才落到本项目这层补 WebDAV 鉴权。加本地字幕之前就得这么改。
56. **想在真机上自动验 SAF 选择器（`OpenDocument`）基本没法自动化。** 两个问题：
    (a) `adb push` 进 `/sdcard` 的文件**不进 MediaStore**，选择器的「最近」列不出来（用
    `content call --uri content://media/external/file --method scan_file --arg <path>` 也不一定进）；
    (b) DocumentsUI 的标题栏（☰ 抽屉、 搜索）**吃不下 adb 注入的点击**，点不动。
    → 这类流程的验证交给人在手机上点，不得在 adb 上耗。
57. **按「类型 / 国家 / 演员」搜库默认搜不到 —— 那些字段在 `title_details` 里，而它是「打开详情页才拉」的懒加载。**
    要全库可用就得先跑「补齐影片信息」（`DetailsRepository.fillMissing`，首页 ⋯ 菜单）。
    改搜索规则或改详情加载策略时，记住这条因果（搜索的单测里也专门锁了一条「没详情时按类型搜不到、按片名照搜」）。
58. **只 `cat` 出 `library.db` + `-wal`、不带 `-shm` 去读库，会读到旧快照；因此曾发生一次误判。**
    核对「补齐」结果时这么读的，SQLite 没应用 WAL，看到的是补齐前的行数，于是断定「没写进去 / TMDB 连不上」，
    实际上 61 条都补上了。**要读 App 正在用的库，`-shm` 必须一起拿（或直接看 App 界面上的数字）。**
    附属教训：**`ping api.themoviedb.org` 不通 ≠ HTTPS 不通** —— 很多网络屏蔽 ICMP，不得以 ping 作为连通性判据。
59. **`by` 委托属性做不了智能转换。** `val scan by vm.scan.collectAsState()` 之后写
    `when { scan is ScanState.Running -> scan.directories }` 会报
    `Smart cast to 'ScanState.Running' is impossible, because 'scan' is a delegated property`。
    → **先读进局部 val**（`val scanState = scan`）再用。首页那堆状态就是这么处理的。

### 正式签名与发版问题（2026-10-16）

60. **⭐ 卸载会销毁该应用在系统 Keystore 里的密钥 —— 于是加密存的东西「备份了也解不开」。**
    换签名必须卸载重装，已提前备份 Room 数据库并完整保留媒体库，
    但**漏了 DataStore 里的凭据**：WebDAV 密码 / TMDB 密钥是用 Keystore AES-GCM 加密的，
    而 Android 在卸载时把该应用在 Keystore 里的密钥一起删了。**哪怕提前拷出 DataStore 文件，
    装回去也解不开密。** 结果用户得重填 WebDAV 密码和 TMDB 密钥。
    → **教训：判断「什么会被卸载带走」要看它是不是走 Keystore，不是看它在哪个文件里。**
    免重填的正解：**卸载前在 App 里点「分享给朋友」把配置复制到剪贴板**（那条路输出明文），
    存下来再卸。见「换成正式签名」那节。
61. **`run-as` 依赖 `debuggable`，正式包一定用不了 —— 不得视为故障。**
    - 想在正式包上读/写 `databases/`、`shared_prefs/` 是做不到的，报 `package not debuggable`。
      这正是 `debuggable` 关上了的**证据**（反过来说，能 `run-as` 就说明包还是可调试的）。
    - 迁移时若需要往正式包里写文件，就**临时**把 `isDebuggable` 打开构建一版，写完再关掉重构建覆盖。
    - 另：`adb shell pm install /sdcard/Download/xxx.apk` 会报 `Can't open file` ——
      Android 15 分区存储下那个文件属主是浏览器 uid、mode 700，shell 读不到。
      **不是包坏了。** 要装就 `adb install -r <本地文件>`（先用 sha256 证明两边字节一致）。

### 播放器行为调整问题（均已修复）

36. **强制横屏的两个问题。**
    - `SENSOR_LANDSCAPE`（跟传感器的横屏）在**系统旋转锁打开时会被忽略**（AOSP 行为：
      锁了就让用户偏好赢）—— 代码明明请求了，画面死活不转。用户的手机锁竖屏，
      所以必须用**硬性** `LANDSCAPE`。代码里按 `Settings.System.ACCELEROMETER_ROTATION`
      分开处理：没锁用 SENSOR（能左右翻转），锁了用硬性（能转起来）。
    - 「还回竖屏」**不能写在播放页的 DisposableEffect 里**：强制横屏会让 Activity 重建，
      闭包里捕获的 `Activity` 随即是已销毁的旧实例，在它身上设方向毫无反应 ——
      表现就是「进了横屏再也回不去，整个 App 卡在横屏」。
      → 方向逻辑挪到 `ui/nav/PlayerOrientation.kt`，由导航外壳在**路由变化时**执行，
      那时拿到的总是当前 Activity。
37. **「离开播放页就暂停」的判据换过三次，前两个都是错的。**
    -  `isChangingConfigurations`：加了「自动横屏」之后就坏了 —— 离开播放页会另外触发
      「旋回竖屏」的配置变更，这个标志变成 true，暂停被跳过（实测返回后一直 PLAYING）。
    -  销毁时读「当前路由」：**composable 的销毁比路由状态更新更早**，读到的还是 player。
    - 最后把 **MediaController 交给 ViewModel 持有**，在 `onCleared()` 里暂停：
      它只在导航条目真被弹出时触发，旋屏/配置变更是保留的，语义正好。
      另外好处：旋屏不再重建控制器，也就不会重新缓冲。
38. **adb 驱动真机的输入方式，模拟器和真机正好相反：**
    - MuMu：`input tap` 会被注入抖动判成滑动 → 用 `input swipe X Y X Y 150`（同点按住）。
    - 真机（三星）：`input swipe`（同点）会失效，`input tap` 才稳。
    - 另外 `uiautomator dump` 的 bounds 是**物理坐标**，Git Bash 下要
      `export MSYS_NO_PATHCONV=1`，否则 `/sdcard/x.xml` 会被当 Windows 路径转换；
      截图存到项目内路径比存 `/tmp` 可靠（后者会被这层转换影响）。

### 详情页重做 + 收藏（2026-10-15，参照 VidHub 版式）

用户给了三张 VidHub 截图要「这种效果」。**已在真机上验证（深浅两套配色各截了一张）**：

- **大图铺满 + 底部渐变 + 片名/元数据叠在图上**（没背景图就用海报顶，都没有才用纯色）。
- 元数据行：`★7.3 · 1937`（评分/年份/N 集）。
- **文件信息行**：`8分钟 | 137.7 MB | 4K` —— 时长来自 `playback_progress.durationMs`（播过才有，
  不编）、大小来自扫描、**分辨率从文件名读**（`MediaTypes.resolutionLabel`，有单测）。
- **大号圆角「继续播放」+ 副标题「上次播放到 05:27」**（没进度时是「播放」）。
- `♡ 收藏`（真能用，见下）、简介（5 行截断）、分集列表照旧。
- 左上角 `‹` 返回（推入页本来只有系统返回手势）。

**收藏**：新表 `favorites`（独立表，理由同进度表 —— 媒体条目每次扫描整表替换）。
**这里第一次写了真迁移** `MIGRATION_3_4`，不再用 destructive：进度和收藏是用户数据，
丢一次就是真丢。只有动 `media_items` 结构时才允许回退到丢表重扫。
**实测验证**：v3→v4 装上后，92 条库和进度都还在（详情页能显示出迁移前存下的时长与进度）。

**和参考图还差的**（并非未实现，而是缺少数据或尚未排期）：
- **类型/地区**（截图里的「日本 | 犯罪,剧情」）：TMDB 搜索结果带 `genre_ids`，
  但要再调一次 `/genre/movie/list` 建映射，还未实现。
- **演职人员**：要调 `/movie/{id}/credits` + 存起来，另开一批。
- **多资源切换**（A(1)·MP4…）：要按 `tmdbId` 把同一部片的多个文件归并；用户库一文件一片，暂时用不上。
- **片单 / 举报图标**：未实现—— 不想放两个点了没反应的假按钮。
- **底部 4 个 Tab（媒体库/文件源/影视服务器/设置）**：尚未实现，属于下一阶段。

### 界面改造（2026-10-15 同一会话，多轮迭代）

用户给了 VidHub 截图 + 自己站点（星屿）截图，要求「浅色、轻盈、优雅」。**每一步都在
三星 S21 Ultra 上截图确认过**（深浅两套配色都看）。这一节记的是**为什么这么做**，
样式细节看代码。

#### 配色：强调色换成用户站点那颗柠檬绿

- `primary = #C4E04F`（亮柠檬绿），`onPrimary = #1A1C10`（近黑）——
  就是用户站点那个「随机开一站」药丸按钮的配色。
- **亮柠檬绿不能当文字**（浅底上对比度只有 1.5:1，完全看不清），所以角色拆开：
  `primary` 只给**色块**（填充按钮、进度条、导航选中胶囊），
  当文字用的一律走 `tertiary`（浅色主题 `#5A7200`，对白卡 5.47:1）。
  已出现问题的位置：M3 的 `TextButton`、`NavigationBar` 默认都拿 `primary` 当文字色，
  所以「扫描/刮削」「刷新」「已连接」这些都得显式改色，见问题 40。
- 浅色主题：底 `#F6F6F3`、卡 `#FFFFFF`、浅卡 `#EFEFEA`、正文 `#17181A`、次要 `#5F6063`。
- 深色主题保留（跟随系统），同样使用柠檬绿强调。**两套主题均已实现**，为产品明确要求。
- 导航栏必须显式给 `containerColor = surface` + 覆盖
  `NavigationBarItemDefaults.colors(...)`，否则 M3 默认的 `secondaryContainer`
  是**淡紫色**，跟柠檬绿完全不搭（第一版就是这样，截图一看就露馅）。
- 改配色后**必须执行对比度脚本**（本文件内提供；15 组前景/背景全部达到 4.5:1 方可完成）。

#### 导航：四个图标 Tab

- 媒体库 / 收藏 / 浏览 / 设置，**只放图标不放文字**（用户要求），
  `contentDescription` 保留中文标签给无障碍。
- **Material 图标可以直接用** —— 之前几个 Stage 一直躲着 `material-icons-extended`
  （怕 Compose 1.12 不再提供），实测能编译能用，只提示 `Icons.Filled.List` 要换
  `Icons.AutoMirrored.Filled.List`。图标只用了 core 里就有的
  （Home / Favorite / FavoriteBorder / List / Settings / MoreVert / ArrowBack）。
- 收藏那个按主流做法**未选中空心、选中实心**。

#### 详情页（改了三轮才对）

1. 第一版：小海报 + 片名/年份 + 播放按钮。用户给 VidHub 截图后重做。
2. 第二版：大图铺满 + 底部渐变 + 片名叠图上。用户指出
   「**图片要延伸到信息块后面**（他画了绿线标范围），**仅背景虚化**」。
3. 第三版（当前）：**整块高度由内容决定**，背景图跟着信息块一起延伸；
   片名以下是**渐进虚化**（见问题 41），白字压在压暗的图上；最底边渐隐到页面底色。
- 片名用**衬线体 + 加粗 + 拉开字距**当艺术字（`FontFamily.Serif`，系统自带，
  不用打包 CJK 字体 —— 一个中文 display 字体动辄十几 MB）。
- 元数据行、文件信息行、按钮、心形**全部居中**（用户要求）。
- 文件信息行 `时长 | 大小 | 分辨率`：时长取 `playback_progress.durationMs`
  （**播过才有，读不到就不显示**）、大小来自扫描、分辨率从文件名读
  （`MediaTypes.resolutionLabel`，有单测）。
- 左上角返回、右上角三个点都是**半透明深色圆底 + 白图标**（图上放纯文字看不见）。

#### 详情页的类型/地区 + 演职人员（2026-10-15 收尾）

用户要 VidHub 那行 `★8.0 | 1989/4/8 | 日本 | 犯罪,剧情` 加下面的「演职人员」。

- **一次 TMDB 请求全拿到**：`/movie/{id}?language=zh-CN&append_to_response=credits` 会带出
  `genres[].name`（类型名，**不用**再去查类型表）、`production_countries`、`runtime`、
  `release_date`（完整日期）、`credits.cast[]` / `credits.crew[]`。
  剧集走 `/tv/{id}`，字段名换成 `first_air_date` / `episode_run_time`。
- **国家名 TMDB 不本地化**（实测中文界面下仍返回 `United States of America`），
  所以自带一张 iso→中文名的小表 `data/Countries.kt`（认不出就退回接口给的英文名，不编）。
- **详情是「打开详情页时才拉、拉过缓存」**，存独立的 `title_details` 表：
  放整库刮削里等于白白多等一两分钟（92 条 = 92 次请求），塞进媒体条目表又太大。
- 时长改为**优先用 TMDB 的官方时长**（不用播过就有），没有才退回播放进度里记的那个。
- 演职人员：**导演排最前**（角色写「导演」），其后按 `order` 取前 20 位；
  头像是 `w185` 的圆形图，**没有头像就用名字首字占位**（不编图）。

#### 右上角菜单（用户要求「可以修改信息、标记等操作」）—— 四个都是真能用的

- **修改信息**：改片名/年份 → 存进 `title_overrides` 表（**独立表**，理由同进度/收藏）。
  改完**海报墙、详情页、重新刮削的检索词**都走这一份 —— 于是「自动匹配错了」有得救
  （实测 `银河` 会被匹配成《银河护卫队》）。“清除”按钮删掉覆盖、回到自动解析的结果。
- **标记为已看 / 未看**：已看 = 把进度直接写到片尾（进度条满格、续播不再从中间开始）；
  未看 = 删掉那条进度。
- **重新刮削**：只重刮这一条。

#### 其余

- **收藏**：新表 `favorites` + 详情页心形 + 收藏 Tab（复用 `PosterGrid`）。
- **欢迎页**：库为空时是「欢迎来到 MediaDock / 媒体库为空 / 扫描媒体库 / 添加数据源」
  （照 VidHub 那个空状态做的），不再是干巴巴一行字。
- **设置页**：改成分组卡片（数据源 / 刮削 / 媒体库 / 关于），
  两个不可逆操作仍带二次确认；**关于里放了 TMDB 署名**——
  当初申请 API key 时承诺过要在应用内显示，不得删除。
- **`ui/Formats.kt`**：`formatSize` / `formatClock` / `formatDurationWords` 统一放一处，
  浏览页和详情页共用（之前各自有一份）。

### 评估后加固与详情页重做（2026-10-15 夜间）

按分析结论实施改进，并将详情页按 VidHub「整页一体」版式重做。改动均已安装至真机（三星 S21 Ultra）验证：升级不丢失数据（92 条记录、进度、改名与详情保持不变），详情页逐屏验证，无崩溃。

#### 代码加固（对应评估里的 P1 / P2 / P3）

- **库回退只留降级方向**：`fallbackToDestructiveMigration(dropAllTables = true)` →
  `fallbackToDestructiveMigrationOnDowngrade(dropAllTables = false)`。现在「漏写升级迁移」的后果是
  **打开数据库时抛出异常**，不再静默清库。见问题 45。
- **退出登录不再连 TMDB 凭据一起删**：`SecureCredentialStore.clear()`（清整个 DataStore）→ `clearSource()`
  （只删数据源 4 个 key）。设置页确认文案原本已说明「数据源地址、账号和密码」，当前实现与文案一致。见问题 46。
- **刮削单条失败不中断整轮**：批内某条网络抖动只算它自己没匹配上，其余继续。鉴权失败仍中止整轮。见问题 47。
- **退场时最后一次进度保存不再被取消**：`PlaybackService.onDestroy` 只停 ticker，不 cancel 整个 scope。见问题 48。
- **扫描 / 刮削各用独立 job**（`scanJob` / `scrapeJob`），取消互不牵连。两者仍互斥 —— 扫描是整表替换
  `media_items`，并发会把刮削中的结果换掉；互斥现在在界面上**显式置灰按钮**，不再「点了没反应」。见问题 49。
- **收藏翻转以库内状态为准**（DAO 加 `countOf`），不再用界面那份可能还没同步的 StateFlow 快照。见问题 50。
- **新增单测** `MigrationCoverageTest`：断言 `MIGRATIONS` 逐级连续、直到 `MediaDatabase.VERSION`。
  改版本号却没补迁移 → 跑单测就红。版本号现在是单一来源常量 `MediaDatabase.VERSION`。单测 27 → **28** 个。

#### 详情页重做（整页一体，海报完整不裁剪）

参考换成 VidHub 的竖版海报版式。过程：用户先指出「简介看不全、没有展开、简介和演职人员割裂、整页不和谐」
→ 第一版改成「图上信息块 + 浅色内容区」→ 用户「不要分成 2 部分、要有有机统一」→ 铺满整页后用户又指出
「横版图放大后只能看到部分海报」。**最后定稿（用户从三个方案里选的）**：

- 顶部大图**改用海报**（`posterPath ?: backdropPath`），按 **2:3 原比例 `ContentScale.Fit` 完整显示、
  不裁剪**，占约 2/3 屏；标题 / 元数据 / 文件信息 / 继续播放 / 收藏压在它下半部。
- **下半部分用海报底部的主色延伸出去**（`ui/detail/PosterTint.kt` 取海报底部一条的平均色，
  单独下一张 `w92` 小图、几 KB）：大图下半部的压暗**渐变成这个色**（不是渐变成黑），
  图以下再用同一个色往下铺、略微渐暗 —— 海报和下面的内容是**同一个色调，看不出分界**。
  取不到色（无图片 / 没网 / 解码失败）退回一块中性深色，不影响功能。
- **文字可读性**：用户指出白字压在亮海报上「看着不清楚」→ 大图下半部那层压暗从中间开始渐深
  （颜色就是页面主色），标题 / 元数据 / 文件信息再各加一层**投影**。
- 简介（可展开）/ 分集 / 演职人员都落在海报延伸出来的这同一片底色上。
- 返回 / 更多**钉在顶部**（不随内容滚），随时够得着。
- 无图片（没匹配上 TMDB）的条目：深色竖向渐变回退，不再是「一片死黑、表现为异常」。
- 简介**可展开 / 收起**：只有真的超过 4 行才出现开关（短简介不白挂开关）；按钮文字走 `primary`
  （深色底上柠檬绿读得清）。
- 刻意**未实现**参考图中的「资源」胶囊（多资源切换）与片单/举报图标：无真实功能时不提供占位按钮。

**为什么不用横版背景图**：16:9 铺满竖屏（1440×3200）要按高度铺满，宽度被撑到 ~5700px，
只露 25% —— 正是用户说的「只能看到部分」。海报是竖版，按原比例铺宽度正好是一块高画面。见问题 51。

### 播放页自绘控件 （2026-10-15 深夜做完，真机验证）

**用户要的**：不要播放器原生控件，改成自绘的 —— 顶部 返回 / 标题 / 字幕 / 倍速 / 锁定，
中间大播放键，底部**细进度条** + 时间；外加**锁定按钮**、**左侧上下滑调亮度**、
**右侧上下滑调音量**。（在线字幕搜索是另一批，要接第三方字幕站。）

**上一轮卡在哪**（记下来不得再犯）：自绘层里的按钮和进度条**收不到触摸**，加 Log 证实只有最外层
那个全屏「点击切换」区能收到事件。当次没解决，回退了。

**这次一次就通了 —— 关键在触摸层的结构：**

> **把「进行验证显示/隐藏」和左右半屏上下滑挂在铺满的父 `Box` 的 `pointerInput` 上，
> 按钮 / 进度条作为它的子节点。** Compose 里**子节点先于父节点拿到事件**，
> 所以点按钮、拖进度条不会被父抢走，只有点空白处才落到父上切显隐。
> 上一轮把全屏「点击切换」层和按钮做成**兄弟**、且点击层压在按钮上 —— 全被它截胡了。
> **不得修改回兄弟结构。**

真机日志（TAG=MediaDock）逐条验过：
```
center play/pause tapped / seekbar tap|drag -> N / speed tapped / subtitle tapped
overlay tap -> controls hide|show      （点空白才走父层）
controls auto-hide                      （播放中 4 秒不操作自动隐藏）
brightness -> 0.51…  volume -> 7        （左半屏上下滑 / 右半屏上下滑）
lock tapped / unlock tapped
```

**实现要点**（都在 `ui/player/PlayerScreen.kt`）：

- **布局照主流播放器**：顶部 `返回 / 标题 / 右上角「⋯」`；中间大播放键；底部**细进度条** + 左时间 / 右时长 +
  **右下角倍速**。**锁定按钮固定在左侧中间**，锁上后 3 秒自动隐藏、进行验证唤回（位置不变）。
- 「⋯」菜单里装：**字幕**（列出现有字幕轨 + 关闭；文件本来没字幕轨就不显示这一项）、
  **详细信息**（名称 / 路径 / 时长 / 画面分辨率·帧率·编码·码率）、**截图**。
- `PlayerView.useController = false`，画面之外全自绘。
- **细进度条自己画**（不用 M3 `Slider`，它轨道太粗）：3dp 轨道 + 已播段，进行验证跳转、
  按住左右拖调位置，两种手势都由它自己消费（按下事件也 consume 掉，免得被父层当点击）。
- 亮度写 `Activity.window.attributes.screenBrightness`（只影响本 Activity，退出播放页自动回系统默认）；
  音量走 `AudioManager` 的 `STREAM_MUSIC`；都是**按累计位移**算，拖动时中间出「亮度 62% / 音量 40%」提示。
- **时间要对秒**：控制器上报的位置是隔一阵才来一次的，用本地时钟补差（若控制器本身在连续外推则不补，
  不会重复计时）；时长还没读出来时显示 `--:--`，不得显示 `00:00`。
- **截图**：`PixelCopy` 从画面那层 `SurfaceView` 取帧 → 存进相册 `Pictures/MediaDock`
  （API 29+ 走 MediaStore，不需要存储权限）。
- 锁定时点空白/上下滑都无效，防误触。

### 在线字幕搜索 + 本地字幕（2026-10-15 深夜）

用户点名的功能。**字幕源做成了可切换的**（用户选的方案：先搭框架，先接 OpenSubtitles）。

- `data/subtitle/SubtitleProvider.kt` —— 接口 + `SubtitleHit` / `SubtitleFile` + 三类异常
  （`SubtitleException` / `SubtitleAuthException` 凭据问题 / `SubtitleQuotaException` 额度问题）+
  语言标记转中文名。**以后加 Assrt 之类的源，只要再写一个实现塞进 `SubtitleRepository.providers`，界面不用动。**
- `data/subtitle/OpenSubtitlesProvider.kt` —— OpenSubtitles.com 的 REST 实现：
  搜索只要 **Api-Key**，下载要**账号密码**换 Bearer；401 当凭据问题（只重新登录一次再试，不做无脑重试）、
  406/429 当额度问题（不重试）；下载直链若是 gz 会解压。
- `data/repo/SubtitleRepository.kt` —— 搜索 / 下载 / **统一落盘**到 `filesDir/subtitles/`。
  落盘是为了：SAF 选来的 `content://` 授权活不长，挂载一律走本地文件。
- 凭据：`SecureCredentialStore` 里 Api-Key 和密码加密存（和 TMDB 那套一样），设置页新增**「字幕源」**分组。
- 播放页「⋯ → 字幕」变成一个面板：**关闭字幕 / 已有字幕轨 / 从本地文件加载… / 在线搜索…**。

**挂载方式**：给当前 `MediaItem` 加一条 `SubtitleConfiguration`（本地 `file://`）后用
`setMediaItem(新条目, 当前进度)` 重设 —— 进度不丢，只重开一下解码。App 侧字幕没有语言标记，
靠播放服务里已开的 `setSelectUndeterminedTextLanguage(true)` 自动选上。

**已验证 / 未验证（如实记）**：

- 播放页字幕面板三项都在；在线搜索对话框能开，没填凭据时给引导文案；设置页「字幕源」分组在。
- 数据源包了 `DefaultDataSource` 之后 http 播放照常（播放到片尾 08:40，没被改坏）。
-  **「从本地文件加载 → 挂载上屏」这条未完成自动化验证**：SAF 选择器的标题栏（抽屉 / 搜索）吃不下
  adb 注入的点击，而且 `adb push` 进去的文件不进 MediaStore 的「最近」，选择器里根本不列它。
  **这条得人在手机上进行验证**（已经把测试字幕推到 `Download/mediadock-test.srt`）。
-  **OpenSubtitles 的真实搜索与下载尚未验证通过**：本机无可用 Api-Key。DTO 是照公开文档的形状写的、
  解析用 `ignoreUnknownKeys` 宽容处理；站点哪天改形状就改 DTO。

### 首页：搜索栏 + 随机推荐 + 补齐影片信息（2026-10-15 夜间，按产品要求暂停时的状态）

用户要「首页搜索栏、推荐栏」。对齐后定的范围：**搜索栏 + 一条「随机推荐」横向行**（下面是原来的海报墙），
搜索范围 = **片名 / 类型 / 国家 / 演职人员**。

- `ui/library/LibrarySearch.kt` —— 搜索过滤的**纯函数**（多关键词按空格切开、**全中**才算命中），
  8 条单测（`LibrarySearchTest`）。匹配 **片名（含手工改名）/ 类型 / 国家 / 演职人员**。
- 首页（媒体库 Tab）从上到下：状态栏（⋯ 菜单）→ **搜索栏** → 提示行 → **随机推荐**（搜索时隐藏）→ 海报墙。
  随机推荐是横向小海报 + 「换一批」；**每次进首页换一批**（`LaunchedEffect(Unit) { vm.reshuffle() }`），
  也能点「换一批」手动换。
- `PosterGrid.kt` 把海报块抽成 `PosterCard`，网格和横滑行共用（只是尺寸不同）。
- **`DetailsRepository.fillMissing()`**：新增「**补齐影片信息**」批量动作（首页 ⋯ 菜单里），
  把已刮削条目缺的详情一次拉齐（4 并发、可取消、单条失败跳过、凭据错整轮停）。
  **为什么非做不可**：类型/国家/演员只存在 `title_details` 里，而那张表是「打开详情页才拉」的懒加载 ——
  不补齐的话，按类型/演员搜几乎搜不到东西（见问题 57）。顺便详情页也能秒开。

**验证状态（暂停时的实况）**：

- 首页布局（搜索栏 / 随机推荐 / 换一批 / 海报墙）真机看过。
- 搜索过滤真机验过：输 `Tit` → 只剩 Titanic 与 Surogat；**搜索时推荐行正确隐藏**；清除后推荐行回来。
- ⋯ 菜单里「扫描 / 刮削 / 补齐影片信息」三项都在（点过、dump 过 bounds）。
- 搜索规则 8 条单元测试全部通过（单元测试总数 28 → **36**）。
-  **尚未验证**：点「补齐影片信息」之后的实际拉取（暂停时正好停在菜单那一步）、
  以及补齐后按类型/国家/演员搜索的实际效果（`input text` 打不了中文，中文搜索得人在手机上试）。

### 分享给朋友：配置自动识别 + 自动填入（2026-10-16）

需求：分享配置时手动输入地址与账号容易出错，需要支持自动识别并填入。

- `data/webdav/ShareConfig.kt` —— **生成 + 解析**一段人能看懂、程序也能认的文本：
  ```
  MediaDock 数据源
  地址：https://files.example.com/dav
  账号：demo
  密码：xxxx-xxxx
  ```
  解析很宽容：标签认中英写法（地址/服务器/链接/url、账号/用户名/user、密码/pass/pwd…）、认全角冒号、
  认 `https://user:pass@host/dav` 这种把账号密码塞进 URL 的写法（含百分号编码）。
  **必须有地址 + 密码才算配置** —— 任意链接不得被当作配置自动填入；该行为由 10 条单元测试覆盖（`ShareConfigTest`）。
- 设置页「数据源」多了 **「分享给朋友」**：把已保存的数据源拼成上面那段文本放进剪贴板，并提示「已复制」。
- 添加数据源页：**进页面自动读剪贴板**，认出来就把三个字段直接填好 + 提示「已填入分享配置 · <地址>」；
  旁边另有「粘贴分享配置」按钮（自动识别没赶上时可以再进行验证，认不出来会说一声）。
  剪贴板用**平台 API** 读（Compose 的 `LocalClipboardManager` 已废弃）；Android 10+ 只允许**前台** App 读 —— 本页在前台，没问题。

**真机验证（一条龙，2026-10-16）**：设置里点「分享给朋友」→ 提示「已复制 · 朋友在『添加数据源』页会自动填入」
→ 退出登录 → 添加数据源页**自动填好了 地址 / 账号 / 密码**（密码 19 位与原密码一致）
→ 点「测试连接」→ 「已连接，1 个条目」

### 首页改版：吸顶搜索栏 + 分类行；设置加「关于 / 我的网站 / 检查更新」（2026-10-16）

用户要求：随机推荐改 5 个、加分类行（标题靠左）、搜索框照他站点「星屿」的样子并吸顶、
设置里放他的网站、加在线版本更新检查。

**首页**（`ui/library/`）：

- **吸顶栏**（滚动时只有它留在顶部）：**全圆角药丸搜索框**（照站点 `css/style.css` 的 `.search`：
  左侧放大镜、有内容时右侧一个圆形 ×、**聚焦时描边变 primary**）+ 右侧圆形 **⋯**（扫描 / 刮削 / 补齐收在这里）。
- 原来那行状态文字从常驻区挪走；任务运行时改成栏下**一条 2dp 细线 + 一行小字**，空闲时不占位。
- 滚动内容 = 一个 `LazyVerticalGrid`，整行的东西用 `item(span = { GridItemSpan(maxLineSpan) })` 跨满：
  **随机推荐（5 条，带「换一批」）** → **分类行（≤6 行，标题靠左 + 真实条数）** → **「全部」** + 海报墙。
- **分类是数据驱动的**（`ui/library/GenreRows.kt` + `GenreRowsTest`）：从已缓存详情的 `genres` 统计，
  按预设优先级（动画 / 喜剧 / 家庭 / 剧情 / 短片 / 冒险 / 音乐 / 纪录片 / 历史 / 战争 / 动作 / 科幻 / 悬疑 / 爱情）取，
  只保留 **≥3 条**的类型、最多 **6 行**、每行最多 15 张。**详情没补齐就没有分类行**（问题 57 那条因果）。
- 搜索时只留过滤后的网格（分类行与随机推荐收起）。
- 注意：用户的屏幕密度被调过（`wm density` 覆盖成 680）→ 屏宽只有约 339dp，**一行只放得下 3 张**。
  横滑行本来就能滚，不得按「一屏 5 张」设计卡片宽度。

**2026-10-16 追加（用户要求：推荐卡片一次一张、更大、带推荐理由；分类与影片间距加大）**：

- 随机推荐改成 **`FeaturedRow`**：**一次只显示一张大卡片**（`HorizontalPager` 横滑翻页 + 下面页码点），
  卡片 **16:9**、标题 / 元信息（★ · 年份 · N 集）/**推荐理由**叠在压暗的图上，右上角仍是「换一批」。
- **推荐理由只写真数据推出来的**（`ui/library/RecommendReason.kt` + `RecommendReasonTest` 9 条）：
  按优先级：已收藏 → 看到 62% · 继续观看 → ★8.4 高分 → 已收藏的《X》也是动画 → ★7.6 口碑良好 → 展示类型。
  **推不出来就不画那行**，不生成缺乏依据的推荐理由。
  判档位用的是**四舍五入到一位小数之后**的分 —— 否则会出现「★8.0 口碑不错」这种自相矛盾（单测锁了）。
- **间距**：行内「小节标题 ↔ 海报」8dp、每行底部再留 12dp、网格 14dp —— 分类与影片之间松一些。

**设置**（`ui/settings/`）：「关于」扩成 版本 + **检查更新** + **星屿 · 我的网站** + TMDB 署名（保留）。
启动静默查一次更新（**失败不吭声**），命中新版本只在「关于」标出来；手动点那次会把「检查中 / 连不上 /
还没发布过更新信息」都说清楚 —— **绝不把查不到说成「已是最新」**。

**发版信息怎么发**（`data/update/UpdateChecker.kt` 的 `UPDATE_URL`）：把下面这份 JSON 放到站点根
`/var/www/portal/mediadock/version.json` 就生效（App 直接 GET，无需鉴权）：

```json
{ "versionCode": 2, "versionName": "0.2.0",
  "url": "https://lovelev.eu.cc/mediadock/MediaDock-0.2.0.apk",
  "notes": "本次更新：…" }
```

`versionCode` 比本机大才算有新版本（本机号在 `app/build.gradle.kts`）。**没放文件时**点「检查更新」
会明确显示「还没发布过更新信息」。

**真机验证（2026-10-16）**：首页截图确认 吸顶药丸搜索框 / 滚动后只有搜索栏在顶 / 随机推荐 5 条 + 换一批 /
分类行标题靠左且条数是真数（动画 71、喜剧 40、家庭 18、剧情 20）；设置页「关于」三项都在、
「检查更新」显示「还没发布过更新信息」；搜索回归验过（搜 `Soviet` 仍精准命中《伊凡雷帝》）。单测 46 → **60**。

### 发布 0.2.0 + 上线更新信息（2026-10-16）

第一个真正对外发布的版本。站点上放好了发版信息，**「检查更新」整条链路真机验通**。

**部署**

- 版本 bump：`versionCode 1 → 2`、`versionName 0.1.0 → 0.2.0`（`app/build.gradle.kts`）。
  `0.1.0` 从未对外发布过，所以 `0.2.0` 是首个发布版。
- 站点目录 `/var/www/portal/mediadock/`（新建，`www-data:www-data` / 755）里放了：
  `MediaDock-0.2.0.apk`（26 531 045 字节，debug 构建）+ `version.json`。
- 传法：`scp` 到 `/tmp` → `sudo mv` 就位 → `chown www-data:www-data`
  （**`bg` 对 `/var/www` 没有写权限**，必须走 sudo 中转）。

**验证（全链路，逐环节留证，截图在 `app/build/verify/`）**

| 环节 | 证据 |
|---|---|
| 站点可取 | `version.json` HTTP 200 / 269 B；APK HTTP 200 / `Content-Length: 26531045` |
| 传输完整 | 本地构建 / 站点下载 / **手机下载** 三者 sha256 全部 = `68aad847…fdd6ab` |
| 发现新版本 | 手机 0.1.0 + 站点 0.2.0 → 设置页「发现新版本 0.2.0」+ notes + 「下载新版本」（`01-update-available.png`）|
| 下载 | 点按钮拉起三星浏览器 → 下载确认 → 落到 `/sdcard/Download/MediaDock-0.2.0.apk`（`02-download.png`）|
| 安装 | 装成 0.2.0 |
| 回到最新 | 0.2.0 下「检查更新」→「已是最新版本」（`04-up-to-date.png`）|
| 升级不丢数据 | 影片数仍 **106** |

**遇到的问题**

-  **`adb shell pm install /sdcard/...` 会报 `Can't open file`** —— Android 15 分区存储下
  `/sdcard/Download` 里那个文件属主是**浏览器 uid**、mode 700，shell 读不到。
  **不是包的问题，不得据此判断「下载坏了」。** 要装就 `adb install -r <本地文件>`
  （先用 sha256 证明两边字节一致，再装）。
- **nginx 给 `.apk` 的 MIME 是 `application/octet-stream`**（标准 `mime.types` 里没有 apk），
  于是三星浏览器会弹「此类文件可能危害您的手机」、文件名预览成 `MediaDock-0.2.0.apk.bin`。
  **实际落盘仍是 `.apk`、大小正确、能装**，所以不影响功能。想去掉这个吓人的提示，
  给 `mediadock` 那个 location 加一条 `types { application/vnd.android.package-archive apk; }` 即可
  （**未实现**，属于体验优化项）。

**以后发新版**：改 `app/build.gradle.kts` 的 `versionCode`/`versionName` → 构建 →
传进 `/var/www/portal/mediadock/` → 同步改 `version.json` 的 `versionCode` 与 `url`。
 **`versionCode` 只增不减**，否则已升上去的用户收不到后续更新。

### 换成正式签名（2026-10-16）

**为什么**：0.2.0 是 `assembleDebug` 出来的 debug 签名包 —— `debuggable=true`（关掉一部分 ART 优化、
任何人插上 adb 就能读应用数据）、未裁剪。而**换签名是不可逆的**：已装用户必须卸载重装，所以越早换成本越低。

**做法**

- keystore 生成在**仓库外**：`E:\code\keystore\mediadock-release.jks`（RSA 4096 / 30 年 / `CN=MediaDock`）。
  同目录 `README.txt` 写了备份说明**和密码**（这样整体备份一个目录就够）。
- `local.properties`（**已 gitignore**）里 4 行：`mediadock.storeFile / storePassword / keyAlias / keyPassword`。
  `app/build.gradle.kts` 读它建 `signingConfigs.release`；**没配 keystore 的机器照样能
  `assembleDebug` / 跑单测**，只是 `assembleRelease` 出未签名包。
-  **keystore 和密码必须备份** —— 丢了以后所有更新都无法覆盖安装，没有补救办法。
  **仓库里只有路径，没有密码。**
- **minify 仍关着**：R8 会裁掉 Room / kotlinx-serialization 依赖的反射，要开必须同时补 keep 规则
  并执行一轮真机验证，属于独立的一步。即使不开，release 也比 debug 小 30%（26.5 MB → **18.6 MB**）。

**debug → release 的迁移流程（已在真机验证通过，可复用）**

签名不同不能覆盖安装，必须卸载重装：

1. `run-as` 将 `databases/library.db` 与 `-wal`、`-shm` 导出（debug 包可读）
2. 本地 `PRAGMA wal_checkpoint(TRUNCATE)` 合并成单文件、`integrity_check` 确认没坏
3. 卸载 → 装**临时把 `isDebuggable` 打开**的 release 包 → `run-as mkdir -p databases` + `cp` 还原库
4. **去掉 `isDebuggable`** 重新构建 → 覆盖安装（同签名 = 普通更新，数据保留）
5. 复验：`run-as` 报 `package not debuggable` = 开关确实关上了

> 第 3 步那个「临时可调试」只是为了还能用 `run-as` 写文件。正式包关掉 debuggable 后 `run-as` 不可用 —— **符合预期**。

** 这次迁移的代价（重要，不得重复）**

**DataStore 里的凭据全丢了。** 因为 Android 在**卸载时销毁该应用在系统 Keystore 里的密钥**，
而 WebDAV 密码 / TMDB 凭据都是用它 AES-GCM 加密存的 ——
**所以哪怕提前备份了 DataStore 文件，装回去也解不开**（问题 60）。

- 媒体库（106 影片 / 进度 / 手动改名 / 详情）**靠上面的流程完整保住了** —— 它在 Room 里，不受 Keystore 牵连。
- **救不回来的只有走 Keystore 的那类**：WebDAV 地址/账号/密码、TMDB 密钥，必须重填。
- **下次要迁移，先让用户在 App 里点「分享给朋友」把配置复制到剪贴板**
  （那条路径输出的是**明文**地址/账号/密码），备份下来再卸载 —— 这样才免重填。

**验证**：0.3.0 装机后 `run-as` 报 `package not debuggable`、签名 `CN=MediaDock`、
库在设备上直接读出来是 106/2/2/96、站点 APK sha256 与本地一致。

### 0.3.1 · 播放页控件加压暗层（2026-10-16）

**问题 62：播放页的控件是白字，压在亮画面上会直接消失。**
详情页当初撞到同一个问题时，解法是「压在亮处的那几行字各加一层投影」。播放页当时没处理，
因为常看的片子画面偏暗，没暴露。这次实测放到《秒速五厘米》**白底字幕卡**那一帧时，
**返回箭头和标题整条不见了**（同一个画面上锁形图标、进度条都还在，因为它们本来就在暗处）。

→ 做法：在控件层的最底下、顶栏与底栏各自的带状区域加一条**竖直渐变**：
顶 `Black .62 → 透明`（高 96dp）、底 `透明 → Black .70`（高 124dp）。
**只压控件所在的那两条带**，画面中间一点没动。见 `ui/player/PlayerScreen.kt` 的 `PlayerControls`。

> 另外：这类「白字压亮画面」在播放器里几乎必然遇到（字幕卡、雪景、纯白转场）。
> 加压暗层比给每个控件加投影省事，也不会让字看着发虚。

**发版**：`versionCode 3 → 4`、`versionName 0.3.0 → 0.3.1`，站点 APK 与 `version.json` 已同步，
发版页（`E:\code\portal\mediadock\`）上三处硬编码版本号与「版本历史」也已更新 ——
**这就是那个「下次发版不得遗漏」清单首次执行，未发现遗漏。**

### 0.3.2 · 取消不再误报 + 分享带上 TMDB（2026-10-16）

**问题 63：`catch (e: Exception)` 会把 `CancellationException` 一起吃下去。**
只要它在协程里，`cancelScan` / `cancelScrape` / 取消字幕搜索就**不会干净地取消**，
而是被转成一条「网络错误 / 搜索失败」的业务失败。全项目扫出 18 处，其中 **16 处在协程路径上**，
都补了 `catch (e: CancellationException) { throw e }` 再落到原来的 `catch (e: Exception)`。
另 2 处（`PixelCopy` 截图、存相册）不在协程里，保持原样。
> 判据：**这个 try 里有没有挂起调用**。有才需要这层，无谓地加只会让代码变吵。

**分享配置带上 TMDB**：生成时多一行 `TMDB 密钥：xxx`，其他设备在「添加数据源」自动识别时一并保存至设置。
 **只在 `parse()` 认得这是分享配置时才取 TMDB** —— 否则剪贴板里任意包含「tmdb」字样的文本
就会把用户的密钥覆盖掉。单测锁了这条，另加一条「多出来的行不能干扰地址/账号/密码的解析」的回归。
（69 → 73 条）

**同时修的**：`gradle.properties` 里写死的 `org.gradle.java.home` 去掉了 —— 那是机器相关路径，
留着 Linux CI 直接构建不了。本机的指向挪到用户级 `$GRADLE_USER_HOME/gradle.properties`。

### 0.3.3 · 迷你播放条（2026-10-16）

**问题 64：外壳加常驻控件，不能复用播放页那个 MediaController。**
播放页的 `PlayerViewModel.onCleared()` 就是「离开播放页就暂停」的触发点（用户明确要的行为，见问题 37）。
把那个控制器挪到外壳复用会让它失去触发点。→ 新建 `NowPlayingViewModel`（**外壳级，活动作用域**）
自己连一个 MediaController；两个控制器连的是同一个 MediaSession，谁按暂停都真的暂停。
`onCleared()` 里**只放开控制器、不暂停** —— 那是界面没了（退到后台继续放才是要的功能）。

**同时修掉一个老限制**：`PlaybackSelection` 只活在内存里，进程被杀后再进播放页会看到
「没有选中要播放的文件」（原注释写着「可接受」）。现在点迷你条会先
`syncSelection()` 从控制器把队列读回来，这条路通了。

**验证**（`uiautomator dump`，非截图）：浏览页播一个文件 → 返回 → 底部出现
`标题 + 已暂停 + 播放/停止` 三个节点；**「已暂停」同时证明问题 37 那条行为没被破坏**。
另：首次返回没生效（当时正在旋回竖屏），再按一次才退出来 —— 属操作时序，不是缺陷。

### 0.5.0 续 2 · 队列：单片也带上同目录的视频（2026-10-17，真机实测）

用户试了上一集/下一集，说「是灰色的，点了没效果」。

**灰是设计使然** —— 详情页原来的队列是 `entries.map { it.path }`：
剧集（≥2 集）有多条，单片只有 1 条，`hasNextMediaItem()` 自然是 false。

**但对该媒体库等同于未实现**：库中**均为独立短片或电影，无剧集**，
那两个按钮会永远灰着。→ `DetailViewModel.playQueue`：
剧集仍用这几集；**单片改成取「同目录的视频」**（和从浏览页点进来时的行为一致）。
拿不到同目录的就退回单条。

**另外修的一个真 缺陷**（真机上验出来的）：播放历史的标题对媒体库条目显示成**纯数字 id**
（`1000043438`）。因为那些条目查不到媒体库（历史比库老），回退用了 `path.substringAfterLast('/')`，
而 `content://media/.../12345` 的最后一段就是数字 id。
→ 现在一次性从 `ContentResolver` 查 `OpenableColumns.DISPLAY_NAME`（**不在每行 composition 里查**），
标题变成 `20240802_110352.mp4` 这种真文件名。

**真机验证（2026-10-17，手机接上后补验的）**：
- 底部三个 Tab（媒体库 / 收藏 / 我的）与右侧圆形搜索按钮，已完成
- 媒体库页顶上的搜索框已消失，进来直接是随机推荐
- 「我的」页三组内容完整，已完成；播放历史包含真实标题与进度（`棕兔夫人 00:08/07:06`、`电影 05:41/1:02:45`）
- 搜索页可进入、可输入
- 主题三态：切到「深色」后**采样画面主色 rgb(38,39,43)** = 深色主题的 surfaceVariant
- 播放页有「上一集 / 暂停 / 下一集」；点下一集后标题从
  「简日双字.国粤日三音轨」变成「简日双语特效.酷漫404」—— **确认已切换片源**

### 0.5.0 续 · 搜索搬到下面（2026-10-17）

用户：「将搜索也放在下面，还是参考 nagomusic」。照它的底栏：**左边三个 Tab，右边一颗圆形搜索按钮**，
搜索不再挂在首页顶上，而是点按钮进**独立的搜索页**。

- 媒体库页的**吸顶搜索框整个删掉**（连带那个 `StickyBar` 调用），只在任务运行时留一条状态条。
- 新增 `Routes.SEARCH` + `ui/search/SearchScreen.kt`：顶部输入框 + 下面直接是海报墙。
  **过滤规则一个字没重写** —— 复用 `LibraryViewModel.visible`（背后是 `LibrarySearch` 那个有单测的纯函数）。
- 搜索页用的是**新的 ViewModel 实例**，`query` 是它自己的 —— 离开再回来是空的，不会和媒体库页串。
- 底栏用 `Row { NavigationBar(weight 1f, windowInsets = WindowInsets(0)); FilledIconButton }`：
  **`windowInsets` 必须清零**，否则导航栏自己吃一次底部安全区、按钮没有，两者会一高一低。
- 进搜索页自动弹键盘（`FocusRequester`），不然还要再进行验证输入框。

 编写脚本补充 import 时遇到一个问题：`'import ...layout.Row' in s` 被 **`RowScope`** 命中了（子串匹配），
`Row` 的 import 没加上。**判 import 要按整行比，不得使用子串。**

>  2026-10-17 这一批**未完成真机安装**：`adb devices` 无设备（设备未连接）。
> 编译 + 82 条单测全部通过，但**界面尚未在设备上验证** —— 待设备连接后补充验证。

### 0.5.0 · 主题三态 + 「我的」页 + 上一集下一集 + 播放历史（2026-10-17）

用户给了 NagoMusic 的三张截图，要求「全部参考 nagomusic」，四条一起做：

**1. 主题三态（跟随系统 / 浅色 / 深色）** —— 照它的 `theme_mode_selector.dart`：
三个**并排**的选项（手机 / 太阳 / 月亮图标），不是循环切换的单个按钮。
放在 **设置 → 外观**（它的位置也是 设置→外观）。
- `ThemeMode` 枚举 + `SecureCredentialStore.themeMode` Flow（**不是机密**，放这里只为省一个
  DataStore；`clearSource()` 不碰它 —— 退出登录不该重置主题）。
- 图标是**手画的三个矢量** `res/drawable/ic_theme_*.xml`：Material 核心图标里没有太阳月亮，
  而为三个图标引 `material-icons-extended` 会把包撑大二十多 MB，不值。
-  **再次出现第 6 项问题**：`fun setThemeMode(...)` 和 `var themeMode` 自动生成的 setter 撞名 →
  改名 `pickThemeMode`。**凡是 `var xxx` 就不得再写 `fun setXxx`。**

**2.「我的」页整合更多菜单、浏览与设置** —— 参考其 `profile_page.dart`：
右上角齿轮 + 三组（资源库 / 媒体库 / 应用）的「图标 + 标题 + 副标题 + ›」行。
- 底部 Tab 从四个变**三个**：媒体库 / 收藏 / 我的（浏览与设置移入「我的」）。
- 媒体库页右上角更多菜单已移除，扫描、刮削与补齐移至「我的 · 媒体库」组，
  并且**副标题直接显示实时状态**（已扫 N 个 / 刮削 x/y / 补齐失败几条）。
- 动作复用 `LibraryViewModel`：仓库是应用作用域的，两个页面拿到的是同一份状态。
- 抽了 `NavHostController.switchTab(route)` —— Tab 栏与「我的」中的「收藏」均使用该方法，
  两处各写一份的话回退栈会越堆越深。

**3. 播放页上一集 / 下一集** —— 中间变成「上一集 / 大播放键 / 下一集」。
队列本来就是「同目录的视频」，所以 S01E01、EP01 这类同名分集天然覆盖。
没有上/下一集时按钮**置灰**（`hasPreviousMediaItem()` / `hasNextMediaItem()`），避免点击无响应。
图标同样手画（`ic_skip_previous.xml` / `ic_skip_next.xml`）。

**4. 播放历史** —— 按 `playback_progress.updatedAt` 倒序。**不用新建表**：
进度表本来就有这个时间戳，是播放进度的副产物。
名字和海报从媒体库那份查；查不到（从浏览页直接播、没进过库的）退回文件名 —— 照样能点开接着看。
点一条会把**同目录的视频**一起当队列，和从浏览页点进来的行为一致（自动下一集）。

**单测**：82 条（这几项都是界面/查询，纯逻辑没新增；`MediaDao` 的新查询由编译期校验）。

### 0.4.1 · 本地媒体两个问题（2026-10-16，真机实测反馈）

用户试了 0.3.6 的本地媒体，反馈「要扫描很久，还得自己找文件夹」。两条都是真问题，成因不同：

**问题 68：`DocumentFile` 的每个属性访问都是一次 ContentResolver 查询。**
`LocalFile` 最初的实现用 `DocumentFile.listFiles()` 遍历，然后逐个读
`child.isDirectory` / `.name` / `.length()` / `.lastModified()` —— **每个都是一次独立查询**。
一个目录 100 个文件 ≈ 400 次 IPC。
→ 改成 `DocumentsContract.buildChildDocumentsUriUsingTree` + **一次 query 把要的列全取回来**，
每个目录只发一次请求。相对路径就是「子项 document id 去掉树根 id 前缀」，与
`LocalMedia.childDocumentId` 互为逆运算。**不得再改回 DocumentFile。**

**「还得自己找文件夹」→ 加「扫描系统媒体库」（MediaStore）作为推荐入口。**
一次权限弹窗（13+ 的 `READ_MEDIA_VIDEO` / 12- 的 `READ_EXTERNAL_STORAGE`）就够，不用选目录，
整库一次查询就回来。**实测 255 个视频 / 27ms。**
- 媒体库的条目存的就是它自己的 `content://media/...` —— **跨重启稳定**，直接当播放地址用，
  不用像 SAF 那样存「树 + 相对路径」再现拼。所以 `LocalMedia` 现在认两种本地前缀。
- 两条路径**互斥**（执行其中一条会清除另一条写入的本地记录），免得同一个文件被两种方式各扫进来一份。
- 代价：只看得见系统媒体扫描器已索引的。少数情况会漏（`nomedia` 目录等），那时用「指定文件夹」回退。

**补充核查结论**：此前选择的 `视频/` 文件夹中**仅有 1 个视频**；曾怀疑新扫描器漏文件，经 MediaStore 反查确认结果正确。其视频实际分散在 `Pictures/WeiXin/` 156 个、
`DCIM/Camera/` 69 个、`DCIM/Screen recordings/` 16 个……**根本不在一个文件夹里** ——
这表明「选择文件夹」方式不适用于该媒体库，媒体库才是正确入口。

**问题 69（本次变更引入并在后续验证中发现）：扫描完成后提示未更新。**
`scanMediaStore()` 里写了 `localHint = "正在读系统媒体库…"` 未再更新，扫描完成后该提示仍持续显示，
**表现为卡死**（因此等待 45 秒后才核对实际结果）。
→ 改成**订阅 `library.scan` 状态**，提示跟着 Running / Done / Failed 走。
**结论：进行中的状态文案必须可终止，不得永久保持。**

**另一处加固**：某个目录 query 抛异常时，原实现仅通过 `getOrNull()` 静默跳过 —— 全扫不到时会显示成
「这个文件夹是空的」，将「无权限」误报为「无文件」。现在记下「有目录读不动」，
全部未扫描到时报告读取失败，而不是空目录。

**验证（真机）**：媒体库扫描 255 个 / **27ms**；影片数 **106 → 361**（WebDAV 那 106 条完好，
证明「按前缀删」的设计经住了）；SAF 那条对一个已知只有 1 个视频的目录报 1 个（与 MediaStore 反查一致）。
单测 79 → **82 条**。

### 0.4.0 · 换新图标（2026-10-16）

图标是用户在 `E:\code\icons\` 里自己做的（方案 G「折面播放」：青柠圆角播放三角 + 一道
斜切口翻成青绿），导出包在 `E:\code\icons\export\`，连 README 和生成脚本一起给了。

按它的 README 走**矢量方案**（比 PNG 那套省事、各密度都清晰）：

    cp android-vector/ic_launcher_{background,foreground,monochrome}.xml  res/drawable/
    cp android-vector/ic_launcher.xml                                    res/mipmap-anydpi-v26/

- 另外启用了 **Android 13+ 主题图标**（新的 `ic_launcher.xml` 带 `<monochrome>` 那行）。
- manifest 里只引了 `@mipmap/ic_launcher`，**没有 `roundIcon`**，所以不用管圆形那套。
- 原来 drawable 里是**金色占位图标**（`#E8B44A`），换成青柠折面。
- 前景用的是 `<group scaleX/Y="0.78">` + 渐变（`aapt:attr`）。0.78 是导出包算好的：
  安全圆直径 66/108 = 61.1%，标记最远点 77%，0.611/0.77 ≈ 0.78 —— **不得随意修改**，
  改大了圆形遮罩会啃掉三角左上/左下的尖角。minSdk 26 ≥ 24，矢量渐变可用。

**验证**：`aapt2 dump resources` 确认 APK 里有 `drawable/ic_launcher_{background,foreground,monochrome}`
与 `mipmap/ic_launcher`，且 `$ic_launcher_foreground__0/__1` 说明渐变成功编进去了
（release 构建会把资源名缩短，所以按文件名 `unzip` 是查不到的，得用 aapt2）。

### 0.3.7 · 日志落盘 + App 内日志页（2026-10-16）

之前出问题只能靠 logcat —— 对朋友来说等于没有（要么截图截不全，要么连电脑）。
现在 `AppLog` 把日志写进 `filesDir/logs/app.log`，设置 → 关于 → **运行日志**里能直接看、复制、清空。

- **两个纪律写在 `AppLog` 的注释里，加日志时守住**：
  ① **绝不写凭据、密码、文件内容** —— 这个文件会留在用户手机里、也可能被复制或转发；
  ② 单文件 512KB 上限，超了滚动一份 `app.1.log`（不做多份，那点历史不如省空间）。
- **未捕获异常也记一份**（`Thread.setDefaultUncaughtExceptionHandler`），
  记完照旧交给系统默认处理器 —— 该崩还是崩，不掩盖。**这是这个功能最值钱的部分**：
  朋友那边闪退，日志里直接有堆栈。
- `PlayerScreen` 里 29 处 `Log.d(TAG, …)` 全部迁到 `AppLog.d("player", …)`
  （顺便给了子标签，比原来所有行都挂同一个 TAG 好读）；播放出错、扫描失败各自补了 `AppLog.e`
  （带原始异常 —— 给用户看人话，落盘的是能查的堆栈）。

**验证（真机）**：装 0.3.7 后设置页显示「运行日志 · 0 B」→ 播一个文件 → 回到日志页
**变成 215 B**，内容为 `D/player tracks attached: 0 groups` / `player: play() called on 2 items`。
写盘、读取、体积显示、子标签格式全部确认。

### 0.3.6 · 本地媒体扫描（2026-10-16）

原来只有 WebDAV 一个来源。加了「本地文件夹」：设置 → 数据源 → 添加本地文件夹（SAF 选目录）→
扫进**同一张 `media_items` 表**。因为走同一个 `TitleParser`，**本地文件进来就有海报墙和刮削**。

**关键设计（三条，改之前先看）**

1. **库里存「相对目录树根的路径」，不存 `content://` URI。** SAF 的 document URI 依赖那次授权
   拿到的树，存下来不可靠；播放时用「树 URI + 相对路径」现拼
   （`DocumentsContract.buildDocumentUriUsingTree(tree, treeDocumentId + "/" + 相对路径)`）。
   树 URI 本身是稳定的（只要 `takePersistableUriPermission` 拿到了长期权限）。
2. **前缀区分来源，不新增数据库列。** 本地条目在 `path` 前加 `local/`（[LocalMedia]）。
   刻意不加列 —— 那要写迁移，而迁移是这个项目最容易出事的地方（问题 39/45）；
   纯字符串约定写错也只是扫描结果不对，碰不到用户数据。
3. ** 扫描必须「按前缀删」，不能再整表删。** 原来是 `deleteAll() + insertAll()` 整表替换，
   两个来源共用一张表后会**互相扫掉**。改成：WebDAV 扫描删 `NOT LIKE 'local/%'`，
   本地扫描删 `LIKE 'local/%'`。**改动 `start()` 时最容易忘的就是这条。**

**其余接线**：`PlayerViewModel` 按前缀解析地址（本地 → content URI），
整队都是本地时不再要求「WebDAV 数据源可用」；本地不做同级目录 PROPFIND（没这个概念）。

**单测**：新增 `LocalMediaTest` 6 条（往返、前导斜杠、**WebDAV 路径不被误认**、
**「localx/」不算本地条目**、document id 拼接、前缀本身）。69 → **79 条**。

**验证（如实记）**：
- 设置页出现「添加本地文件夹…」；点它**系统选择器正常拉起**（`documentsui/PickActivity`）
- 编译 + 79 条单测全部通过
-  **「选文件夹 → 扫描 → 播放」这条链未完成自动化验证** —— SAF 选择器吃不下 adb 注入的点击
  （问题 56）。这一段要用户自己在手机上点一次才算验过。

### 0.3.5 · 音轨切换（2026-10-16）

用户的片子里有「国粤日三音轨」这种（ffprobe 实测那个文件**真有 3 条 AAC 音轨**），
但代码里只处理 `TRACK_TYPE_TEXT`，**没有任何音轨入口** —— 播放器自己挑一条就定了。

做法：照字幕菜单那套加一个「音轨」面板（`audioGroups` → `● 当前 / 点击切换`）。
只有 ≥2 条音轨才显示菜单项（一个选项的菜单没意义）。
标签 = `Format.label` → 语言表 → `音轨 N`，再带声道数（`5.1` / `立体声`）。
语言表要自带：media3 报的是容器里的原样标记，MKV 里通常是 **ISO 639-2/T 三字母码**
（`jpn` / `chi` / `yue`），而 `Locale.forLanguageTag` 只认 BCP-47 的两字母形式，喂进去会原样返回。

**问题 67（这次真正的绊脚石，值得单独记）：`onTracksChanged` 只在轨道「变化」时回调，不会补发。**
监听器是从 Compose 的 `DisposableEffect(controller)` 里挂上去的。只要挂上去时播放器**早就 prepare 完了**
（续播、旋屏、从迷你条进来都会这样），那个回调就**永远不会来**，`tracks` 恒为 null ——
表现是音轨菜单整块消失。更问题的是**字幕菜单一直受同一个 缺陷 影响却没人发现**：
它显示的是光秃秃的「字幕」而不是「字幕（开）」，因为 `subtitlesOn` 也一直算不出来。
→ 修法：挂监听时**先主动读一次 `controller.currentTracks`**。
> 教训：**不得假设「事件回调会提供初始状态」**。凡是从事件里攒状态的地方，
> 挂监听时都该同时读一次当前值。

**另外调了个缓冲参数**：只动「卡顿后重新起播」那一档（默认 5s → 8s） ——
源是夸克经 VPS 中转（约 3MB/s），带宽一抖就 stall/resume 来回切，多等一点体感顺。
**其余三档保持默认**：调大 `maxBufferMs` 是白吃内存（60s @ 3MB/s ≈ 180MB），没实测依据不动。

**验证**（真机）：
- `tracks attached: 0 groups` → `tracks changed: 4 groups`（日志，证明修复生效）
- ⋯ 菜单出现「音轨」；面板列出 **音轨 1/2/3 · 立体声**，当前那条标 ●
- 点「音轨 3」→ 日志 `audio -> 音轨 3 · 立体声` + `tracks changed` → 重开面板 **● 已移到第 3 条**
-  标签是「音轨 N」而不是「国语/粤语/日语」：**ffprobe 实测那三条轨的 `language` 都是 `und`**、
  也没有 title —— 文件本身没写元数据，App 只能如实编号。想真正标出来得靠改文件名约定或手工标注。

### 0.3.4 · 底栏与搜索栏压到 NagoMusic 的尺寸（2026-10-16）

用户反馈「底部栏和搜索栏太高」，参照他手机上装的 NagoMusic。
**尺寸是从它源码取的**（`lib/components/layout/bottom_chrome.dart`、
`lib/components/common/app_search_field.dart`），不是估的：

| | NagoMusic | 改前 | 改后 |
|---|---|---|---|
| 底部 Tab 条 | 56 | **80**（M3 默认） | **56** |
| 搜索框 | 38 | 40 | **38** |
| 吸顶栏整条 | — | 54 | **48** |
| 迷你播放条 | 52 | 54 | **52** |

- M3 的 `NavigationBar` 默认 80dp，内部用的是 `defaultMinSize`，**外面给 `.height(56.dp)` 压得下来**
  （真机量过确实生效）—— 不用自己重写整个栏。
- 真机实测（3200px 屏 / 密度 680 → 1dp = 4.25px）：底栏块 80dp → 62dp，搜索栏 54dp → 48dp。

### CI（2026-10-16 接入）

`.github/workflows/build.yml`：**推 master 就跑**（装 android-37.2 平台 → 从 secrets 还原 keystore →
单测 → `assembleRelease` → 上传产物），**打 `v*` tag 额外 `gh release create`**。

- 签名密钥以 **base64 存在仓库 secret**：`ANDROID_KEYSTORE_BASE64` / `ANDROID_STORE_PASSWORD` /
  `ANDROID_KEY_PASSWORD` / `ANDROID_KEY_ALIAS`。除了省掉手工发版，**另外是本机那份 keystore 的异地冗余**
  （本机丢了就永久不能再更新这个 App —— 见「换成正式签名」那节）。
- CI 复用现有的读取方式：把 4 个键写进 `local.properties`，**build.gradle.kts 一个字没改**。
- **问题 65：`compileSdk = 37` + `compileSdkMinor = 2` 的平台目录叫 `android-37.2`，runner 上没预装**，
  得 `sdkmanager --install "platforms;android-37.2"`（build-tools 同理）。
- **问题 66：`gradlew` 从 Windows 提交会丢可执行位**，CI 上直接 `Permission denied`（17 秒就红）。
  修法是 `git update-index --chmod=+x gradlew` —— **不得在 CI 里打 `chmod` 补丁**，那样对克隆的人也还是坏的。
- **验证**：CI 执行通过，耗时 8m38s，下载它产出的 APK，`apksigner` 报 `CN=MediaDock, O=lovelev, C=CN`
  且证书指纹与本地构建一致 —— 确认 CI 出的是**真签名包**，不是没签名的半成品。

### 诊断记录：`dumpsys media_session` 包含其他应用会话

**该问题单独记录，因为曾导致整体误判**：
用 `adb shell dumpsys media_session | grep -oE "state=..." | tail -1` 读播放状态时，
读到的是**其他应用的会话**（其 custom actions 为「关闭/收藏」，本项目不存在这些操作），
因此看到 `PAUSED` 即判定播放失败，并据此回退了本可正常工作的自绘控件。
实际会话状态为 `BUFFERING, position=399227`（**正在播**，且该位置与点击进度条 70% 的目标位置一致
—— 说明 seek 一直是好的）。

**正确做法**：只认 `package=com.lovelev.mediadock` **紧跟的那一段**状态，不得使用 `tail -1`。
（真机一台机器上可能有多个媒体会话：音乐 App、浏览器、系统媒体恢复等，均可能混入。）

### kotlin-lsp（Kotlin 代码智能，2026-10-15 接入）


| 东西 | 位置 |
|---|---|
| 插件 | `kotlin-lsp@claude-plugins-official` v1.0.0（user 范围，已启用） |
| 服务端 | `E:\code\tools\kotlin-lsp\`（1.2 GB，自带 JBR，build `ILS-263.6379.0`） |
| 索引目录 | `E:\code\tools\kotlin-lsp-data\` |
| PATH 入口 | `E:\code\tools\bin\kotlin-lsp.exe` |
| stub 源码 | `E:\code\tools\kotlin-lsp-stub\kotlin-lsp-stub.c` |

1. **Windows 上必须存在真正叫 `kotlin-lsp.exe` 的文件。** 插件 LSP 配置把 command 写死成
   `kotlin-lsp`，而 Windows 的 `CreateProcess` / Node 裸 `spawn` **都不做 PATHEXT 展开** ——
   实测 PATH 里有 `kotlin-lsp.cmd` 时 `spawn('kotlin-lsp')` 仍 `ENOENT`，官方包自带的 `.cmd` 因此没用。
   → 用 GCC 编了转发 stub。重建：
   `gcc -static -municode -O2 -o E:\code\tools\bin\kotlin-lsp.exe E:\code\tools\kotlin-lsp-stub\kotlin-lsp-stub.c`
2. **索引目录必须指到 E 盘**，否则 `RocksDBException: Failed to rename ... 系统无法将文件移到不同的磁盘驱动器`，
   索引直接失败。stub 里已硬编码 `--system-path=E:\code\tools\kotlin-lsp-data`。
3. **EULA 不用管**（日志里 `eulaHash=null`）。
4. **只对新会话生效** —— 插件启用后必须重启 Claude Code。

> 另外结论：官方市场里针对这个项目**只有 kotlin-lsp 值得装**。`code-review` / `simplify` / `run` /
> `frontend-design` / `security-review` / `impeccable` 本来就可用，重复安装只会吃常驻 token 且互相打架。

### 0.5.0 发布 + 交接整理（2026-10-17）

**发布**：0.5.0（versionCode 13）已发到站点。APK 18,717,785 B，最终 sha256
`94488c6d…56e7dd`（本地 / 服务器 / HTTPS 下载三处一致）；`version.json` 的 versionCode = 13；
发版页三处版本号与「版本历史」已更新。真机「设置 → 关于 → 检查更新」实测命中线上
`version.json`（nginx 日志 15:22–15:23 三条 `okhttp/5.5.0` 200），显示「已是最新版本」。

**问题 70：详情页上下两条白边（用户真机反馈）。**
详情页本身是始终深色的整页（海报取色铺满），但导航外壳给所有非播放页套了
`Modifier.padding(insets)`，状态栏/导航栏那两块露出 **Activity 的浅色 windowBackground**
（`android:Theme.Material.Light.NoActionBar`）—— 截图像素实测上边 0–115px、下边 3168–3199px
都是 `(246,246,243)`。
→ 修法：`Routes.DETAIL` 和播放页一样**不套 Scaffold 内边距**，改由详情页自己处理：
`Column` 加 `navigationBarsPadding()`、顶部 `TopBar` 加 `statusBarsPadding()`（海报因此能铺到
状态栏下面，整页一体）；同时新增 `SystemBarAppearance(route, darkTheme)`：详情页强制浅色系统栏
图标，其余页面跟随 App 主题（不是跟随系统 —— 用户可能把主题强制成深色而系统还是浅色）。
**不得再给详情页套回 Scaffold 内边距。**

**问题 71：首页最上面那条孤线（用户随后反馈）。**
`LibraryScreen` 顶部无条件画了一条 `HorizontalDivider()`。它原本是给「扫刮补状态条」收边的，
但 0.5.0 把搜索框搬走后，没有状态条时它就成了贴在状态栏下面的一条孤线。
→ 改成只在 `runningLabel != null`（真有状态条）时画。**不得将分隔线留在没有上方内容的位置。**

**代码整理（不改变行为）**
- `PlayerScreen.kt` 1313 → 457 行，拆成：`PlayerControls.kt`（自绘控件）、
  `PlayerComponents.kt`（进度条 / 锁定键 / 对话框 / 提示）、`PlayerSubtitles.kt`
  （挂载 / 在线搜索）、`PlayerCapture.kt`（截图存相册）、`PlayerFormatting.kt`
  （音轨标签 / 错误翻译 / 队列判定）。全是移动，没有改逻辑。
- 新增 `WebDavClientTest`（JDK 自带 HttpServer，不引依赖）：断言 401 / 429 / 500
  **都只发一次请求**并抛对应异常 —— 把「绝不重试」从注释纪律变成红灯。单测 82 → **85**。
- `hs_err_pid46688.log`（161KB JVM 崩溃日志）原来被 git 跟踪，已 `git rm --cached`
  删除并加 `hs_err_pid*.log` 规则。
- README / WORKLOG 顶部版本号从 0.3.0 修正到 0.5.0。

**真机验证（S21 Ultra，release 签名包）**：播放页起播（`PLAYING`，position 4.9s → 107s）、
点按唤出控件再点暂停（`PAUSED`，日志 `center play/pause tapped`）、⋯ 菜单
（字幕 / 详细信息 / 截图；单音轨时音轨项正确隐藏）、字幕菜单（关闭字幕 / 从本地文件加载… /
在线搜索…）、返回后迷你播放条显示「已暂停」。无崩溃。

**注意**：站点上的 `MediaDock-0.5.0.apk` 在发布后**被同名替换过两次**（先白边修复 + 内部拆分，
再首页孤线修复），versionCode 仍是 13，所以不会弹更新提示；当前站点文件 sha256 以上面那条为准。

**0.5.1（versionCode 14）**：把上面两处界面修复作为**正式版本**重新发布，不再同名覆盖 0.5.0。
APK sha256 `fbdbcc80…4bf8fe`；`version.json` 已指向 0.5.1；真机从 0.5.0 点「检查更新」
能看到「发现新版本 0.5.1」。同一轮还在站点 nginx 加了 `.apk` 的 MIME
（`application/vnd.android.package-archive`，配置备份 `personal-site.bak.apkmime.20261017`），
浏览器不再把 APK 报成「可能有害 / .bin」。

**本地字幕挂载（真机验证，2026-10-17）**：播放页 ⋯ → 字幕 → 从本地文件加载…，
在系统文件选择器里选中 `[花语字幕组][她和她的猫][01][GB][720][中日双语][招募]-1.srt`，
日志记录 `subtitle mounted: … @377946ms`。**这项从「待验证」转为已通过。**

**0.5.2（versionCode 15，2026-10-17）**：字幕与播放页这一轮暴露/反馈的问题。

**问题 72：挂载字幕会把播放队列缩成 1 条。**
`mountSubtitle` 原来用 `controller.setMediaItem(单个)` —— Media3 的语义是**替换整个播放列表**，
挂完字幕队列就只剩当前这一条，「自动下一集」静默失效（真机 `dumpsys media_session` 里
`size=1` 就是这么来的）。→ 改成重建整个队列、只替换当前 index 的那一条
（`setMediaItems(items, index, position)`）。验证：挂字幕后 `queue=4`，播到结尾 01 → 02 正常。

**问题 73：未知扩展名被默默当成 `.srt`。**
用户在文件选择器里选中了 `.torrent`，`SubtitleRepository.save()` 把不认识的扩展名一律改成 `srt`
落盘，SubripParser 拿二进制硬解，日志里全是 `Skipping invalid index`，界面上什么也不显示 ——
看起来就是「选了字幕但没反应」。→ 未知扩展名直接报「这不是字幕文件（.xxx）」，
只接受 srt / ass / ssa / vtt / sub。

**播放页 UI（用户反馈）**
- ⋯ 菜单加了图标 + 强调色（字幕 / 音轨 / 详细信息 / 截图），容器色抬高一档；
- 详细信息改成**可滚动**（横屏 / 小屏时下面的行不会再看不见）；
- 上一集 / 下一集改成 **YouTube 式**：播放 / 上一集 / 下一集三个半透明圆钮成组放在左下角，
  时间「当前 / 总长」紧跟其后，倍速在右下角；画面中央不再放大播放键（点画面本身显隐控件）；
- 亮度 / 音量 / 「已挂载字幕」提示停留 0.9s → **2.2s**，字号与底色也加重了。

**整体风格与速度（同一轮，用户要求）**
- 全局 Shapes 放大到 10 / 12 / 18 / 24 / 30（M3 默认 8 / 12 / 16 / 28），卡片、对话框、
  菜单、按钮统一更圆润；
- 播放页控件淡入淡出（180ms / 140ms）；页面切换统一淡入 + 1/20 屏宽的轻微横移；
- **问题 74：开启 R8**（`isMinifyEnabled = true` + `isShrinkResources = true`）。
  APK **18.7 MB → 3.39 MB**。Room / kotlinx-serialization / Media3 的 consumer 规则够用，
  无需手写 keep；但开 R8 必须真机跑这四条：**库/海报（Room+Coil）、播放、字幕、检查更新
  （序列化）**。这一轮四条都过了。以后加反射型库时记得重新回归。

**0.5.3（versionCode 16，2026-10-17）**
- **在线字幕验证通过**：设置里填 API Key + 账号 + 密码 → 播放页 ⋯ → 字幕 → 在线搜索…，
  查询返回 50 条结果。**共用 2 次搜索、0 次下载**（产品负责人提示 key 有额度限制，不得超额请求）。
- **搜索结果看不全**：原来用 `AlertDialog`，横屏时 text 槽被压得很矮，列表只露出半行。
  → 改成自定义 `Dialog`（`usePlatformDefaultWidth = false`）：固定标题/输入，结果列表
  `weight(1f)` 占满剩余高度并滚动；关闭改成标题栏右侧的**小药丸**，不再单占一行。
- ⋯ 菜单把「详细信息」缩成「详细」，菜单宽度收窄（`widthIn(max = 180.dp)` + 更紧凑的
  contentPadding）。
- **问题 75：`【电影】` 被当成片名**。`TitleParser` 的「方括号中文优先」规则把分类标签也算了进去，
  `【电影】秒速五厘米….mp4` 解析成「电影」。→ 加 `CATEGORY_TAGS` 过滤（电影/动画/纪录片…），
  单测锁住；在线字幕和刮削的预填都会受益。
- **问题 76（严禁重犯）：`connectedAndroidTest` 会卸载被测包。** 用同包名/正式签名的
  “verify” 构建跑 connected 测试后，Gradle 在测试结束卸载 `com.lovelev.mediadock`，
  把真实数据库、DataStore 与 Keystore 凭据一起清空。2026-10-19 已发生一次：
  用 `-PverifySamePackage` 跑 Compose UI 测试后 App 被卸载。恢复方式：
  从 `E:	mpts.db`（v11，280 文件/127 作品/139 集）回填数据库，重新填写 WebDAV/
  TMDB/OpenSubtitles 凭据；播放恢复为 `PLAYING`。**该 verify 入口已删除**；
  instrumentation 只能跑 `.debug` 包，绝不针对真实包名。

**0.5.4（versionCode 17，2026-10-17）**

- 背景：新增动漫目录采用裸数字集数（`片名01.mp4`）及 `S01`/`S02` 分季结构，原解析规则无法聚合。
- 变更：
  1. 支持裸数字集数识别，排除分辨率数值；
  2. 从路径中的 `S01`、`Season 1`、`第1季` 目录补充季号；
  3. 同一剧集按季目录的上一层合并；
  4. 扫描整批替换时，按路径保留已完成的刮削结果。
- 验证：海报墙显示「4 集」徽标；重扫后待刮削数量为 83/255；海报进度条显示正常；底部导航改为圆角悬浮容器。

**0.5.5（versionCode 18，2026-10-17）**

- 背景：详情页「修改信息 / 重新匹配」未生效，且修改仅作用于当前单集。
- 原因：`rescrapeOne` 未合并标题覆盖表；保存操作未触发重新匹配。
- 变更：
  1. 覆盖表参与重新刮削；
  2. 修改信息与匹配结果按整部剧应用；
  3. 新增 TMDB 候选选择，应用后作废旧详情缓存；
  4. 页面解释性文案改为简短表述。
- 验证：单元测试通过；真机确认覆盖与重刮流程可用。在线候选因当时网络不可达未能验证。

**0.5.6（versionCode 19，2026-10-17）**

- 背景：详情页的信息层级与同类产品差异较大，需要按参考应用重构。
- 变更：
  1. 顶部改为 16:9 横版背景图，标题、评分、日期与类型移至图下；
  2. 播放操作改为全宽白色按钮，收藏保留为下方图标；
  3. 新增季标签，选中态以下划线标识；
  4. 分集区域默认横向滚动；重复点击当前季标签切换为双列全量视图；
  5. 分集卡片接入 TMDB 剧照、分集标题与时长，按季请求并缓存至 `episode_meta`；
  6. `title_details` 增加总集数与总季数，展示格式为「共 38 集（库中 28 集）」；
  7. 「在线匹配」改为独立页面，展示全部候选（海报、标题、年份、评分、简介），选择后整剧应用。
- 数据：数据库版本升至 7，新增 `MIGRATION_6_7`（`title_details` 两列、`episode_meta` 表）。
- 验证：迁移链单测通过；真机确认详情页布局与分集数据加载正常。
- 备注：第二季扫描已确认收录（`S02` 目录 10 个视频）；此前显示 28 集为旧页面数据。

**0.5.7（versionCode 20，2026-10-17）**

- 背景：按既定顺序补齐媒体库能力，并统一搜索页版式。
- 变更：
  1. 扫描完成后自动刮削新增条目（仅处理未刮削项，不自动重试历史失败项）；
  2. 媒体库新增排序（默认 / 评分 / 年份 / 名称 / 最近添加）与「仅未看」筛选；
  3. 同一部电影的多文件按 TMDB ID 合并为一张海报，详情页提供版本切换；
  4. 新增用户片单，支持创建、加入、移除与整单播放，入口位于「我的」；
  5. 搜索页重做：搜索框、类型 / 排序 / 类型 / 地区 / 年份筛选、三列海报网格，
     卡片包含类型角标、评分与上映日期；
  6. 详情页横幅高度调整为半屏。
- 数据：数据库版本升至 8，新增 `MIGRATION_7_8`（`playlists`、`playlist_items`）。
- 验证：单元测试通过；真机确认搜索页版式、半屏横幅、片单创建与空态。
- 数据源：2026-10-17 用户将网盘目录「电影」改名为「影视」。OpenList 账号 `movie1`–`movie8`
  的 `base_path` 已从 `/夸克/电影` 更新为 `/夸克/影视`；重启 OpenList 后 WebDAV 根恢复 207。
- 扫描与刮削：扫描得到 280 个视频；自动刮削执行 90 条未刮削项，匹配 0 条，
  原因为手机端 TMDB 不可达。恢复代理后手动点「刮削」可重试这 90 条。
- 待验证：第二季（38 集 / 第 2 季标签）、加入片单完整链路。

### 2026-10-18 · 接手加固：媒体流 401/429 与凭据恢复

**背景**

接手评估时发现两处边界与项目第一硬约束（401/429 绝不自动重试）冲突：

1. `PlaybackService` 使用 Media3 默认的 `DefaultLoadErrorHandlingPolicy`。该策略把 HTTP
   响应错误视为可重试错误，点播默认重试 3 次 —— 一次播放会发出 4 个 401 请求。
   浏览 / 扫描的防线覆盖不到媒体流。
2. 凭据恢复失败时（Auto Backup 恢复到新机、Keystore 密钥被销毁、重装），
   `CryptoBox.decrypt` 返回空串，`SecureCredentialStore.load()` 仍返回
   `StoredSource(password = "")`，界面按“已登录”处理；扫描或播放会带着空密码发 Basic 请求。

**变更**

1. 新增 `player/WebDavLoadErrorPolicy.kt`：继承默认策略的其余行为，只在异常链中出现
   `InvalidResponseCodeException` 且响应码为 401 / 429 时返回 `C.TIME_UNSET`（不再重试）。
   `PlaybackService` 通过 `DefaultMediaSourceFactory.setLoadErrorHandlingPolicy(...)` 挂载。
2. `SecureCredentialStore.load()`：密码密文缺失或解密失败时返回 `null`（视为凭据损坏），
   使起始页回到“添加数据源”；`cached` 同步置空，媒体数据源取不到凭据会直接抛错，不发出请求。
3. `CryptoBox` 新增 `decryptOrNull()`；`decrypt()` 保留原语义（解不开返回空串）供
   TMDB / 字幕等非数据源凭据沿用。
4. 新增 `WebDavLoadErrorPolicyTest` 6 条：401 / 429 / 500 / 外层包装 / 普通网络错误 / null。
   单测总数 90 → **96**。

**验证**

- `:app:testDebugUnitTest`：96 / 96 通过。
- `:app:assembleRelease` 成功（R8，APK 3.4 MB）；`adb install -r` 同签名覆盖安装成功，
  媒体库、进度与凭据均保留。
- 启动后直接进入媒体库（未跳“添加数据源”），确认 Keystore 解密链路未受影响。
- 打开详情页播放：日志 `tracks changed: 2 groups`，媒体会话 position 前进至 10.9 s、
  buffered 92.5 s，无播放错误日志。
- 全程未制造任何 401 / 429 请求，未对线上 WebDAV 发起额外探测。

**备注**

- 未升版本、未发布站点；本次属于加固批次，按既定节奏攒批后再发版。
- 回滚方式：移除 `WebDavLoadErrorPolicy` 的挂载即可恢复默认重试行为；不涉及数据库与用户数据。
- 评估中其余 P1（分享功能明文输出凭据、全局放行明文流量、Android 14 部分媒体访问、
  迁移 schema 校验）本轮未动。

### 2026-10-18 · 第二季验证与命名解析修正

**背景**

验证「葬送的芙莉莲」第二季时，详情页仍显示「共 38 集（库中 28 集）」且只有「第 1 季」。
排查后确认是**命名解析**问题：S02 文件名是 `29.简日双语.mp4` … `38.简日双语.mp4`，
`stripLeadingIndex` 原本设计用于目录序号（`1.河西走廊`），却同样作用在文件名上，
把开头的 `29.` 剥掉，导致没有集数、被当成独立电影。由此还引出一张无海报的「简日双语」
推荐卡，以及「搜索进详情有季标签、主页点同名卡没有」的差异。

**变更**

1. `TitleParser.parse` 新增 `inSeasonDir` 参数：只有位于季目录（`S01` / `Season 1` / `第1季`）
   时，才把文件名开头的 `NN.` / `NN-` / `NN_` 当集数；不在季目录时仍只当目录序号。
   扫描器（WebDAV / 本地）与字幕搜索预填都传入该参数。单测 96 → **98**。
2. **主页移除排序 / 「全部」两个筛选胶囊**（用户要求）；`WallControls` 与相关下拉菜单删除。
3. **海报裁切修正**：详情页横幅与首页大卡在只有竖版海报、没有横版背景图时改用
   `ContentScale.Fit`（完整显示，空余处用页面主色铺底）；有横版背景图时仍 `Crop`。

**验证**

- 单测 **98 / 98** 通过；`:app:assembleRelease` 成功并同签名覆盖安装。
- 重扫 280 个视频后：详情页显示「**共 38 集（库中 38 集）**」，出现「第 2 季」标签，
  展开后 **29–38 集全部存在**，第 1 / 2 季切换正常。
- 主页已无「排序 · 默认」「全部」两个胶囊。
- 发现一处**命名导致**的误合并：目录/条目名为 `简日双语` 的 63 集被聚成一部剧
  （3 个季标签）。这是共享发布标签目录导致的，需要用户改名或按剧拆分；不是解析器特判能解决的。
- 海报 `Fit` 改动已安装至真机；本次打开的「简日双语」条目没有海报（纯色回退），
  只有海报的条目需用户在实际片子上确认效果。

**备注**

- 未升版本、未发站点。
- S02 分集标题暂时显示为「29. 29.简日双语」：TMDB 第 2 季分集元数据尚未拉到时的文件名回退；
  代理可用并成功拉取后会显示真实分集名。
- 命名规则与已知限制已同步到第 6 节，后续按「名字问题」优先向用户说明。

### 2026-10-18 · 架构文档：10TB / 多数据源 / HDR 高规格

**背景**

用户明确后续规模会到 10TB 以上，并要求具备网易爆米花级功能与 HDR 等高规格播放。
需要把“当前实现能否承接”讲清楚，并给出可执行的演进路线，而不是继续在单源、全量列表、
内存任务上叠加功能。

**变更**

- 新增 `ARCHITECTURE.md`：现状盘点、目标模块划分、数据模型（稳定 `logicalId`、`sources`、
  `works`/`episodes`、`scan_runs`、`scrape_tasks`、`media_stream_info`、`device_capabilities`）、
  扫描/刮削框架、Paging/FTS、迁移策略、Phase 0–4 路线图与风险表。
- 播放章节扩展为 **3.7 播放与高规格**：设备能力模型、`StreamInfo` 探测与缓存、
  `PlaybackPlan`（HDR 直出/色调映射/DV 基础层/HLG→SDR、音频直通/解码/下混、刷新率匹配、
  失败回退链）、Media3 渲染要点、格式/设备策略、测试矩阵。
- Phase 3 拆成 3A 能力模型 → 3B HDR 直出 → 3C 降级/软解 → 3D 画中画/投屏/字幕样式。
- 补充状态管理与 UI 架构、错误模型与源健康、缓存/离线、媒体识别与特殊结构、搜索与索引、
  后台任务与调度、性能与容量预算、数据库运维与数据质量、Epic 拆解、ADR 与非目标。
- 新增第 13 节「质量属性评估与改进清单」：15 项质量属性矩阵、P0/P1/P2 代码改进项
  （含文件位置与验收）、内聚/耦合治理顺序、质量门禁（DoD）与待决策 D1–D12。
- README 与 `NEXT_SESSION.md` 增加架构文档入口。

**验证**

- 本次只改文档，无代码改动；单测仍为 98 条。
- 文档中的约束与现有实现一致：401/429 不重试、真迁移、版本单一来源。

**备注**

- 用户于 2026-10-18 拍板：先手机 + 投屏、TV 单列 Phase 4；多端同步暂不做但预留 `SyncProvider`；
  数据源范围包括 WebDAV、本地、SMB 与 Emby/Jellyfin；身份迁移采用**一次性切换**，
  以自动备份、逐键校验和回滚守卫控制风险。
- 同日产品决策：离线下载、多用户/儿童模式、可选崩溃上报、平板/折叠屏全部纳入 Phase 5。
  ARCHITECTURE.md 新增第 14 节，补齐设置/凭据分层、发现/首页、元数据提供方、播放会话生命周期、
  网络与源适配契约、安全/供应链/发布、测试/诊断、自适应 UI、离线下载与架构治理。
- HDR/DV/音频直通的具体实现排在 Phase 3，前置是 Phase 0–2 的稳定身份、多源与增量扫描。
- 高规格播放的验收依赖真机与片源矩阵（见 ARCHITECTURE.md 3.7.6），不以编译通过代替。

### 2026-10-18 · 文档与文案规范化

**背景**：项目交付标准由个人项目提升为企业级，需要统一文档结构与语言，并清理界面中不必要的解释性提示。

**变更**：
- `ARCHITECTURE.md` 完成第二版修订：补充 v9 迁移字段映射、身份合并冲突规则、多集单文件模型、
  `FileSource` / `CatalogSource` 契约、投屏接收端能力模型、明文流量与迁移守卫等；全文统一为正式书面语。
- `README.md`、`NEXT_SESSION.md` 按企业级文档重写，新增文档与文案规范。
- `WORKLOG.md` 统一标题与用语；历史条目在不改变技术事实的前提下完成语言规范化。
- 界面文案：空状态统一为最短标签；删除解释性提示与第二人称表述；错误信息统一为可操作表述。

**验证**：`:app:assembleDebug` 与 `:app:assembleRelease` 构建通过；`:app:testDebugUnitTest` 98 条单元测试全部通过；release 包同签名覆盖安装至真机（SM-G998U1），启动无崩溃，设置页文案正常，TMDB 凭据与字幕密钥已掩码显示。

**备注**：本次未改变业务逻辑与数据结构；`ARCHITECTURE.md` 中的数据模型修订仍为设计草案，需按 Phase 0a 单独发版实施。

### 2026-10-18 · S1：v8→v9 迁移与兼容 VIEW 验证

**背景**：按架构第 15 节的启动门槛，在 0a 前先验证 v8→v9 的 SQL 迁移、兼容 VIEW 与 Room 读取路径。

**变更**：
- 开启 `exportSchema = true`，生成并提交 `app/schemas/com.lovelev.mediadock.data.local.MediaDatabase/8.json`；移除 `.gitignore` 对 `app/schemas/` 的忽略。
- 新增 `tools/spikes/s1/s1_migration_spike.py`：用 v8 schema 构造夹具，原型 v9 迁移（UUID + `logical_key`、movie/episode versions、`file_segments`、分表进度、兼容 VIEW），逐键校验。
- 新增 androidTest `CompatibilityViewTest`：验证 Room `@DatabaseView` 可读取兼容 VIEW 并支持 `IN (:paths)`；新增 androidTest 依赖、`kspAndroidTest` 与 debug 包名后缀 `.debug`（避免 instrumentation 覆盖 release）。
- release 包名、签名与数据不受影响。

**验证**：
- S1 SQL spike 全部通过：VIEW 6 行与 legacy 逐列一致；`IN(path)` 正常；works 4、episodes 2、media_files 6、movie_versions 4、episode_versions 2；进度 2+1；收藏/片单按作品去重为 2；改名/详情/分集元数据映射正确。
- `connectedDebugAndroidTest` 在 SM-G998U1（Android 15）通过：Room `@DatabaseView` 读取 VIEW 正常。
- release 仍为 0.5.7 / versionCode 20，数据未动；98 条单测通过；release 构建通过。
- 设备上的 debug/test 包位于 secondary user 151，shell 无权限卸载；不影响 release。

**备注**：
- S1 只验证迁移与 VIEW；v9 正式 schema/迁移仍待 0a 实施。
- `@DatabaseView` 方案成立；0a 需把旧 `MediaItemEntity` 的读路径切到视图 POJO，写路径改新表。
- S1 发现：兼容 VIEW 需要 `works.scrape_state` / `scraped_at`；`title_details` 的
  `release_date` / `runtime_minutes` / `country` / `genres` / `cast` 需落在 `works`
  或独立 `work_details`；0a schema 必须补齐。
- 下一步：S2 中文 FTS、S3 DLNA/SMB 选型、S4 FFmpeg 16KB/HDR。

### 2026-10-18 · S2：中文搜索 20k 基准

**背景**：架构 D5 需在 FTS4（`search_text`）与 FTS5 trigram 之间选定中文搜索方案，
并以 20k 条数据验证查询延迟与正确性。

**变更**：
- 新增 `ChineseSearchBenchmarkTest`（androidTest）：真机生成 20k 条合成作品，
  分别建立 FTS4（CJK 逐字空格 + 短语）、FTS5 trigram（若支持）与 LIKE 基线，
  测量 p50/p95 并校验标题/演员查询结果。
- 无产品代码变更。

**验证（SM-G998U1 / Android 15）**：
- FTS5 trigram **不可用**：`no such module: fts5`。
- FTS4：p50 **1.20 ms**、p95 **3.44 ms**；LIKE 基线 p50 7.85 ms、p95 8.11 ms。
- 20k 条数据库约 **5.7 MB**；标题、2 字查询、演员查询均命中；测试通过。
- 结论：选择 **FTS4 + `search_text`**，查询把 CJK 串转为空格短语；不使用 FTS5。

**备注**：
- 索引随标题/别名/演员变化重建；`MediaItemEntity` 的标题覆盖变更也要触发重建。
- 2 字中文查询可用（FTS5 trigram 需 ≥3 字，且目标机不支持）。
- 下一步：S3 DLNA/SMB 选型、S4 FFmpeg 16KB/HDR。

### 2026-10-18 · S3：DLNA / SMB 客户端评估

**背景**：S3 需为投屏与 SMB 数据源选定客户端库，并验证 Android 可运行性。

**变更**：
- 评估 jUPnP 3.0.5（CDDL-1.0）与 SMBJ 0.15.0（Apache 2.0）。
- 新增 `NetworkClientsSpikeTest`：原始 SSDP M-SEARCH + 组播锁发现；SMBJ 客户端构造。
- 主 manifest 增加 `CHANGE_WIFI_MULTICAST_STATE`（投屏发现需要）；jUPnP 依赖未保留。

**验证（SM-G998U1 / Android 15）**：
- **jUPnP 不采用**：Android 模块启动时 `NoClassDefFoundError: org.eclipse.jetty.server.Server`；
  其 Jetty 依赖未随包声明，兼容线为 `javax.servlet` 的旧 Jetty。
- **SMBJ 可构造**：`SMBClient()` 在 Android 上正常；真实共享连接待用户提供测试 SMB 共享。
- **SSDP**：原始 M-SEARCH 在持有组播锁时正常执行；当前 Wi-Fi（10.64.23.0/24）返回 **0 个设备**，
  可能无 DLNA 渲染器或网络隔离；真机投屏需在有电视的环境补验。
- 全部 androidTest 通过。

**备注**：
- 选定：SMB 用 **SMBJ**；DLNA 用 **自研 SSDP/SOAP 控制器 + 本地 HTTP 代理**。
- WebDAV / SMB / 本地文件不能被电视直接读取（Basic 认证/非 HTTP）；投屏时手机需起临时
  Range HTTP 代理，只暴露当前会话、随机路径、结束即停。
- Emby/Jellyfin 优先 Direct Play URL；转码时提示 HDR/Atmos 降级。
- DLNA 无鉴权：仅可信局域网、用户确认、不暴露凭据。
- 下一步：S4 FFmpeg 16KB + HDR/DV/Atmos 真机能力。

### 2026-10-18 · S4：FFmpeg 16KB / HDR / DV / Atmos 真机能力

**背景**：S4 需确定高规格播放的承诺范围，并评估 FFmpeg 软解的 16 KB、包体与许可证。

**变更**：
- 新增 `PlaybackCapabilitiesSpikeTest`：显示 HDR 类型、视频/音频解码器 profile、逐样本实播。
- 新增 `tools/spikes/s4/generate_samples.sh` 与 androidTest 样本
  （HDR10 MP4/MKV、HLG、AV1 10-bit、4K60 HDR10、E-AC-3 5.1、TrueHD 5.1、DTS 5.1）。
- ARCHITECTURE 增加 S4 矩阵、ADR-026 与风险项；路线图调整 FFmpeg 软解优先级。

**验证（SM-G998U1 / Android 15 / SDK 35）**：
- 显示：`hdrTypes=[HDR10,HLG,HDR10_PLUS]`，**无 Dolby Vision**。
- 视频：`c2.qti.hevc.decoder` 硬解 Main10/Main10HDR10/Main10HDR10Plus；VP9 硬解
  Profile2HDR/Profile2HDR10Plus；AV1 仅软解 `c2.android.av1-dav1d.decoder`；
  4K60 HDR10 前 2 s 0 丢帧。
- 音频：AC-3 6ch、E-AC-3 8ch、E-AC-3 JOC 16ch 可解；**TrueHD、DTS 轨道
  `isSupported=false`**，播放状态 READY 但静音且无错误。
- HDR 元数据：`Format.colorInfo` 给出 BT2020/ST2084/HLG；`hdrStaticInfo` 在 MP4 与 MKV
  中均为空，不能用于判断 HDR10+。
- FFmpeg：`nextlib-media3ext:1.11.1-0.16.0` 的 arm64 `.so` 合计 7.51 MiB，
  `LOAD p_align=0x4000`（16 KB 合规）；POM 许可证 **GPL-3.0**。官方 Media3
  `decoder_ffmpeg` **仅音频**、未发布 Maven 构件，需自建（LGPL）。
- 当前 release APK 3.27 MiB：`.so p_align=0x4000` 且 `zipalign -P 16` 通过。
- 98 条单测与 release 构建通过；`PlaybackCapabilitiesSpikeTest` 3 项全部通过。

**备注**：
- 结论：HDR 承诺 HDR10/HLG/HDR10+，不承诺 DV 与 Atmos 直通；FFmpeg 视频软解后置（ADR-026）。
- 新增待办：播放页对 `isSupported=false` 的音频轨提示「无音频解码器」，避免静音无感。
- DV P5/P8 未实测（无片源/设备）；P8 预期回落 HEVC 基础层，P5 预期不可播。
- S1–S4 全部完成；下一步按 15.10 进入 0a（数据层 + 兼容 VIEW）。

### 2026-10-19 · 0a：v9 数据层、兼容 VIEW 与真机迁移

**背景**：0a 只切数据层：把用户数据从 path 迁到 UUID/内容模型，旧 UI 读路径保持不变，
并满足「真迁移、零丢失、可回滚」。

**变更**：
- 新增 `:core:model`（SourceId / LogicalKey / LogicalKeyCodec / normalizeRemoteKey /
  确定性 UUID）与 `:core:database`（Room 实体/DAO/迁移/`MediaLibraryStore`）。
- v9 schema：sources / media_files / works / episodes / movie_versions / episode_versions /
  file_segments / 三类 progress / favorites / playlists（user_id）/ playlist_items /
  title_overrides / work_details / content_tombstones / subtitles / provider_health /
  scrape_tasks / media_fts（FTS4）。旧表改名 `*_legacy` 保留到 v10。
- 兼容 VIEW：`media_items`、`playback_progress`、`favorites_view`、`playlist_items_view`、
  `title_overrides_view`、`title_details`；旧 DAO 只读不变，写路径统一走新表。
- `MigrationGuard`：升级前 WAL 检查点 + 备份到 `filesDir/migration/`，写 `v9_state.json`；
  迁移失败恢复最近备份。debug 启动执行 `ShadowCompare`。
- 新增 `MigrationV8ToV9Test`（脱敏 fixture + 逐列 VIEW 校验 + 回滚演练）与
  `MediaLibraryStoreTest`（扫描/收藏/片单/进度/改名/详情/刮削合并/清库）。

**验证（SM-G998U1 / Android 15，0.5.7 → 本版覆盖安装）**：
- `media_items_legacy` 280 行 → `media_files` 280、`works` 127、`episodes` 139、
  `movie_versions` 126 + `episode_versions` 154 = 280。
- 「葬送的芙莉莲」38 集，季 1–2；详情页显示「共 22 集（库中 11 集）」等原有信息正常。
- ShadowCompare：**比对 280 行，差异 0 处**。
- 迁移前用户表（收藏/片单/进度/改名）为空，迁移后仍为空；实测写入收藏与播放进度后，
  `favorites` / `movie_progress` 与兼容 VIEW 同步；测试写入已清理。
- `MigrationV8ToV9Test`、`MediaLibraryStoreTest`、S1 兼容 VIEW 测试全部通过；
  98 条单测与 release 构建通过。
- 升级备份已生成：`filesDir/migration/library-v8-*.db`。

**备注**：
- 兼容 VIEW 需要 per-file 的解析标题/年份/季集与 v8 一致，故 `media_files` 增
  `parsed_*` 与 `legacy_compat`；`scrapedAt` 是作品级派生值，shadow compare 不比它。
- 延后：SAF 用户可见备份导出、字幕文件索引、genres/people/cast 规范化（Phase 1/2）。
- 临时校验入口：`./gradlew :app:assembleDebug -PverifySamePackage` 生成同包名可调试包，
  仅用于只读核对真机库；正常 debug 仍是 `.debug` 包名。
- 项目接受开源，许可证采用 GPL-3.0（ADR-027）；Phase 3 可引入 GPL FFmpeg。

### 2026-10-19 · 0b：网络/数据模块骨架与查询、刮削抽取

**背景**：0a 迁移稳定后，按架构拆出网络与数据模块，强制依赖方向，为 Phase 1 的
WorkManager 扫描/持久刮削做准备；不改变用户可见行为。

**变更**：
- 新增 `:core:network`：WebDAV 客户端与解析、TMDB Client、OpenSubtitles Provider、
  UpdateChecker、Countries；依赖 `:core:model`。
- 新增 `:core:data`：`LibraryQueryRepository`（items / details / observeByPaths）、
  `MetadataScraper`（TMDB 匹配/候选）、`PathIdentity`；依赖 model/database/network。
- `TitleParser`、`MediaTypes` 移入 `:core:model`；`UpdateCheckerTest`、`WebDavClientTest`
  随实现移入 `:core:network`。
- `LibraryRepository` 的读查询委派给 `LibraryQueryRepository`，单条匹配委派给
  `MetadataScraper`；扫描/刮削状态机暂时仍留在应用层（Phase 1 抽取）。

**验证（SM-G998U1 / Android 15）**：
- `:core:model:test`、`:core:network:test`、`:app:testDebugUnitTest`、
  `:core:database:testDebugUnitTest` 全部通过；database instrumentation 全部通过。
- 0.7.0 release 覆盖安装：媒体库 280 条目正常；WebDAV 播放进入 `PLAYING`
  （buffered 40 s），日志无 401/429、无崩溃。
- 测试产生的进度已清理，库中用户数据仍为空（与 0a 基线一致）。

**备注**：
- 依赖方向：model ← database/network ← data ← app；没有 core 模块反向依赖 app。
- 延后：完整仓库与扫描状态机抽取（Phase 1 E1.6）、`:core:datastore`、
  `:core:ui` / `:feature:*` 拆分。

### 2026-10-19 · Phase 1 E1.1：WorkManager 增量扫描与断点续跑

**背景**：扫描从内存 job 迁到 WorkManager + 持久队列，要求增量、可中断续跑、
401/429 不重试，并保持现有 UI 行为。

**变更**：
- v10 schema：`scan_runs`、`directory_cache`、`scan_queue`；`9→10` 迁移与测试。
- `:core:network`：PROPFIND 增加 `getetag` 与集合自身元数据；新增
  `propfindWithCache`（If-None-Match / If-Modified-Since，304/412 → 未变化）。
- `:core:data`：`ScanEngine`（持久 BFS、目录级差异写入、目录缓存跳过子树、
  401/429 封锁、FULL 收尾软删除、上限/截断、取消保留队列）。
- app：`ScanWorker`（唯一名 `scan:<sourceId>`、dataSync 前台通知、网络约束、
  网络故障退避）、`ScanScheduler`；`LibraryRepository.start()` 改为入队并观察 WorkInfo。
- 兼容 VIEW `media_items` 仍为唯一读路径；扫描完成后 Done 显示库总量而非本轮变更数。

**验证（SM-G998U1 / Android 15）**：
- 首轮：53 目录 / 280 文件，`DONE`；缓存 53 条；队列 0。
- OpenList 不返回集合 ETag、也不支持 304，仅返回 Last-Modified；改为
  「Last-Modified + 子项数」判定未变化后，下一轮 **1 目录 / 0 文件**。
- 中途取消：`RUNNING`，5 目录 / 92 文件，队列 35；再次启动续跑至
  **52 目录 / 280 文件**、队列 0、`media_files` 280、缺失 0。
- `ScanEngineTest`（全量/增量跳过/删除/401/续跑）、`MigrationV9ToV10Test` 与
  全部既有单测/instrumentation 通过；0.8.0 release 真机验证。

**备注**：
- OpenList 的集合元数据不可靠时，可加“保守模式”（只跳过文件解析、不跳过 PROPFIND）；
  当前默认按架构 3.4 跳过子树。
- FULL 扫描接口已支持（`ScanScheduler.start(..., ScanMode.FULL)`），单独 UI 入口待补。
- 剩余 Phase 1：E1.3 Paging/works、E1.4 FTS、E1.5 20k 压测。

### 2026-10-19 · Phase 1 E1.2：持久刮削队列、Worker 与预算

**背景**：刮削从内存循环迁到 `scrape_tasks` 持久队列 + WorkManager，要求可暂停/续跑、
单条失败退避、凭据错误整轮停止、按日预算限流。

**变更**：
- v11：`scrape_tasks` 增加 `(content_id, content_kind, provider, task_kind)` 唯一索引；
  `MigrationV10ToV11Test` 通过。
- `ScrapeTaskDao`：到期任务、状态/尝试次数计数、失败退避、手动匹配清理。
- `:core:data` `ScrapeEngine`：按任务驱动匹配；每个任务每轮只处理一次；
  失败 1→2→4… 分钟指数退避（上限 24h）；401 整轮停止；预算耗尽暂停。
- app：`ScrapeWorker`（唯一名 `scrape`、网络约束、WorkInfo 进度）、`ScrapeScheduler`；
  `LibraryRepository` 改为入队 + 观察 WorkInfo；扫描后自动入队，手动“刮削”同样。
- HTTP 缓存：共享 OkHttp 增加 32 MB 磁盘缓存（仅 GET）。
- 预算：DataStore `scrape_budget`，默认 1000 次/天。

**验证（SM-G998U1 / Android 15）**：
- 升级 v10→v11 成功并生成 `library-v10-*.db` 备份。
- 库内 13 个待刮削作品全部入队；设备当前无法连接 TMDB（IPv6 连接超时），
  任务按退避保留（`attempts=1–2`、`next_attempt_at` 递增），取消后 13 条仍为 PENDING；
  UI 进度由 WorkInfo 驱动，取消只停 Worker，不丢任务。
- 发现并修复：同一轮内退避到期会重复领取任务（进度 17/13）；现每任务每轮只处理一次。
- `ScrapeEngineTest`（成功/失败退避/401/预算/幂等）与全部既有单测/instrumentation 通过。

**备注**：
- 网络恢复后在「我的 → 刮削」即可继续处理这 13 条（到期任务）。
- 刮削预算设置项、FULL 扫描 UI 入口留待后续。
- 剩余 Phase 1：E1.3 Paging/works、E1.4 FTS、E1.5 20k 压测。

### 2026-10-19 · Phase 1 E1.3：works 分页海报墙

**背景**：首页原先全量读取 `media_items` 并在内存分组，20k 库会卡顿/占内存；
E1.3 改为直接查询 `works` 的 Paging 3 分页。

**变更**：
- `WorksCardDao` + `WorksCardSql`：RawQuery（Room PagingSource）返回作品卡，
  含海报/评分/集数、已看完集数、电影进度、收藏标记与全部文件 path。
- `LibraryQueryRepository`：`worksPager`（30/页）、`randomWorks`（5）、
  `recentWorks`（200，分类行）、`favoriteWorks`（20，推荐理由）。
- `LibraryViewModel`：首页墙改为 `PagingData<WorkCard>`；推荐/分类行改为有界查询，
  不再收集全库条目；扫描完成刷新头部。
- `LibraryScreen`：`LazyPagingItems` + `WorkPosterCard`/`WorkFeaturedRow`/`WorkSectionRow`；
  支持追加加载、失败重试与刷新状态；详情仍按卡片携带的 paths 导航。
- 搜索页暂未改（仍全量内存过滤），E1.4 处理。

**验证（SM-G998U1 / Android 15）**：
- 0.10.0 覆盖安装；127 个作品分页展示，系列卡（24 集）点入详情显示
  「共 22 集（库中 26 集）」与多季切换；滚动加载正常，无崩溃。
- `WorksPagingTest`（分页/路径/集数/收藏/进度/随机与有界查询）与全部既有
  单测/instrumentation 通过。

**备注**：
- 作品卡集数按 works 聚合（跨季），与旧「按目录分组」的显示可能不同；
  详情页仍以文件/集为单位。
- 搜索页仍是全量内存过滤，E1.4 改为 FTS 分页后移除。
- 剩余 Phase 1：E1.4 FTS、E1.5 20k 压测。

### 2026-10-19 · Phase 1 E1.4：FTS4 搜索、SQL 筛选与分页

**背景**：搜索页原先全量读取 `media_items`/`title_details` 后在内存过滤，20k 库不可用。
E1.4 改为 FTS4 + SQL 过滤/排序 + Paging。

**变更**：
- `SearchText`（`:core:model`）：索引文本 CJK 逐字加空格；查询把中文转逐字短语、
  拉丁词转前缀（FTS4 前缀必须不带引号）；单测覆盖。
- `SearchIndexer`（`:core:database`）：索引标题/手工改名/类型/演员；`media_fts`
  在全新安装时自动创建；`provider_health(fts_index)` 记录索引版本，启动后台重建一次；
  扫描/刮削/改名/详情写入时同步刷新。
- `WorksCardSql`：增加 FTS 子查询与类型/类型标签/地区/年份/排序过滤；作品卡接入
  `title_overrides` 的显示标题，并补 `country` / `releaseDate`。
- `SearchViewModel`：输入与筛选项 250ms 防抖；筛选选项取最近 500 部（有界）；
  结果走 Paging 3。`SearchScreen` 改为 `LazyPagingItems` 三列网格。
- **移除** `-PverifySamePackage` 诊断构建（见问题 76）。

**验证（SM-G998U1 / Android 15）**：
- 真机库 FTS 索引 127/127，`provider_health(fts_index)=2`；直接 SQL `MATCH '"芙 莉 莲"'`
  命中「葬送的芙莉莲」。
- `SearchPagingTest`：中文（含空格）、拉丁前缀、演员名、手工改名、类型/地区/年份筛选全部通过。
- 搜索页空查询显示作品；「动漫」筛选正常返回结果并分页。
- 因问题 76 的卸载事故，数据已从导出副本恢复；WebDAV/TMDB/OpenSubtitles 凭据已重填，
  播放恢复为 `PLAYING`（position 25s）。

**备注**：
- FTS4 前缀必须写 `term*`（不带引号）；中文短语用 `"芙 莉 莲"`。
- 搜索页 UI 的中文输入由仓库级测试覆盖；自动化 UI 输入在本次尝试中不可靠，未保留。
- 剩余 Phase 1：E1.5 20k 压测。

### 2026-10-19 · Phase 1 E1.5：20k 合成库压测

**背景**：Phase 1 的验收要求是在 20k 文件 / 5k 作品规模下验证首屏、分页、搜索、内存与
数据库体积不退化。此前 S2 只测了裸 FTS 表，未覆盖真实 schema 与 `WorksCardSql`。

**变更**：
- 新增 `Stress20kTest`（app androidTest，仅对 `.debug` 包运行）：经生产写层
  `MediaLibraryStore.applyDirectoryListing` 播种 4,000 部电影 + 1,000 部剧
  （每部 2 季 × 8 集），共 **20,000 文件 / 5,000 作品 / 16,000 集**；
  其中 300 部补详情（类型/国家/演员）、20 部补收藏与进度。
- 测量项：首页首屏（RECENT/TITLE）、整库 167 页滚动、随机推荐/分类行/收藏行、
  7 类搜索、类型/类型标签/年份筛选、进程 PSS 峰值、数据库文件体积。
- 无产品代码变更。

**验证（SM-G998U1 / Android 15，`.debug` 包）**：

| 指标 | 实测 | 目标 |
|---|---|---|
| 播种 20k 入库 | 188 s；PSS 峰值 255.7 MB | 无 OOM |
| 首页首屏 p95（RECENT） | 356.7 ms | ≤ 1 s |
| 首页首屏 p95（TITLE） | 151.8 ms | — |
| 整库滚动 167 页 / 5,000 行 | 每页 p95 330.2 ms；总 50.1 s | 不卡 |
| 搜索 p95（中文/逐字/拉丁/演员/数字） | 94.7 ms（最慢「剧1」102.3 ms） | ≤ 300 ms |
| 筛选 p95（类型/类型标签/年份） | 191.7 ms | — |
| 随机推荐 5 / 分类行 200 / 收藏 20 | 75.6 / 314.5 / 84.0 ms | — |
| 数据库体积 | 20.3 MB | < 200 MB |
| PSS 峰值 | 255.7 MB（debug 基线 217.0 MB） | < 300 MB |

- 计数校验：`media_files` 20,000、`works` 5,000、`episodes` 16,000，与播种一致。
- 测试通过；release 安装与数据未受影响（instrumentation 使用 `com.lovelev.mediadock.debug`，
  测试结束 Gradle 只卸载该包）。
- 单元测试无产品代码变更，任务全部 UP-TO-DATE。

**备注**：
- `RECENT` 排序首屏约为 `TITLE` 的 2.3 倍（356.7 vs 151.8 ms）；分类行取 200 条约 314 ms。
  均达标，但首页首屏是当前最慢查询，后续可考虑为 `works.added_at` 建索引或先选 id 再回表。
- debug 包的 PSS 基线（217 MB）包含测试进程与调试符号开销；release 实际占用应更低。
- 播种耗时主要来自逐作品 FTS 刷新与写事务，真实扫描的墙钟时间仍由 PROPFIND 主导。

### 2026-10-20 · 无解码器音轨提示（0.12.0）

**背景**：S4 结论显示目标机没有 TrueHD/DTS 解码器，选中这类音轨时播放器只会静音且没有任何
反馈，用户容易误判为片子损坏。本次在播放页补明确提示。

**变更**：
- `PlayerFormatting`：新增 `audioCodecName`、`AudioTrackStatus`、`audioTrackStatuses` 与
  `unsupportedAudioWarning`；判定与 media3 解耦，便于 JVM 单测。
- `PlayerScreen`：选中音轨无解码器时显示常驻提示条（不随控件自动隐藏，锁定状态下仍可见）；
  起播瞬间轨道分批上报，提示连续成立 1.5 秒才出现，避免闪烁；同时落一条
  `audio unsupported` 日志（含 mime/selected/supported）。
- `PlayerControls`：音轨菜单给无解码器轨道追加「· 无解码器」并用 error 色标注。
- `AudioWarningTest`：覆盖 TrueHD/DTS 命名、已选中支持/不支持、无选中但全部不支持、
  部分不支持但已选中支持轨、未知编码等 5 组判定。

**验证（SM-G998U1 / Android 15，`.debug` 包）**：
- DTS 单音轨样本：`audio/vnd.dts selected=false supported=false` 上报后约 1.5 秒，
  界面出现「当前音轨（DTS）无解码器，播放可能无声」，控件自动隐藏后提示仍在。
- AAC+DTS 双音轨样本：播放器选中 AAC，不出现提示；音轨菜单显示
  「● 音轨 1 · 立体声」与「音轨 2 · 5.1 · 无解码器」。
- 单测通过；测试用 `.debug` 包与样本视频已删除，release 安装与用户数据未受影响。

**备注**：
- ExoPlayer 遇到不支持的音轨会**直接不选中**。只看「已选中轨」会漏报，因此规则为：
  有选中轨时按选中轨判断；没有任何选中轨时，仅当全部音轨都不受支持才提示
  （避免把用户主动关闭音轨误报成解码失败）。
- 发现一个与本次改动无关的既有行为：同一队列内从详情页重新选择另一个条目时，
  播放器会继续播旧条目（`holdsQueue` 只比较队列、不比较选中项）。已在 0.12.1 修复。

### 2026-10-20 · 0.12.1：同队列重选条目修复

**背景**：播放页只用 `holdsQueue()` 判断控制器上挂的是不是同一批队列；同目录/同季的另一个
条目点播放时队列没变，于是跳过 `setMediaItems`，继续播旧条目。旋屏时的「不重设」保护与
「新选择要切换」被混为一谈。

**变更**：
- `PlaybackSelection`：新增选择令牌（`selectionToken` / `appliedToken` 与 `needsApply`）。
  每次显式 `set()` 自增令牌；`markApplied()` 记录已应用。
- `PlayerViewModel`：暴露 `needsSelectionApply` 与 `markSelectionApplied()`。
- `PlayerScreen`：`!holdsQueue || needsSelectionApply` 时才按新选中项设置队列并记录
  `apply selection` 日志；旋屏/配置变更令牌不变，不重设。
- `NowPlayingViewModel.syncSelection()`：从迷你条回到播放页时沿用控制器当前项，
  写回选择后立即标记已应用，避免按续播进度回退。
- 新增 `PlaybackSelectionTest`（5 条）。

**验证（SM-G998U1 / Android 15，release 0.12.1）**：
- 同队列 A→B：日志 `apply selection idx=1 of 26 (sameQueue=true)`，画面标题切到新条目。
- 播放中点「下一集」后触发配置变更（`wm density 640` → 恢复）：无 `apply selection`，
  标题与进度保持在新条目。
- 迷你条回到播放页：无 `apply selection`，从暂停位置继续，未回到队首或旧进度。
- 单元测试通过；0.12.1（versionCode 28）已覆盖安装。

**备注**：
- 令牌只在显式选择时自增；配置变更复用同一 ViewModel/令牌，因此不会重设，满足
  「旋屏不中断」的既有约束。

### 2026-10-20 · 片单完整链路验证

**背景**：路线图中「片单完整链路」长期待办，但详情页「加入片单」与片单页代码在此前版本
已实现，缺的是端到端真机验证。

**变更**：无产品代码变更（仅补充验证记录）。

**验证（SM-G998U1 / Android 15，release 0.12.1）**：
- 详情页 → 更多操作 → 加入片单 → 新建「TestList」→ 新建并加入：对话框关闭。
- 我的 → 片单 → 打开 TestList → 列表显示「千与千寻 · 2001 · 千与千寻 (2001).mkv」。
- 从片单播放：播放器队列 1 条，`apply selection idx=0 of 1 (sameQueue=false)`，
  MediaSession `PLAYING`，进度从 01:09 持续推进。
- 再次从详情页选择已有片单加入：同一作品按 `(playlist, work)` 幂等，不产生重复条目。
- 删除片单：确认后列表回到「暂无片单」，媒体文件与播放进度不受影响。
- 验证完成后已删除临时片单，用户数据恢复原状。

**备注**：
- 片单按**作品**存储，展示时取一个代表文件（路径字典序最小）。剧集条目从片单播放时
  只播该代表文件，不会自动串起全季；「片单增强」（整剧展开、排序、导入导出）仍列在 8.2。
- 片单页用 `container.library.items` 做海报/标题解析，属于全量读取；20k 库下建议改为
  `observeByPaths` 有界查询（已具备该接口），列入后续优化。

### 2026-10-20 · 0.13.0：多数据源并存（Phase 2）

**背景**：Phase 2 要求 WebDAV 多账号、本地、SMB、Emby/Jellyfin 同时可用；本轮**不做跨源合并**
（同一作品在不同源各自成卡，E2.5 推迟）。数据库身份层已支持多源，主要改造在应用层的
路径键、单凭据链路与界面。

**变更**：
- `SourcePaths`（`:core:model`）：旧 WebDAV 与本地保持裸路径；额外源写成
  `source:<sourceId>/<remoteKey>`。`ScannedFile` 增加 `legacyPath` / `providerItemId`，
  写层删除 `sourceOf()` 猜测、改为显式 sourceId。
- 多源注册表：`SourceConfig` + `SourceRepository`；凭据以 `sources_v2` 加密 JSON 存放，
  首次启动把旧的单源四键迁移为 id=`webdav`；`SourceDao` 维护 `sources` 表；
  支持按源启用/停用/删除（删除同时清理该源文件、目录缓存与队列，保留作品与用户数据）。
- 扫描：`ScanWorker` 按源类型分派（WebDAV / SMB / Emby / Jellyfin）；
  Emby/Jellyfin 走 `CatalogSyncEngine` 分页同步（伪文件 `item:<id>`）并写服务端元数据；
  `LibraryRepository` 汇总各源进度，401/429 只封锁对应源。
- 播放：`PlayerViewModel` 按路径前缀选源；`MultiSourceHttpDataSource` 以**根路径最长前缀**
  匹配并补 Basic/`X-Emby-Token`；`RoutingDataSource` 把 `smb://` 交给新增的
  `SmbDataSource`（SMBJ 随机读）；字幕发现按源分组。
- 刮削：额外源的 TMDB 键追加 `@<sourceId>`，同标题作品匹配也按源隔离，避免跨源自动合并；
  媒体服务器海报支持完整 URL（`TmdbClient.posterModel`）。
- 界面：数据源管理页（列表/启停/编辑/分享/删除影响统计）、添加页支持四种类型、
  启动路由改为看注册表、浏览页新增源选择器、设置页数据源入口。
- 构建：SMBJ 转为主依赖（APK 约 +1.4MB）；R8 对 SMBJ 引用的 Java GSS / javax.el 缺失类豁免。

**验证**：
- 单元测试：`SourcePathsTest`、`ScopedLogicalKeyTest`、`SourceUrlMatcherTest`、`SmbUrlTest`、
  `CatalogMappingTest` 等；app 91 条与 core:model 全部通过。
- Instrumentation（`.debug`）：`MultiSourceStoreTest` 验证同一相对路径来自两个源可共存、
  文件/作品身份不同；core:database 全部测试通过；`SourceRegistryTest` 验证注册表加密往返、
  按 id 删除与停用。
- 真机（SM-G998U1）：0.13.0 覆盖安装成功；升级后设置页显示「数据源 · 1 个」，
  原凭据完成迁移；数据源管理页、四类型添加表单可用。
- **双源扫描**：添加第二个 WebDAV 源后同时扫描，总计 **560 个视频（280 + 280）**；
  扫描完成自动进入刮削。删除第二个源（影响 280 个文件）后重新扫描回到 **280 个视频**，
  作品与播放进度保留。
- **双源播放**：浏览页源选择器可在两个源之间切换；从第二个源播放
  「云之彼端，约定的地方 (2004).mp4」状态为 `PLAYING`（进度推进、字幕正常），
  主源《千与千寻》同样 `PLAYING`；限定路径解析与最长根前缀鉴权均生效。
- **网络波动说明**：补验期间服务器链路间歇性重置（手机与 PC 端均复现，
  PC 侧同一账号 PROPFIND 曾返回 207 后又连接重置），期间出现过 `connection closed`
  与一次 Matroska 解析 NPE；网络稳定后同一文件正常播放，判定为链路问题而非代码回归。

**备注**：
- 浏览页目前只列 WebDAV 源；SMB 与媒体服务器通过媒体库扫描/同步进入（SMB 目录浏览后置）。
- Emby/Jellyfin 登录成功后把 AccessToken 作为 apiKey 加密保存，令牌过期需重新编辑并测试。
- 本地与旧 WebDAV 仍共享不带后缀的 TMDB 键，保持既有合并行为；额外源带 `@sourceId`。
- 双 WebDAV 源（扫描/播放/删除）已完成真机验收；SMB / Emby / Jellyfin 无测试环境，
  验收清单待环境到位后补跑。

### 2026-10-20 · 0.13.1：SMB 目录浏览与刮削重试

**背景**：0.13.0 的浏览页只列出 WebDAV 源，SMB 数据源虽可扫描/播放但没有目录浏览入口；
网络恢复后需要补跑一次刮削重试。

**变更**：
- 新增 `SmbBrowser`：浏览页按目录单次连接列举（认证失败映射为 `SmbAuthException`，
  界面进入 Blocked 不给重试入口）。
- `BrowserViewModel` 支持 WebDAV / SMB 两类文件源：源选择器列出全部可用文件源，
  WebDAV 保持 PROPFIND + 目录缓存，SMB 每次导航单次列举；播放队列仍写限定路径。
- 刮削重试：网络恢复后手动执行一轮（匹配 1 条，26 条未匹配，任务清空）。

**验证（SM-G998U1 / Android 15）**：
- 0.13.1（versionCode 30）覆盖安装；浏览页 WebDAV 源列表、目录进入、返回、刷新均正常。
- 刮削：本轮结束后状态「匹配 1 条 · 26 条没匹配上」；剩余失败属 TMDB 匹配精度问题，
  记入后续路线图。
- SMB 浏览路径因无 SMB 共享未做真机验证（代码与编译通过）。

**备注**：
- SMB 目录浏览使用每次连接的方式，未做连接复用；浏览是低频操作，保持简单。
- 「刮削重试」待办不再挂着：剩余 26 条是匹配失败而非任务积压。

### 2026-10-20 · 0.13.2：TMDB 匹配精度与首页首屏索引

**背景**：刮削有 26 条失败，旧匹配只取「第一条有海报的结果」，短标题/年份偏差时容易挑错；
E1.5 中首页 `RECENT` 首屏 p95 356.7 ms，是当前最慢查询。

**变更**：
- TMDB 匹配（`:core:network`）：
  - 新增 `TmdbScoring`：标题归一化（忽略空格/标点）、标题关联分（完全匹配/前缀/包含）、
    年份接近度、海报与热度加权；**无标题关联的候选直接淘汰**，不再盲取第一条。
  - 首轮无可接受结果时尝试标题变体（去掉「第N季 / Season N / S01」、方括号内容、冒号后副标题）
    与去年份补搜；结果按 id 去重。手动匹配仍返回全部候选。
  - `MetadataScraper` 在类型搜索无结果时**跨类型兜底一次**（电影↔剧集）。
  - `ScrapeEngine` 记录未匹配与成功匹配（原标题 → TMDB 标题/id），便于复盘。
- 首页首屏（v12 迁移）：
  - `works` 增加 `(added_at, id)` 索引；`WorksSort.RECENT` 改为
    `ORDER BY added_at DESC, id DESC`，首页分页可直接走索引且只读前 N 条。
  - 新增 `MIGRATION_11_12`（真实迁移）与 `MigrationV11ToV12Test`；`MediaDatabase.VERSION=12`。

**验证（SM-G998U1 / Android 15）**：
- 单元测试：`TmdbScoringTest` 7 条（精确匹配胜过热度和错误结果、短标题不误配、
  年份 ±1、原名匹配、标点忽略、变体、无关联淘汰）通过；迁移覆盖与全部单测通过。
- 20k 压测（`Stress20kTest`，仅 `.debug` 包）：

| 指标 | 0.13.0 前 | 0.13.2 | 变化 |
|---|---|---|---|
| 首页首屏 p95（RECENT） | 356.7 ms | **53.0 ms** | ≈6.7× |
| 整库滚动每页 p95 | 330.2 ms | **45.9 ms** | ≈7.2× |
| 整库滚动总耗时 | 50.1 s | **5.9 s** | ≈8.5× |
| 分类行 200 条 p95 | 314.5 ms | **34.4 ms** | ≈9.1× |
| 搜索 p95 | 94.7 ms | 72.5 ms | 波动 |
| DB 体积 | 20.3 MB | 20.6 MB | +0.3 MB |

- 真机 0.13.2 覆盖安装：v11→v12 迁移成功，媒体库、详情与播放不受影响。
- 刮削：新一轮「匹配 2 条 · 18 条没匹配上」。剩余未匹配集中在「奥斯卡最佳动画短片合集」
  的 1934–1995 年短片（如「乌龟与兔子」「公牛费迪南德」），TMDB 无对应中文标题，
  属翻译/片源命名问题，适合走「手动在线匹配」。受退避影响，后续轮次按计划自动重试。

**备注**：
- 匹配请求量：变体/跨类型只在首轮无可接受结果时触发，最多每任务多发几次；每日预算按任务计数。
- `RECENT` 同时间戳的条目现在按 id 倒序（原来按标题）；首页只承诺最近优先，同刻并列无业务含义。
- 匹配精度仍有上限：需要更多中文译名数据或手动匹配；本轮先保证不误配并记录失败标题。

### 2026-10-20 · 0.13.3：片单增强与 E2.5 跨源合并

**背景**：片单只支持「一条作品播一个代表文件」，长列表还会全量读取媒体库；多数据源虽已并存，
但同一作品在不同源仍各自成卡（0.13.0 刻意隔离），需要按架构 E2.5 合并并提供版本切换/失败切换。

**变更**：
- 片单增强：
  - 片单页海报/标题改为 `observeByPaths` 有界查询，不再读全库。
  - 剧集条目播放时展开为整剧分集（按季/集排序）；电影只播选中版本。
  - 新增排序（默认顺序 / 名称 / 年份）与「分享片单」（标题+年份+路径复制到剪贴板）。
- E2.5 跨源合并：
  - 刮削逻辑键去掉 `@sourceId` 后缀，扫描时按 title/year/type 跨源匹配；同一作品的多源文件
    合并为一个作品，文件保存在 `movie_versions`/`episode_versions`。
  - 兼容 0.13.0 历史后缀键：下次刮削时归一化并参与合并。
  - 新增版本查询 `movieVersionRows`/`episodeVersionRows`；选源顺序
    pinned > 源优先级 > 本地源 > 体积更大；详情页版本标签显示「来源 · 分辨率 · 体积」。
  - 播放页为每个条目准备其它版本的地址；非 401/429 的播放错误按顺序自动换源
    （401/429 绝不重试、也不切换）。
  - 电影详情不再把多个版本当连续剧集播；单文件短片仍保留同目录队列。

**验证（SM-G998U1 / Android 15）**：
- 单测/instrumentation：`PlaylistQueueTest`（剧集展开 3 集且有序、电影单版本）、
  `MultiSourceStoreTest`（两源同标题合并为一个作品、两个版本）、迁移覆盖与全部单测通过。
- 真机 0.13.3：添加第二个 WebDAV 源并扫描 → 文件总数 560，但《千与千寻》详情只有一个条目，
  版本为「Second · 2.2 GB」与「files.lovelev.eu.cc · 2.2 GB」；切到 Second 版本后
  `PLAYING`。删除第二源后复扫回到 280 个视频，作品与主源版本保留。
- 临时片单与第二源已清理。

**备注**：
- 跨源合并在扫描阶段按「标题 + 年份 + 类型」判断，未刮削的相似条目可能被合并；
  有 TMDB 身份的作品按 tmdbId 合并，无法识别的保持独立。
- 失败切换只覆盖「换一个版本再试」，未做网络分级；401/429 保持零重试。
- 片单导入未实现（本轮只做导出/分享）；版本 pin 的入口未做，但查询与排序已支持 pinned。

### 2026-10-20 · E1.7：UI 去容器与旋转状态迁移

**背景**：架构要求 composable 不碰 `AppContainer`，但 `SourcesScreen`、`HistoryScreen`、
`PlaylistsScreen`、`PlaylistDetailScreen` 仍没有 ViewModel，`MineScreen` / `SearchScreen`
只是拿容器造 VM，`NavHost` 也直接读容器；同时若干对话框与选择状态放在 `remember` 里，
旋屏即丢，`AddSourceScreen` 还会在重建时重新读取剪贴板覆盖用户输入。

**变更**：
- 新增 `AppViewModel`（主题、起始路由、播放/详情选择桥、重新登录）与 `UiFactories`
  （页面 ViewModel 工厂集合）；`MainActivity.onCreate` 保留容器，`HomeScaffold` /
  `AppNavHost` 不再接收 `AppContainer`。
- 新增 `SourcesViewModel`、`HistoryViewModel`、`PlaylistsViewModel`、
  `PlaylistDetailViewModel`；`MineScreen` / `SearchScreen` 改为接收 ViewModel。
- 旋转状态迁移：数据源详情/删除影响数、片单新建草稿/排序/分享提示、设置确认框、
  详情选中版本/季/展开、播放器锁定/信息/字幕搜索均进入 ViewModel。
- `SourceViewModel.applySharedOnce`：自动识别剪贴板每个 ViewModel 只应用一次；
  手动「粘贴分享配置」仍可重复应用。
- 抽出纯函数 `initialRoute`、`historyPlayQueue`、`sortPlaylistRows`、
  `buildPlaylistShareText`，便于单测。

**验证**：
- 单元测试 **102 条通过**（新增 11 条：初始路由、历史队列、片单排序/分享）；debug 与签名
  release 构建均通过。
- 真机 SM-G998U1 覆盖安装 release 包（versionCode 32）：启动落在媒体库；我的页扫描/刮削/补齐、
  搜索筛选与结果、设置页「1 个」数据源正常；数据源详情与删除对话框显示「影响 280 个文件」，
  横屏后对话框与影响数保留；播放历史列表与续看入口正常；片单新建时草稿名横屏保留，
  加入条目、排序菜单、分享提示、删除片单均通过；详情页打开与版本区正常；播放 15:06 文件
  position 推进至 1:09，字幕在线搜索预填「皆为爱」；日志无 FATAL。
- 验证用临时片单已删除，未改动数据库/迁移与 401/429 策略。

**备注**：
- 容器仍由 `MediaDockApp`、`ScanWorker`、`ScrapeWorker`、`PlaybackService` 使用；
  `ui/` 下仅 ViewModel 构造函数引用 `AppContainer`，屏幕 composable 已完全不接触容器。
- 未新增 Hilt/Robolectric/coroutines-test/Turbine 依赖；未提升版本号，未 push。

### 2026-10-20 · E1.6：拆分 LibraryRepository（627 行 → 门面 + 4 个协作者）

**背景**：`LibraryRepository` 同时承担扫描状态机（远程 WorkManager + 本地 SAF/MediaStore）、
刮削状态机与自动触发、TMDB 手动匹配、版本排序、查询委托和清库，约 627 行；架构 E1.6 要求
单类 <300 行、职责单一，同时保持现有行为不变。

**变更**：
- `LibraryRepository` 缩为门面（175 行）：只保留装配、查询委托、`cancelAll`/`reset`/
  `clearLibrary` 等生命周期入口。
- 新增 `ScanCoordinator`（270 行）：远程按源观察 WorkManager、本地 SAF/MediaStore 扫描、
  401/429 封锁集合、启动/取消/`resetBlocked`，远程与本地共用同一个 `ScanState`。
- 新增 `ScrapeCoordinator`（125 行）：刮削状态机、自动触发、手动开始/取消、凭据变更；
  扫描/刮削互斥通过 `isScanActive` / `isScanRequested` lambda 查询，不复制标志。
- 新增 `MetadataMatchService`（125 行）：手工匹配、单条/整剧重刮、片名覆盖。
- 新增 `PlaybackVersionRepository`（73 行）：版本查询与排序；纯比较器 `VersionRank`
  单独抽出。
- 新增 `LocalFileMapper`：本地文件 → `ScannedFile` 的映射原样搬出。
- `ScanState` / `ScrapeState` 移到 `LibraryStates.kt`（包名不变）；`PlayableVersion`
  改为顶层类型，只更新详情页两处 import。
- 清理无调用方的 `matchedCount` 与单路径 `saveOverride` 重载。
- 应用作用域仍由门面创建并注入两个 coordinator；WorkManager unique work 名、键与
  401/429 策略未改动。

**验证**：
- 单元测试 **109 条通过**（新增 7 条：本地文件映射 3、版本排序 4）；debug 与签名 release
  构建均通过。
- 真机 SM-G998U1 覆盖安装 release 包：我的页扫描完成「共 280 个视频」，自动刮削进入
  并结束（匹配 0 条 · 24 条未匹配）；详情页版本区显示
  「files.lovelev.eu.cc · 2.2 GB」；从详情播放 `PLAYING`，position 推进到 228s；
  日志无 FATAL。
- 为避免破坏真实数据，本轮未执行「清空媒体库」「退出登录」与删除数据源；
  对应代码路径仅为委托，`cancelScanFor` 的取消顺序在 `ScanCoordinator` 内保持不变。

**备注**：
- 扫描/刮削状态仍为应用作用域单例；ViewModel 调用方式未变，`container.library` 公开 API
  除死代码外保持一致。
- 未改数据库、迁移、Worker 名称与 401/429 策略；未提升版本号，未 push。

### 2026-10-20 · Phase 3A（E3.1）：设备能力、StreamInfo 探测与播放诊断

**背景**：Phase 0/1 已完成，Phase 2 真机验收暂缓。Phase 3A 要求先建立
`DeviceCapabilities` / `StreamInfo` 与诊断面板，不改变播放行为，为后续 HDR 直出、
降级软解与投屏提供事实基础。

**变更**：
- `:core:model` 新增 `StreamInfo`、`DeviceCapabilities`、`MediaSpecBadges` 与 HDR 分类规则。
- DB v13：新增 `stream_info_cache(path, size_bytes, schema_version, probed_at, payload_json)`，
  真实迁移 `MIGRATION_12_13`，提交 `13.json`；JSON payload 避免为可变长轨道再拆三张表。
- 抽出 `PlayableUrlResolver` 与 `AppContainer.playbackDataSourceFactory`，播放服务与探针共用
  同一套 URL 解析/鉴权；播放行为不变。
- `StreamProbeRepository`：按需探测、Room 缓存（schema v3、30 天 TTL、size 校验）、并发 2 +
  single-flight；失败返回 UNKNOWN，不阻塞播放，不写失败缓存。
- 探针用独立 `ExoPlayer` 做只读 `prepare()` 读取 `currentTracks` 的 Format（与播放器同一套
  解析器），不渲染、不播放、音量 0；复用 401/429 策略。
- 新增设置页「播放能力 / 诊断」：设备身份、显示 HDR/亮度/模式、视频/音频解码器、
  音频输出、最近播放的实际解码器/格式/丢帧/首帧；支持刷新与脱敏复制。
- 播放页 ⋯ 菜单新增「诊断」：当前流格式、实际解码器、丢帧、首帧、设备显示 HDR 对照；
  原「详细」对话框保持不动。
- 详情页规格徽标改由真实 `StreamInfo` 驱动：分辨率、HDR10/HDR10+/HLG/DV、Atmos（仅 JOC）；
  设备不支持时弱化显示；探测未知时不显示徽标，不再从文件名猜。
- `PlaybackService` 只新增 `AnalyticsListener` 写诊断 store；渲染器、轨道选择、缓冲、
  音频焦点与错误策略均未改。
- 应用显示名改为 **Mooring**（`strings.xml`、剪贴板标签、截图文件名、诊断标题）；
  applicationId/包名仍为 `com.lovelev.mediadock`。

**验证**：
- 单测：core:model **53** 条、app **123** 条全部通过；debug 与签名 release 构建通过。
- 真机 SM-G998U1 覆盖安装：v12→v13 迁移后媒体库仍为 280 个视频；能力页与 S4 记录一致
  （HDR10/HLG/HDR10+、无 DV；HEVC/VP9 硬解、AV1 软解；AC-3/E-AC-3/JOC 可解；
  TrueHD/DTS 无解码器）。
- 详情页真实探测返回 `video=1 audio=1`，徽标显示 **1080P**；二次打开命中 Room 缓存。
- 播放后设置页「最近播放」显示 `c2.qti.hevc.decoder` / `c2.sec.flac.decoder`、
  2560×1440、丢帧 17、首帧已渲染；播放页「诊断」菜单项存在。
- 日志无 FATAL；401/429 零重试策略未改。

**备注**：
- 显示名改动不涉及包名/签名/更新链；GitHub 仓库与项目文档仍使用 MediaDock。
- StreamInfo 缓存 schema 版本当前为 3；旧探测结果会在升级后自动失效。
- SMB/Emby 探针复用同一数据源工厂，代码路径已接入，但无环境实测。

### 2026-10-20 · Phase 3B（E3.2）：HDR/刷新率/音频策略与诊断

**背景**：3A 已建立能力与流信息。3B 原计划 HDR 直出、刷新率匹配、音频直通/下混。核实
Media3 1.11.1 后确认：HDR 直出与帧率匹配在默认路径已生效，音频直通由系统按输出设备自动决定；
`preferredDisplayModeId` 在手机上通常被忽略，tunneling/自定义 AudioSink 风险高且无验收环境。
因此采用保守方案：不强制底层路径，补计划/实际诊断与两个安全开关。

**变更**：
- `core:model` 新增 `PlaybackPlan`/`PlaybackPlanInput`/`PlaybackPlanner`，按设备能力与设置
  计算 HDR 直出/SDR/不支持、音频直通/解码/无解码器、硬件解码与声道上限，附可解释 notes。
- 新增 `PlaybackSettingsStore`（SharedPreferences，服务可同步读取）：
  帧率匹配 自动/关闭、音频声道上限 自动/5.1/立体声；默认等于原行为。
- `PlaybackService` 仅在创建时读取上述设置：`setMaxAudioChannelCount` 与
  `setVideoChangeFrameRateStrategy`；渲染器、LoadControl、AudioAttributes、音频焦点、
  错误策略均未改。设置变更下次播放生效。
- `DeviceCapabilityProbe` 新增 `passthroughMimes`：用 `AudioCapabilities.isPassthroughPlaybackSupported`
  对固定 MIME 集合探测；能力页显示“直通/仅解码”。
- `PlaybackDiagnostics` 增加结构化字段（HDR、宽高、帧率、音频 MIME/声道）；
  `DiagnosticsAnalyticsListener` 填充，`CapabilitiesReport` 展示。
- 播放页「诊断」增加计划 vs 实际：计划 HDR/音频/解码/帧率匹配、当前刷新率、实际解码器/格式/丢帧。
- 设置页新增「播放」区：帧率匹配、音频声道上限，注明下次播放生效与上限只约束轨道选择。

**验证**：
- 单测：core:model **58** 条、app **125** 条全部通过；debug 与签名 release 构建通过。
- 真机 SM-G998U1：S4 `Gamma.Movie.2022.mp4`（4K60 HDR10）诊断显示
  **计划 直出·HDR10 / 硬件 / 60fps / 匹配开**，实际解码器 `c2.qti.hevc.decoder`，
  **丢帧 0**、首帧已渲染，当前刷新率 120Hz；EAC3 样本在扬声器上为解码。
- 发现并修复真实 bug：Media3 的 `COLOR_TRANSFER_HLG=7` 与 Android `MediaFormat` 的 `HLG=3`
  不同，导致 HLG 样本初次被计划为 SDR；已在 `FormatSpec` 使用 Media3 常量，并在
  `StreamExtractor` 边界归一化。修复后未再复测 HLG（用户要求停止设备验证）。
- 刷新率：60fps/24fps 内容下显示仍为 120Hz；120 是 24/60 的整数倍，属于无缝匹配，
  系统未暴露面板 VRR 实际值。未强制 `preferredDisplayModeId`。

**备注**：
- 音频直通/Atmos、HDMI/eARC、DV 直出未验证，也不承诺；等外部设备矩阵。
- 未启用 tunneling、未自定义 AudioSink、未做 tone mapping（3C）。
- 应用版本 0.14.0（versionCode 34）；显示名仍为 Mooring。

### 2026-10-20 · 0.14.0 发布

**发布**：`versionCode 34` / `versionName 0.14.0`；签名 APK 5,091,876 B，
sha256 `1f8437a6104e7e3d77b0b0e42bee5bf1fa9baec32544a55e8a62fd82bf6724ee`
（本地与服务器一致）。站点 `/var/www/portal/mediadock/` 已更新
`MediaDock-0.14.0.apk`、`version.json` 与 `index.html`；服务器侧 HTTPS 验证
`version.json` 为 vc34、APK HTTP 200 / `content-length: 5091876` /
`application/vnd.android.package-archive`。本机 curl 受网络重置影响，改用服务器回环验证。
GitHub `master` 与 `v0.14.0` tag 推送后由 CI 构建并发布 Release。

### 2026-10-21 · Phase 3C（部分）：FFmpeg 软解回退

**背景**：3C 原计划色调映射、DV 基础层与 FFmpeg 软解。本轮只接入 FFmpeg 软解回退；
按用户要求**不做真机验证**。

**变更**
- 依赖 `io.github.anilbeesetti:nextlib-media3ext:1.11.1-0.16.0`（GPL-3.0，ADR-027；版本前缀须与 media3 一致）。
- `PlaybackService` 改用 `NextRenderersFactory`，扩展渲染器模式为 `ON`：FFmpeg 排在平台渲染器之后，
  只在平台没有可用解码器时接管。未使用 `PREFER`；硬解优先级与 401/429 策略均未改动。
- `DeviceCapabilityProbe` 把 FFmpeg 能解的 MIME 并入设备能力（只补平台缺失的 MIME）。
  不做这一步，播放计划与诊断会把 TrueHD/DTS 判成「无解码器，将静音」，与实际不符。
  MIME 表与 `FfmpegLibrary.getCodecName` 一一对应，升级 nextlib 时必须同步核对。
- `packaging.jniLibs.useLegacyPackaging = true`：四 ABI 的 FFmpeg 原生库压缩存放，
  APK 40.9 MB → **19.8 MB**。未裁剪 ABI，保留 MuMu（x86_64）可安装性。

**能力范围（更正旧文档预期）**
- 视频：H.264 / HEVC / VP8 / VP9 / AV1 / MPEG-1 / MPEG-2。
- 音频：AAC、MP3、AC-3、E-AC-3（含 JOC）、TrueHD、DTS / DTS-HD、Vorbis、Opus、FLAC、ALAC、AMR、G.711。
- **不含 RMVB（RV40）与 DivX / MPEG-4 ASP**；Media3 也没有 RealMedia 容器解析器。
  旧文里「RMVB / AVI 可播」的预期不成立：AVI 容器能识别，但常见老编码解不了；RMVB 连容器都不认。
- S21 Ultra 上平台已有 H.264/HEVC/VP9 硬解与 AV1 软解，实际收益主要是
  **TrueHD/DTS 音频从静音变为可解**；视频软解只在平台缺解码器的设备上生效。

**验证**
- `:core:model:test` 58 条 + `:app:testDebugUnitTest` 125 条，全部通过。
- `assembleRelease` 成功；APK 标签仍为 `Mooring`，包名 `com.lovelev.mediadock`，
  版本 0.14.0 / vc34 未变；包含四个 ABI 的 `libavcodec.so` 与 `libmedia3ext.so`；
  R8 mapping 中 `FfmpegLibrary` 保持原名（native 方法 keep 规则生效）。
- **未做真机验证**（用户明确要求跳过），因此「DTS/TrueHD 实际出声」「软解画质与功耗」
  均未实测，下次装包时应补。

**备注**
- 运行时硬解失败不会自动切到 FFmpeg（nextlib 的 `ON` 只解决「没有平台解码器」）。
  要覆盖这类失败需引入 `DecoderManager` 手动切换与错误恢复策略，列入后续工作。
- 包体由 5.1 MB 增至 19.8 MB；若需更小，可只保留 `arm64-v8a` + `x86_64` 两个 ABI（约减半），
  代价是放弃 32 位设备。

### 2026-10-21 · Phase 3D（部分）：字幕字号与画中画

**背景**：3C 剩余项（色调映射、DV 基础层、运行时切软解）暂缓后转入 3D。
首版曾提供字号/颜色/背景/描边四组覆盖，真机发现它会把带特效的 ASS 字幕换成系统默认字体、
并抹掉原有底色。按用户要求收敛为「只改字号」，带特效的字幕一律不碰。

**变更**
- 字幕：设置页新增「字幕」→「字号」（小/标准/大/特大），存 SharedPreferences。
- 应用：仅当选中字幕不是 ASS/SSA 且字号不是「标准」时，对 `PlayerView.subtitleView`
  关闭内嵌字号并设置 `DEFAULT_TEXT_SIZE_FRACTION × scale`；字体、颜色、底色、描边全部沿用字幕文件本身。
- 特效字幕：当前选中轨为 `MimeTypes.TEXT_SSA` 时完全不改 `SubtitleView`（含字号），原样渲染。
- 画中画：manifest 增加 `supportsPictureInPicture` 与 `configChanges`（避免进出小窗重建 Activity）；
  播放页「⋯」菜单新增「画中画」；Android 12+ 播放中按 Home 自动进入；进入后隐藏全部自绘控件；
  离开播放页时关闭自动进入，避免其它页面回桌面也缩成小窗。比例 16:9。

**验证（真机 SM-G998U1）**
- 画中画：播放中按 Home，日志 `pip mode -> true`，窗口树中 MainActivity 变为 792×446 小窗；
  小窗内播放位置继续推进（237s → 273s）；回到全屏记录 `pip mode -> false`。
- 字幕：修复后「中文特效by晓之绿」恢复黑底白字与原有字体（截图确认）；
  设置页只有字号一项，选项可持久化（重进设置仍保持）。
- **字号放大实测**：同一「简体中文」轨，标准字号字幕字形高 67 px，特大 100 px（×1.49，
  与 1.5 倍一致）。截图取自真机横屏 3200×1440。
- 单测 **126 条**通过；`assembleRelease` 成功；APK 标签 Mooring、`com.lovelev.mediadock`
  0.14.0 / vc34 未变；`aapt2 dump xmltree` 确认 `supportsPictureInPicture=true`。

**备注**
- **特效字幕判定必须看 `Format.codecs`，不能看 `sampleMimeType`**：Media3 把字幕轨的
  `sampleMimeType` 统一成 `application/x-media3-cues`，原始格式只保留在 `codecs`
  （实测 ASS 为 `text/x-ssa`，SRT 为 `application/x-subrip`）。首版按 MIME 判断失效，
  已在真机日志中发现并修正。
- 新增 `logTextTracks`：每次轨道变化打印字幕轨的 mime/codecs/label/选中状态，便于排查。
- `configChanges` 带来的旋转行为变化未系统回归；设备旋转锁为横屏时，播放页与其它页都跟随锁定。
- 版本号未提升，改动待统一发版。

### 2026-10-21 · 0.15.0 发布

**版本**：`versionCode 35` / `0.15.0`；签名 APK **19,793,481 B**，sha256
`98bbac8cffb8c560e5b2c3778c7bc9f86b3fd5ee2c8f4b57243b952bf5e5cec1`（本地与服务器一致）。
内容：FFmpeg 软解回退、字幕字号、画中画。

**站点**：`/var/www/portal/mediadock/` 已更新 `MediaDock-0.15.0.apk`、`version.json`、`index.html`
（旧页备份为 `index.html.bak-0.14.0`）。服务器侧 HTTPS 验证：`version.json` 为 vc35；
APK HTTP/2 200 / `content-length: 19793481` / `application/vnd.android.package-archive`；
页面显示「最新 0.15.0」。

**GitHub**：`master`（96dc50c）与 `v0.15.0` 已推送。本机到 `github.com:22` 被代理拦截
（连接被 `198.18.0.170` 关闭），改用 `ssh.github.com:443` 推送，未修改仓库 remote 配置；
CI 按 tag 构建 Release。

**真机**：release 包 `install -r` 覆盖安装成功，版本 0.15.0 / vc35，数据保留、启动正常。

**备注**：发布页仍使用 MediaDock 品牌名（应用显示名自 0.14.0 起为 Mooring），本次未改；
APK 体积由 5.1 MB 增至 18.9 MB，来源是四 ABI 的 FFmpeg 原生库。

### 2026-10-21 · UI 观感批次 1（未发版）

**背景**：功能项暂缓，先提升直观观感与手感。

**变更**
- 新增 `ui/common/Interaction.kt`：卡片按压缩放（0.97 弹簧）+ 轻触觉（CONTEXT_CLICK）；
  新增 `ui/common/EmptyState.kt`：淡图标 + 最短标签的空状态。
- 应用按压/触觉：首页推荐大卡、分类海报卡、收藏网格海报。
- 底栏：Tab 切换加轻触觉，选中图标 1.08 倍弹簧缩放；搜索按钮同样加轻触觉。
- 首页首屏加载：细进度条改为骨架屏（大卡 + 两排海报呼吸占位）。
- 空状态统一：收藏、播放历史、片单列表、片单详情。

**验证**
- 编译通过；单测 126 条通过；release 包覆盖安装（vc35，与已发布 0.15.0 同号，仅本地）。
- 真机：首页正常渲染、推荐卡点击可进详情、底栏切换与收藏空状态正常，无崩溃。

**备注**
- 本轮改动未提交、未发版；正式发版需提升 versionCode。
- 骨架屏只在首屏刷新且无缓存时出现，网络快时可能一闪而过。

### 2026-10-21 · UI 观感批次 2（未发版）

**背景**：继续观感与交互打磨，功能项不动。

**变更**
- 详情页：
  - 顶部大图改回「竖版海报优先」，按 2:3 铺满宽度（约 2/3 屏）、`Fit` 不裁剪；
    修复横版图铺满竖屏被放大，以及两侧留缝、同色延伸断开的问题。
  - 视差：海报随滚动以 0.08 系数位移；在绘制阶段读取 `ScrollState`，不触发整页重组。
  - 标题区入场淡入 + 上移（380ms）。
  - 播放按钮加轻触觉；剧集卡片按压 0.97 + 触觉。
- 播放页：
  - 双击左/右半屏回退/快进 10 秒，带轻触觉与「±10 秒」提示。
  - 长按 450ms 进入 3× 快进，松手还原原倍速。
  - 单击切换控件延后到双击窗口结束，双击时不会先闪一下控件。
- 设置 / 搜索：
  - 设置选项与主题选择颜色过渡 180ms；分组卡片加 1dp `tonalElevation`。
  - 搜索框聚焦时底色与描边颜色过渡；筛选胶囊选中色/缩放过渡 + 轻触觉。
  - 搜索结果首屏改为三列骨架屏；搜索卡片加按压反馈。
  - 筛选行移入可滚动网格：**搜索框固定，标签随结果一起上滑**（用户要求，给结果让空间）。
- 全局：标题类字距统一收为 0（中文字形更紧凑）。

**验证**
- 编译通过；单测 126 条通过；release 包覆盖安装（vc35，仅本地）。
- 用户自测：双击快进、长按快进通过；详情页海报完整且无缝（截图核对）。

**备注**
- 未提交、未发版；正式发版需提升 versionCode。
- 视差为位移余量把图放大 1.12，属轻微裁切；若观感仍像「放大」，把该系数置 0 即可关闭视差。

### 2026-10-21 · UI 观感批次 3（未发版）

**背景**：迷你播放条、列表行、播放控件与深色模式的细节打磨。

**变更**
- 迷你播放条：进度条 3dp 圆角、500ms 动画跟随；整条高度 56dp；按钮触控区 48dp；
  点击条身/播放/停止都有轻触觉；出现与消失用 expand/shrink + 淡入淡出
  （停止播放标题变 null 时也会收起，而不是瞬间塌掉）。
- 片单 / 播放历史：条目改为圆角卡片（`surfaceVariant` + 1dp tonal），12dp 边距 + 8dp 间隔，
  去掉分隔线；整卡按压 0.97 + 轻触觉。
- 播放控件：进度条拖动吸附到整秒；拖动时轨道 3→6dp、端点圆点淡入；按下进度条有触觉；
  底部播放/上一集/下一集按钮按压缩放 0.9 + 触觉。
- 深色模式：两套配色显式补齐 `outlineVariant` 与 `surfaceContainer` 五档面色，
  分隔线与卡片层次在深色下可辨。

**验证**
- 编译通过；单测 126 条通过；release 包覆盖安装（vc35，仅本地）。
- 由用户真机验证。

**备注**
- 未提交、未发版；正式发版需升 versionCode。

### 2026-10-22 · 分享文本携带 TMDB 与字幕源（未发版）

**背景**：朋友拿到分享配置后，刮削与在线字幕还要再问一遍密钥。要求分享文本直接带上。

**变更**
- `ShareConfig.build` 增加可选参数：`tmdbSecret`、`subtitleApiKey` / `subtitleUser` /
  `subtitlePassword`，有值才写对应行（`TMDB 密钥：` / `字幕 API Key：` / `字幕账号：` / `字幕密码：`）。
- 新增 `ShareConfig.parseSubtitle`：至少要有 API Key 才返回字幕凭据。
- 解析数据源前先剥掉字幕行 —— 否则「字幕账号」会被「账号」正则当成 WebDAV 账号。
- `SourcesViewModel.shareText` 改为 suspend，分享时读取 TMDB 与字幕凭据一并拼入。
- `SourceViewModel.applyShared`：导入时同时写入 TMDB 与字幕凭据；字幕缺的字段沿用本机已有值，
  不会把账号密码冲掉；提示注明包含哪些。
- 单测新增 3 条（生成/解析、账号不串味、无字幕源返回 null）。

**验证**
- 单测 **129 条**通过；release 包覆盖安装（vc36 本地）。

**备注**
- 分享文本现在含明文密钥，转发需谨慎；界面未加额外警告（用户要求直接分享）。
- 0.15.1 的服务器发布仍待完成：2026-10-21 起 VPS SSH 在密钥交换阶段被断开（网页 443 正常），
  GitHub master 与 tag v0.15.1 已推送。

### 2026-10-22 · 0.15.2 发布

**版本**：`versionCode 37` / `0.15.2`；签名 APK **19,806,317 B**，sha256
`cd3f5bf3c47381588c4a0c47e238c41cced6802f0135de3c2ff045e3f176afea`（本地与服务器一致）。
内容：分享文本携带 TMDB 与字幕源，加上 0.15.1 的 UI 观感批次（0.15.1 未上传站点，直接发 0.15.2）。

**站点**：`/var/www/portal/mediadock/` 已更新 `MediaDock-0.15.2.apk`、`version.json`、`index.html`
（旧页备份 `index.html.bak-0.15.0`）。HTTPS 验证：`version.json` 为 vc37；APK HTTP/2 200 /
`content-length: 19806317` / `application/vnd.android.package-archive`；页面显示「最新 0.15.2」。

**GitHub**：`master`（9f2b7ed）与 `v0.15.2` 已推送（走 `ssh.github.com:443`）。

**真机**：release 包覆盖安装成功，0.15.2 / vc37。

**SSH 故障与处理**：2026-10-21 起 VPS SSH 在密钥交换阶段被断开，网页 443 正常。
日志显示大量 `[preauth]` 爆破连接，`MaxStartups`（默认 10:30:100）被打满后 sshd 丢弃新连接。
经 Azure「运行命令」（无需 SSH）写入 `sshd_config.d/99-anti-flood.conf`：
`MaxStartups 100:30:200`、`LoginGraceTime 20`，reload 后恢复。fail2ban 当前封禁 0。

---

## 8. 待办

### 8.1 近期任务

1. **第二季验证**：已完成（2026-10-18）——「葬送的芙莉莲」38 集（库中 38），第 2 季 29–38 正常。
2. **刮削重试**：已完成（2026-10-20）——网络恢复后手动执行一轮：匹配 1 条、
   26 条未匹配（任务清空）。剩余失败属 TMDB 匹配精度问题（短标题/无年份），列入后续优化。
3. **片单完整链路**：已完成验证（2026-10-20）——详情加入片单（新建/选择已有）、
   片单列表、从片单播放、删除片单全部真机通过；临时片单已清理。剧集只播代表文件，
   整剧展开列入「片单增强」。
4. **S2**：已完成（2026-10-18）——目标机不支持 FTS5；FTS4 20k 条 p95 3.44 ms，选定 FTS4 + `search_text`。
5. **S3**：已完成评估（2026-10-18）——SMBJ 选定；DLNA 自研，jUPnP 因 Jetty 不采用；
   真机投屏与 SMB 实网连接待有设备/共享后补验。
6. **S4**：已完成（2026-10-18）——S21 Ultra 支持 HDR10/HLG/HDR10+，无 DV；TrueHD/DTS 无解码器；
   nextlib 为 GPL-3.0 且 +7.5 MiB/arm64，FFmpeg 视频软解后置（ADR-026）。
7. **0a**：已完成（2026-10-19）——v9 数据层 + 兼容 VIEW；真机迁移 280 文件 / 127 作品 / 139 集，
   shadow compare 0 差异；回滚演练通过。
8. **0b**：已完成（2026-10-19）——`:core:network` / `:core:data` 骨架，查询与 TMDB 匹配抽取；
   依赖方向单向，0.7.0 真机播放验证通过。
9. **Phase 1 E1.1**：已完成（2026-10-19）——WorkManager 增量扫描、目录缓存、
   断点续跑；真机 280 文件，增量 1 目录/0 文件，取消后恢复至 280。
10. **Phase 1 E1.2**：已完成（2026-10-19）——`scrape_tasks` 持久队列、ScrapeWorker、
   每日预算与指数退避；真机 13 条任务入队并保留，TMDB 网络不可达时按退避重试。
11. **Phase 1 E1.3**：已完成（2026-10-19）——works 分页海报墙（30/页），
   推荐/分类行有界查询；真机 127 作品与系列详情验证通过。
12. **Phase 1 E1.4**：已完成（2026-10-19）——FTS4 中文搜索、SQL 筛选与分页；
   真机索引 127/127，中文/演员/改名/筛选测试通过。
13. **Phase 1 E1.5**：已完成（2026-10-19）——`Stress20kTest` 播种 20k 文件 / 5k 作品；
    首屏 p95 356.7 ms、搜索 p95 94.7 ms、DB 20.3 MB、PSS 峰值 255.7 MB，全部达标。
    **Phase 1 至此完成。**
14. **禁止事项**：不得用同包名/正式签名跑 connected 测试（会卸载真实包，见问题 76）。
15. **运维记录**：OpenList `movie1`–`movie8` 的 `base_path` 已改为 `/夸克/影视`；
    修改数据库后需重启 OpenList 才生效。
16. **同一队列重新选择条目**：已修复（2026-10-20，0.12.1）——选择令牌区分新选择与配置变更；
    真机验证 A→B 切换、配置变更不重设、迷你条恢复不回退。详见同名条目。
17. **多源双源真机验收**：已完成（2026-10-20）——双源扫描 560（280+280）、
    第二源与主源播放 `PLAYING`、删除第二源后复扫回到 280；详见 0.13.0 条目。
18. **SMB / Emby / Jellyfin 真机验收（待环境）**：代码与单测完成；需 SMB 共享与媒体服务器后
    按「添加源 → 扫描/同步 → 播放 → 删除源」验收。
19. **SMB 目录浏览**：已完成（2026-10-20，0.13.1）——浏览页支持 SMB 源；无共享环境，
    真机验收待补。

### 8.2 功能路线图（按优先级）

1. **播放格式兼容**：FFmpeg 视频软解经 S4 评估后**后置**——官方 Media3 扩展仅音频，
   nextlib 为 GPL-3.0（+7.5 MiB/arm64）。若确需 RMVB 等格式，需自建 LGPL 视频解码器
   或接受 GPL；当前优先补「无音频解码器」提示。
2. **多数据源并存**：已完成（2026-10-20，0.13.0）——WebDAV 多账号、SMB、Emby/Jellyfin 代码接入；
   **E2.5 跨源合并已完成（0.13.3）**：同作品合并、版本切换与播放失败切源。
   待补：SMB/媒体服务器环境验收、版本 pin 入口、失败切换的更多实网场景。
3. **刮削深度**：分集标题与剧照已完成；后续可补预告片、演员页、季海报。
   豆瓣评分无公开 API，暂以 TMDB 评分代替。
4. **播放体验**：字幕样式（字号 / 颜色 / 背景 / 延迟微调）、画中画、投屏
   （DLNA / AirPlay / Chromecast）、HDR 元数据处理。
5. **跨设备同步**：播放进度、收藏、片单；优先采用导出 / 导入配置或轻量账号，
   避免引入云依赖。
6. **片单增强**：部分完成（0.13.3）——剧集整季展开、排序、分享导出、海报有界查询已上线；
   导入、手动拖拽排序、从片单批量刮削待做。
7. **媒体库**：增量扫描、缩略图预取、更多筛选维度（演员 / 国家）。
8. **TMDB 匹配精度**：部分完成（0.13.2）——打分/标题变体/跨类型兜底/原名匹配已上线；
   剩余 18 条为 1934–1995 年奥斯卡短片（TMDB 无中文译名），建议手动匹配；
   后续可考虑引入译名数据或按片长/投票数二次确认。
9. **工程**：R8 keep 规则复核与启动性能测试。
10. **明确不做**：Android TV / 遥控器、网盘聚合（除非确有需要）。

### 8.3 工程与质量

- 性能基线：E1.5 建立 20k 基线与 `Stress20kTest`；0.13.2 已通过 `works(added_at,id)`
  索引把首屏 p95 从 356.7 ms 降到 53.0 ms、滚动每页 330.2 → 45.9 ms。
- UI 回归：关键页面截图对比（可选）。
- 崩溃上报：本地日志已覆盖，远端上报未实现。
- 文档：新增条目按「背景 / 变更 / 验证 / 备注」书写。

---

## 9. 命令速查

```bash
# 构建
cd /e/code/MediaDock
export JAVA_HOME="E:\code\tools\jdk21"; export GRADLE_USER_HOME="E:\code\.gradle"
./gradlew :app:assembleDebug

# 跑文件名解析的单测（本机可跑，不需要真机）
./gradlew :app:testDebugUnitTest
# 报告：app/build/reports/tests/testDebugUnitTest/index.html

# 安装至设备
E:/code/tools/android-sdk/platform-tools/adb.exe devices -l
E:/code/tools/android-sdk/platform-tools/adb.exe install -r app/build/outputs/apk/debug/app-debug.apk

# 看日志
E:/code/tools/android-sdk/platform-tools/adb.exe logcat -s MediaDock:* AndroidRuntime:E

# VPS 侧看 WebDAV 状态（用户执行）—— 命令含服务器地址，见长期记忆 / 服务器手册
```

---

## 附录 A：需求来源与取舍（2026-10-15 用户给的「想法」文件）

用户给过一份偏 ISO 标准/设计规范的想法文档，说「不用都参考，提取有用的部分」。
下面记下**采纳了什么、明确拒绝了什么及理由** —— 免得以后有人照着那份文件重做一遍。

### 已满足项

- 凭据加密存：Keystore + AES-GCM （Stage 1 就做了）
- 错误信息不得仅为原始错误码：所有 `WebDavException`/`TmdbException` 均转换为用户可读信息
- 深色沉浸式界面 + 界面退居幕后 （深墨底主题，播放页全黑）
- 对比度 ≥ 4.5:1：**算过了**，明暗两套配色里最低的一对（浅色 primary/surface）是 5.19:1，
  全部达标，不需要改。
- 触控热区 ≥ 48dp：列表行 14dp 上下内边距 + 正文行高 ≈ 48dp，海报块 > 104dp，
  M3 的 TextButton 自带 48dp 最小高度
- 海报 `contentDescription` 已设（用解析出的片名）
- Range 续传/seek：ExoPlayer 本来就这么干（验收项就是拖进度条秒跳）
- 硬解失败降级软解：media3 自动处理，无需自行实现

### 明确不采纳

- **「摒弃后台常驻 Service，改用 WorkManager」** —— 对本项目是错的。后台**播放**必须用
  `MediaSessionService`（Android 上媒体播放的正解）；WorkManager 是给可延迟的后台同步用的。
  而且扫描按硬约束本来就只能用户手动触发，不需要 WorkManager。
- **「强制启用 HTTPS」** —— 会打断局域网 http NAS 的场景。现在是「鼓励 HTTPS，
  但 `network_security_config` 放行明文」，保留。
- **ISO 12207 那一整套**（RTM 需求追踪矩阵、SRS、Issue ID、Changelog、SonarQube、
  CI/CD 门禁、覆盖率 ≥80%）—— 个人单模块项目上这套流程的成本远大于收益。
  保留其中真正便宜的 20%：**决策记录**（本文件）+ **纯逻辑写单测**（文件名解析已有 13 个）。
- **跨端一致性（含电视端）** —— 已锁定只做手机/平板，不做 TV。
- **Sardine-Android 的 License 审查** —— 本项目没用它（只有 JitPack，拉取不稳）。

### 保留待办

见下面附录 B 的功能清单。

---

## 附录 B：功能清单与成本分级（来自同一份「想法」文件）

按「改动量 / 收益」排的，**不是承诺**，只是下次挑活时的依据。

### 低复杂度（约 1–2 小时）

- **倍速播放** 已做（Stage 6）
- **自动播放下一集** 已做（Stage 6，同目录作队列）
- **危险操作二次确认** 已做（Stage 7 的「清空媒体库」「退出登录」）
- **截图** 已做（播放页「⋯」菜单里，`PixelCopy` 取一帧存相册）
- **底部/Tab 结构调整**：外壳只有 `MainActivity.HomeScaffold` 一个文件，
  新增一个 Tab（例如「我的」）约需 5 行代码。**顶部目录栏与左右滑动页同属增量改动，主要集中在壳层。**
- **危险操作二次确认**：Stage 7 的「清缓存/退出登录」照做即可。

### 中等复杂度

- **分季分集 + 一键播整剧**：数据早就有了（`season`/`episode` 已落库），
  差的是按剧集归并的界面 + 详情页。建议先做 `ui/detail/`，否则点海报直接播体验很跳。
- **字幕样式调节**（位置/颜色/边框/背景）：media3 `SubtitleView` 支持，还未实现。
- **音频均衡器**：Android `Equalizer` + ExoPlayer 的 audioSessionId，能做但要想清楚 UI 放哪。

### 高复杂度 / 低优先级

- **在线字幕搜索**：要接第三方字幕站（如 OpenSubtitles），要额外账号、限流和合规考虑，
  量级和当初接 TMDB 接近。
- **GIF 导出**：要引入编码器（FFmpeg 或自写），体积和复杂度都不小。
- **OCR 文字识别**：要 ML Kit。
- **视频调色**：要 GL shader / `Effect` 管线。

> 优先级建议：**先完成「播放体验」相关项（倍速、自动下一集、进度记忆）**，
> 上述项目实现成本低、相互关联且使用频率高；分季分集应与详情页同步实施；
> OCR、调色与 GIF 类功能暂缓。

---

## 附录 C：协作与交付约定

- **稳定优先**：修改配置时，稳定性与安全性优先于整洁性；先备份，修改后必须验证，不确定时先执行只读诊断。
- **文案**：界面不得复述用户已可见的状态；空状态使用最短标签；真实信息（上限、快捷键）应保留。
- **记录**：决策、结果、问题与下一步应写入项目文件（本文件），不记录流水账。
- 交流语言：中文。
