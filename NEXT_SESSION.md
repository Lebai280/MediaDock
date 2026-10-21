# MediaDock 当前迭代交接

## 开始前

1. 阅读 `E:\code\MediaDock\WORKLOG.md`。其中包含当前状态、强制约束、代码地图、问题记录与命令速查。
2. 会话工作目录应为 `E:\code`。长期记忆按项目目录分桶，本项目对应 `E--code`；在子目录启动会话将无法加载既有记忆。
3. 本文档只描述当前迭代的交接信息；长期架构见 `ARCHITECTURE.md`。

## 强制约束

1. **401/429 不得自动重试。** OpenList 的登录失败锁定按来源 IP 计数；nginx 反向代理使所有客户端显示为 `127.0.0.1`，任一客户端密码错误可能导致所有 WebDAV 客户端被锁定。代码中已设置多道防线，不得移除或绕过。
2. 允许对线上 WebDAV 发起少量、单次探测请求（已获授权），但必须遵守：不重试；出现 401/429 立即停止；不高频执行 PROPFIND。凭据不得写入仓库。
3. **数据库变更必须使用真实迁移。** 新增表或列必须写入 `MediaDatabase.MIGRATIONS` 数组，并同步提升 `MediaDatabase.VERSION`。破坏性回退已移除，仅保留降级方向；缺失升级迁移会在打开数据库时抛出异常。版本号必须单一来源；变更后必须通过 `MigrationCoverageTest`。进度、收藏、改名与详情属于用户数据，不是缓存。
4. 退出登录仅调用 `clearSource()`，不得调用 `clear()` 清空整个 DataStore，否则会同时删除 TMDB 凭据。
5. 修改完成后必须安装到真机验证，不得仅以编译通过作为完成标准。验证需说明检查项并提供日志证据，无需提供截图。
6. SDK、Gradle 缓存与工具链统一安装至 E 盘。
7. 发版节奏：完成一批修改后统一提升版本并发布，不逐项发版。发布流程见 `WORKLOG.md`（APK → version.json → 发布页；versionCode 只增不减）。
8. 文档新条目按「背景 / 变更 / 验证 / 备注」书写，使用正式书面语。
9. **不得用同包名/正式签名的构建跑 `connectedAndroidTest`**：Gradle 会在测试结束卸载该包，
   清空真实数据库、DataStore 与 Keystore 凭据（见 WORKLOG 问题 76）。

## 构建、测试与设备

```bash
cd /e/code/MediaDock
export JAVA_HOME="E:\code\tools\jdk21" && export GRADLE_USER_HOME="E:\code\.gradle"
./gradlew :app:assembleRelease          # 签名包（签名配置读取 local.properties）
./gradlew :app:testDebugUnitTest        # 98 个单元测试，无需真机
```

CI 已接入（`.github/workflows/build.yml`）：推送 `master` 自动执行单元测试并构建签名包；推送 `v*` 标签自动发布 Release。签名密钥以 base64 形式存储在仓库 Secret 中。

### 真机自动化验证注意事项

- 真机使用 `input tap`；MuMu 模拟器使用 `input swipe X Y X Y 150`（同点按住）。
- `uiautomator dump` 返回物理坐标；Git Bash 下需设置 `export MSYS_NO_PATHCONV=1`。
- 播放页控件 4 秒后自动隐藏。自动化点击必须将「唤醒控件」与「点击目标」放在同一条 adb 命令中；中间等待超过 4 秒会导致点击失效。
- 中文文本不得通过 awk/shell 提取：Git Bash 的 GBK 编码会破坏参数。坐标应硬编码，或使用 Python 按 UTF-8 解析。
- 读取播放状态时仅匹配 `package=com.lovelev.mediadock` 紧随的片段；设备上可能存在其他应用的媒体会话。
- `screencap` 不包含 SurfaceView 上的视频层，其结果不能用于判断播放是否正常；应检查日志中的 position。
- 设备屏幕密度已调整为 `wm density 680`（屏宽约 339dp），单行可显示 3 张海报。
- TMDB 访问依赖代理（设备端 Clash）。`ping api.themoviedb.org` 不通不代表 HTTPS 不通。
- 媒体库权限可通过 adb 授予：`adb shell pm grant com.lovelev.mediadock android.permission.READ_MEDIA_VIDEO`。
- 自动化操作前检查 `topResumedActivity`；微信通话或前台切换会抢占点击。发现前台不是 MediaDock 时立即停止自动点击。

