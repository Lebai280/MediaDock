# MediaDock — 交接文档

> **新会话请从这里读。** 决策 / 结果 / 坑 / 下一步全部在这一个文件里，不记流水账。
> 最后更新：2026-10-16（已纳入 git，远程 `Lebai280/MediaDock` 私有仓库）
>
> 📋 `NEXT_SESSION.md` 是一段**已经写好、可直接整段复制**的交接提示词（含构建命令与下一步），
> 开新窗口时把它贴进去即可。本文件是细节归档。

### ⚠️ 新窗口请开在 `E:\code`，不要开在 `E:\code\MediaDock`

长期记忆按**项目目录**分桶（`~/.claude/projects/<slug>/memory/`）。本项目的记忆桶是
`E--code`（对应 `E:\code`）。若在 `MediaDock` 子目录下开新窗口，会新建 `E--code-MediaDock`
桶，**已有的记忆一条都加载不到**（含用户的协作偏好）。
好在工具链与构建配置都写死在项目文件里，与窗口开在哪无关。

---

## 0. 一句话

**MediaDock** 是用户自建的 Android 影音播放器：填入 WebDAV 地址 + 账号密码 → 浏览目录 → 播放视频，
并带海报墙媒体库（TMDB 刮削）。数据源是用户自己的 **OpenList WebDAV**。
用户原先用 VidHub，现在要一个自己的前端。

> 🔒 **运维细节（WebDAV 地址 / 服务器 / SSH / 账号）刻意不写进本仓库** —— 本文件要上 GitHub。
> 需要时去**长期记忆**（`vps-proxy-node` 那条）或用户自己的服务器手册里找。

- 项目路径：`E:\code\MediaDock`
- 包名 / applicationId：`com.lovelev.mediadock`
- 当前版本：`0.3.0`（versionCode 3）—— 首个**正式签名**的发布版

---

## 1. 现在到哪了

| 阶段 | 状态 |
|---|---|
| **Stage 0** 构建链打通 | ✅ 完成 |
| **Stage 1** 添加数据源 + WebDAV 连通性测试 | ✅ 完成，真机验证通过 |
| **Stage 2** 目录浏览 | ✅ 完成，真机验证通过 |
| **Stage 3** 播放 | ✅ 完成，真机验证通过 |
| **Stage 4** 媒体库 | ✅ 完成，**已装机验证**（扫描 92 个视频） |
| **Stage 5** 刮削（TMDB） | ✅ 完成，**已装机验证**（80/92 匹配，海报出图） |
| **Stage 6** 播放体验 | ✅ 完成，**已装机验证**（续播 00:24→01:19、旋屏不中断、后台播放） |
| **分季分集 + 详情页** | ✅ 完成，**详情页已装机验证**；分季聚合只有单测（库里没剧可试） |
| **Stage 7** 字幕与设置 | ✅ 完成，**设置页已在真机验证**；字幕库里没有字幕文件，未验 |
| **界面改造**（VidHub 版式 + 用户站点配色） | ✅ 完成，**每步都在真机看过效果**；详情页重做成整页一体（海报完整不裁剪 + 取色延伸）|
| 播放页自绘控件 | ✅ **完成，真机验证**（触摸 / 手势 / 锁定 / 自动隐藏全过），见下 |

> **2026-10-15 在 MuMu 模拟器（Android 12 / SDK 32 / x86_64）上跑了一轮完整验证。**
> 详见下面「真机验证结果」一节 —— 抓到 1 个致命 bug（kotlinx-serialization 插件没 apply）
> 和 2 个真机才暴露的问题（横屏详情页背景图、MuMu 的点击注入）。

**Stage 1 的真机验证结果（2026-10-15，用户截图确认）**：填自己的 WebDAV 地址 +
OpenList 账号后显示 **「已连接，7 个条目」**，并正确列出中文目录名：

```
【Netflix纪录片100部】/
【新海诚】系列电影.双语特效字幕【共10部】/
奥斯卡最佳动画短片 1932-2024/
皮克斯经典动画电影合集（297G）/
```

→ PROPFIND、XML 命名空间解析、中文百分号编解码、Basic 鉴权全部正确。

**尚未验证**：杀掉 App 重进后凭据是否还在（Keystore 解密链路）。

### Stage 2 的进展与验证（2026-10-15）

代码已完成、编译通过。**本机 adb 无设备，尚未装机跑过** —— 下面是"已经证明了的"和"还没证明的"。

已经用真实服务器（用户提供的账号，共 3 次**已鉴权** PROPFIND，全部 `207`，无 `401`）证明：

- 子目录 PROPFIND 返回 `自身的 href + 全部子项`，子项 href 是**带完整父路径**的绝对路径
  （`/dav/知乎 推荐…/1.河西走廊/`），不是相对被请求集合的短名。
- 空格编码成 `%20`（不是 `+`），全角括号/书名号按 UTF-8 百分号编码 → `decodePath` 的处理是对的。

还没证明（要真机点一下才知道）：

- 列表渲染、面包屑、返回键、刷新、缓存是否符合预期。
- 普通**文件**条目是否带 `getcontentlength`（根目录与抽查的子目录里全是目录，
  没有文件样本）。真返回 null 时界面只是不显示大小，不会出错。

**最新 APK**：`E:\code\MediaDock\app\build\outputs\apk\debug\app-debug.apk`

### Stage 3 的进展（2026-10-15，同一会话）

代码完成、编译通过。**仍然没有设备，没装机跑过。** 已经能证明的和不能证明的分开写：

已经证明（静态可查）：

- 打包后的 manifest 里 `PlaybackService` 带着 `foregroundServiceType=0x2`(mediaPlayback) +
  media3 要求的 intent-filter；`FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` /
  `POST_NOTIFICATIONS` 都在。（`WAKE_LOCK` 由 media3 自己并进来的，正常。）
- 整个 Stage 3 通过编译，没有警告。

还没证明（必须真机点一下）：

- **播放能不能起来** —— 最大的不确定点是 `PlaybackService` 的 `android:exported="false"`，
  见坑 14。若失败，界面会显示「连接播放服务失败」（不是白屏），照着改就行。
- 拖 ≥1GB MKV 进度条是否秒跳（服务器侧应看到 `206`）、旋屏是否不中断。
- 通知栏控制、锁屏控制。

> **2026-10-15 用户确认「可以正常播放」** —— 起播这条路通了，`exported="false"` 是对的
> （坑 14 那条兜底用不上了）。进度条秒跳 / 旋屏 / 通知栏还没单独验过。

### Stage 4 的进展（2026-10-15，同一会话）

代码完成、编译通过；KSP 确实生成了 `MediaDatabase_Impl.kt`（Room 处理器跑到了）。
**没装机跑过。** 没验证的是：扫描能不能跑完、海报墙长什么样、取消是否干净。

已知会让人意外的两点（都在界面上有交代，不是暗坑）：

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
- 老数据（凭据）跨版本升级后仍在 → Keystore 解密链路顺带验证过了。

### 用户追加的两个播放器行为（都已实现并在真机上验证）

1. **进播放页自动横屏，离开还回系统** ✓
   - 播放页 3200x1440（横屏）→ 返回后 1440x3200（竖屏）。
   - 用户手机锁着竖屏，所以用的是**硬性** `LANDSCAPE`（理由见坑 36）。
2. **离开播放页就暂停，但保留通知** ✓
   - 返回后 `state=PAUSED(2)`，前台服务仍在（`isForeground=true`）→ 能从通知栏接着看。
   - 实现见坑 37（判据不能靠 `isChangingConfigurations`，也不能靠销毁时读路由）。

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

**这轮抓到的 bug（都已修）**：见坑 33 / 34 / 35。

**还没验的**（库里没对应数据，不是代码问题）：

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

#### ⚠️ TMDB 在你这里只能走代理

实测：直连 `api.themoviedb.org` 解析到 `face:b00c`（DNS 污染特征）并超时；
走 Clash（`127.0.0.1:7897`）立刻 `401`（= 连通，只是没带凭据）。

→ **手机上必须开着 VPN/代理**，否则刮削和海报都会失败（报「连不上 TMDB」）。
`api.themoviedb.org` 和 `image.tmdb.org` 两个域名都要能通。
这是环境约束，不是代码问题；App 侧无法绕过。

> 补充（同日）：自家那个 WebDAV 域名也出现过一次本机默认解析器超时，
> 但服务器本身是好的（绕过 DNS 直连 = 207 / 0.47s，阿里 DNS 和 DNSPod 都能解析出正确 IP）。
> 结论：**坏的是客户端解析那一步**，多半跟为了 TMDB 去调代理/DNS 有关。
> 自家的域名在 Clash 里应该走 `DOMAIN-SUFFIX,lovelev.eu.cc,DIRECT`（顺带这条对播放也更快）。
>
> 又补充：用户后来在**模拟器**里跑，报 `failed to connect to api.themoviedb.org/31.13.83.2` ——
> `31.13.83.2` 是 Facebook 的 IP（DNS 污染），源地址 `10.0.2.15` 是模拟器 NAT。
> **模拟器要走宿主机的代理**：宿主机在模拟器里是 `10.0.2.2`，不是 `127.0.0.1`。
> 启动参数 `-http-proxy http://10.0.2.2:7897`，或在模拟器 WiFi（AndroidWifi）里手填代理；
> 前提是 Clash 开了「允许局域网连接」。

### Stage 6 的进展（2026-10-15，同一会话）

用户选了「播放体验」这一批：**进度记忆 + 倍速 + 自动播放下一集**。代码完成、编译通过、
单测仍全过。**没装机跑过。**

- **进度**：单独一张 `playback_progress` 表（坑 23），播放中每 5 秒落一次、暂停时补一次，
  **写在 `PlaybackService` 里**（播放器住在服务里，退出播放页/锁屏时进度也得继续记）。
- **续播**：`setMediaItems(items, startIndex, startPositionMs)` 一次把队列和续播位置都设好。
  停在片尾 30 秒内不算续播点（否则每次都从片尾开始）。
- **倍速**：播放页右上角，`DropdownMenu` 选 0.5×–3×。**没做持久化** ——
  速度是播放器的属性，同一会话换集不丢；重启 App 回到 1×。
- **自动下一集**：点文件时把**同目录的视频**一起当队列传下去（见坑 24 的队列指纹）。
- 海报块底部会出一条细进度条，一眼看出哪些看了一半。

**和原计划的偏差**：WORKLOG 原来写「详情页显示『继续播放 12:34』」，但详情页（`ui/detail/`）
还没做，所以把「继续播放」做成了**海报块的进度条 + 自动续播**。等详情页做了可以在那儿再显示一次。

### 分季分集 + 详情页（2026-10-15，同一会话）

代码完成、编译通过、**分组逻辑有 6 个单测**（`WallEntryTest`，用例覆盖了平铺电影合集、
同目录多集、只有一集、分季排序、两个剧不互相合并）。**没装机跑过。**

- **海报墙按「剧」聚合**：识别出集数、且同一目录里 ≥2 集的条目并成一块，块上标「N 集」。
  规则与理由见坑 27 —— 关键是**不能简单按目录分组**。
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

代码完成、编译通过、**字幕配对有 7 个单测**（`MediaTypesTest`）。**没装机跑过。**
全部 7 个 Stage 都写完了一轮，接下来是集中真机验证。

- **同级目录字幕**：播放时**发一次 PROPFIND** 拿所在目录，本地按文件名配对（见坑 29 的规则），
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

## 2. 硬约束（动手前必读）

### ⚠️ 401 / 429 绝对不允许自动重试

OpenList 的**登录失败锁定按来源 IP 计数**，而 nginx 反代让所有客户端都显示为 `127.0.0.1` ——
**任何一个客户端输错密码会把所有人的 WebDAV 一起锁死**（电影 + 音乐全挂）。

