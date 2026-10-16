接手 E:\code\MediaDock 项目（Android 影音播放器，Kotlin + Jetpack Compose）。

**第一步：读 E:\code\MediaDock\WORKLOG.md**（约 1100 行）。那是完整交接文档：现状 / 硬约束 /
代码地图 / 路线图 / 坑清单（**编号到 59**）/ 命令速查 / 附录。读完直接开始，不要重新推导已有结论。

> ⚠️ **新窗口请开在 `E:\code`，不要开在 `E:\code\MediaDock`** —— 长期记忆按项目目录分桶，
> 本项目的桶是 `E--code`；开在子目录会新建 `E--code-MediaDock` 桶、**已有记忆一条都加载不到**。
> （工具链和构建配置都写死在项目文件里，与窗口开在哪无关。）

## 绝对要遵守的约束

1. **401/429 绝不自动重试。** OpenList 的登录失败锁定按来源 IP 计数，nginx 反代让所有客户端都
   显示成 127.0.0.1 —— 任何一个客户端输错密码会把所有人的 WebDAV 锁死。代码里有多道防线，别破坏。
2. 可以用下面这个账号对用户线上 WebDAV 发**少量、单次**探测请求（用户已授权），但必须：不重试、
   见到 401/429 立刻停手、别高频 PROPFIND。其他账号一律不碰。
   **地址 / 账号 / 密码见用户在对话里贴的那段交接提示词** —— 凭据**刻意不写进仓库**
   （仓库要提交，别让密码进 git 历史；要查就去翻用户贴的提示词或长期记忆）。
3. **数据库只能写真迁移**（加表/加列都要），而且**迁移要进 `MediaDatabase.MIGRATIONS` 数组**——
   容器整批注册。破坏性兜底**已经去掉**（只留降级方向）：漏写升级迁移现在是**打开库时抛异常**，
   不再是静默清库。版本号用 `MediaDatabase.VERSION`（单一来源），改版本号必须同步补迁移，
   否则 `MigrationCoverageTest` 跑红。进度 / 收藏 / 改名 / 详情都是用户数据，不是缓存。
   另：退出登录只调 `clearSource()`（别 `clear()` 整个 DataStore，那会连 TMDB 凭据一起删）。
4. **改完要装到真机跑一遍并截图**，别停在「编译通过」（用户明确要的交付标准）。
   真机：三星 S21 Ultra，adb 序列号 `R5CR128MCLH`（`adb devices -l` 确认；多个设备时加 `-s`）。
5. SDK / Gradle 缓存 / 工具一律装 E 盘。

## 构建 / 装机 / 驱动

```bash
cd /e/code/MediaDock && export JAVA_HOME="E:\code\tools\jdk21" && export GRADLE_USER_HOME="E:\code\.gradle" && ./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest        # 69 个单测，本机可跑，不需要真机
```

驱动真机的坑（都实测过）：
- **真机用 `input tap`**；MuMu 模拟器反而要用 `input swipe X Y X Y 150`（同点按住）。
- `uiautomator dump` 的 bounds 是**物理坐标**；Git Bash 下要 `export MSYS_NO_PATHCONV=1`。
- 截图存**项目内路径**（`app/build/verify/`）比 `/tmp` 可靠；偶尔会抓到瞬时空帧，隔几秒再截。
- **`adb push` 的路径要用 Windows 风格**（`E:/code/...`），Git Bash 的 `/tmp/...` adb 认不了。
- **读播放状态只认 `package=com.lovelev.mediadock` 紧跟的那段** —— 机器上还有别的媒体会话会混进来。
- **`screencap` 抓不到 SurfaceView 上的视频层**（常是黑的），别据此判断「播放坏了」——查日志的 position。
- 用户的**屏幕密度被调过**（`wm density` 覆盖成 680 → 屏宽约 339dp），一行只放得下 3 张海报；
  别按「一屏 5 张」设计卡片宽度。
- TMDB 要能通代理（手机上的 Clash）；`ping api.themoviedb.org` 不通 ≠ HTTPS 不通（很多网络屏蔽 ICMP）。

## 现在到哪了（2026-10-16）

8 个功能阶段全部完成 + 界面改造完成，**都在真机上逐屏验证过**：

- **详情页**：整页一体沉浸式 —— 海报按原比例（2:3）完整显示不裁剪，标题等信息压在它下半部，
  下半部分用**海报底部的主色**延伸出去（大图底边的压暗渐变成这个色，图以下同色铺开），
  简介（可展开）/ 分集 / 演职人员都在同一片底色上。取色见 `ui/detail/PosterTint.kt`（坑 51/52）。