## 当前状态（2026-10-19）

**`0.11.0`（versionCode 26）已安装至真机；`0.10.0` 为 E1.3 分页版，`0.9.0` 为 E1.2 刮削版。**

- 0.5.7：扫描后自动刮削新增条目；媒体库排序与「仅未看」筛选；多版本按 TMDB ID 合并并在详情页切换；用户片单；搜索页按参考版式重做（筛选行 + 三列网格）；详情页横幅半屏；DB v8（片单表）。
- 0.5.6：详情页重做、在线匹配整页列表、DB v7。
- 0.5.5：手动匹配修复；文案精简。
- 0.5.4：分季聚合、刮削缓存、圆角导航。

**数据源**：OpenList `movie1`–`movie8` 的 `base_path` 已由 `/夸克/电影` 更新为 `/夸克/影视`（目录重组），重启 OpenList 后 WebDAV 根恢复 207。
**最近一次扫描**：280 个视频；自动刮削执行 90 条、匹配 0 条（设备端 TMDB 不可达）。恢复代理后可手动执行「刮削」重试。

**2026-10-18 加固（未发版）**：媒体流 401/429 不再由 ExoPlayer 自动重试（`WebDavLoadErrorPolicy`）；凭据解密失败时强制返回「添加数据源」，不再发送空密码请求。98 条单元测试全部通过；release 包已使用同一签名覆盖安装并完成回归。

**2026-10-18 命名解析修正**：季目录内的 `29.简日双语.mp4` 等序号前缀现识别为集数（单元测试 98 条）；「葬送的芙莉莲」已验证为 38 集（库中 38），第 2 季 29–38 正常。主页已移除排序与「全部」筛选胶囊；详情横幅与首页大卡在仅有海报时改为完整显示。命名规则与限制见 `WORKLOG.md` 第 6 节。

**2026-10-18 文档与文案规范化**：`ARCHITECTURE.md` 完成第二版修订；`README.md`、`NEXT_SESSION.md` 按企业级文档重写；`WORKLOG.md` 统一用语；界面空状态与错误提示改为简洁正式表述；TMDB 凭据、OpenSubtitles API Key 与密码改为掩码显示。构建、98 条单元测试与真机覆盖安装验证均通过。

**2026-10-18 S1–S4 完成**：S1 迁移/兼容 VIEW、S2 中文 FTS4、S3 SMBJ + 自研 DLNA 均已记录。
S4 在 S21 Ultra 实测：显示支持 HDR10/HLG/HDR10+（无 DV）；HEVC/VP9 硬解、AV1 仅软解、
4K60 HDR10 无丢帧；AC-3/E-AC-3 可解，TrueHD/DTS 无解码器（静音且无提示）。
FFmpeg：nextlib 为 GPL-3.0（arm64 +7.5 MiB）；官方 Media3 扩展仅音频。结论见 ADR-026。

**2026-10-19 0a 完成**：v9 数据层已切换 —— `:core:model` / `:core:database` 模块、
UUID/内容模型、兼容 VIEW、升级前自动备份与 shadow compare 均落地。真机由 0.5.7 覆盖升级：
280 文件 → 127 作品 / 139 集；「葬送的芙莉莲」38 集完整；shadow compare 280 行 0 差异；
回滚演练与写路径集成测试通过。用户数据表在升级前为空，迁移后仍为空。
项目接受开源，采用 GPL-3.0（ADR-027）。