代码里已经做了四道防线（改代码时不要破坏它们）：

1. OkHttp **不设置 `Authenticator`** —— 设置了它才会对 401 自动重发；不设置，401 原样返回。
2. `SourceViewModel` 里 `state is Running` 时按钮禁用，防连点。
3. 收到 401/429 后置 `retryBlocked = true`，**按钮变灰，直到用户修改任一输入框**才解锁。
4. 只在连通成功时才落盘凭据。

**并且：不要拿用户的线上 WebDAV 做试探请求**（包括未鉴权的 PROPFIND —— 那同样算一次登录失败）。
连通性测试一律交给用户在 App 里用正确密码做。

其它约束：
- **不要高频 PROPFIND**：目录列表要缓存 + 显式刷新；启动时不要全盘递归扫描，扫描必须用户主动触发且可取消。
- 健康状态码 = `207 / 206 / 200`；`404` = 路径失效（不是网络错）；`429` = 已被锁定。
- **所有 SDK / Gradle 缓存 / 工具一律装 E 盘**（C 盘仅剩 ~34 GB，且 `AppData` 有 EFS 加密问题）。

### ⚠️ 数据库只能写真迁移，兜底只留降级方向

`media_items` 是缓存（丢了重扫），但 `playback_progress` / `favorites` / `title_overrides` /
`title_details` 都是**用户数据，丢一次就是真丢**。规矩：

- 加表 / 加列写真迁移，进 `MediaDatabase.MIGRATIONS` 数组（容器整批注册，见坑 39）。
- 版本号改 `MediaDatabase.VERSION`（单一来源），**必须同时补一条迁移**，否则 `MigrationCoverageTest` 跑红。
- 升级方向**没有破坏性兜底**：缺迁移就抛异常（响亮），绝不静默清库（坑 45）。
  动 `media_items` 结构时写一条显式命名迁移；兜底一旦生效，用户数据也跟着一起没。

---

## 3. 已锁定的技术决策

| 项 | 决定 | 备注 |
|---|---|---|
| 播放内核 | **Media3 / ExoPlayer** | 不用 libVLC（用户选的） |
| 设备形态 | **只做手机 / 平板** | **不做** Android TV / 遥控器 |
| 刮削 | **TMDB API** | 用户会自备 key（尚未提供） |
| 数据源 | OpenList / AList WebDAV | |
| WebDAV 客户端 | **在 OkHttp 上手写** | 不用 `sardine-android`（只发 JitPack，本机拉取不稳） |
| DI | **手写 `AppContainer`** | 不引入 Hilt（省一个注解处理器） |
| 凭据存储 | DataStore + **Android Keystore AES-GCM** | 密码加密落盘 |
| 界面 | Jetpack Compose + Material3 | 单一 `:app` 模块 |
| 首页结构 | **底部两个 Tab：浏览 / 媒体库**；播放页与设置全屏、不带 Tab | 2026-10-15 用户选的。媒体库曾是浏览页右上角一个按钮，藏得太深，已挪下来 |
| 播放架构 | **MediaSessionService 持有播放器 + 界面用 MediaController 挂上去** | 旋屏/退出播放页都不打断；通知栏可控 |
| 播放鉴权 | **逐次请求补 Authorization**，且只给数据源主机 | 见坑 12 |
| 要播的文件 | `AppContainer.playbackSelection`（内存） | **不进 nav 参数**（会进 Bundle） |
| 媒体 HTTP 客户端 | 与 PROPFIND 的**分开**：无 `callTimeout` + `Accept-Encoding: identity` | 见坑 10 / 11 |
| 媒体库数据源 | **Room 是海报墙的唯一数据源**；扫描只负责往库里灌 | Stage 4 |
| 扫描 | 用户主动触发 · 顺序 BFS · 有界 · 可取消 · 扫完一次性事务替换 | 见坑 16 |
| 扫描器归属 | `LibraryRepository` 是**应用作用域**（不是 ViewModel） | 退出页面/旋屏不打断扫描；副作用是封锁不会自己消失，见坑 17 |
| 刮削 | **TMDB** | 用 search 端点一步到位（返回里就带 poster/backdrop/overview/rating），不再打详情接口 |
| TMDB 凭据 | 存 secure prefs（Keystore 加密），**绝不进 BuildConfig / 源码** | Bearer token 和 32 位 api_key 两种都认，别让用户猜 |
| 文件名解析 | `data/tmdb/TitleParser.kt`，**由单测锁住** | 规则是从真实命名里总结的，见坑 19 |
| 刮削并发 | 4 个一批（`chunked(4)` + `awaitAll`），不强求 Semaphore | 见坑 20 |
| 播放进度 | **单独一张表**，不挂在媒体条目上 | 媒体条目每次扫描整表替换，挂上面会被扫没，见坑 23 |
| 进度落库位置 | 在 `PlaybackService` 里，播放中 5 秒一次 + 暂停时一次 | 播放器住在服务里，界面走了也得继续记 |
| 播放队列 | 同目录视频由点击方算好，放进 `PlaybackSelection.queue` | 有队列才有「自动下一集」，见坑 24 |
| 倍速 | 不持久化，重启回 1× | 只在同一会话内保持（播放器的属性） |
| 海报墙聚合 | **只对「识别出集数 + 同目录 ≥2 集」的条目录成剧** | 不能按目录分组，见坑 27；纯函数有单测 |
| 详情页入参 | `DetailSelection.paths`（内存），详情页再按 path 去 Room 查 | 不传 nav 参数（路径含中文与 `/`，还会进 Bundle） |
| 外挂字幕 | **播放时现查同级目录**（一次 PROPFIND + 文件名配对），不存库 | 见 Stage 7 那节 / 坑 29 |
| 回到数据源页 | `popUpTo(navController.graph.id) { inclusive = true }` | 底部 Tab 会让 `popUpTo(BROWSE)` 弹不动，见坑 31 |

---

## 4. 环境与构建

### 构建链（本机首次搭，全部在 E 盘）

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

### 安装到手机

```bash
E:/code/tools/android-sdk/platform-tools/adb.exe install -r \
  E:/code/MediaDock/app/build/outputs/apk/debug/app-debug.apk
```

> **当前 adb 没有连接设备** —— 用户目前是手动拷 APK 安装的。需要读 logcat 排错时，
> 请用户插 USB 开「USB 调试」并在「传输文件」模式下连接。

### 版本锁定（均已实测为最新稳定版）

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
| `data/webdav/MultistatusParser.kt` | 解析 `207 Multi-Status`；**只按 local name 匹配**命名空间前缀。条目 path 一律**相对数据源根**（见坑 8），并剔掉 Depth:1 返回的集合自身 |
| `data/webdav/WebDavUrl.kt` | 地址解析、补尾斜杠、`rootCollection(rawUrl, rootPath)` 求数据源根、`addPathSegments` 正确编码、不把 `+` 当空格的百分号解码 |
| `data/webdav/WebDavModels.kt` | `WebDavEntry`（path/name/isDirectory/size/lastModified/contentType） |
| `data/webdav/WebDavException.kt` | `Unauthorized` / `Locked` / `NotFound` / `Http` / `Network` / `Malformed`，每类带一句人话 |
| `data/prefs/SecureCredentialStore.kt` | DataStore 存地址/用户名/根路径；`CryptoBox`（内部）用 Keystore AES-GCM 加解密密码；**`clearSource()` 只清数据源**（退出登录不再误删 TMDB key，坑 46） |
| `ui/nav/Routes.kt` | 路由常量（`ADD_SOURCE` / `BROWSE`） |
| `ui/source/SourceViewModel.kt` | 表单状态 + `TestState` + `test()`（单次请求，失败封锁重试） |
| `ui/source/AddSourceScreen.kt` | 添加数据源界面；连通成功后出「进入浏览」按钮（`onConnected`） |
| `ui/browse/BrowserViewModel.kt` | 目录浏览状态机：进入/返回/刷新/重试、目录缓存、`inFlight` + `nav` 序号防重（见坑 9）、401/429 后 `blocked` 停止一切自动请求 |
| `ui/browse/BrowserScreen.kt` | 面包屑（可点跳祖先）+ 刷新 + 列表（目录带 `›`、文件带大小、空目录显示「空目录」）+ `BackHandler` 返回上级；**点目录进目录、点文件进播放页**。媒体库入口已挪到底部 Tab |
| `player/PlaybackService.kt` | `MediaSessionService`：持有 ExoPlayer + MediaSession；`sessionActivity` 指向 MainActivity；**进度计时器**；媒体数据源 = `DefaultDataSource` 包住 WebDAV 那层（本地 `file://` 字幕要靠它，坑 55） |
| `player/WebDavMediaDataSource.kt` | 媒体流 DataSource：`open()` 时补 Basic 头，且只给数据源主机；取不到凭据直接抛错 |
| `player/PlaybackSelection.kt` | 「要播什么」的内存传递：选中的文件 + 同目录队列（刻意不进 nav 参数） |
| `ui/player/PlayerViewModel.kt` | 由 path 拼出绝对地址（顺带刷新 `credentials.cached`）+ 队列指纹 + 续播位置 |
| `ui/player/PlayerScreen.kt` | `MediaController` + `AndroidView(PlayerView)`；**自绘控件**（`useController=false`）：顶部返回/标题/字幕/倍速/锁定、中间大播放键、底部细进度条；左右半屏上下滑调亮度/音量；自动隐藏；`setMediaItems(队列, 起播位, 续播位)`；旋屏不重设；把 401/429/404 翻成人话。**触摸层结构见坑 53，别改回兄弟节点** |
| `data/local/MediaItemEntity.kt` | 库表：`path` 当主键（相对数据源根，天然唯一，还能直接喂回 `child()`）；含解析结果与刮削结果 |
| `data/local/MediaDao.kt` / `MediaDatabase.kt` | 只读 Flow + 整体替换 + 标记单条刮削结果。**`media_items` 是缓存**（丢表重扫只影响它）；进度 / 收藏 / 改名 / 详情是用户数据，只走真迁移。DB 现在 **v6**（迁移链 3→4→5→6，全在 `MIGRATIONS` 数组，见坑 39 / 45） |
| `data/local/PlaybackProgress.kt` | 进度表 + DAO。独立于媒体条目，见坑 23 |
| `data/repo/ProgressRepository.kt` | 存进度 / 查续播点（片尾 30 秒内不算）/ 给海报墙的 Flow |
| `data/MediaTypes.kt` | 「哪些扩展名算视频 / 算字幕」+ **字幕与视频的配对规则**的唯一定义。配对有单测（`MediaTypesTest`） |
| `data/repo/LibraryRepository.kt` | 扫描器 + 刮削器 + `ScanState`/`ScrapeState` + 库的 Flow。应用作用域，见坑 16 / 17；**`scanJob`/`scrapeJob` 各自独立 + `cancelScan`/`cancelScrape`/`cancelAll`**（坑 49） |
| `data/tmdb/TitleParser.kt` | 文件名 → 检索词/年份/季集。规则来自真实命名，见坑 19，改动必须同步改单测 |
| `data/tmdb/TmdbClient.kt` | `search/movie` / `search/tv` + 两种凭据 + 429 按 `Retry-After` 退避（见坑 21） |
| `ui/library/LibraryViewModel.kt` | 很薄：把仓库的三个 Flow 转给界面 + 扫描/刮削开关 |
| `ui/library/LibraryScreen.kt` | 海报墙（有海报用 Coil 取图，没有就用解析出的片名占位）+ 扫描/刮削/设置 + 进度；失败不抹掉已有内容；**按剧聚合，点块进详情页** |
| `ui/library/WallEntry.kt` | 海报墙聚合的**纯函数** + `orderedEpisodes()`。有单测（`WallEntryTest`），改动必须同步改 |
| `ui/detail/DetailScreen.kt` / `DetailViewModel.kt` / `DetailSelection.kt` | 详情页：整页一体 —— 海报按原比例完整显示 + 下半部分用海报取色延伸（`PosterTint`）；可展开简介 / 分集 / 演职人员 |
| `ui/detail/PosterTint.kt` | 取海报底部一条的平均色，供详情页做「颜色延伸」背景（见坑 52） |
| `ui/settings/SettingsScreen.kt` / `SettingsViewModel.kt` | TMDB 凭据 + 清空媒体库 + 退出登录（后两个带二次确认） |
| `ui/theme/Theme.kt` | 配色：**柠檬绿强调**（取用户站点），浅/深两套跟随系统；亮色只做色块、文字走 `tertiary`（坑 40） |
| `ui/HeartButton.kt` | 收藏心形：矢量图标 + 缩放动画 + 48dp 热区，支持传描边色（压图上要用浅色） |
| `ui/Formats.kt` | `formatSize` / `formatClock` / `formatDurationWords`，各处共用 |
| `ui/library/PosterGrid.kt` | 海报墙组件，媒体库页与收藏页共用；`WallEntry.paths()` 也在这 |
| `ui/favorites/FavoritesScreen.kt` / `FavoritesViewModel.kt` | 收藏 Tab（复用 PosterGrid，按收藏 path 过滤） |
| `ui/nav/PlayerOrientation.kt` | 播放页自动横屏、离开还回系统（**必须在导航外壳里做**，见坑 36） |
| `data/local/TitleOverride.kt` | 手工改的片名/年份，独立表；所有读媒体条目的地方都会合并它 |
| `data/local/TitleDetail.kt` | 类型/国家/时长/上映日期/演职人员，独立表；**打开详情页时才拉**并缓存 |
| `data/repo/DetailsRepository.kt` | 拉详情（已有就跳过，不重复请求）+ 解析演职人员 JSON |
| `data/subtitle/SubtitleProvider.kt` | **字幕源接口**（可切换）+ 结果/异常模型 + 语言名。加新源只写一个实现 |
| `data/subtitle/OpenSubtitlesProvider.kt` | OpenSubtitles.com REST：搜索用 Api-Key、下载用账号换 Bearer；406/429 当额度不重试 |
| `data/repo/SubtitleRepository.kt` | 字幕搜索 / 下载 / **统一落盘**到 `filesDir/subtitles/`；挂载一律走本地文件（坑 56） |
| `data/Countries.kt` | iso-3166-1 → 中文国名（TMDB 不本地化国家名） |
| `data/repo/FavoriteRepository.kt` | 收藏仓库 |
| `data/local/MediaDatabase.kt` | **`MIGRATIONS` 数组在这儿**，容器整批注册（坑 39）；版本号 `VERSION` 单一来源，迁移链由 `MigrationCoverageTest` 锁住（坑 45） |