- **播放页自绘控件**：顶部 返回/标题/右上角「⋯」菜单（字幕 / 详细信息 / 截图），底部 细进度条 +
  左时间右时长、右下角倍速，**锁定按钮固定在左侧中间**（锁上 3 秒自动隐藏、点一下唤回）；
  左右半屏上下滑调亮度/音量；播放中 4 秒自动隐藏控件。触摸/手势两个坑见坑 53/54。
  截图用 `PixelCopy` 存相册；详细信息是 TMDB 的时长/分辨率/编码/码率。
- **首页（媒体库 Tab）**：**吸顶**全圆角药丸搜索框（照用户站点「星屿」，聚焦描边变柠檬绿）+ 同栏 ⋯
  （扫描/刮削/补齐）；下面是 **随机推荐（一次一张 16:9 大卡片、横滑翻页、带「推荐理由」）→
  分类行（≤6 行、标题靠左带真实条数、数据驱动自 `title_details.genres`）→ 「全部」海报墙**。
- **设置**：数据源（含**分享给朋友**）/ 刮削 / **字幕源** / 媒体库 / **关于**（版本 / **检查更新** /
  **星屿 · 我的网站** / TMDB 署名）。
- **字幕**：源可切换（`SubtitleProvider` 接口，先接了 OpenSubtitles）；播放页「⋯ → 字幕」里
  「关闭字幕 / 已有轨 / **从本地文件加载…** / **在线搜索…**」。下载和本地选的字幕统一落盘到
  `filesDir/subtitles/` 再挂载（`file://` 能读靠的是媒体数据源外面包了 `DefaultDataSource`，坑 55）。
- **纯逻辑都有单测**（69 条）：`LibrarySearchTest` / `GenreRowsTest` / `RecommendReasonTest` /
  `ShareConfigTest` / `UpdateCheckerTest` / `MigrationCoverageTest` / `TitleParserTest` /
  `WallEntryTest` / `MediaTypesTest`。

## 下一步（按优先级）

1. **当前 `0.3.7`（versionCode 10）**，已装真机；**站点上发的是 `0.3.2`**（落后 5 个版本）。
   沿革：0.3.0 首个正式签名版 · 0.3.1 播放页压暗 · 0.3.2 取消不再误报 + 分享带 TMDB ·
   0.3.3 迷你播放条 · 0.3.4 底栏/搜索栏压到 NagoMusic 尺寸 · 0.3.5 **音轨切换**（顺带修了
   坑 67：`onTracksChanged` 不补发，连累字幕菜单）· 0.3.6 **本地媒体扫描** ·
   0.3.7 **日志落盘 + 运行日志页**。详见 WORKLOG 各节。
   **已接入 CI**：推 master 自动跑单测 + 出签名包，打 `v*` tag 自动发 Release ——
   手工发版只剩「传站点 + 改发版页 + 改 version.json」。
   **发版流程**：bump `versionCode`/`versionName` → push → 传 APK 到
   `/var/www/portal/mediadock/` → 改 `version.json` → 改发版页三处硬编码版本号与「版本历史」。
   `versionCode` **只增不减**。keystore 在仓库外（**要备份，丢了就不能再更新**），
   CI 里也存了一份 base64（兼作异地冗余）。
2. ⚠️ **本地媒体那一段没验完**：「选文件夹 → 扫描 → 播放」这条链**没法自动化**
   （SAF 选择器吃不下 adb 注入点击，坑 56），要用户自己在手机上点一次。见 WORKLOG「0.3.6」。
3. ~~手机上需要用户重填凭据~~ ✅ **已完成**（2026-10-16）：WebDAV 与 TMDB 凭据都已填回，
   浏览器里实测过浏览目录、详情页（TMDB 刮削数据在）、播放（position 在走）、内嵌双语字幕渲染。
   媒体库 106 条完整保住，没有重扫。凭据因由见 WORKLOG 坑 60。
3. **等用户配合的两项**：本地字幕挂载（手机上 ⋯→字幕→从本地文件加载…，测试字幕在
   `Download/mediadock-test.srt`）、在线字幕真实请求（设置→字幕源 填 OpenSubtitles 的 API Key + 账号）。
4. **依赖库数据的 4 项**（不是代码问题）：同级目录字幕、分季分集聚合、自动播放下一集、
   海报块那条 3dp 进度条。要验得先往库里放一部带字幕的剧。
5. **可选**：开 R8 minify（现在关着）。能再砍体积，但**必须**同时补 Room /
   kotlinx-serialization 的 keep 规则并跑一轮真机验证（编译过 ≠ 反射没被裁）。属独立一步。
6. 候选功能：多资源切换（按 tmdbId 归并同一部片的多个文件）、片单/举报、海报墙筛选排序。
   成本分级见 WORKLOG 附录 B。

## 文档规矩

改完东西**及时**更新 `WORKLOG.md`（决策 / 结果 / 坑 / 下一步）和本文件 —— 用户明确要求
「方便别人接手」。坑清单是编号的，新坑接着编。改纯逻辑**同步改对应单测**（上面那份清单）。