**2026-10-19 0b 完成**：`:core:network`（WebDAV/TMDB/字幕/更新检查）与 `:core:data`
（`LibraryQueryRepository`、`MetadataScraper`、`PathIdentity`）已拆出；`TitleParser` /
`MediaTypes` 移入 `:core:model`。依赖方向 model ← database/network ← data ← app。
0.7.0 真机覆盖安装后媒体库与 WebDAV 播放正常（`PLAYING`，无 401/429）。

**2026-10-19 Phase 1 E1.1 完成**：v10 增加 `scan_runs` / `directory_cache` / `scan_queue`；
扫描改为 `ScanWorker`（WorkManager、dataSync 前台、唯一名 `scan:<sourceId>`），支持目录缓存、
增量跳过、断点续跑与 401/429 封锁。真机 280 文件：首轮 53 目录/280 文件；增量
1 目录/0 文件；中途取消后从 5 目录/92 文件续跑到 52 目录/280 文件。0.8.0 已覆盖安装。

**2026-10-19 Phase 1 E1.2 完成**：v11 给 `scrape_tasks` 加唯一索引；刮削改为持久队列 +
`ScrapeWorker`（唯一名 `scrape`），支持每日预算、单条指数退避、401 整轮停止。
真机 13 个待刮削作品入队；当前 TMDB 网络不可达，任务按退避保留，取消不丢任务。
0.9.0 已覆盖安装。

**2026-10-19 Phase 1 E1.3 完成**：首页海报墙改为 `works` Paging 3（30/页），
卡片携带集数、进度、收藏与全部文件 path；随机推荐 5 部、分类行取最近 200 部，不再全量读库。
真机 127 作品分页展示、系列卡点入详情正常；`WorksPagingTest` 通过。0.10.0 已覆盖安装。

**2026-10-19 Phase 1 E1.4 完成**：搜索改为 FTS4 + SQL 筛选/排序 + Paging；
索引覆盖标题/改名/类型/演员，启动时按版本重建。真机索引 127/127，中文与筛选测试通过。
同日发生一次「同包名 connected 测试卸载真实包」事故，数据已从导出副本恢复、凭据已重填；
该诊断入口已删除（见 WORKLOG 问题 76）。0.11.0 已覆盖安装。

**2026-10-19 Phase 1 E1.5 完成**：`Stress20kTest`（仅 `.debug` 包）经生产写层播种
20,000 文件 / 5,000 作品 / 16,000 集，真机实测：首屏 p95 356.7 ms（目标 ≤ 1 s）、
搜索 p95 94.7 ms（目标 ≤ 300 ms）、整库 167 页滚动每页 p95 330 ms、
DB 20.3 MB（目标 < 200 MB）、PSS 峰值 255.7 MB（目标 < 300 MB）。**Phase 1 全部完成。**

**2026-10-20 无解码器音轨提示（0.12.0）**：播放页对 TrueHD/DTS 等无解码器音轨显示常驻提示
（1.5 秒防抖），音轨菜单标注「无解码器」；真机用 DTS 单轨与 AAC+DTS 双轨样本验证通过，
release 包已覆盖安装。发现同一队列内重新选择条目仍播旧条目的既有问题。

**2026-10-20 同队列重选条目修复（0.12.1）**：`PlaybackSelection` 增加选择令牌，
播放页只在「新选择」时应用 startIndex，配置变更/迷你条恢复不重设。真机验证：
同队列 A→B 正确切换；「下一集」后触发配置变更不回退；迷你条恢复继续原进度。0.12.1 已覆盖安装。

**2026-10-20 片单完整链路验证**：详情 → 加入片单（新建 / 选择已有）→ 我的 → 片单 →
打开 → 从片单播放 → 删除片单，全部真机通过；无产品代码变更，临时片单已清理。
剧集条目从片单播放只播代表文件（整剧展开仍属「片单增强」）。