资源：`res/values/{strings,themes}.xml`、`res/xml/network_security_config.xml`（放行明文以便局域网 http NAS）、
`res/mipmap-anydpi-v26/ic_launcher.xml` + `res/drawable/ic_launcher_{background,foreground}.xml`（纯矢量图标，无 PNG）

构建文件：`settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`、`gradle/libs.versions.toml`、
`gradle/wrapper/*`、`app/build.gradle.kts`、`app/proguard-rules.pro`、`.gitignore`

**计划中但尚未创建**的目录：（暂无 —— `domain/` 用不上）

> **版本管理（2026-10-16 起）**：项目已是 git 仓库，远程在
> **https://github.com/Lebai280/MediaDock**（**私有**），分支 `master`。
> `.gitignore` 已排除 `local.properties` / `*.keystore` / `*.apk` / `build/` / `.gradle/`。
> ⚠️ 本文件要上 GitHub，**运维细节（WebDAV 地址 / 服务器 / SSH / 账号）一律不写进来**（见文件开头）。
> 另注：`gradle.properties` 里写死了本机 JDK 路径（`org.gradle.java.home`），换机器编译要改。

---

## 6. 路线图（已批准的计划，Stage 2 起）

**Stage 2 — 目录浏览** ✅（代码完成，待真机验证）：`BrowserScreen`，文件夹优先排序、面包屑、返回栈。
目录切换用 `WebDavUrl.child(root, path, isCollection=true)`；条目 path 与地址重建见坑 8。
**点文件进播放页** 归 Stage 3 —— 现在文件行不可点（没有水波纹，不假装能点）。

**Stage 3 — 播放** ✅（代码完成，待真机验证）：`PlaybackService`（MediaSessionService，持有 ExoPlayer）
+ `PlayerScreen`（`PlayerView` 塞进 `AndroidView`，通过 `MediaController` 挂到服务上）。
鉴权见坑 12；`callTimeout` 与 gzip 两个坑见 10 / 11。
**不要**写触碰 `Range` 的 interceptor —— Range 由 DataSource 依据 seek 位置自动发。
验收：拖 ≥1GB MKV 的进度条能秒跳（服务器侧应看到 `206`）；旋转屏幕不中断。
「旋转不中断」的实现要点是坑 13（别无条件重设媒体项）。

**已知遗留（小的）**：播放页被 MainActivity 的 Scaffold 内边距框住了，视频没有真正全屏
（状态栏/导航栏那圈是黑的）。要顶到边得把内边距从 NavHost 下放到各个页面，涉及
AddSourceScreen / BrowserScreen 一起改 —— 属于打磨项，没做。

**Stage 4 — 媒体库** ✅（代码完成，待真机验证）：用户主动点「扫描」→ 顺序 BFS 递归 →
`MediaItemEntity` 入 Room → 海报墙（未刮削时用文件名占位）。
Room 是海报墙的唯一数据源。**不要在启动时自动全盘扫描**（见硬约束）。
入口在浏览页头部的「媒体库」按钮（设置页要等 Stage 7）。

**已知取舍**：

- 海报墙是**平铺**的：一个视频一块。按剧集/剧名归并属于 Stage 5 的活儿（那要先做文件名解析）。
- 视频扩展名收得比较宽（含 `avi`/`rmvb`/`wmv`）。**media3 对这些不一定解得了**，
  点开可能提示播放失败 —— 宁可让用户看见自己的文件，也不要偷偷藏起来。
- 媒体库**还没成为 App 的首页**（起始页仍是浏览页）。等 Stage 6「继续播放」做完再定更合适。

**Stage 5 — 刮削** ✅（代码完成 + 解析器/检索已验证，待真机验证）：
文件名解析 → TMDB → 落库 poster/backdrop/overview/rating；
Coil 加载 `https://image.tmdb.org/t/p/w500{posterPath}`。
- API key 存 secure prefs，**不写进 BuildConfig** ✓
- 4 并发 + 429 按 `Retry-After` 退避 ✓（坑 21：这个 429 和 WebDAV 那个是两回事）
- `scrapeState` 列（`UNSCRAPED|MATCHED|FAILED`）+ `scrapedAt`，避免重复请求 ✓
- 刮削只处理 `scrapeState != 'MATCHED'`，所以再点一次「刮削」= 重试上次没匹配上的

**有意留到后面的**：单条详情页（`ui/detail/`）还没有，点海报是直接播；
按剧集/剧名归并海报墙也没做（现在是一个文件一块，剧集多了会很长）。
这两个都依赖 Stage 5 已经落库的 `tmdbId`/`season`/`episode`，做起来不难。

**Stage 6 — 进度记忆 + 播放体验** ✅（代码完成，待真机验证）：
进度表 + 5 秒节流落库（在服务里）+ 暂停补记；`setMediaItems(items, startIndex, startPositionMs)`
一次设好队列与续播位；同目录视频作队列（自动下一集）；海报块细进度条；倍速菜单。
片尾 30 秒内不算续播点。**详情页没做**，所以「继续播放」目前体现在海报进度条上。

**Stage 7 — 字幕与设置**：同级目录 `.srt/.ass` 作为可选字幕轨（`MediaItem.SubtitleConfiguration`）
+ 内嵌字幕轨切换；设置页含 TMDB key、清缓存、退出登录。

### 文件名解析规则（Stage 5 要用）

去扩展名 → 点/下划线转空格 → 去 `[...]` 与发布标签（1080p / x265 / HEVC / WEB-DL / BluRay / -GROUP）→
先匹配 `S(\d{1,2})E(\d{1,3})` 或 `(\d{1,2})x(\d{1,3})` 判剧集，否则取第一个四位年份判电影，
再否则整体当标题。

### 后台播放的 manifest 要求（Stage 3 要用）

`FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` 权限，service 标
`foregroundServiceType="mediaPlayback"`，API 33+ 还要运行时 `POST_NOTIFICATIONS` ——
否则 service 启动即抛异常。

---

## 7. 坑清单（全部已解决，别再踩）

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

### Stage 2 踩到的（已修）

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

### Stage 3 踩到的（已修）

10. **给 PROPFIND 用的那个 OkHttp 客户端不能拿去放视频。** 它带了 `callTimeout(45s)`，
    而 `callTimeout` 是**整个请求**的总时限 —— 一个几 GB 的文件要连着读好几分钟，
    用它会「播一会儿就断」，看着特别像网络问题。
    → AppContainer 里另开了 `mediaOkHttpClient`，**不设 callTimeout**。
11. **媒体流必须 `Accept-Encoding: identity`。** OkHttp 默认加 `Accept-Encoding: gzip`
    并**透明解压**响应体，于是 `Content-Length` 和实际拿到的字节数对不上，
    字节区间（Range）随之失效 —— 正好就是「拖进度条」要用的东西。
    → 只在 `mediaOkHttpClient` 上挂了一个只改这一个头的 interceptor。**Range 一个字没碰。**
12. **鉴权头要逐次请求补，不能写死在 DataSource 工厂里。** 凭据是从 DataStore 异步读的，
    写死在工厂就有「播放器已经 prepare、凭据还没读出来」的空窗，那一下发出去的是
    **未鉴权请求** —— 在 OpenList 上等于记一次登录失败，会连累所有人。
    → 改成 `open()` 时才从 `credentials.cached` 取；取不到**直接抛错**，绝不发出去。
    并且只在目标主机 == 数据源主机时才加头，密码不会跟着跑到别的 host 去。
13. **旋屏不能让播放从头开始。** Activity 重建 → 新的 MediaController；如果
    `LaunchedEffect` 里无条件 `setMediaItem`，就会 reset 到 0。
    → 判 `currentMediaItem?.mediaId != mediaId` 才 set（mediaId 用的就是文件路径）。
    「旋转屏幕不中断」靠的就是这个判断。
14. **`PlaybackService` 的 `android:exported` 还没在真机上确认过。** 现在写 `false`
    （同 UID 绑定不受 exported 限制，不导出也别的东西连不上这个媒体会话）。
    **若点播放毫无反应 / 显示「连接播放服务失败」，第一件事就是把它改成 `true` 再试** ——
    密码外泄这一层已经由坑 12 的主机判断挡住了，所以改 true 是安全的。
15. `init { ... }` 块里**不能写 `return`**（Kotlin 不允许），要包一个私有函数再 return。

### Stage 4 踩到的（已修）

16. **取消扫描后不能顺手把半份结果写进去。** 第一版是「循环退出 → 写库」，而取消也是
    「循环退出」，于是取消会把自己当成扫描完成，用不完整的结果覆盖掉上一次的库。
    → 写库前显式判 `isActive`，取消就直接 return（库保持上一次的样子）。
17. **应用作用域的封锁不会自己消失。** 浏览页的 `blocked` 是靠 ViewModel 重建清掉的；
    `LibraryRepository` 活在进程里，重建不了 —— 用户重填密码后照样扫不了。
    → 加了 `reset()`，由导航层在「重新登录」那条路上显式调一次。