**2026-10-20 多数据源并存（0.13.0）**：Phase 2 代码接入——WebDAV 多账号、SMB（SMBJ）、
Emby/Jellyfin（Catalog 同步 + Direct Play）；`source:<id>/<key>` 限定路径、加密多源注册表、
按源扫描与聚合进度、按源 401 隔离、最长根前缀鉴权路由、数据源管理界面与四类型添加表单。
不做跨源合并（E2.5 推迟）。单元测试 91 条与 core:model 全部通过；`MultiSourceStoreTest`
与 `SourceRegistryTest` 真机通过。**双 WebDAV 源真机验收完成**：双源扫描 560（280+280）、
两个源分别播放 `PLAYING`、删除第二源后复扫回到 280；SMB / Emby / Jellyfin 无测试环境，验收后补。

**2026-10-20 SMB 目录浏览（0.13.1）**：浏览页支持 SMB 源（源选择器列出全部文件型源，
SMB 每次导航单次列举）；WebDAV 浏览回归正常。刮削重试执行一轮：匹配 1 条、26 条未匹配
（任务清空，剩余属 TMDB 匹配精度问题）。SMB 浏览真机验收待共享环境。

**2026-10-20 TMDB 匹配精度与首页首屏（0.13.2）**：TMDB 匹配改为候选打分（标题关联/年份/
原名/热度），无标题关联直接淘汰；首轮无结果时尝试标题变体与去年份，并跨类型兜底一次；
记录未匹配与成功匹配日志。首页 `works` 增加 `(added_at, id)` 索引（v12 真迁移），
RECENT 分页走索引。20k 压测：首屏 p95 **356.7 → 53.0 ms**，滚动每页 **330.2 → 45.9 ms**，
总滚动 50.1 → 5.9 s。真机 0.13.2 覆盖安装、v11→v12 迁移成功。当前匹配 2 条 / 18 条未匹配，
剩余为 1934–1995 年奥斯卡短片（TMDB 无对应中文标题），适合手动匹配。

**2026-10-20 片单增强与 E2.5 跨源合并（0.13.3）**：片单页改有界查询；剧集条目播放展开整季，
电影只播选中版本；新增排序与分享片单。E2.5：刮削/扫描跨源合并同一作品，版本按
pinned > 优先级 > 本地 > 体积排序，详情页版本标签显示来源；非 401/429 播放错误自动换版本。
真机：双源扫描后《千与千寻》仍为单条目、两个来源版本，切到 Second 版本 `PLAYING`；
删除第二源复扫回到 280。片单导入与版本 pin 入口待做。

**2026-10-20 E1.7 完成（未提升版本号）**：新增 `AppViewModel` 与 `UiFactories`，
`MainActivity` 的 `HomeScaffold` / `AppNavHost` 不再接收 `AppContainer`；新增
`SourcesViewModel`、`HistoryViewModel`、`PlaylistsViewModel`、`PlaylistDetailViewModel`，
`MineScreen` / `SearchScreen` 改为接收 ViewModel。数据源/片单/设置/详情/播放器的对话框与
选择状态已迁入 ViewModel，AddSource 剪贴板改为每个 ViewModel 只自动应用一次。
单元测试 **102 条通过**，真机逐屏回归通过；`ui/` 下仅 ViewModel 构造函数引用容器。
剩余：`DetailScreen` 等超长文件仍待治理。

**2026-10-20 E1.6 完成（未提升版本号）**：`LibraryRepository` 从 627 行缩为 175 行门面，
拆出 `ScanCoordinator`（270）、`ScrapeCoordinator`（125）、`MetadataMatchService`（125）、
`PlaybackVersionRepository`（73）；`ScanState`/`ScrapeState` 移到 `LibraryStates.kt`，
`PlayableVersion` 改为顶层类型。新增本地文件映射与版本排序单测，**109 条单测通过**；
真机扫描 280、自动刮削、详情版本区与播放 `PLAYING` 均正常，无 FATAL。
v1.0 门槛（Phase 0 + Phase 1）至此达成；下一步转入 Phase 2 环境验收。
剩余治理：`DetailScreen` 等超长文件与 UI 层持续拆分。

**2026-10-20 Phase 3A（E3.1）完成**：新增 `DeviceCapabilities`/`StreamInfo`/规格徽标规则；
DB v13 `stream_info_cache`（真实迁移）；全源按需探测（独立 ExoPlayer prepare 读 Format，
Room 缓存、并发 2、失败 UNKNOWN）；设置页「播放能力 / 诊断」与播放页「诊断」对话框；
详情页徽标改由真实流信息驱动。core:model 53 条 + app 123 条单测通过；真机验证 v13 迁移、
能力页、1080P 徽标、实际解码器/丢帧均正常，无 FATAL。应用显示名改为 **Mooring**，
包名/applicationId 与更新链不变。下一步：Phase 3B（HDR 直出/刷新率/音频直通）。

**2026-10-20 Phase 3B（E3.2）完成，0.14.0 已发布**：新增 `PlaybackPlan`/`PlaybackPlanner`、
播放设置（帧率匹配 自动/关闭、音频声道上限 自动/5.1/立体声）；`PlaybackService` 只在创建时
读取并应用；设备能力页补直通 MIME；诊断增加 HDR/分辨率/帧率/声道与“计划 vs 实际”；
设置页新增「播放」区。真机 4K60 HDR10 样本：计划直出 HDR10、硬件解码、60fps、丢帧 0；
修复 Media3 与 MediaFormat 的 HLG 常量差异。音频直通/DV/HDMI 未验证也不承诺。
core:model 58 + app 125 条单测通过。版本 **0.14.0（versionCode 34）**，显示名 Mooring。
下一步：Phase 3C（tone mapping / DV 基础层 / FFmpeg 软解）。

**2026-10-21 Phase 3C（部分）：FFmpeg 软解回退（按要求未真机验证）**：接入
`io.github.anilbeesetti:nextlib-media3ext:1.11.1-0.16.0`（GPL-3.0）；`PlaybackService`
使用 `NextRenderersFactory` 的 `ON` 模式，FFmpeg 只在平台无可用解码器时接管；
设备能力探测并入 FFmpeg MIME，修正 TrueHD/DTS 被误判为「无解码器，将静音」的问题。
覆盖视频 H.264/HEVC/VP8/VP9/AV1/MPEG-1/2，音频 AAC/MP3/AC3/EAC3/TrueHD/DTS/Vorbis/Opus/FLAC/ALAC/AMR/G.711；
**不含 RMVB（RV40）与 DivX/MPEG-4 ASP，Media3 也不支持 RealMedia 容器**——旧文档里
「RMVB / AVI 可播」的预期不成立。原生库压缩存放，APK 5.1 MB → 19.8 MB，未裁剪 ABI。
单测 183 条通过；DTS/TrueHD 实际出声与软解功耗均待下次装包补验。

**2026-10-21 Phase 3D（部分）：字幕字号与画中画（真机验证）**：设置页新增字幕字号
（小/标准/大/特大）；仅非 ASS/SSA 字幕且字号非标准时改字号，字体、颜色、底色、描边沿用原文件；
选中特效字幕时完全不碰。播放页「⋯」新增画中画，Android 12+ 播放中按 Home 自动进入，
小窗内隐藏自绘控件，离开播放页关闭自动进入。真机验证：PiP 小窗 792×446 且播放继续
（`pip mode -> true/false`）；特效字幕恢复原黑底白字；同一 SRT 轨字形高度标准 67 px → 特大 100 px
（×1.49）。单测 126 条通过。特效判定看 `Format.codecs`（`text/x-ssa`），
不能看被 Media3 归一化的 `sampleMimeType`（恒为 `application/x-media3-cues`）。
未验证：`configChanges` 后的旋转回归。

**2026-10-21 0.15.0 已发布**：vc35，APK 19,793,481 B，sha256 `98bbac8c…e5cec1`；
站点 APK / version.json / index.html 已更新并经 HTTPS 验证；GitHub master 与 v0.15.0 已推送
（22 端口被拦截，走 ssh.github.com:443）；真机已覆盖安装，数据保留。