18. Room 的 KSP 产物在 `app/build/generated/ksp/debug/**kotlin**/`（不是 `java/`），
    Room 2.8 生成的是 Kotlin。想确认处理器真跑到了就找 `MediaDatabase_Impl.kt`。

### Stage 5 踩到的

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
21. **TMDB 的 429 和 WebDAV 的 401/429 是完全相反的两回事，别把规矩串了。**
    - WebDAV（OpenList）：429 = 账号被锁，**绝不能重试**，重试会连累所有人。
    - TMDB：429 = 限流，**按 `Retry-After` 退避重试才是对服务器友善的做法**。
    代码里两处都写了醒目注释，改的时候别把这条规矩挪错地方。
22. 解析器要同时看**文件名和父目录名**。扫描时父目录名从 path 现算：
    `path.substringBeforeLast('/', "").substringAfterLast('/')`。

### Stage 6 踩到的

23. **进度绝不能挂在 `media_items` 上。** 每次扫描都是 `deleteAll + insertAll` 整表替换，
    进度写在里面会被每扫一次抹一遍。而且从浏览页直接播、没进过媒体库的文件也得有进度。
    → 单开 `playback_progress` 表（path 主键）。
24. **队列指纹必须和实际队列同源。** 播放页拿「队列指纹」判断控制器上挂的是不是同一批；
    第一版指纹直接取 `playbackSelection.queue`，而实际队列还做了「空则兜底成单元素」，
    两者不同源 → 旋屏时会误判成「不是同一批」→ 重新 `setMediaItems` → **播放被拽回开头**，
    正好毁掉「旋转不中断」。→ 两者都从同一个 `queue` 字段来（见 `PlayerViewModel`）。
25. **`remember` 不能写在 `LazyVerticalGrid` 的内容 lambda 里** ——
    那是 `LazyGridScope`，不是 `@Composable` 上下文，直接编译报错。要提到 grid 外面。
26. 海报墙按目录分组要**先 `groupBy` 一次（O(n)）**，别在每块海报里再 `filter` 整个库（O(n²)）。
    （现在换成了 `WallEntry.kt` 里的 `groupWall()`，一次性算好。）

### 分季分集踩到的

27. **海报墙绝不能「按目录分组」** —— 这是最危险的一条。用户的
    「豆瓣电影Top250【723GB】」这类合集目录里很可能**平铺着几百部电影**，
    按目录分会把它们并成一块；「皮克斯」那种一片一目录的结构又正好相反。
    真正的判据是**有没有识别出集数**：只有 `episode != null` 的条目才参与聚合成剧，
    而且同一个目录里要有 **≥2 集**（否则片名里碰巧匹配到 E 数字的单片会变成「只有 1 集的剧」）。
    → `ui/library/WallEntry.kt` 的 `groupWall()`，**有单测锁着**，改之前先看 `WallEntryTest`。
28. **Room 的 `IN (:paths)` 遇到空列表会生成 `IN ()`，那是 SQL 语法错。**
    详情页的入参来自内存（进程被杀就空了），所以 `LibraryRepository.observeByPaths()`
    在空列表时直接返回 `flowOf(emptyList())`，别把空列表交给 Room。

### Stage 7 踩到的

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

### 真机验证这一轮踩到的（都已修）

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
    （顺带：这个 bug 一开始被我误判成「详情页全黑 / 白屏」，其实是背景图还在加载、又占满屏。）
35. **MuMu 上 `adb shell input tap` 会被当成滑动。** 注入的触摸有抖动，一 tap 就被 Compose
    判成拖动 → 海报墙滚动而不点进去。**用「同点按住」代替**：
    `adb shell input swipe X Y X Y 150`（起止同点 + 150ms），可靠命中。
    另外 `uiautomator dump` 给出的 bounds 是**物理坐标空间**（2560x1440），
    但 `wm size` 报的是逻辑尺寸（1440x2560）；E 盘 Git Bash 还会把 `/sdcard/x.xml`
    当 Windows 路径转换 → 要 `export MSYS_NO_PATHCONV=1`。

### 模拟器上跑 TMDB 的正确姿势（已验证）

```bash
# 让模拟器走宿主机的 Clash。10.0.2.2 = 宿主机的回环（不是 127.0.0.1！）
adb shell settings put global http_proxy 10.0.2.2:7897
# 撤销
adb shell settings put global http_proxy :0
```
OkHttp 默认读系统代理，所以设完刮削就能通。**前提是 Clash 允许来自本机回环的连接**
（只监听 127.0.0.1 也行，因为 10.0.2.2 映射的就是宿主机回环）。

### 界面改造踩到的

39. **⭐ 加迁移必须同时确认容器注册了它 —— 忘了就是静默清库。**
    加了 `title_overrides` 表（v4→v5）并写了 `MIGRATION_4_5`，但 `AppContainer` 里
    `.addMigrations()` 只注册了 `MIGRATION_3_4`，于是 4→5 没有可用迁移 →
    走了 `fallbackToDestructiveMigration` 兜底 → **媒体库、播放进度、收藏全被清掉**。
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
    改完颜色**必须跑对比度脚本**，别凭肉眼。
41. **Compose 的 `Modifier.blur` 只吃一个半径，做不了渐变模糊。**
    用户要「从中间往下虚化逐渐加深」，一刀切（上半清晰下半模糊）的接缝非常生硬。
    → 做法：**底层放一张重模糊的图，上层放同一张清晰的图，用竖直渐变把上层的
    不透明度从中间往下归零**（`graphicsLayer { compositingStrategy = Offscreen }` +
    `drawWithContent { drawRect(渐变, blendMode = DstIn) }`）。
    两层同尺寸同位置，看不出是两张图，视觉上就是渐进加深。
42. **别用 `♡` / `♥` 字形当图标** —— 用户明确指出「太扁、点击时大小会变」：
    那是两个不同字形，宽高都不一样，一点下去布局就跳。
    → 用矢量图标（Material 的 `Favorite` / `FavoriteBorder`），画布尺寸恒定，
    填充/描边只是颜色和绘制方式不同；再加一个 `Animatable` 缩放弹一下（主流做法）。
    点击热区要 ≥48dp，图标本身 30dp 就够。

### 类型/演职人员这轮踩到的

43. **⭐ `init {}` 块必须写在它引用的属性之后。** Kotlin 按**声明顺序**初始化：
    我把 `init { entries.first { ... } }` 插在了 `val entries = ...` **前面**，
    编译一切正常，一打开详情页就
    `NullPointerException: Attempt to invoke interface method 'Flow.collect' on a null object reference`。
    → 把 `init` 移到所有属性之后。**这类 bug 只在运行时出现，编译和单测都拦不住。**
44. 顺手记：`StateFlow.stateIn(..., 初值)` 的初值是**立刻**发出的，
    所以 `flow.first()` 会拿到初值而不是第一个真实值。要等真实数据得用
    `first { it.isNotEmpty() }` 这种带条件的。

### 加固 + 详情页重做这轮踩到的（2026-10-15 深夜）

45. **兜底策略：宁可打不开，也不要静默清库。** `fallbackToDestructiveMigration(dropAllTables = true)`
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
    退场时最后一段进度就丢了。**只 cancel ticker，别 cancel 整个 scope。**
49. **扫描和刮削共用一个 `job` 字段会互相牵连。** 取消刮削会顺手把扫描结果提示也抹成 Idle；
    刮削中点扫描又静默无反应。拆成 `scanJob` / `scrapeJob` + `cancelScan()` / `cancelScrape()` / `cancelAll()`。
    两者仍互斥（扫描整表替换 `media_items`，并发会把刮削中的结果换掉），互斥在界面上**置灰按钮**，
    别让人点了没反应。
50. **别拿界面那份 StateFlow 的快照当「当前是否收藏」。** `favorites` 是 `WhileSubscribed` 的 StateFlow、
    初值是空集，`path !in favorites.value` 在订阅到位前恒为 true —— 翻转方向就反了。
    改成 `FavoriteRepository.toggle(path)` 先查库（DAO 加 `countOf`）再翻。
51. **横版背景图铺满竖屏会被放大到只剩一条。** 16:9（1.78）铺 1440×3200（0.45）要按高度铺满，
    宽度被撑到 ~5700px，只露 1440/5700 ≈ 25% —— 用户原话「放大后只能看到部分海报」。
    详情页顶部改用**海报按原比例（2:3）`Fit` 完整显示**（不裁剪）。
    `Fit` 而非 `Crop`：万一只有横版图也完整显示、上下空档露出页面底色，接得上。
52. **要「一体、不割裂」，就别用另一张图/另一种材质去铺下半部分** —— 用海报自己的颜色。
    做法：取海报**底部一条的平均色**当页面主色，大图下半部的压暗**渐变成这个色**（不是黑），
    图下面再用同一个色铺（略微渐暗）。这样图和内容是同一色调，接缝看不出来。
    取色单独下一张 `w92`（几 KB）；**亮度要压到 ≤0.30 再当底色**，否则白字读不清；
    取不到就退回中性深色。`ui/detail/PosterTint.kt`。
53. **Compose 自绘覆盖层压在 `PlayerView` 上、按钮收不到触摸 —— 是层的结构问题，不是 SurfaceView 的锅。**
    `AndroidView(PlayerView)` 上盖一层 Compose 控件，只要把「点空白显示/隐藏」挂在**铺满的父 `Box`**
    的 `pointerInput` 上、控件作为**子节点**，子节点就先拿到事件，按钮 / 进度条全正常。
    反例（上一轮失败的写法）：把全屏「点击切换」层和按钮做成**兄弟**、且让点击层**声明在后面**
    （= 画在上面、也先拿事件）—— 它会截胡所有触摸，按钮一次都收不到。**别改回兄弟结构。**
    （顺带：`screencap` 抓 SurfaceView 上的视频帧经常是黑的，别据此判断「播放坏了」——
    看日志里 `PlaybackState` 的 position 有没有在走。）
54. **同一个节点上挂两个并行 `pointerInput`（一个 `detectTapGestures`、一个 `detectVerticalDragGestures`）
   会互相抢事件。** 表现：上下滑调亮度/音量**又抖又不灵敏**，一次 700px 的滑动只生效了几个像素
    （日志显示累计行程只有 ~9px）。因为前一个手势检测器把事件流吞掉了大半，后一个只吃到末尾一小段。
    → 合成**一个** `pointerInput`，用 `awaitEachGesture { awaitFirstDown() … }` 手写循环同时判「点」
    和「滑」，并且**用累计位移**算调节量（拿单次事件的增量去加起始值只会原地抖）。
    同理：进度条要 `down.consume()`，否则父层也会跟着起手势，在进度条上拖会被误判成「点了一下」。
55. **本地（`file://`）字幕读不了 —— 因为媒体数据源被我们那层只认 http(s) 的 factory 包住了。**
    `WebDavMediaDataSource` 底层是 OkHttpDataSource，只处理 http(s)；直接把它交给
    `DefaultMediaSourceFactory` 的话，`file://`（本地字幕）和 `content://` 都是死路。
    → 外面再包一层 `DefaultDataSource.Factory(context, 我们的factory)`：本地/内容 URI 走它自己的
    数据源，其余才落到我们这层补 WebDAV 鉴权。加本地字幕之前就得这么改。
56. **想在真机上自动验 SAF 选择器（`OpenDocument`）基本没法自动化。** 两个坑：
    (a) `adb push` 进 `/sdcard` 的文件**不进 MediaStore**，选择器的「最近」列不出来（用
    `content call --uri content://media/external/file --method scan_file --arg <path>` 也不一定进）；
    (b) DocumentsUI 的标题栏（☰ 抽屉、🔍 搜索）**吃不下 adb 注入的点击**，点不动。
    → 这类流程的验证交给人在手机上点，别在 adb 上耗。
57. **按「类型 / 国家 / 演员」搜库默认搜不到 —— 那些字段在 `title_details` 里，而它是「打开详情页才拉」的懒加载。**
    要全库可用就得先跑「补齐影片信息」（`DetailsRepository.fillMissing`，首页 ⋯ 菜单）。
    改搜索规则或改详情加载策略时，记住这条因果（搜索的单测里也专门锁了一条「没详情时按类型搜不到、按片名照搜」）。
58. **只 `cat` 出 `library.db` + `-wal`、不带 `-shm` 去读库，会读到旧快照 —— 我因此误诊了一次。**
    核对「补齐」结果时这么读的，SQLite 没应用 WAL，看到的是补齐前的行数，于是断定「没写进去 / TMDB 连不上」，
    实际上 61 条都补上了。**要读 App 正在用的库，`-shm` 必须一起拿（或直接看 App 界面上的数字）。**
    附属教训：**`ping api.themoviedb.org` 不通 ≠ HTTPS 不通** —— 很多网络屏蔽 ICMP，别拿 ping 当连通性判据。
59. **`by` 委托属性做不了智能转换。** `val scan by vm.scan.collectAsState()` 之后写
    `when { scan is ScanState.Running -> scan.directories }` 会报
    `Smart cast to 'ScanState.Running' is impossible, because 'scan' is a delegated property`。
    → **先读进局部 val**（`val scanState = scan`）再用。首页那堆状态就是这么处理的。

### 换正式签名 + 发版踩到的（2026-10-16）

60. **⭐ 卸载会销毁该应用在系统 Keystore 里的密钥 —— 于是加密存的东西「备份了也解不开」。**
    换签名必须卸载重装，我提前想到了备份 Room 库并完整保住了媒体库，
    但**漏了 DataStore 里的凭据**：WebDAV 密码 / TMDB 密钥是用 Keystore AES-GCM 加密的，
    而 Android 在卸载时把该应用在 Keystore 里的密钥一起删了。**哪怕提前拷出 DataStore 文件，
    装回去也解不开密。** 结果用户得重填 WebDAV 密码和 TMDB 密钥。
    → **教训：判断「什么会被卸载带走」要看它是不是走 Keystore，不是看它在哪个文件里。**
    免重填的正解：**卸载前在 App 里点「分享给朋友」把配置复制到剪贴板**（那条路输出明文），
    存下来再卸。见「换成正式签名」那节。
61. **`run-as` 依赖 `debuggable`，正式包一定用不了 —— 别当成故障。**
    - 想在正式包上读/写 `databases/`、`shared_prefs/` 是做不到的，报 `package not debuggable`。
      这正是 `debuggable` 关上了的**证据**（反过来说，能 `run-as` 就说明包还是可调试的）。
    - 迁移时若需要往正式包里写文件，就**临时**把 `isDebuggable` 打开构建一版，写完再关掉重构建覆盖。
    - 另：`adb shell pm install /sdcard/Download/xxx.apk` 会报 `Can't open file` ——
      Android 15 分区存储下那个文件属主是浏览器 uid、mode 700，shell 读不到。
      **不是包坏了。** 要装就 `adb install -r <本地文件>`（先用 sha256 证明两边字节一致）。

### 播放器行为调整踩到的（都已修）

36. **强制横屏的两个坑。**
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
    - ❌ `isChangingConfigurations`：加了「自动横屏」之后就坏了 —— 离开播放页会顺带触发
      「旋回竖屏」的配置变更，这个标志变成 true，暂停被跳过（实测返回后一直 PLAYING）。
    - ❌ 销毁时读「当前路由」：**composable 的销毁比路由状态更新更早**，读到的还是 player。
    - ✅ 最后把 **MediaController 交给 ViewModel 持有**，在 `onCleared()` 里暂停：
      它只在导航条目真被弹出时触发，旋屏/配置变更是保留的，语义正好。
      顺带好处：旋屏不再重建控制器，也就不会重新缓冲。
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

**和参考图还差的**（都不是「没做」，是缺数据或没轮到）：
- **类型/地区**（截图里的「日本 | 犯罪,剧情」）：TMDB 搜索结果带 `genre_ids`，
  但要再调一次 `/genre/movie/list` 建映射，还没做。
- **演职人员**：要调 `/movie/{id}/credits` + 存起来，另开一批。
- **多资源切换**（A(1)·MP4…）：要按 `tmdbId` 把同一部片的多个文件归并；用户库一文件一片，暂时用不上。
- **片单 / 举报图标**：没做 —— 不想放两个点了没反应的假按钮。
- **底部 4 个 Tab（媒体库/文件源/影视服务器/设置）**：还没做，是下一批的事。

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
  踩过的地方：M3 的 `TextButton`、`NavigationBar` 默认都拿 `primary` 当文字色，
  所以「扫描/刮削」「刷新」「已连接」这些都得显式改色，见坑 40。
- 浅色主题：底 `#F6F6F3`、卡 `#FFFFFF`、浅卡 `#EFEFEA`、正文 `#17181A`、次要 `#5F6063`。
- 深色主题保留（跟随系统），同样是柠檬绿强调。**两套都做**是用户明确要的。
- 导航栏必须显式给 `containerColor = surface` + 覆盖
  `NavigationBarItemDefaults.colors(...)`，否则 M3 默认的 `secondaryContainer`
  是**淡紫色**，跟柠檬绿完全不搭（第一版就是这样，截图一看就露馅）。
- 改配色后**一定要跑对比度脚本**（本文件里有；15 组前景/背景全过 4.5:1 才算完）。

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
   片名以下是**渐进虚化**（见坑 41），白字压在压暗的图上；最底边渐隐到页面底色。
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
  当初申请 API key 时承诺过要在应用内显示，别删。
- **`ui/Formats.kt`**：`formatSize` / `formatClock` / `formatDurationWords` 统一放一处，
  浏览页和详情页共用（之前各自有一份）。

### 加固 + 详情页重做（2026-10-15 深夜，接手一轮评估之后）

用户让「按你分析的改进」动手，顺带把详情页按 VidHub 那种「整页一体」重做。改动都已装机
（三星 S21 Ultra）验证：升级不丢数据（92 条 / 进度 / 改名 / 详情原样）、详情页逐屏看过、无崩溃。

#### 代码加固（对应评估里的 P1 / P2 / P3）

- **库兜底只留降级方向**：`fallbackToDestructiveMigration(dropAllTables = true)` →
  `fallbackToDestructiveMigrationOnDowngrade(dropAllTables = false)`。现在「漏写升级迁移」的后果是
  **打开库时抛异常**（响亮），不再是静默清库。见坑 45。
- **退出登录不再连 TMDB 凭据一起删**：`SecureCredentialStore.clear()`（清整个 DataStore）→ `clearSource()`
  （只删数据源 4 个 key）。设置页确认文案本来就说「数据源地址、账号和密码」，现在名副其实。见坑 46。
- **刮削单条失败不中断整轮**：批内某条网络抖动只算它自己没匹配上，其余继续。鉴权失败仍中止整轮。见坑 47。
- **退场时最后一次进度保存不再被取消**：`PlaybackService.onDestroy` 只停 ticker，不 cancel 整个 scope。见坑 48。
- **扫描 / 刮削各用独立 job**（`scanJob` / `scrapeJob`），取消互不牵连。两者仍互斥 —— 扫描是整表替换
  `media_items`，并发会把刮削中的结果换掉；互斥现在在界面上**显式置灰按钮**，不再「点了没反应」。见坑 49。
- **收藏翻转以库内状态为准**（DAO 加 `countOf`），不再用界面那份可能还没同步的 StateFlow 快照。见坑 50。
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
  取不到色（没图 / 没网 / 解码失败）退回一块中性深色，不影响功能。
- **文字可读性**：用户指出白字压在亮海报上「看着不清楚」→ 大图下半部那层压暗从中间开始渐深
  （颜色就是页面主色），标题 / 元数据 / 文件信息再各加一层**投影**。
- 简介（可展开）/ 分集 / 演职人员都落在海报延伸出来的这同一片底色上。
- 返回 / 更多**钉在顶部**（不随内容滚），随时够得着。
- 没图（没匹配上 TMDB）的条目：深色竖向渐变兜底，不再是「一片死黑、看着像坏了」。
- 简介**可展开 / 收起**：只有真的超过 4 行才出现开关（短简介不白挂开关）；按钮文字走 `primary`
  （深色底上柠檬绿读得清）。
- 刻意**没做**参考图里的「资源」胶囊（多资源切换）和片单 / 举报图标 —— 没有真实功能，不做假按钮。

**为什么不用横版背景图**：16:9 铺满竖屏（1440×3200）要按高度铺满，宽度被撑到 ~5700px，
只露 25% —— 正是用户说的「只能看到部分」。海报是竖版，按原比例铺宽度正好是一块高画面。见坑 51。

### 播放页自绘控件 ✅（2026-10-15 深夜做完，真机验证）

**用户要的**：不要播放器原生控件，改成自绘的 —— 顶部 返回 / 标题 / 字幕 / 倍速 / 锁定，
中间大播放键，底部**细进度条** + 时间；外加**锁定按钮**、**左侧上下滑调亮度**、
**右侧上下滑调音量**。（在线字幕搜索是另一批，要接第三方字幕站。）

**上一轮卡在哪**（记下来别再犯）：自绘层里的按钮和进度条**收不到触摸**，加 Log 证实只有最外层
那个全屏「点击切换」区能收到事件。当次没解决，回退了。

**这次一次就通了 —— 关键在触摸层的结构：**

> **把「点一下显示/隐藏」和左右半屏上下滑挂在铺满的父 `Box` 的 `pointerInput` 上，
> 按钮 / 进度条作为它的子节点。** Compose 里**子节点先于父节点拿到事件**，
> 所以点按钮、拖进度条不会被父抢走，只有点空白处才落到父上切显隐。
> 上一轮把全屏「点击切换」层和按钮做成**兄弟**、且点击层压在按钮上 —— 全被它截胡了。
> **别改回兄弟结构。**

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
  **右下角倍速**。**锁定按钮固定在左侧中间**，锁上后 3 秒自动隐藏、点一下唤回（位置不变）。
- 「⋯」菜单里装：**字幕**（列出现有字幕轨 + 关闭；文件本来没字幕轨就不显示这一项）、
  **详细信息**（名称 / 路径 / 时长 / 画面分辨率·帧率·编码·码率）、**截图**。
- `PlayerView.useController = false`，画面之外全自绘。
- **细进度条自己画**（不用 M3 `Slider`，它轨道太粗）：3dp 轨道 + 已播段，点一下跳转、
  按住左右拖调位置，两种手势都由它自己消费（按下事件也 consume 掉，免得被父层当点击）。
- 亮度写 `Activity.window.attributes.screenBrightness`（只影响本 Activity，退出播放页自动回系统默认）；
  音量走 `AudioManager` 的 `STREAM_MUSIC`；都是**按累计位移**算，拖动时中间出「亮度 62% / 音量 40%」提示。
- **时间要对秒**：控制器上报的位置是隔一阵才来一次的，用本地时钟补差（若控制器本身在连续外推则不补，
  不会重复计时）；时长还没读出来时显示 `--:--`，别显示 `00:00`。
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

- ✅ 播放页字幕面板三项都在；在线搜索对话框能开，没填凭据时给引导文案；设置页「字幕源」分组在。
- ✅ 数据源包了 `DefaultDataSource` 之后 http 播放照常（播放到片尾 08:40，没被改坏）。
- ⚠️ **「从本地文件加载 → 挂载上屏」这条没能自动化验**：SAF 选择器的标题栏（抽屉 / 搜索）吃不下
  adb 注入的点击，而且 `adb push` 进去的文件不进 MediaStore 的「最近」，选择器里根本不列它。
  **这条得人在手机上点一下**（已经把测试字幕推到 `Download/mediadock-test.srt`）。