**版本沿革**：0.3.0 首个正式签名版 · 0.3.1 播放页压暗 · 0.3.2 取消提示修正与分享携带 TMDB · 0.3.3 迷你播放条 · 0.3.4 底栏与搜索栏尺寸调整 · 0.3.5 音轨切换 · 0.3.6 本地媒体 · 0.3.7 日志落盘 · 0.4.0 新图标 · 0.4.1 本地媒体性能修复与系统媒体库扫描 · 0.5.0 主题三态、「我的」页、上一集/下一集、播放历史、搜索移至底部 · 0.6.0 Phase 0a 数据层 · 0.7.0 Phase 0b 网络/数据模块 · 0.8.0 Phase 1 E1.1 增量扫描 · 0.9.0 Phase 1 E1.2 持久刮削 · 0.10.0 Phase 1 E1.3 works 分页 · 0.11.0 Phase 1 E1.4 FTS 搜索 · 0.12.0 无解码器音轨提示 · 0.12.1 同队列重选条目修复 ·
0.13.0 多数据源并存（Phase 2 代码接入）· 0.13.1 SMB 目录浏览 ·
0.13.2 TMDB 匹配精度与首屏索引 · 0.13.3 片单增强与 E2.5 跨源合并 ·
0.14.0 Phase 3A/3B 设备能力、播放计划与 HDR/音频诊断 ·
0.15.0 FFmpeg 软解回退、字幕字号与画中画 ·
0.15.1 UI 观感与交互打磨（按压/触觉、骨架屏、详情页视差、双击/长按快进、搜索与深色细节）。

### 界面结构

- 底部三个标签页与右侧圆形搜索入口：媒体库、收藏、我的。
- 媒体库页不设顶部搜索框，进入后展示随机推荐、分类行与海报墙。
- 「我的」页分为三组：资源库（收藏、播放历史、片单、浏览）、媒体库（扫描、刮削、补齐，含实时状态）、应用（设置、版本与日志）。
- 设置 → 外观：主题三态（跟随系统、浅色、深色）。
- 播放页：上一集、主播放键、下一集；更多菜单包含字幕、音轨、详细信息与截图。

## 下一步

1. **SMB / Emby / Jellyfin 真机验收（待环境）**：代码与单测完成；SMB 共享与媒体服务器到位后
   按「添加源 → 扫描/同步 → 播放 → 删除源」验收（SMB 目录浏览一并验证）。
2. **Phase 3C 剩余**：色调映射与 DV 基础层；FFmpeg 部分需补真机验证
   （DTS/TrueHD 出声、软解画质与功耗），以及 `DecoderManager` 运行时切换（硬解失败后回退）。
3. **Phase 3D 剩余**：投屏（DLNA / Cast，需电视环境）、跳过片头尾（需先定标记方案）；
   字幕样式与画中画已实现，待真机回归（小窗出画面、Home 自动进入、旋转行为）。
4. **E2.5 收尾（可选）**：版本 pin 入口、播放失败切换的更多实网场景、跨源版本规格比较。
5. **TMDB 匹配精度（部分完成）**：剩余 18 条为 1934–1995 年奥斯卡短片，建议手动匹配；
   后续可考虑译名数据源。
6. **片单增强（部分完成）**：导入、手动拖拽排序、从片单批量刮削待做。
7. **跨设备同步**：按 D2 暂缓，预留 `SyncProvider`。
8. 完整待办与功能路线图见 `WORKLOG.md` 第 8 节。

长期架构与 10TB / 多数据源改造计划见 `ARCHITECTURE.md`：先完成稳定的 `logicalId`、`sources` 表、增量扫描与持久任务/分页，再实施高级功能。

## 文档规范

完成修改后应及时更新 `WORKLOG.md`（背景、变更、验证、备注）与本文件。重要修改完成后应提交 commit。纯逻辑修改必须同步更新对应单元测试。新增问题按编号追加至问题记录。