- ⚠️ **OpenSubtitles 的真实搜索/下载没跑通过** —— 本机没有用户的 Api-Key。DTO 是照公开文档的形状写的、
  解析用 `ignoreUnknownKeys` 宽容处理；站点哪天改形状就改 DTO。

### 首页：搜索栏 + 随机推荐 + 补齐影片信息（2026-10-15 深夜，用户要求暂停时的状态）

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
  不补齐的话，按类型/演员搜几乎搜不到东西（见坑 57）。顺便详情页也能秒开。

**验证状态（暂停时的实况）**：

- ✅ 首页布局（搜索栏 / 随机推荐 / 换一批 / 海报墙）真机看过。
- ✅ 搜索过滤真机验过：输 `Tit` → 只剩 Titanic 与 Surogat；**搜索时推荐行正确隐藏**；清除后推荐行回来。
- ✅ ⋯ 菜单里「扫描 / 刮削 / 补齐影片信息」三项都在（点过、dump 过 bounds）。
- ✅ 搜索规则 8 条单测全过（总单测 28 → **36**）。
- ⚠️ **没验**：点「补齐影片信息」之后的实际拉取（暂停时正好停在菜单那一步）、
  以及补齐后按类型/国家/演员搜索的实际效果（`input text` 打不了中文，中文搜索得人在手机上试）。

### 分享给朋友：配置自动识别 + 自动填入（2026-10-16）

用户原话：「我分享给朋友时输入链接账号密码很容易输错，能不能弄一个可以自动识别，自动填入我发的账号密码」。

- `data/webdav/ShareConfig.kt` —— **生成 + 解析**一段人能看懂、程序也能认的文本：
  ```
  MediaDock 数据源
  地址：https://files.example.com/dav
  账号：demo
  密码：xxxx-xxxx
  ```
  解析很宽容：标签认中英写法（地址/服务器/链接/url、账号/用户名/user、密码/pass/pwd…）、认全角冒号、
  认 `https://user:pass@host/dav` 这种把账号密码塞进 URL 的写法（含百分号编码）。
  **必须有地址 + 密码才算配置** —— 剪贴板里随便一个链接不该被当成配置去乱填。10 条单测锁着（`ShareConfigTest`）。
- 设置页「数据源」多了 **「分享给朋友」**：把已保存的数据源拼成上面那段文本放进剪贴板，并提示「已复制」。
- 添加数据源页：**进页面自动读剪贴板**，认出来就把三个字段直接填好 + 提示「已填入分享配置 · <地址>」；
  旁边另有「粘贴分享配置」按钮（自动识别没赶上时可以再点一下，认不出来会说一声）。
  剪贴板用**平台 API** 读（Compose 的 `LocalClipboardManager` 已废弃）；Android 10+ 只允许**前台** App 读 —— 本页在前台，没问题。

**真机验证（一条龙，2026-10-16）**：设置里点「分享给朋友」→ 提示「已复制 · 朋友在『添加数据源』页会自动填入」
→ 退出登录 → 添加数据源页**自动填好了 地址 / 账号 / 密码**（密码 19 位与原密码一致）
→ 点「测试连接」→ 「已连接，1 个条目」✓

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
  只保留 **≥3 条**的类型、最多 **6 行**、每行最多 15 张。**详情没补齐就没有分类行**（坑 57 那条因果）。
- 搜索时只留过滤后的网格（分类行与随机推荐收起）。
- 注意：用户的屏幕密度被调过（`wm density` 覆盖成 680）→ 屏宽只有约 339dp，**一行只放得下 3 张**。
  横滑行本来就能滚，别按「一屏 5 张」去设计卡片宽度。

**2026-10-16 追加（用户要求：推荐卡片一次一张、更大、带推荐理由；分类与影片间距加大）**：

- 随机推荐改成 **`FeaturedRow`**：**一次只显示一张大卡片**（`HorizontalPager` 横滑翻页 + 下面页码点），
  卡片 **16:9**、标题 / 元信息（★ · 年份 · N 集）/**推荐理由**叠在压暗的图上，右上角仍是「换一批」。
- **推荐理由只写真数据推出来的**（`ui/library/RecommendReason.kt` + `RecommendReasonTest` 9 条）：
  按优先级 你收藏过的 → 看到 62% · 接着看 → ★8.4 高分 → 你收藏的《X》也是动画 → ★7.6 口碑不错 → 摆类型。
  **推不出来就不画那行**，不编「猜你喜欢」这种没依据的话。
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

**踩到的**

- ⚠️ **`adb shell pm install /sdcard/...` 会报 `Can't open file`** —— Android 15 分区存储下
  `/sdcard/Download` 里那个文件属主是**浏览器 uid**、mode 700，shell 读不到。
  **不是包的问题，别据此判断「下载坏了」。** 要装就 `adb install -r <本地文件>`
  （先用 sha256 证明两边字节一致，再装）。
- **nginx 给 `.apk` 的 MIME 是 `application/octet-stream`**（标准 `mime.types` 里没有 apk），
  于是三星浏览器会弹「此类文件可能危害您的手机」、文件名预览成 `MediaDock-0.2.0.apk.bin`。
  **实际落盘仍是 `.apk`、大小正确、能装**，所以不影响功能。想去掉这个吓人的提示，
  给 `mediadock` 那个 location 加一条 `types { application/vnd.android.package-archive apk; }` 即可
  （**没做**，属打磨项）。

**以后发新版**：改 `app/build.gradle.kts` 的 `versionCode`/`versionName` → 构建 →
传进 `/var/www/portal/mediadock/` → 同步改 `version.json` 的 `versionCode` 与 `url`。
⚠️ **`versionCode` 只增不减**，否则已升上去的用户收不到后续更新。

### 换成正式签名（2026-10-16）

**为什么**：0.2.0 是 `assembleDebug` 出来的 debug 签名包 —— `debuggable=true`（关掉一部分 ART 优化、
任何人插上 adb 就能读应用数据）、未裁剪。而**换签名是不可逆的**：已装用户必须卸载重装，所以越早换成本越低。

**做法**

- keystore 生成在**仓库外**：`E:\code\keystore\mediadock-release.jks`（RSA 4096 / 30 年 / `CN=MediaDock`）。
  同目录 `README.txt` 写了备份说明**和密码**（这样整体备份一个目录就够）。
- `local.properties`（**已 gitignore**）里 4 行：`mediadock.storeFile / storePassword / keyAlias / keyPassword`。
  `app/build.gradle.kts` 读它建 `signingConfigs.release`；**没配 keystore 的机器照样能
  `assembleDebug` / 跑单测**，只是 `assembleRelease` 出未签名包。
- ⚠️ **keystore 和密码必须备份** —— 丢了以后所有更新都无法覆盖安装，没有补救办法。
  **仓库里只有路径，没有密码。**
- **minify 仍关着**：R8 会裁掉 Room / kotlinx-serialization 依赖的反射，要开必须同时补 keep 规则
  并跑一轮真机验证，属于独立的一步。即使不开，release 也比 debug 小 30%（26.5 MB → **18.6 MB**）。

**debug → release 的迁移流程（真机上跑通过，可复用）**

签名不同不能覆盖安装，必须卸载重装：

1. `run-as` 把 `databases/library.db` + `-wal` + `-shm` 捞出来（debug 包可读）
2. 本地 `PRAGMA wal_checkpoint(TRUNCATE)` 合并成单文件、`integrity_check` 确认没坏
3. 卸载 → 装**临时把 `isDebuggable` 打开**的 release 包 → `run-as mkdir -p databases` + `cp` 还原库
4. **去掉 `isDebuggable`** 重新构建 → 覆盖安装（同签名 = 普通更新，数据保留）
5. 复验：`run-as` 报 `package not debuggable` = 开关确实关上了

> 第 3 步那个「临时可调试」只是为了还能用 `run-as` 写文件。正式包关掉 debuggable 后 `run-as` 就用不了了 —— **这是对的**。

**⚠️ 这次迁移的代价（重要，别重蹈）**

**DataStore 里的凭据全丢了。** 因为 Android 在**卸载时销毁该应用在系统 Keystore 里的密钥**，
而 WebDAV 密码 / TMDB 凭据都是用它 AES-GCM 加密存的 ——
**所以哪怕提前备份了 DataStore 文件，装回去也解不开**（坑 60）。

- 媒体库（106 影片 / 进度 / 手动改名 / 详情）**靠上面的流程完整保住了** —— 它在 Room 里，不受 Keystore 牵连。
- **救不回来的只有走 Keystore 的那类**：WebDAV 地址/账号/密码、TMDB 密钥，必须重填。
- **下次要迁移，先让用户在 App 里点「分享给朋友」把配置复制到剪贴板**
  （那条路径输出的是**明文**地址/账号/密码），备份下来再卸载 —— 这样才免重填。

**验证**：0.3.0 装机后 `run-as` 报 `package not debuggable`、签名 `CN=MediaDock`、
库在设备上直接读出来是 106/2/2/96、站点 APK sha256 与本地一致。

### 0.3.1 · 播放页控件加压暗层（2026-10-16）

**坑 62：播放页的控件是白字，压在亮画面上会直接消失。**
详情页当初撞到同一个问题时，解法是「压在亮处的那几行字各加一层投影」。播放页当时没处理，
因为常看的片子画面偏暗，没暴露。这次实测放到《秒速五厘米》**白底字幕卡**那一帧时，
**返回箭头和标题整条不见了**（同一个画面上锁形图标、进度条都还在，因为它们本来就在暗处）。

→ 做法：在控件层的最底下、顶栏与底栏各自的带状区域加一条**竖直渐变**：
顶 `Black .62 → 透明`（高 96dp）、底 `透明 → Black .70`（高 124dp）。
**只压控件所在的那两条带**，画面中间一点没动。见 `ui/player/PlayerScreen.kt` 的 `PlayerControls`。

> 顺带：这类「白字压亮画面」在播放器里几乎必然遇到（字幕卡、雪景、纯白转场）。
> 加压暗层比给每个控件加投影省事，也不会让字看着发虚。

**发版**：`versionCode 3 → 4`、`versionName 0.3.0 → 0.3.1`，站点 APK 与 `version.json` 已同步，
发版页（`E:\code\portal\mediadock\`）上三处硬编码版本号与「版本历史」也已更新 ——
**这就是那个「下次发版别忘」清单第一次实跑，照着走没漏。**

### 0.3.2 · 取消不再误报 + 分享带上 TMDB（2026-10-16）

**坑 63：`catch (e: Exception)` 会把 `CancellationException` 一起吃下去。**
只要它在协程里，`cancelScan` / `cancelScrape` / 取消字幕搜索就**不会干净地取消**，
而是被转成一条「网络错误 / 搜索失败」的业务失败。全项目扫出 18 处，其中 **16 处在协程路径上**，
都补了 `catch (e: CancellationException) { throw e }` 再落到原来的 `catch (e: Exception)`。
另 2 处（`PixelCopy` 截图、存相册）不在协程里，保持原样。
> 判据：**这个 try 里有没有挂起调用**。有才需要这层，无谓地加只会让代码变吵。

**分享配置带上 TMDB**：生成时多一行 `TMDB 密钥：xxx`，朋友那边在「添加数据源」自动识别时一并存进设置。
⚠️ **只在 `parse()` 认得这是分享配置时才取 TMDB** —— 否则剪贴板里随便一段带「tmdb」字样的文本
就会把用户的密钥覆盖掉。单测锁了这条，另加一条「多出来的行不能干扰地址/账号/密码的解析」的回归。
（69 → 73 条）

**顺手修的**：`gradle.properties` 里写死的 `org.gradle.java.home` 去掉了 —— 那是机器相关路径，
留着 Linux CI 直接构建不了。本机的指向挪到用户级 `$GRADLE_USER_HOME/gradle.properties`。

### 0.3.3 · 迷你播放条（2026-10-16）

**坑 64：外壳加常驻控件，不能复用播放页那个 MediaController。**
播放页的 `PlayerViewModel.onCleared()` 就是「离开播放页就暂停」的触发点（用户明确要的行为，见坑 37）。
把那个控制器挪到外壳复用会让它失去触发点。→ 新建 `NowPlayingViewModel`（**外壳级，活动作用域**）
自己连一个 MediaController；两个控制器连的是同一个 MediaSession，谁按暂停都真的暂停。
`onCleared()` 里**只放开控制器、不暂停** —— 那是界面没了（退到后台继续放才是要的功能）。

**顺手修掉一个老限制**：`PlaybackSelection` 只活在内存里，进程被杀后再进播放页会看到
「没有选中要播放的文件」（原注释写着「可接受」）。现在点迷你条会先
`syncSelection()` 从控制器把队列读回来，这条路通了。

**验证**（`uiautomator dump`，非截图）：浏览页播一个文件 → 返回 → 底部出现
`标题 + 已暂停 + 播放/停止` 三个节点；**「已暂停」同时证明坑 37 那条行为没被破坏**。
另：首次返回没生效（当时正在旋回竖屏），再按一次才退出来 —— 属操作时序，不是缺陷。

### 0.5.0 续 · 搜索搬到下面（2026-10-17）

用户：「将搜索也放在下面，还是参考 nagomusic」。照它的底栏：**左边三个 Tab，右边一颗圆形搜索按钮**，
搜索不再挂在首页顶上，而是点按钮进**独立的搜索页**。

- 媒体库页的**吸顶搜索框整个删掉**（连带那个 `StickyBar` 调用），只在任务运行时留一条状态条。
- 新增 `Routes.SEARCH` + `ui/search/SearchScreen.kt`：顶部输入框 + 下面直接是海报墙。
  **过滤规则一个字没重写** —— 复用 `LibraryViewModel.visible`（背后是 `LibrarySearch` 那个有单测的纯函数）。
- 搜索页用的是**新的 ViewModel 实例**，`query` 是它自己的 —— 离开再回来是空的，不会和媒体库页串。
- 底栏用 `Row { NavigationBar(weight 1f, windowInsets = WindowInsets(0)); FilledIconButton }`：
  **`windowInsets` 必须清零**，否则导航栏自己吃一次底部安全区、按钮没有，两者会一高一低。
- 进搜索页自动弹键盘（`FocusRequester`），不然还要再点一下输入框。

⚠️ 写脚本补 import 时踩了个小坑：`'import ...layout.Row' in s` 被 **`RowScope`** 命中了（子串匹配），
`Row` 的 import 没加上。**判 import 要按整行比，别用子串。**

> ⚠️ 2026-10-17 这一批**没能装到真机**：`adb devices` 是空的（手机没连）。
> 编译 + 82 条单测全过，但**界面没在设备上看过** —— 等手机接上要补验。

### 0.5.0 · 主题三态 + 「我的」页 + 上一集下一集 + 播放历史（2026-10-17）

用户给了 NagoMusic 的三张截图，要求「全部参考 nagomusic」，四条一起做：

**1. 主题三态（跟随系统 / 浅色 / 深色）** —— 照它的 `theme_mode_selector.dart`：
三个**并排**的选项（手机 / 太阳 / 月亮图标），不是循环切换的单个按钮。
放在 **设置 → 外观**（它的位置也是 设置→外观）。
- `ThemeMode` 枚举 + `SecureCredentialStore.themeMode` Flow（**不是机密**，放这里只为省一个
  DataStore；`clearSource()` 不碰它 —— 退出登录不该重置主题）。
- 图标是**手画的三个矢量** `res/drawable/ic_theme_*.xml`：Material 核心图标里没有太阳月亮，
  而为三个图标引 `material-icons-extended` 会把包撑大二十多 MB，不值。
- ⚠️ **踩回坑 6**：`fun setThemeMode(...)` 和 `var themeMode` 自动生成的 setter 撞名 →
  改名 `pickThemeMode`。**凡是 `var xxx` 就别再写 `fun setXxx`。**

**2.「我的」页收编 ⋯ / 浏览 / 设置** —— 照它的 `profile_page.dart`：
右上角齿轮 + 三组（资源库 / 媒体库 / 应用）的「图标 + 标题 + 副标题 + ›」行。
- 底部 Tab 从四个变**三个**：媒体库 / 收藏 / 我的（浏览与设置移入「我的」）。
- 媒体库页右上角那个 ⋯ **删掉了**，扫描 / 刮削 / 补齐搬进「我的 · 媒体库」组，
  并且**副标题直接显示实时状态**（已扫 N 个 / 刮削 x/y / 补齐失败几条）。
- 动作复用 `LibraryViewModel`：仓库是应用作用域的，两个页面拿到的是同一份状态。
- 抽了 `NavHostController.switchTab(route)` —— Tab 栏和「我的」里的「收藏」都走它，
  两处各写一份的话回退栈会越堆越深。

**3. 播放页上一集 / 下一集** —— 中间变成「上一集 / 大播放键 / 下一集」。
队列本来就是「同目录的视频」，所以 S01E01、EP01 这类同名分集天然覆盖。
没有上/下一集时按钮**置灰**（`hasPreviousMediaItem()` / `hasNextMediaItem()`），别让人点了没反应。
图标同样手画（`ic_skip_previous.xml` / `ic_skip_next.xml`）。

**4. 播放历史** —— 按 `playback_progress.updatedAt` 倒序。**不用新建表**：
进度表本来就有这个时间戳，是播放进度的副产物。
名字和海报从媒体库那份查；查不到（从浏览页直接播、没进过库的）退回文件名 —— 照样能点开接着看。
点一条会把**同目录的视频**一起当队列，和从浏览页点进来的行为一致（自动下一集）。

**单测**：82 条（这几项都是界面/查询，纯逻辑没新增；`MediaDao` 的新查询由编译期校验）。

### 0.4.1 · 本地媒体两个真问题（2026-10-16，用户实测反馈）

用户试了 0.3.6 的本地媒体，反馈「要扫描很久，还得自己找文件夹」。两条都是真问题，成因不同：

**坑 68：`DocumentFile` 的每个属性访问都是一次 ContentResolver 查询。**
`LocalFile` 最初的实现用 `DocumentFile.listFiles()` 遍历，然后逐个读
`child.isDirectory` / `.name` / `.length()` / `.lastModified()` —— **每个都是一次独立查询**。
一个目录 100 个文件 ≈ 400 次 IPC。
→ 改成 `DocumentsContract.buildChildDocumentsUriUsingTree` + **一次 query 把要的列全取回来**，
每个目录只发一次请求。相对路径就是「子项 document id 去掉树根 id 前缀」，与
`LocalMedia.childDocumentId` 互为逆运算。**别再改回 DocumentFile。**

**「还得自己找文件夹」→ 加「扫描系统媒体库」（MediaStore）作为推荐入口。**
一次权限弹窗（13+ 的 `READ_MEDIA_VIDEO` / 12- 的 `READ_EXTERNAL_STORAGE`）就够，不用选目录，
整库一次查询就回来。**实测 255 个视频 / 27ms。**
- 媒体库的条目存的就是它自己的 `content://media/...` —— **跨重启稳定**，直接当播放地址用，
  不用像 SAF 那样存「树 + 相对路径」再现拼。所以 `LocalMedia` 现在认两种本地前缀。
- 两条路**互斥**（跑一条会清掉本地那一半），免得同一个文件被两种方式各扫进来一份。
- 代价：只看得见系统媒体扫描器已索引的。少数情况会漏（`nomedia` 目录等），那时用「指定文件夹」兜底。

**顺带查清一件事**：用户之前选的 `视频/` 文件夹里**只有 1 个视频** —— 我一度怀疑新扫描器漏文件，
用 MediaStore 反查确认了它是对的。而他的视频其实散在 `Pictures/WeiXin/` 156 个、
`DCIM/Camera/` 69 个、`DCIM/Screen recordings/` 16 个……**根本不在一个文件夹里** ——
这正好说明「选文件夹」这条路对他不合适，媒体库才是对的入口。

**坑 69（我自己引入又自己抓到的）：扫描完成后提示不更新。**
`scanMediaStore()` 里写了 `localHint = "正在读系统媒体库…"` 就再没改过，扫完那句还挂着，
**看起来像卡死**（我因此白等了 45 秒才去查真实结果）。
→ 改成**订阅 `library.scan` 状态**，提示跟着 Running / Done / Failed 走。
**教训：别把「进行中」的文案写死，它得有出口。**

**另一处加固**：某个目录 query 抛异常时，原来只 `getOrNull()` 悄悄跳过 —— 全扫不到时会显示成
「这个文件夹是空的」，把「没权限」说成了「没有文件」。现在记下「有目录读不动」，
一个都没扫到就报「读不了」而不是「空」。

**验证（真机）**：媒体库扫描 255 个 / **27ms**；影片数 **106 → 361**（WebDAV 那 106 条完好，
证明「按前缀删」的设计经住了）；SAF 那条对一个已知只有 1 个视频的目录报 1 个（与 MediaStore 反查一致）。
单测 79 → **82 条**。

### 0.4.0 · 换新图标（2026-10-16）

图标是用户在 `E:\code\icons\` 里自己做的（方案 G「折面播放」：青柠圆角播放三角 + 一道
斜切口翻成青绿），导出包在 `E:\code\icons\export\`，连 README 和生成脚本一起给了。

按它的 README 走**矢量方案**（比 PNG 那套省事、各密度都清晰）：

    cp android-vector/ic_launcher_{background,foreground,monochrome}.xml  res/drawable/
    cp android-vector/ic_launcher.xml                                    res/mipmap-anydpi-v26/

- 顺带启用了 **Android 13+ 主题图标**（新的 `ic_launcher.xml` 带 `<monochrome>` 那行）。
- manifest 里只引了 `@mipmap/ic_launcher`，**没有 `roundIcon`**，所以不用管圆形那套。
- 原来 drawable 里是**金色占位图标**（`#E8B44A`），换成青柠折面。
- 前景用的是 `<group scaleX/Y="0.78">` + 渐变（`aapt:attr`）。0.78 是导出包算好的：
  安全圆直径 66/108 = 61.1%，标记最远点 77%，0.611/0.77 ≈ 0.78 —— **别随手改**，
  改大了圆形遮罩会啃掉三角左上/左下的尖角。minSdk 26 ≥ 24，矢量渐变可用。

**验证**：`aapt2 dump resources` 确认 APK 里有 `drawable/ic_launcher_{background,foreground,monochrome}`
与 `mipmap/ic_launcher`，且 `$ic_launcher_foreground__0/__1` 说明渐变成功编进去了
（release 构建会把资源名缩短，所以按文件名 `unzip` 是查不到的，得用 aapt2）。

### 0.3.7 · 日志落盘 + App 内日志页（2026-10-16）

之前出问题只能靠 logcat —— 对朋友来说等于没有（要么截图截不全，要么连电脑）。
现在 `AppLog` 把日志写进 `filesDir/logs/app.log`，设置 → 关于 → **运行日志**里能直接看、复制、清空。

- **两个纪律写在 `AppLog` 的注释里，加日志时守住**：
  ① **绝不写凭据、密码、文件内容** —— 这个文件会留在用户手机里、也可能被他复制给别人；
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
   刻意不加列 —— 那要写迁移，而迁移是这个项目最容易出事的地方（坑 39/45）；
   纯字符串约定写错也只是扫描结果不对，碰不到用户数据。
3. **⚠️ 扫描必须「按前缀删」，不能再整表删。** 原来是 `deleteAll() + insertAll()` 整表替换，
   两个来源共用一张表后会**互相扫掉**。改成：WebDAV 扫描删 `NOT LIKE 'local/%'`，
   本地扫描删 `LIKE 'local/%'`。**改动 `start()` 时最容易忘的就是这条。**

**其余接线**：`PlayerViewModel` 按前缀解析地址（本地 → content URI），
整队都是本地时不再要求「WebDAV 数据源可用」；本地不做同级目录 PROPFIND（没这个概念）。

**单测**：新增 `LocalMediaTest` 6 条（往返、前导斜杠、**WebDAV 路径不被误认**、
**「localx/」不算本地条目**、document id 拼接、前缀本身）。69 → **79 条**。

**验证（如实记）**：
- ✅ 设置页出现「添加本地文件夹…」；点它**系统选择器正常拉起**（`documentsui/PickActivity`）
- ✅ 编译 + 79 条单测全过
- ⚠️ **「选文件夹 → 扫描 → 播放」这条链没能自动化验** —— SAF 选择器吃不下 adb 注入的点击
  （坑 56）。这一段要用户自己在手机上点一次才算验过。

### 0.3.5 · 音轨切换（2026-10-16）

用户的片子里有「国粤日三音轨」这种（ffprobe 实测那个文件**真有 3 条 AAC 音轨**），
但代码里只处理 `TRACK_TYPE_TEXT`，**没有任何音轨入口** —— 播放器自己挑一条就定了。

做法：照字幕菜单那套加一个「音轨」面板（`audioGroups` → `● 当前 / 点击切换`）。
只有 ≥2 条音轨才显示菜单项（一个选项的菜单没意义）。
标签 = `Format.label` → 语言表 → `音轨 N`，再带声道数（`5.1` / `立体声`）。
语言表要自带：media3 报的是容器里的原样标记，MKV 里通常是 **ISO 639-2/T 三字母码**
（`jpn` / `chi` / `yue`），而 `Locale.forLanguageTag` 只认 BCP-47 的两字母形式，喂进去会原样返回。

**坑 67（这次真正的绊脚石，值得单独记）：`onTracksChanged` 只在轨道「变化」时回调，不会补发。**
监听器是从 Compose 的 `DisposableEffect(controller)` 里挂上去的。只要挂上去时播放器**早就 prepare 完了**
（续播、旋屏、从迷你条进来都会这样），那个回调就**永远不会来**，`tracks` 恒为 null ——
表现是音轨菜单整块消失。更坑的是**字幕菜单一直受同一个 bug 影响却没人发现**：
它显示的是光秃秃的「字幕」而不是「字幕（开）」，因为 `subtitlesOn` 也一直算不出来。
→ 修法：挂监听时**先主动读一次 `controller.currentTracks`**。
> 教训：**别假设「事件回调会给你初始状态」**。凡是从事件里攒状态的地方，
> 挂监听时都该顺手读一次当前值。

**顺带调了个缓冲参数**：只动「卡顿后重新起播」那一档（默认 5s → 8s） ——
源是夸克经 VPS 中转（约 3MB/s），带宽一抖就 stall/resume 来回切，多等一点体感顺。
**其余三档保持默认**：调大 `maxBufferMs` 是白吃内存（60s @ 3MB/s ≈ 180MB），没实测依据不动。

**验证**（真机）：
- `tracks attached: 0 groups` → `tracks changed: 4 groups`（日志，证明修复生效）
- ⋯ 菜单出现「音轨」；面板列出 **音轨 1/2/3 · 立体声**，当前那条标 ●
- 点「音轨 3」→ 日志 `audio -> 音轨 3 · 立体声` + `tracks changed` → 重开面板 **● 已移到第 3 条**
- ⚠️ 标签是「音轨 N」而不是「国语/粤语/日语」：**ffprobe 实测那三条轨的 `language` 都是 `und`**、
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
  `ANDROID_KEY_PASSWORD` / `ANDROID_KEY_ALIAS`。除了省掉手工发版，**顺带是本机那份 keystore 的异地冗余**
  （本机丢了就永久不能再更新这个 App —— 见「换成正式签名」那节）。
- CI 复用现有的读取方式：把 4 个键写进 `local.properties`，**build.gradle.kts 一个字没改**。
- **坑 65：`compileSdk = 37` + `compileSdkMinor = 2` 的平台目录叫 `android-37.2`，runner 上没预装**，
  得 `sdkmanager --install "platforms;android-37.2"`（build-tools 同理）。
- **坑 66：`gradlew` 从 Windows 提交会丢可执行位**，CI 上直接 `Permission denied`（17 秒就红）。
  修法是 `git update-index --chmod=+x gradlew` —— **别在 CI 里打 `chmod` 补丁**，那样对克隆的人也还是坏的。
- **验证**：CI 跑通 8m38s，下载它产出的 APK，`apksigner` 报 `CN=MediaDock, O=lovelev, C=CN`
  且证书指纹与本地构建一致 —— 确认 CI 出的是**真签名包**，不是没签名的半成品。

### ⚠️ 诊断教训：`dumpsys media_session` 里混着别的 App 的会话

**这一条值得单独记，因为它让我误判了整件事**：
用 `adb shell dumpsys media_session | grep -oE "state=..." | tail -1` 读播放状态时，
读到的是**另一个 App 的会话**（它的 custom actions 是「关闭/收藏」，我这边根本没有），
于是看到 `PAUSED` 就断定「播放坏了」，还据此回退了本来能用的自绘控件。
实际上我的会话当时是 `BUFFERING, position=399227`（**正在播**，而且那个位置正是我点进度条 70% 的目标值
—— 说明 seek 一直是好的）。

**正确做法**：只认 `package=com.lovelev.mediadock` **紧跟的那一段**状态，别用 `tail -1`。
（真机一台机器上可能有多个媒体会话：音乐 App、浏览器、系统媒体恢复等，都会混进来。）

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

> 顺带结论：官方市场里针对这个项目**只有 kotlin-lsp 值得装**。`code-review` / `simplify` / `run` /
> `frontend-design` / `security-review` / `impeccable` 本来就可用，重复安装只会吃常驻 token 且互相打架。

---

## 8. 待办

1. **剩下的真机验证项**（其余都过了，见「真机验证结果」一节）：
   - **同级目录字幕**：找个带 `.srt/.ass` 的片子播放，看字幕是否自动上（语言标记为空时要靠
     `selectUndeterminedTextLanguage` 才会自动选）、播放器控件里的字幕按钮能否切换。
   - **分季分集的「N 集」聚合**：找一部剧（同目录 ≥2 集）扫进库，看海报墙是否聚成一块带「N 集」。
   - **自动播放下一集**：让一集放完，看是否自动续下一集、进度是否各自记。
   - **海报块那条 3dp 进度条**：看有没有（数据通路已被详情页验证）。
2. **Stage 8 之前先想清楚要什么**：8 个阶段都写完并验过一轮了。候选见附录 B ——
   截图 / 顶部导航 / 左右滑动 / 日志面板 / 音频均衡器 / 在线字幕搜索 / OCR 等。
3. **验证凭据持久化**：杀掉 App 重进，确认不用重填（Keystore 解密链路）。
4. **可选**：按 `tmdbId` 把「拆成 Season 1/Season 2 两个目录」的剧合并成一块。
5. ~~**可选**：`git init` 纳入版本管理~~ ✅ **已完成**（2026-10-16，私有仓库 `Lebai280/MediaDock`，见代码地图那节）。
6. ~~**可选**：让用户插 USB，以便 adb 装包 + 读 logcat~~ ✅ **设备已连上**（2026-10-16：
   三星 S21 Ultra，序列号 `R5CR128MCLH`，`adb devices -l` 可见；已装的就是当前源码版本）。
7. **可选**：播放页真正全屏（见 Stage 3 的遗留项）。
8. **可选**：TMDB 匹配精度 —— 实测两字标题会被泛化匹配（`银河` → 《银河护卫队》）。
   要改善得加辅助判据（年份容差、片长、投票数阈值）。
9. 功能清单与成本分级见**附录 B**；另一份「想法」文件的采纳/拒绝理由见**附录 A**。

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

# 装到手机
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

### 已经满足的（不用动）

- 凭据加密存：Keystore + AES-GCM ✓（Stage 1 就做了）
- 错误不要抛冰冷错误码：所有 `WebDavException`/`TmdbException` 都翻成了人话 ✓
- 深色沉浸式界面 + 界面退居幕后 ✓（深墨底主题，播放页全黑）
- 对比度 ≥ 4.5:1：**算过了**，明暗两套配色里最低的一对（浅色 primary/surface）是 5.19:1，
  全部达标，不需要改。
- 触控热区 ≥ 48dp：列表行 14dp 上下内边距 + 正文行高 ≈ 48dp，海报块 > 104dp，
  M3 的 TextButton 自带 48dp 最小高度 ✓
- 海报 `contentDescription` 已设（用解析出的片名）
- Range 续传/seek：ExoPlayer 本来就这么干（验收项就是拖进度条秒跳）✓
- 硬解失败降级软解：media3 自动处理，不用自己写 ✓

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

### 保留待办（有用但还没做）

见下面附录 B 的功能清单。

---

## 附录 B：功能清单与成本分级（来自同一份「想法」文件）

按「改动量 / 收益」排的，**不是承诺**，只是下次挑活时的依据。

### 便宜（各 1–2 小时的量级）

- ~~**倍速播放**~~ ✅ 已做（Stage 6）
- ~~**自动播放下一集**~~ ✅ 已做（Stage 6，同目录作队列）
- ~~**危险操作二次确认**~~ ✅ 已做（Stage 7 的「清空媒体库」「退出登录」）
- ~~**截图**~~ ✅ 已做（播放页「⋯」菜单里，`PixelCopy` 取一帧存相册）
- **底部/Tab 结构调整**：外壳只有 `MainActivity.HomeScaffold` 一个文件，
  加一个 Tab（比如「我的」）≈ 5 行。**顶部目录栏、左右滑动页也都是加法，改动集中在壳层。**
- **危险操作二次确认**：Stage 7 的「清缓存/退出登录」照做即可。

### 中等

- **分季分集 + 一键播整剧**：数据早就有了（`season`/`episode` 已落库），
  差的是按剧集归并的界面 + 详情页。建议先做 `ui/detail/`，否则点海报直接播体验很跳。
- **字幕样式调节**（位置/颜色/边框/背景）：media3 `SubtitleView` 支持，还没做。
- **音频均衡器**：Android `Equalizer` + ExoPlayer 的 audioSessionId，能做但要想清楚 UI 放哪。

### 贵 / 优先级最低

- **在线字幕搜索**：要接第三方字幕站（如 OpenSubtitles），要额外账号、限流和合规考虑，
  量级和当初接 TMDB 差不多。
- **GIF 导出**：要引入编码器（FFmpeg 或自写），体积和复杂度都不小。
- **OCR 文字识别**：要 ML Kit。
- **视频调色**：要 GL shader / `Effect` 管线。

> 一句话建议：**先把「播放体验」那一批（倍速 + 自动下一集 + 进度记忆）做完**，
> 它们便宜、彼此相关、且都是每天都会用到的；分季分集要配详情页一起做；
> OCR / 调色 / GIF 这类先放着。

---

## 附：用户协作偏好（来自长期记忆，务必遵守）

- **稳定优先**：改配置时稳定/安全 > 整洁；先备份、改完必验、拿不准先只读诊断。
- **文案**：界面上去掉复述已可见状态的整句；空状态只留最短标签；真信息（上限/快捷键）保留。
- **记录**：决策/结果/坑/下一步要落到项目文件（就是本文件），不记流水账。
- 中文交流。
