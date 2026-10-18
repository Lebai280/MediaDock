# MediaDock 架构与 10TB 改造计划

> 状态：草案（2026-10-18），供评审后按阶段实施。
> 修订：2026-10-18 第二版。评审后补充：v9 迁移映射与逐键校验、身份合并规则、多集单文件、源契约拆分、投屏能力模型、明文流量落地方式。
> 目标：支撑 10TB+ 媒体库、多数据源、网易爆米花级功能；手机/平板优先，TV 与多端同步按决策单独排期。
> 本文只写架构与计划，不含运维信息（WebDAV 地址 / 账号 / 服务器一律不写进仓库）。

---

## 0. 结论摘要

- 现有架构（Kotlin + Compose + Media3 + Room + WebDAV）是合格的 MVP 底座，**不重写**。
- 但当前实现是“单数据源 + 全量列表 + 全量扫描 + 内存任务”的模型，直接叠 10TB 和爆米花级功能会先撞到四个地基：
  1. **数据身份**：用户数据按 `path` 存，改名/移动/多源会错位；
  2. **多数据源**：`StoredSource` 单例、单根目录；
  3. **增量扫描**：全量 BFS + 整表替换 + 2 万文件上限；
  4. **持久任务 + 分页**：扫描/刮削是内存 job，列表是全量 Flow + 内存过滤。
- 建议顺序：**先把这四块地基做完，再加高级功能**；否则每加一个功能都会返工。
- v9 身份迁移**单独发一版**（只切数据层，UI 与仓库结构不动），验证通过后再做重构；迁移与重构不混版本，否则出错无法定位。
- 高规格播放（HDR10 / HDR10+ / HLG / DV / 10-bit / Atmos / 高刷）按 **Phase 3 的 3A–3D** 推进：
  先能力探测与诊断，再直出，再降级/软解，最后投屏与体验项。
- 已定方向（2026-10-18）：手机 + 投屏、TV 单列；暂不做多端同步（预留接口）；
  多源包含 WebDAV / 本地 / SMB / Emby / Jellyfin；身份迁移一次性切换 + 迁移守卫。
- 剩余主题（设置/凭据分层、发现/首页、元数据提供方、播放会话生命周期、网络契约、
  安全/供应链、测试/诊断、自适应 UI、离线下载、用户资料、架构治理）见 **第 14 节**。
- 企业级评审整改与启动门槛见 **第 15 节**：6 个阻塞项、UUID + `logical_key`、兼容 VIEW、
  完整多集模型、4 个 spike；先修阻塞再开工。
- SQLite 不是瓶颈：5k–20k 条视频对 Room 很轻松；瓶颈是扫描请求数、TMDB 配额、海报渲染和状态管理。

---

## 1. 前提与待定决策

### 1.1 本文假设

- 维护规模：1–2 人（含 AI 辅助开发）；交付标准：企业级，强调可维护性、可审计性与可交接性。
- 手机/平板优先；TV、投屏、多端同步作为可选阶段。
- 10TB 主要在 WebDAV / OpenList（可能多个账号或多个 NAS），本地媒体为补充。
- 库规模按 **5,000–20,000 个视频文件、1,000–5,000 个作品**估算。
- 不引入自建后端，除非选择“多端同步”。

### 1.2 已定决策（2026-10-18）

| 决策 | 结论 | 影响 |
|---|---|---|
| TV / 投屏 | **先做手机 + 投屏；TV 单列为 Phase 4** | 当前 UI 不按 TV 重构；投屏在 Phase 3 |
| 多端同步 | **暂不做，预留 `SyncProvider`** | 不引入后端；先做本地导出/导入 |
| 数据源 | **WebDAV + 本地 + SMB + Emby/Jellyfin 都要** | Phase 2 同时支持四类源；复杂度最高 |
| 身份迁移 | **一次性切换 + 迁移守卫** | 工期短；必须有自动备份、校验与回滚 |
| 迁移发版 | **v9 单独发版**，与重构解耦 | 出问题能定位；旧列留到 v10 做 shadow compare |
| 投屏主路径 | **DLNA 优先，Cast 可选** | 国产设备通常无 GMS，Cast 发不出去 |
| 明文流量 | 保留 `http://` + **OkHttp 主机批准清单** | `network_security_config` 是编译期静态配置，无法覆盖用户新增主机 |
| DI | 手写容器，暂不引入 Hilt | 模块化后再评估 |

> 其余待定项见 13.7（D5–D12）；D8 / D11 / D13 / D14 已于 2026-10-18 决定纳入 Phase 5，
> 具体设计见第 14 节。

---

## 2. 现状盘点

### 2.1 应当保留的部分

- **分层边界**：`data/webdav`、`data/local`、`data/repo`、`player`、`ui/*` 职责清楚。
- **Room 是媒体库唯一数据源**：扫描只负责灌数据，海报墙只读 Room；这个原则必须保留。
- **播放架构**：`MediaSessionService` 持有 ExoPlayer，界面用 MediaController；后台播放、进度落库都在服务里。
- **凭据隔离**：`SecureCredentialStore` + Keystore AES-GCM；解密失败视为未配置。
- **硬约束**：401/429 不重试、真迁移、`MIGRATIONS` 数组、版本单一来源。
- **纯逻辑测试**：解析、分组、搜索、分享、更新、迁移链已有 98 条单测。

### 2.2 规模上来会先坏的地方

| 现状 | 10TB / 爆米花级下的问题 | 对策 |
|---|---|---|
| `StoredSource` 单例、单根 | 无法同时挂多个盘/账号 | `sources` 表 + `sourceId` |
| 扫描 `MAX_DIRECTORIES=4000` / `MAX_FILES=20000` | 大库直接撞上限 | 增量扫描 + 可配置上限 |
| 全量 BFS + `deleteAll` / 整批替换 | 每次扫全库，慢且浪费请求 | 目录级缓存 + 差异写入 |
| 内存 job + `StateFlow` | 进程一死扫描/刮削全丢 | WorkManager + 持久队列 |
| `observeAll()` + 内存 `groupWall` / 搜索过滤 | 2 万条会卡、内存高、搜索全表扫 | Paging 3 + SQL 过滤 + FTS |
| 进度/收藏/片单按 `path` 存 | 改名/移动/多源后用户数据错位 | 稳定 `logicalId` |
| 单 `LibraryRepository` 约 650 行 | 扫描/刮削/状态机/映射混在一起 | 拆 Scanner / Scraper / 查询仓库 |
| 详情页/首页按 `tmdbId` 内存合并 | 大库组合复杂、不可分页 | `works` 表 + 版本关系表 |
| 刮削无配额/续跑 | 2 万条 TMDB 会中断、重复请求 | 持久队列 + 令牌桶 + 预算 |

---

## 3. 目标架构

### 3.1 分层与模块

```mermaid
flowchart TD
    APP[:app<br/>组装 / 导航 / 主题] --> F1[:feature:library]
    APP --> F2[:feature:detail]
    APP --> F3[:feature:player]
    APP --> F4[:feature:sources]
    APP --> F5[:feature:settings]
    F1 --> DATA[:core:data<br/>仓库 / 用例 / 任务调度]
    F2 --> DATA
    F3 --> DATA
    F4 --> DATA
    F5 --> DATA
    F1 --> UI[:core:ui]
    F2 --> UI
    F3 --> UI
    F4 --> UI
    F5 --> UI
    DATA --> DB[:core:database]
    DATA --> NET[:core:network]
    DATA --> DS[:core:datastore]
    DATA --> MODEL[:core:model]
    UI --> MODEL
    DB --> MODEL
    NET --> MODEL
    DS --> MODEL
```

- **`:core:model`**：纯 Kotlin 数据模型、`SourceId` / `LogicalId`、纯函数（解析、分组规则）。
- **`:core:database`**：Room 实体 / DAO / 迁移 / FTS。只依赖 `:core:model`。
- **`:core:network`**：WebDAV、TMDB、字幕源等客户端；接口 + 实现。
- **`:core:datastore`**：凭据、设置、主题、同步游标。
- **`:core:ui`**：共享 Compose 组件（海报卡、评分、空态、加载）与主题 token；feature 只能依赖它，不能互相依赖。
- **`:core:data`**：仓库接口与实现、扫描/刮削调度、逻辑身份解析、播放地址解析。
- **`:feature:*`**：Compose 页面 + ViewModel + UiState；**禁止 feature 之间互相依赖**，跨页导航由 `:app` 负责。
- **`:app`**：`AppContainer`（或 Hilt 入口）、导航、底部栏、主题。

> 模块数量控制在 8–12 个（含 `:core:ui`）。模块划分以强制依赖方向、缩短构建时间和降低变更成本为目标，不以数量为目标。

### 3.2 技术框架

**保留**：Kotlin 2.x、Jetpack Compose（Material3）、Media3/ExoPlayer、Room、OkHttp、Coil3、kotlinx-serialization、DataStore、Navigation Compose、R8。

**新增（按阶段）**：

| 能力 | 选型 | 说明 |
|---|---|---|
| 长任务 | WorkManager | 扫描/刮削/同步；前台类型 `dataSync` |
| 分页 | Paging 3 + Room PagingSource | 海报墙按 `works` 分页 |
| 搜索 | Room FTS4（或 FTS5 trigram） | 中文分词需专门处理 |
| 播放软解 | Media3 FFmpeg decoder | RMVB/AVI/10-bit 回退；注意包体与许可证 |
| SMB | SMBJ 或 jcifs-ng（评估后二选一） | SMB2/3；自定义 DataSource 流式播放 |
| Emby / Jellyfin | REST API（OkHttp） | 直接播放/转码；元数据以服务端为准 |
| 投屏 | DLNA（自研 SSDP/SOAP + 本地 HTTP 代理）；Cast 可选 | Phase 3；jUPnP 因 Jetty 依赖不采用（S3） |
| 依赖注入 | 暂时手写容器 | 模块化后按需评估 Hilt |
| 测试 | room-testing / work-testing / 假服务器 | 迁移、仓库、任务 |

**不引入**：NoSQL、跨端框架重写、微服务、为同步先建账号体系。

### 3.3 数据模型（核心）

#### 3.3.1 逻辑身份（最重要）

现有用户数据（进度/收藏/片单/改名）按 `path` 存；10TB 多源场景下必须改成**逻辑身份**：

- 已匹配：`tmdb:movie:<id>`、`tmdb:tv:<id>:s<S>:e<E>`；作品级用 `tmdb:tv:<id>` / `tmdb:movie:<id>`。
- 未匹配：`file:<sourceId>:<remoteKey>`（临时身份）。
- 一个逻辑条目可以对应**多个物理文件**（多版本、多数据源）；播放时选一个。
- 用户数据只引用 `logicalId`，播放位置按“集/电影”而不是文件。
- 文件移动/改名后，扫描重新关联到同一 `logicalId`；未匹配文件被刮削后，执行一次“临时身份 → 匹配身份”的迁移（合并进度/收藏/片单）。
- **未匹配文件移动是已知缺口**：`file:` 身份依赖 `remoteKey`，改名/移动即新身份。处理方式：同源扫描时做“疑似移动”启发式（文件名相似 + size 接近 + mtime 接近），**只生成合并建议，不自动执行**；或明确提示用户先刮削再整理目录。
- **身份合并冲突规则**（临时身份 → 匹配身份、多文件合并到同一逻辑条目时）：
  - 进度：`updated_at` 最新者胜；位置超过新时长按新时长 clamp；
  - 收藏 / 片单：并集；
  - 改名：手动覆盖优先，其余保留最新；
  - 冲突一律写入迁移/合并报告，供诊断页查看。
- `LogicalId` 视为不透明字符串，统一经 `LogicalIdCodec` 生成与解析，禁止各处手拼。

#### 3.3.2 表结构（建议）

```text
sources(
  id TEXT PK, type TEXT, name TEXT, base_url TEXT, root_path TEXT,
  credential_ref TEXT, enabled INTEGER, priority INTEGER, created_at INTEGER,
  last_scan_at INTEGER, health TEXT,
  root_generation INTEGER      -- 根路径变更时 +1，作废旧目录缓存/队列
)

media_files(
  id TEXT PK,                 -- 物理文件 UUID
  source_id TEXT,             -- FK sources
  remote_key TEXT,            -- 归一化键：NFC、去尾斜杠、解码一致
  provider_item_id TEXT,      -- Emby/Jellyfin item id（其它源为空）
  logical_id TEXT,            -- 派生值，可空；多集文件由 file_episodes 决定
  media_kind TEXT,            -- main / sample / trailer / extra
  name TEXT, size_bytes INTEGER, mtime TEXT, etag TEXT,
  scan_run_id INTEGER, missing_at INTEGER,
  UNIQUE(source_id, remote_key)
)

file_episodes(
  file_id TEXT, episode_id TEXT, segment_index INTEGER,
  PRIMARY KEY(file_id, episode_id)
)                             -- 一文件多集、多碟分段

works(
  id TEXT PK,                 -- tmdb:movie:<id> / tmdb:tv:<id> / file:...
  tmdb_id INTEGER, type TEXT, title TEXT, sort_title TEXT, year INTEGER,
  poster_path TEXT, backdrop_path TEXT, overview TEXT, rating REAL,
  total_seasons INTEGER, total_episodes INTEGER,
  collection_id TEXT,         -- 系列电影分组，不等于剧集
  metadata_source TEXT,       -- TMDB / EMBY / JELLYFIN / NFO
  external_ids_json TEXT,     -- 其它服务端 ID（Imdb/Tvdb/Emby 等）
  added_at INTEGER,           -- 首次入库时间（冗余，供“最近添加”排序）
  latest_file_mtime INTEGER,  -- 冗余，避免每页聚合
  updated_at INTEGER
)

collections(id TEXT PK, tmdb_id INTEGER, name TEXT)

episodes(
  id TEXT PK,                 -- tmdb:tv:<id>:s<S>:e<E>
  work_id TEXT, season INTEGER, episode INTEGER,
  title TEXT, still_path TEXT, runtime INTEGER, air_date TEXT,
  UNIQUE(work_id, season, episode)
)

work_versions(work_id, file_id, source_id, quality_label, size_bytes,
              pinned INTEGER, PRIMARY KEY(work_id, file_id))
work_genres(work_id, genre, PRIMARY KEY(work_id, genre))
people(id, name, profile_path)
work_cast(work_id, person_id, role, ordering, PRIMARY KEY(work_id, person_id))

users(id TEXT PK, name TEXT, kind TEXT, pin_hash TEXT, created_at INTEGER)

progress(logical_id, user_id, position_ms, duration_ms, completed INTEGER,
         updated_at, device_id, PRIMARY KEY(logical_id, user_id))
favorites(logical_id, user_id, added_at, PRIMARY KEY(logical_id, user_id))
playlists(id, user_id, name, created_at)
playlist_items(playlist_id, logical_id, added_at, PRIMARY KEY(playlist_id, logical_id))
title_overrides(logical_id, title, year, PRIMARY KEY(logical_id))

subtitles(
  id TEXT PK, logical_id TEXT, file_id TEXT, language TEXT,
  source TEXT, path TEXT, format TEXT, encoding TEXT, downloaded_at INTEGER,
  UNIQUE(logical_id, file_id, language, source)
)

downloads(
  id TEXT PK, logical_id TEXT, file_id TEXT, state TEXT,
  bytes_total INTEGER, bytes_done INTEGER, local_path TEXT,
  created_at INTEGER, updated_at INTEGER, error TEXT
)

scan_runs(id INTEGER PK AUTOINCREMENT, source_id, mode, status,
          started_at, finished_at, dirs, files, error)
directory_cache(source_id, remote_key, etag, last_modified,
                last_scan_run_id, child_count, PRIMARY KEY(source_id, remote_key))
scan_queue(source_id, scan_run_id, remote_key, depth,
           PRIMARY KEY(source_id, remote_key))

scrape_tasks(logical_id TEXT PK, provider TEXT, state TEXT, attempts INTEGER,
             next_attempt_at INTEGER, priority INTEGER, last_error TEXT, updated_at INTEGER)

media_stream_info(
  file_id TEXT PK, container TEXT, video_codec TEXT, profile INTEGER, level INTEGER,
  bit_depth INTEGER, color_primaries TEXT, color_transfer TEXT, color_matrix TEXT,
  hdr_format TEXT, dolby_vision_profile INTEGER,
  width INTEGER, height INTEGER, frame_rate REAL, bitrate INTEGER,
  audio_tracks_json TEXT, subtitle_tracks_json TEXT,
  probed_at INTEGER, probe_source TEXT, probe_error TEXT
)

device_capabilities(
  device_key TEXT PK, display_hdr_types TEXT, max_width INTEGER, max_height INTEGER,
  max_refresh REAL, video_codecs_json TEXT, audio_outputs_json TEXT,
  passthrough_codecs_json TEXT, updated_at INTEGER
)

media_fts USING FTS4(title, aliases, people, tokenize=unicode61)
  -- 中文方案见 D5；若选 FTS5 trigram，需手写建表并纳入迁移管理
```

说明：

- `media_files` 是物理事实，`works/episodes` 是展示与用户数据的逻辑层。
- **一文件多集**（`S01E01-E02`、合集、连播）通过 `file_episodes` 关联；`media_files.logical_id` 只是便捷派生，权威关系在 `file_episodes`。多集文件的进度按“文件”记，播放到片尾时把覆盖的集全部标记完成（见 3.15）。
- `works` 是**分页单位**：海报墙/搜索/筛选都查 `works`，不再在内存里对全量 `media_items` 做 `groupWall`。
- `work_versions` 支持一部电影的多个文件/来源；详情页的版本切换从这张表来；`pinned` 表示用户固定版本。
- 剧集进度按 `episodes.id`（即对应 `logical_id`）记录；作品级“已看几集”由聚合查询得到。
- `progress.completed` 表示“已看完”，用于“未看”筛选与自动下一集；默认 `position >= 90%` 或距片尾 < 2 分钟，可手动标记。
- `works.added_at` / `latest_file_mtime` 是冗余列，由扫描/刮削维护，用于“最近添加”排序，避免分页查询做聚合。
- `subtitles` 持久化外挂字幕索引（语言、来源、编码），文件删除/改名后可重新关联；播放器不再只靠目录约定。
- `playlists` 带 `user_id`，与 Phase 5 多资料一致；片单条目仍只引用 `logical_id`。`title_overrides` 保持库级（不随资料变化）。
- `users` 保存资料（默认资料 + 儿童模式 PIN）；所有用户数据用 `user_id` 关联，迁移时默认填 `default`。
- `media_fts` 是搜索索引，内容来自 `works` + 演员 + 别名；中文分词见 3.6 / D5。
- 收藏 / 片单 / 改名引用**作品级** `logicalId`（电影或剧集）；`progress` 引用集级（电影即作品级）。
- `progress.device_id` 只记录最后写入设备；多设备同步启用后改 `progress_devices` 或每设备一行，避免互相覆盖。

> **评审修订（2026-10-18）**：B1 / B2 的身份与多集模型以 **15.2 / 15.3** 为准；
> 本节表结构是评审前草案，保留用于对照。

### 3.4 扫描框架

**目标**：10TB 库首次扫描可完成；后续增量扫描仅处理变化；支持断点续扫；401/429 不得影响其他数据源或设备。

- **执行载体**：`ScanWorker`（WorkManager，唯一工作名 `scan:<sourceId>`），长任务 `setForeground`；Android 14+ 声明 `FOREGROUND_SERVICE_DATA_SYNC` 与 `foregroundServiceType="dataSync"`。
- **模式**：`INCREMENTAL`（默认）/ `FULL`（用户手动）。
- **扫描代际**：每次扫描生成 `scan_run_id`；`scan_queue` 仅消费当前代际；FULL 重扫或根路径变更时清空该 source 的旧队列，避免新旧任务混用。
- **键归一化**：所有 `remote_key` 入库前经过 `normalizeRemoteKey()`（Unicode NFC、百分号解码一致、去尾斜杠、统一分隔符）；无法归一化的条目记录并跳过，避免唯一约束失效与身份漂移。
- **目录缓存**：`directory_cache` 存 `etag` / `last_modified` / `child_count`；根目录优先；如果集合的 etag/mtime 未变且子项数量一致，可跳过子树（OpenList 的集合元数据若不稳，提供“保守模式”：只跳过文件解析，不跳过 PROPFIND）。
- **差异写入**：
  - 新文件 → 插入 `media_files`；
  - 已有文件 → 比较 `size/mtime/etag`，变化则更新；
  - 目录中缺失 → 标 `missing_at`（软删除），不立即删用户数据；**连续 3 次扫描仍缺失**才清理物理记录；
  - 作品最后一个文件缺失时保留用户数据，作品标记“不可用”，不自动删除。
- **持久队列**：`scan_queue` 存 BFS 待扫目录；进程被杀后按 `scan_runs` 续跑。
- **限速与安全**：每个 source 单飞（串行），可配置 1–2 并发；请求间最小间隔；401/429 **立即停止该 source 的所有请求**，写 `sources.health=BLOCKED`，由全局状态展示；绝不重试。
- **上限**：目录/文件上限改成可配置（默认 200k 目录 / 200k 文件），达到上限时标记为“截断”，不得声明为完整扫描。
- **忽略与分类**：默认跳过隐藏目录、`@eaDir`、`.trash`、回收站、0 字节文件；`sample` / `trailer` / `featurette` 归为 `media_kind != main`，不进作品墙。
- **根路径变更**：修改 source 根路径 = `root_generation + 1`，旧目录缓存与队列作废，触发一次 FULL 扫描；未匹配的 `file:` 身份会重建，UI 必须提示“进度可能无法自动关联”。
- **时长上限**：Android 15 对 `dataSync` 前台服务有 6 小时/24 小时限制；Worker 每 N 条写检查点，被系统停止后自动重新入队续跑。
- **约束偏好**：扫描可选“仅 Wi-Fi / 仅充电”，映射为 WorkManager `UNMETERED` / `requiresCharging`。
- **批量写库**：每 N 条执行一批 `upsert`；不得在单个事务内跨网络请求。

### 3.5 刮削框架

**目标**：2 万条目可暂停、可续跑、按配额限速、失败不阻塞整轮。

- **持久队列**：`scrape_tasks`（含 `provider` 列）；扫描/详情页/手动重试都只入队。
- **Worker**：`ScrapeWorker` + 网络约束；批大小 4；令牌桶限速（按 TMDB 配额可配）；429 按 `Retry-After` 退避；401/凭据错误 → 整源暂停并引导设置；单条失败只影响自己。
- **HTTP 缓存**：TMDB 响应走 OkHttp 磁盘缓存 + ETag；重复刮削/详情页刷新不重复消耗配额。
- **优先级**：用户正在看的作品 > 最近添加 > 其余；失败条目按 `next_attempt_at` 指数退避。
- **预算**：DataStore 记录当日请求数；达到预算自动暂停，第二天/用户手动恢复。
- **匹配**：先精确（标题+年份+季集），失败再去年份；候选结果落库供手动匹配；匹配成功后写 `works/episodes` 并做“临时身份 → 匹配身份”的用户数据合并（规则见 3.3.1）。

### 3.6 媒体库查询与 UI

- **分页**：海报墙/搜索/筛选统一查询 `works`（Paging 3），每页 30–60；筛选、排序在 SQL 完成（类型、年份、未看、最近添加、评分）。
- **未看筛选**：查询 `progress.completed = 0` 或不存在进度；判定规则与自动下一集共用（见 3.15）。
- **搜索**：
  - 英文/数字：FTS4（`unicode61`）或 SQL `LIKE`；
  - 中文：**已定 FTS4 + `search_text`**（CJK 字符间加空格，查询转为短语）；目标机不支持 FTS5 trigram（S2）。
  - 输入防抖 250ms；搜索走 Paging，不一次性加载全库。
- **字幕检索**：OpenSubtitles 的 moviehash 需要读文件，WebDAV 上代价高；优先按标题 + 季集 + 语言搜索，hash 只用于本地/已下载文件。
- **UI 状态**：每个 feature 统一 `ViewModel + UiState`；composable 不得直接拿 `AppContainer`；筛选/排序状态用 `SavedStateHandle` 或持久化。
- **图片**：Coil 磁盘缓存设上限；列表只预取可见页；海报/剧照分尺寸请求；20k 条不做本地缩略图，除非有明确收益。
- **详情页**：作品详情、季/集、版本列表都来自 `works/episodes/work_versions`；按需加载，不在打开时拉全库。
- **未识别作品**：`file:` 作品详情仅显示文件名、大小、路径与“手动匹配”入口，不生成虚假海报或简介。

### 3.7 播放与高规格（HDR / 10-bit / 多声道）

**目标**：高规格片源做到“能直出就直出，不能直出就明确降级”，不静默黑屏、偏色或卡顿。

#### 3.7.1 能力模型

- **`DeviceCapabilities`**：显示支持的 HDR 类型（HDR10 / HDR10+ / HLG / DV）、最大分辨率与刷新率、可用视频解码器（MIME / profile / level / 位深）、音频输出与直通能力（HDMI / eARC / 蓝牙 / 扬声器）、最大声道数。
- **`StreamInfo`**：容器、视频编码 / profile / level、位深、色彩原色 / 传输 / 矩阵、HDR 类型、DV profile、分辨率 / 帧率 / 码率、音轨（编码 / 声道 / Atmos）、字幕轨（文本 / PGS）。
- **探测来源**：
  - 显示：`Display.getHdrCapabilities()`、`Display.getMode()` / `supportedModes`；
  - 解码：`MediaCodecList` + `MediaFormat` 支持检查；**最终以解码器实际初始化成功为准**；
  - 音频：`AudioManager` / `AudioDeviceInfo` / Media3 `AudioCapabilities`；
  - 片源：Media3 extractor 读到 `Format.colorInfo` / `sampleMimeType` / `codecs`；必要时做轻量容器头解析。
- **以实测为准**：`MediaCodecList` / `Display.getHdrCapabilities()` 只是声明能力；解码器以初始化成功为准，显示以实际渲染为准——直出后校验 surface dataspace / 解码器输出 `colorInfo`，不一致按 SDR 处理。
- **缓存**：设备能力每次开机 / 显示变化更新；片源信息按文件版本缓存，**不在扫描时探测 20k 条**。

#### 3.7.2 播放决策（PlaybackPlan）

输入：`logicalId` + 候选文件 / 版本 + `StreamInfo` + `DeviceCapabilities` + 用户偏好。

输出：选中的文件 / 版本、解码器（硬件 / FFmpeg 软解）、HDR 模式、音频模式、显示模式（分辨率 + 刷新率）、字幕渲染方式、失败回退链。

规则：

- 优先选设备**真正能渲染**的最高规格；不以文件名或扩展名判断。
- 版本 tie-breaker：用户 `pinned` > 分辨率 > HDR 类型 > 码率 > 音轨数 > 源优先级。
- **HDR**：显示与解码器都支持 → 直出；否则按格式降级（HDR10+ → HDR10；DV → HDR10 基础层；HLG → SDR）；都不行 → 色调映射。
- **10-bit**：设备支持 10-bit 解码则直出，否则 8-bit 输出或软解。
- **音频**：输出端支持直通 → 直通；否则解码；手机扬声器 / 蓝牙 → 下混；Atmos 元数据在 PCM 解码后丢失，要在 UI 标明。
- **刷新率**：按片源帧率匹配（23.976 / 24 / 25 / 30 / 50 / 60 / 120）；离开播放页 / PiP 时恢复。
- **回退**：解码器初始化失败 → 软解 / FFmpeg；当前版本不可用 → 另一个版本 / 源；401/429 → 标记源封锁，**不重试**。
- **回退执行者**：由 `PlaybackSessionController` 执行；记录已尝试的 `(fileId, decoder, hdrMode)`，同一组合不重试；硬件解码初始化失败直接换软解，不反复试硬解。

#### 3.7.3 渲染与 Media3

- 保留 Media3：`Format.colorInfo`、`DefaultTrackSelector`、`DefaultRenderersFactory`（可加 FFmpeg 扩展）。
- **HDR 直出**：SurfaceView / PlayerView 输出 HDR；控件层保持 SDR 安全色；不得在 HDR 画面上叠加会提高亮度的半透明层。
- **色调映射**：优先硬件 / Media3 effect；4K 软件色调映射代价高，必须检测并提示；不支持时明确显示“HDR→SDR”。
- **帧率**：`VideoFrameReleaseHelper` + 显示模式；避免在后台 / PiP 切换模式。
- **音频**：`DefaultAudioSink` / `AudioCapabilities`；HDMI / eARC 直通；蓝牙 / 扬声器下混。
- **字幕**：文本字幕样式；PGS 等图形字幕按 Media3 能力处理，不支持时明确提示（可后接 FFmpeg）。
- **进度落库**：播放中每 5–10 秒写一次，暂停/切集/退后台/结束时立即写；避免高频 DB 写。
- **诊断**：播放信息面板显示实际解码器、HDR 模式、音频输出、丢帧、码率；写日志（不含凭据）。

#### 3.7.4 探测与缓存

- 首次播放 / 打开详情页时按需探测；用 Range 请求只读容器头；超时或失败标记 unknown，不阻塞播放。
- 缓存键：`fileId + size + mtime/etag`；文件变化自动失效。
- 探测结果用于：详情页规格徽标（4K / HDR10 / DV / Atmos）、播放决策、筛选（“只看 HDR”）。
- 不在扫描时全量探测；可选后台“补齐媒体信息”任务按优先级慢慢补。

#### 3.7.5 格式与设备策略

- **HDR10**：主流设备支持；直出。
- **HDR10+**：设备支持则直出，否则按 HDR10 静态元数据播放。
- **HLG**：设备支持则直出，否则 SDR 映射。
- **Dolby Vision**：profile 5 / 8 / 7 差异大；仅在设备 / 解码器明确支持时直出；否则用 HDR10 基础层或 SDR 映射。**不承诺所有设备支持 DV**。
  - 部分设备“声明支持但实际绿屏/紫屏”：维护已知设备 allowlist + 用户开关；首次 DV 播放异常自动回 HDR10 基础层并记录诊断。
- **音频**：AAC / AC3 / EAC3 / TrueHD / DTS；直通依赖 HDMI / eARC 与功放；手机端按能力下混。
  - `AudioDeviceInfo` 无法完整查询接收端格式：采用“尝试直通 → 初始化/播放失败回 PCM 解码”，诊断面板显示实际输出模式。
  - 默认音轨按用户语言偏好，其次原语言；选择结果持久化。
- **视频编码**：HEVC Main10 / AV1 / VP9；硬解优先，FFmpeg 回退。
- **容器**：MP4 / MKV；MKV 的 PGS / 多音轨要按轨道处理。
- **投屏**：国产设备通常无 GMS，Media3 Cast 不可用；**DLNA 为主路径**，Cast 可选。
  - DLNA 控制器自研：SSDP 发现 + SOAP（AVTransport / RenderingControl）；不使用 jUPnP
    （Android 模块依赖未声明的 Jetty，兼容线老旧）。
  - **本地 HTTP 代理**：WebDAV / SMB / 本地文件无法被电视直接读取（Basic 认证 / 非 HTTP），
    手机需起一个带 Range 的临时 HTTP 服务，只暴露当前会话、随机路径、结束即停。
  - Emby / Jellyfin 优先 Direct Play URL；确认电视可访问且不转码；转码提示 HDR/Atmos 降级。
  - 投屏前用 `RendererCapabilities` 重新计算 PlaybackPlan；移交失败回本地。
  - DLNA 无鉴权：只在可信局域网、需用户确认、不暴露凭据。
- **Emby / Jellyfin 转码**：优先 Direct Play；转码会丢失 HDR/Atmos，必须在 UI 标明
  “转码中，画质/音质可能降级”；能力协商以客户端能力为准。

#### 3.7.6 测试矩阵

- **设备**：S21 Ultra（HDR10+ / HEVC 10-bit / 高刷）、一台 DV 设备（如有）、SDR 模拟器、Chromecast / TV、HDMI / eARC。
- **片源**：HDR10、HDR10+、HLG、DV P5 / P8、4K60 HEVC、AV1、TrueHD Atmos、DTS-HD、PGS 字幕。
- **验收**：支持的格式直出且规格徽标正确；不支持时按预期降级并给出原因；音频直通 / 下混符合预期；刷新率匹配；无持续丢帧；401/429 不重试。

### 3.8 多数据源

**范围（已定）**：WebDAV（OpenList，可多账号）、本地（SAF / MediaStore）、SMB、Emby / Jellyfin，全部在 Phase 2 支持。

- **契约拆分**：目录型源实现 `FileSource.list(parentKey)`；Emby/Jellyfin 实现 `CatalogSource.sync(cursor)`（items 分页 + 增量），扫描器走两条路径，`scan_runs` 对 catalog 源记录“同步条目数”而不是目录数。
- **注册**：`sources` 表 + 添加/编辑/停用界面；每个 source 独立凭据（Keystore 加密）、根路径、优先级、限速、健康状态。
- **WebDAV**：沿用现有客户端；多账号各建一个 source；401/429 全局封锁。
- **本地**：SAF / MediaStore；离线可播；SAF 授权被收回时把源标为 `AUTH_REQUIRED`，提示重新选择目录。
- **SMB**：主机 + 共享 + 凭据；SMB2/3 客户端（SMBJ / jcifs-ng 评估后定）；自定义 `DataSource` 支持 Range 流式播放；认证失败映射为 `AuthError`，不重试风暴。
- **Emby / Jellyfin**：
  - 服务器地址 + 凭据；只读元数据可用 API Key，播放/进度回写用用户 token。
  - 条目列表、元数据、播放地址来自服务端；`ProviderIds` 里的 TMDB ID 用于与其它源合并，没有 TMDB ID 时用服务端 item id 作临时身份。
  - 播放优先 **Direct Play**；转码会丢失 HDR/Atmos，必须在 UI 标明“转码中，画质/音质可能降级”。
  - 服务端已有元数据，**不重复 TMDB 刮削**；`works.metadata_source` 标记来源。
  - 播放地址可能带 token：日志、分享、诊断包必须脱敏。
  - 可选把进度回写服务端（后置），本地进度始终为准。
- **合并**：同一作品/集按 `logicalId` 合并；`work_versions` 保留每个来源的文件。
- **选源顺序**：用户 `pinned` 版本 > `priority` 数值小者（0 最高）> 本地 > 健康源 > 规格更高者；失败按顺序切换，不回头重试已失败源。
- **去重**：同一作品按 `tmdbId + season/episode` 合并；同一集的不同编码是版本，不是重复条目；无法识别时保留为独立版本，不得强行合并。
- **删除 source**：默认保留 `works` 与用户数据，删除该源的 `media_files` / `work_versions` / 凭据；作品无剩余文件时标记“不可用”。删除前二次确认并显示影响条目数。
- **OpenList 多账号**：每个账号建立一个 source，共享逻辑库；不得假设所有账号的根路径一致。
- **源健康**：每源独立 `SourceHealth`；一个源 401/429 不影响其它源。

### 3.9 同步（可选）

- **接口**：`SyncProvider { suspend fun pull(since): SyncDelta; suspend fun push(delta) }`。
- **实现 A（推荐先做）**：WebDAV 上的 JSON 文件（用户自己的网盘），按设备分文件，最后写入者优先；适用于小规模多设备场景。
- **实现 B**：VPS 上的轻量 API + 用户令牌；需要服务端与冲突处理，后置。
- **同步内容**：进度、收藏、片单、改名；不同步凭据。
- **冲突**：按 `updatedAt` 最后写入者赢；片单/收藏可做并集；删除用 tombstone。

### 3.10 凭据与安全

- 每个 source 独立加密存储；解密失败 → 视为未配置，回到添加数据源（已实现）。
- `android:allowBackup` 排除凭据 DataStore；或接受恢复后强制重登。
- 明文流量：保留局域网 http 支持，但在添加数据源页对 `http://` 明确警告；后续可做按主机白名单。
- 分享功能：二次确认，默认不包含 TMDB 密钥。
- 日志：不写凭据/URL 中的密码/文件内容；手势类日志异步化。

### 3.11 可观测性与测试

- **日志**：结构化 tag（scan/scrape/player/sync）；落盘 + 可在设置里查看；20k 库的扫描/刮削进度可在“我的”页看到。
- **测试**：
  - 纯逻辑：解析、分组、筛选（现有）；
  - Room：`MigrationTestHelper` + 导出的 schema；v8→v9 起每一步都测；
  - 仓库：假 WebDAV（JDK HttpServer）+ 假 TMDB + 内存 Room；
  - WorkManager：`work-testing`；
  - 关键 UI：少量 Compose UI 测试（可选）；
  - 压力：生成 20k 条合成数据，测扫描写入、分页、搜索、内存。

### 3.12 状态管理与 UI 架构

- **单向数据流**：ViewModel 暴露 `StateFlow<UiState>`；界面只发事件（筛选、点击、重试），不直接改仓库。
- **UiState**：每个页面一个不可变状态（`Loading / Empty / Content / Error`）；错误是状态的一部分，不使用全局弹窗作为回退。
- **分页 + 筛选**：`PagingData<WorkCard>` + 筛选条件；筛选变化用 `flatMapLatest` 重建 Pager；筛选/排序状态进 `SavedStateHandle`，进程被杀可恢复。
- **导航**：单 Activity + NavHost；路由用类型化定义（或集中常量）；深度链接预留 `mediadock://play/<logicalId>`、`mediadock://detail/<logicalId>`。
- **跨页选择**：播放/详情请求放 `SavedStateHandle` 或持久化的 `PlaybackRequest`，不再用应用级可变单例；进程死亡后可恢复。
- **Compose 性能**：稳定 key、`@Immutable` UI 模型、`derivedStateOf`、仅加载可见页图片；不得将 Room 实体直接作为 UI 模型（字段变化会触发重组）。
- **TV（如实施）**：独立 `:feature:tv`，处理 DPAD 焦点、焦点恢复与 overscan；手机 UI 不承担 TV 适配。
- **无障碍 / 国际化**：contentDescription、对比度 ≥ 4.5:1、TalkBack、RTL（已有 `supportsRtl`）；中文/英文资源分离。

### 3.13 错误模型与源健康

**统一错误类型**（UI 不得解析错误字符串）：

> **评审修订（2026-10-18）**：Provider health 与 Source health 必须分离，详见 **15.5**。
> 下表把 TMDB / 字幕的 401 标成“源 BLOCKED”的条目以 15.5 为准。

| 类型 | 来源 | 行为 |
|---|---|---|
| `AuthError(401)` | WebDAV / TMDB / 字幕 | 源进入 `BLOCKED`；不重试；引导重新登录/填凭据 |
| `RateLimited(429)` | WebDAV | 源进入 `BLOCKED`；不重试；提示等待或联系管理员 |
| `RateLimited(429)` | TMDB / 字幕 | 按 `Retry-After` 退避；配额问题不无限重试 |
| `NotFound(404)` | WebDAV | 标记文件/目录缺失；不重试 |
| `Forbidden(403)` | WebDAV | 路径级权限问题：跳过该目录/文件并在扫描报告计数；**不升级为整源 `BLOCKED`** |
| `ProxyAuthRequired(407)` | 代理 | 提示检查代理配置；不当作源凭据错误 |
| `ServerError(5xx)` | 全部 | 有界退避（最多 N 次）；不永久封锁源 |
| `NetworkError` | 全部 | 连接层有限重试 + 退避；用户可见 |
| `ParseError` | XML / JSON / 容器 | 记录并跳过该条；不重试 |
| `StorageError` | Room / 文件 | 明确报错；**绝不静默清库** |
| `Cancelled` | 协程 | 原样抛出，不显示为业务失败 |
| `PlaybackError` | 解码/容器 | 按 `PlaybackPlan` 回退；网络错误不触发 401 重试 |

- **`SourceHealth` 状态机**：`HEALTHY / DEGRADED / BLOCKED / AUTH_REQUIRED`，持久化在 `sources.health`；所有请求前先过 `SourceGate`。
- **401/429 是全局闸门**：任何一个入口（浏览、扫描、详情、字幕、播放）收到都置 `BLOCKED`，所有入口停止自动请求；只有用户重新登录或明确恢复才解除。
- **离线**：展示 Room 缓存；网络操作禁用并说明原因；本地媒体仍可播；进度先落本地，恢复网络后同步。
- **测试**：每个客户端都有“401/429 只发一次请求”的单测；新增入口必须遵守。

### 3.14 缓存与离线

| 层 | 内容 | 预算 / 策略 |
|---|---|---|
| Room | 媒体条目、作品、详情、进度、任务 | < 200 MB；WAL；定期 `ANALYZE` |
| TMDB HTTP | 作品/分集 JSON | OkHttp 磁盘缓存 + ETag；30–90 天，省配额 |
| Coil 磁盘 | 海报 / 背景 / 剧照 | 默认 512 MB，可配置；LRU |
| `media_stream_info` | 编码/HDR/音轨探测结果 | 很小；按文件版本失效 |
| 字幕 | 下载/本地字幕 | `filesDir/subtitles` + `subtitles` 表索引；清理旧文件 |
| 日志 | 运行日志 | 512 KB 滚动一份 |
| 视频 | 不缓存整片 | 10TB 远端；可选热点小缓存（后置） |

- **TTL**：TMDB 作品/分集元数据 30–90 天；海报长期缓存；目录缓存按扫描；流信息按 `size/mtime/etag`。
- **失效**：文件变化、源根路径变化、TMDB 版本升级、用户手动“清缓存/重新刮削”。
- **离线体验**：可浏览已缓存海报墙、可看本地媒体；远端播放/扫描/刮削明确提示离线；进度与收藏本地照常写。

### 3.15 媒体识别与特殊结构

- **识别规则**以 WORKLOG 第 6 节为准：集数（`SxxExx` / `EPxx` / `1x03` / 末尾裸数字 / 季目录内开头 `NN.`）、季目录（`S01` / `Season 1` / `第1季`）、片名优先级。
- **特别篇 / OVA / 花絮**：统一放 `season = 0`，显示“特别篇”，**不并入正片集数**；是否合并由用户手动决定。
- **动漫编号**：支持“按季 1..N”和“全剧绝对集数（29–38）”两种；有季目录时以目录为准；无法判断时允许手动指定编号方式。
- **多版本**：同一 `logicalId` 多个文件进 `work_versions`；按分辨率/HDR/码率/音轨排序；用户可固定版本。
- **多碟 / 分段**：同一集多个文件时合并为一个逻辑集，播放时按顺序拼接（后期）；先按版本处理。
- **一文件多集**：`file_episodes` 记录覆盖集；进度按文件记，播放到片尾时把覆盖的集全部标记完成；中途退出只记文件级位置，不猜测集边界。
- **已看判定**：默认 `position >= 90%` 或距片尾 < 2 分钟；用户可手动标记；与“未看”筛选、自动下一集共用同一规则。
- **合集（Collection）**：TMDB collection 是“系列电影”的分组，不等于电视剧；使用独立 `collection_id`，不得混入剧集。
- **重复文件**：同 `tmdbId + season/episode` 视为版本；不重复创建用户数据。
- **未识别**：赋予临时 `file:` 身份并显示“未识别”；详情页仅提供文件名、大小、路径与手动匹配入口，不生成虚假元数据；手动匹配后执行进度与收藏迁移。
- **命名问题政策**：凡由命名或目录结构导致的识别问题，向用户说明问题类别，给出当前规则与推荐写法，由用户调整命名；不得实现仅对特定文件名生效的特判。

### 3.16 搜索与索引

- **查询管线**：输入防抖 250ms → 归一化（大小写、全半角、去首尾空格）→ FTS/LIKE → 筛选 → 排序 → 分页。
- **中文搜索（已定，S2）**：FTS4 + `search_text`（标题/别名/演员/类型，CJK 字符间加空格），
  查询把 CJK 串转为空格短语；目标机不支持 FTS5 trigram。见 ADR-024。
- **筛选维度**：类型、年份、国家、类型标签、演员、来源、未看、4K/HDR（来自 `media_stream_info`）。
- **索引**：
  - `works(type, year, rating, updated_at)`
  - `works(tmdb_id, type)` 唯一（`tmdb_id` 非空时）
  - `works(added_at)`、`works(latest_file_mtime)`
  - `episodes(work_id, season, episode)` 唯一
  - `work_genres(genre, work_id)`
  - `work_cast(person_id, work_id)`
  - `media_files(source_id, remote_key)` 唯一
  - `media_files(source_id, provider_item_id)` 唯一（非空时）
  - `media_files(logical_id)`、`file_episodes(episode_id)`
  - `progress(logical_id, user_id)`
  - `scrape_tasks(state, next_attempt_at)`
  - `subtitles(logical_id, language)`
  - `downloads(state)`
- **排序**：SQL 侧完成；中文标题排序维护 `sort_title`（拼音或笔画），不得依赖数据库默认排序。
- **性能**：FTS 索引 + 分页 + 限制返回；禁止全表 `LIKE '%x%'` 扫描 20k 条。

### 3.17 后台任务与调度

| 任务 | 载体 | 约束 | 前台 |
|---|---|---|---|
| 扫描 | WorkManager，唯一名 `scan:<sourceId>` | 网络；单源串行；Android 15 单次前台 6 小时上限，需检查点续跑 | `dataSync` |
| 刮削 | WorkManager，`scrape` | 网络；配额/退避 | 可选 |
| 补齐详情 | WorkManager，低优先级 | 网络 | 否 |
| 流信息探测 | WorkManager，低优先级 | 网络；按需 | 否 |
| 同步 | WorkManager，`sync` | 网络 | 否 |
| 缓存清理 | WorkManager，周期 | 无 | 否 |

- **前台服务**：Android 14+ 长任务用 `dataSync` 类型 + 权限 + 通知；任务进度写入 Room，UI 观察。
- **优先级**：用户主动触发的用 expedited；后台维护低优先级；同一 source 的扫描/刮削不并发。
- **取消**：每阶段检查 `isActive`；取消不写半份结果；取消不抛业务错误。
- **进程死亡**：任务状态持久化；重启后按 `scan_runs` / `scrape_tasks` 续跑。
- **Doze / 电池**：不自建精确闹钟；依赖 WorkManager；长任务可暂停。
- **测试**：`work-testing` 覆盖约束、重试、取消、续跑。

### 3.18 性能与容量预算

| 项 | 预算 / 估算 | 说明 |
|---|---|---|
| DB 大小 | < 200 MB | 20k 文件 + 5k 作品 + FTS |
| 图片缓存 | 默认 512 MB | 可配置；LRU |
| 应用内存 | < 300 MB | 分页窗口；不加载全库 |
| 冷启动 | < 2 s | 不读全库；懒初始化；Baseline Profile |
| 首次扫描 | 10k ≈ 30–60 min；20k ≈ 1–3 h | 顺序 PROPFIND；200–500 ms/目录；以实测为准；可续跑 |
| 增量扫描 | < 首次的 10% | 目录缓存 + 只处理变化 |
| 刮削 20k 条 | 理想 20–60 min，实际数小时 | 4 并发 + 配额；可暂停 |
| 4K HDR 播放 | 50–100 Mbps | 依赖源站带宽与缓冲 |
| UI 帧率 | 60 / 120 fps | 主线程无磁盘 IO；图片按尺寸加载 |

- 用 Macrobenchmark / Baseline Profile / Perfetto / StrictMode 监控；20k 合成数据是性能验收基线。
- 任何“全量加载 / 全表扫描 / 主线程 IO”都视为性能缺陷，不允许进主干。

### 3.19 数据库运维与数据质量

- **基础设置**：WAL、`synchronous=NORMAL`、`busy_timeout`；大批删除后 `VACUUM` 或 `auto_vacuum=INCREMENTAL`；定期 `ANALYZE`。
- **FTS 维护**：schema 变更后重建；提供“重建索引”调试入口。
- **备份 / 导出**：迁移前自动备份数据库；后续提供“导出用户数据（进度/收藏/片单/改名）”，便于换机。
- **导出格式**：JSON 带 `schemaVersion`、导出时间、校验和；导入时校验版本并给冲突策略（跳过 / 覆盖 / 合并）。
- **完整性检查**：`PRAGMA integrity_check` / `foreign_key_check`；设置页调试入口显示表数量、大小、孤立记录数。
- **数据质量任务**：孤立文件、重复条目、缺失文件、过期元数据、坏片单；只报告 + 可修复，不自动删用户数据。
- **损坏恢复**：打开库失败时明确报错并保留原文件；绝不 destructive 清库。
- **迁移失败恢复的工程约束**：Room 的 `onUpgrade` 抛异常只会回滚事务，不会自动恢复备份。恢复必须在打开数据库之前完成：检测迁移标记 → 关闭并替换 `db` 与 `-wal` / `-shm` → 重新打开；备份放在应用私有目录。
- **迁移前后**：记录版本、行数、校验和；迁移失败可回滚到备份。

---

## 4. 数据迁移策略

### 4.1 版本与前置

1. **先开启 `exportSchema = true`**，用当前 v8 代码生成 `app/schemas/8.json` 并提交（当前 `.gitignore` 忽略了 `app/schemas/`，要移除该规则）。
2. 从 v9 开始，每次结构变更都必须有：
   - `MediaDatabase.VERSION` +1；
   - `MIGRATIONS` 数组新增一条；
   - schema JSON 提交；
   - `MigrationTestHelper` 测试通过。
3. 升级方向绝不允许破坏性回退；降级方向保持现状。

### 4.2 v8 → v9（地基迁移）

> **评审修订（2026-10-18）**：v9 采用**兼容 VIEW**，完整映射与备份/状态文件见 **15.4 / 15.7**；
> 本节旧表结构描述以 15.2 / 15.3 为准。

- 新增表：`sources`、`media_files`、`file_episodes`、`works`、`collections`、`episodes`、`work_versions`、`work_genres`、`people`、`work_cast`、`users`、`subtitles`、`downloads`、`scan_runs`、`directory_cache`、`scan_queue`、`scrape_tasks`、`media_fts`。
- **`media_items` 的去向（必须明确）**：
  - v9 起 `media_items` 只读，不再参与读写；数据复制到 `media_files`（物理事实），内联元数据拆到 `works` / `episodes`。
  - 字段映射见下表。

| 旧字段 | 新位置 | 说明 |
|---|---|---|
| `path` | `media_files.remote_key`（归一化后） | 原始 `path` 列保留作备份 |
| `name` / `size` / `mtime` | `media_files.*` | 直接复制 |
| `tmdbId` + 类型 | `works.tmdb_id` / `works.type` | 无 tmdbId → `file:` 作品 |
| 标题 / 海报 / 简介 / 评分 | `works.*` | 以旧缓存为准，缺失的后续刮削补 |
| 季 / 集 | `episodes` + `file_episodes` | 仅 tmdb 匹配的剧集生成；一文件多集写入 `file_episodes` |
| 进度 / 收藏 / 片单 / 改名 | 按 `logical_id` 回填 | 见下 |

- 若现有详情缓存在独立表，按 `tmdbId` 映射到 `works` / `episodes`；无法映射的保留原表只读，并在报告中计数。

- 现有用户表加列/改造：
  - `playback_progress` / `favorites` / `playlist_items` / `title_overrides`：加 `logical_id`（旧 path 列保留但不再写入）；`playback_progress` 加 `completed`。
- 回填：
  - 默认资料：插入 `users(id='default')`，旧进度/收藏/片单的 `user_id` 全部填 `default`；
  - 默认 source：现有 WebDAV 数据源；本地媒体单独 `LOCAL` source；
  - 凭据：现有单例凭据写入默认 source 的 `credential_ref`，DataStore 键加 source 前缀；解密失败视为未配置；
  - `logical_id`：有 `tmdbId` + 集数 → `tmdb:tv:<id>:s<S>:e<E>`；有 `tmdbId` + 无集数 → `tmdb:movie:<id>`；否则 `file:<sourceId>:<normalizedKey>`；
  - 用户数据按 path 映射到 logical_id；同一逻辑条目多个文件时按 3.3.1 的冲突规则合并；
  - 一文件多集：按解析结果写 `file_episodes`，旧数据无法判断时按单集处理并在报告中列出。
- **一次性切换**（已定）：v9 迁移完成后，App 直接读写新结构；旧列/旧表保留到 v10 作为人工回滚备份，不再参与读写。
- **迁移守卫（必须实现）**：
  1. 迁移前自动备份数据库（含 WAL 检查点）到应用私有目录，并导出用户数据 JSON；
  2. 在事务中完成建表 + 回填 + 切换；
  3. **逐键校验**：不只对行数，对 `(logical_id, position, completed)`、收藏/片单/改名做校验和，前后必须一致；
  4. 生成迁移报告：未映射、多对一、空 tmdbId、身份冲突的条目数与样例；
  5. 任一校验失败 → 回滚事务、恢复备份、阻止进入主界面并提示错误；
  6. 迁移成功也保留备份，直到用户确认新版本稳定；
  7. 提供调试开关做 **shadow compare**：新旧列各读一次，差异写日志，跑 1–2 个版本再删旧列。
- **发布要求**：v9 是高风险版本，必须先跑脱敏 v8 fixture + 真实库副本 + 回滚演练，再发版；版本说明中明确“升级前会自动备份”。
- **单独发版**：v9 只切数据层，UI 与仓库结构不动；通过后再做 Phase 0 剩余重构。当前库规模小（38 集），迁移成本低，重点在映射正确性，不在性能。

### 4.3 迁移验证

- **CI**：`MigrationTestHelper` 覆盖 v8→v9；再加一条“脱敏 v8 fixture”的集成测试。
- **fixture 生成**：用脚本按 v8 schema 生成种子库（含进度/收藏/片单/改名），提交到测试资源；不得使用真实用户数据库。
- **迁移前**：自动备份 + 导出用户数据；记录行数与校验和。
- **迁移后**：核对视频数、进度条数、收藏数、片单数、改名数、详情数、总集数；逐项与备份一致。
- **回滚演练**：人为制造校验失败，确认能恢复备份并阻止进入主界面。
- **真机抽查**：10 个作品（进度、收藏、版本、季集）全部正常。
- 只有以上全部通过，才允许进入下一阶段。

---

## 5. 分阶段路线图

> **v1.0 定义**：Phase 0 + Phase 1 完成（迁移稳定、增量扫描、持久刮削、分页/FTS）。后续 Phase 是增强，不是 v1.0 门槛。

### Phase 0 — 地基（建议先做）

**目标**：不改用户可见行为，先把数据模型和模块骨架搭好。

- **0a 地基（先单独发版）**：开启/提交 schema；`:core:model` / `:core:database` 骨架；v9 迁移（sources + logicalId + 映射表 + 回填 + 逐键校验 + shadow compare）；迁移报告。
- **0b 结构（迁移验证通过后再做）**：`:core:network` / `:core:data` 骨架；`MetadataScraper`、`LibraryQueryRepository` 抽取。
- **后移到 Phase 1**：拆 `MediaScanner`（Phase 1 本来就要重写成 WorkManager 版）、统一 ViewModel / UiState、移除 composable 对 `AppContainer` 依赖。
- 验收：现有 38 集、播放、收藏、片单全部正常；迁移测试与回滚演练通过；无数据丢失。
- 估算：0a 约 1–2 周；0b 约 1 周（按 1–2 人维护规模估算，实际以进度为准）。

### Phase 1 — 10TB 扫描与刮削

- WorkManager 增量扫描 + `directory_cache` + 断点续扫 + 限速/封锁。
- 持久 `scrape_tasks` + Worker + 配额/退避。
- Paging 3 + SQL 筛选/排序 + FTS 搜索。
- 验收：20k 合成数据扫描不 OOM；中断后续跑；刮削可暂停/恢复；首页和搜索不卡。

### Phase 2 — 多数据源

- `sources` 管理界面；每源凭据/优先级/健康状态。
- `MediaSource` 实现：**WebDAV、本地、SMB、Emby、Jellyfin**（已定）。
- 实施顺序：**2a WebDAV 多账号 + 本地 → 2b SMB → 2c Emby/Jellyfin**；每一档单独验收，避免四类源同时引入。
- 多源合并、版本切换、播放失败切换；Emby/Jellyfin 优先 Direct Play。
- 验收：WebDAV（可多账号）+ 本地 + SMB + Emby/Jellyfin 至少各挂一个；同一作品合并；
  一个源被封锁/不可用不影响其它源；转码时明确提示 HDR/Atmos 可能降级。

### Phase 3 — 高规格播放

- **3A 能力模型**：`DeviceCapabilities` + `StreamInfo` 探测 + 播放诊断面板（先不改播放行为）。
- **3B HDR 直出**：HDR10 / HDR10+ / HLG 直出，刷新率匹配，音频直通 / 下混。
- **3C 降级与软解**：色调映射、DV 基础层、FFmpeg 软解回退（RMVB / AVI / 10-bit）。
- **3D 体验**：画中画、投屏（Media3 Cast / DLNA）、字幕样式、跳过片头尾。
- 验收：支持的格式直出且规格徽标正确；不支持时按预期降级并说明原因；4K60 HDR 无持续丢帧；
  HDMI / eARC 音频直通可用；RMVB/AVI/10-bit 至少一种原本不能播的格式可播；401/429 不重试。

### Phase 4 — TV（已定，单列）

- TV feature + 焦点/遥控器 + 10-foot UI；复用同一套播放/媒体库核心。
- 验收：遥控器可完成浏览、详情、播放、字幕/音轨切换；HDR/音频直通按接收端能力。
- 多端同步按 D2 **暂缓**，预留 `SyncProvider`；如恢复，排 Phase 6。

### Phase 5 — 生态与治理

- **用户资料 / 儿童模式**：`user_id`、分资料进度/收藏/片单、PIN、内容分级。
- **离线下载**：单集/单片下载、断点续传、配额、仅 Wi-Fi、前台通知。
- **平板 / 折叠屏**：`WindowSizeClass`、多栏、横竖屏、键盘。
- **诊断 / 可选崩溃上报**：诊断包、结构化指标、默认关闭的远端上报。
- **许可证与署名**：TMDB / OpenSubtitles / FFmpeg / Media3 等。
- **架构治理**：依赖规则检查、ADR、废弃策略、feature flag / 灰度。
- 验收：多资料隔离；离线播放可用；平板布局通过；诊断包不含凭据；许可证页完整。

### 5.1 Epic 拆解

| 阶段 | Epic | 交付 | 验收 |
|---|---|---|---|
| 0a | E0.1 Schema 导出 | `exportSchema` + `8.json` + MigrationTestHelper | 迁移测试脚手架能跑 |
| 0a | E0.2 核心数据模块 | `:core:model` / `:core:database` 骨架 | 依赖方向单向，构建通过 |
| 0a | E0.3 v9 表与回填 | 映射表 + sources/works/episodes/file_episodes/logicalId | v8 数据完整迁移 |
| 0a | E0.4 迁移验证 | 脱敏 v8 fixture + 逐键校验 + shadow compare + 回滚演练 | 数据零丢失 |
| 0b | E0.5 网络/数据模块 | `:core:network` / `:core:data` 骨架 | 依赖方向单向 |
| 1 | E1.6 仓库拆分 | Scanner（随 Phase 1 重写）/ Scraper / QueryRepository | 现有行为不变 |
| 1 | E1.7 UI 状态统一 | 页面 ViewModel + UiState | composable 不再拿 AppContainer |
| 1 | E1.1 增量扫描 | 目录缓存 + WorkManager + 续跑 | 20k 扫描可中断续跑 |
| 1 | E1.2 持久刮削 | `scrape_tasks` + Worker + 预算 | 可暂停/恢复/限速 |
| 1 | E1.3 Works/分页 | `works` 查询 + Paging 3 | 20k 海报墙不卡 |
| 1 | E1.4 搜索/筛选 | FTS + SQL 过滤 + 排序 | 中文搜索可用 |
| 1 | E1.5 压测 | 20k 合成数据 | 内存/帧率达标 |
| 2 | E2.1 FileSource | WebDAV 多账号 + 本地 | 2a 验收 |
| 2 | E2.2 SMB | SMB2/3 + 流式播放 | 2b 验收 |
| 2 | E2.3 CatalogSource | Emby / Jellyfin 同步 + Direct Play | 2c 验收 |
| 2 | E2.4 源管理 UI | 添加/编辑/停用/健康/删除影响 | 凭据隔离、401 不串源 |
| 2 | E2.5 合并/版本 | `work_versions` + 失败切换 + 选源规则 | 同作品多源合并；转码有提示 |
| 3 | E3.1 能力模型 | `DeviceCapabilities` + `StreamInfo` + 诊断 | 规格徽标正确 |
| 3 | E3.2 HDR 直出 | HDR10/HDR10+/HLG + 刷新率 + 音频 | 真机直出/直通 |
| 3 | E3.3 降级/软解 | 色调映射 + DV 基础层 + FFmpeg | 不支持时明确降级 |
| 3 | E3.4 体验 | PiP / 投屏 / 字幕样式 / 跳过 | 真机通过 |
| 4 | E4.1 TV | TV feature + 焦点 + 10-foot UI | 遥控器可用 |
| 5 | E5.1 用户资料 | 多资料 + 儿童模式 + PIN | 资料隔离 |
| 5 | E5.2 离线下载 | 下载队列 + 配额 + 断点续传 | 离线可播 |
| 5 | E5.3 自适应 UI | 平板/折叠多栏 | 布局通过 |
| 5 | E5.4 诊断/上报 | 诊断包 + 可选远端 | 默认关闭、无凭据 |
| 5 | E5.5 生态治理 | 许可证 + 依赖检查 + feature flag | 门禁通过 |

### 5.2 粗略规模（按 1–2 人维护规模估算）

| 阶段 | 粗估 | 说明 |
|---|---|---|
| 0a | 1–2 周 | 迁移、映射、校验、回滚演练 |
| 0b | 1 周 | 模块骨架 |
| 1 | 3–5 周 | 扫描/刮削/分页/FTS |
| 2 | 4–6 周 | WebDAV+本地 → SMB → Emby/Jellyfin 分档 |
| 3 | 4–6 周 | 能力模型、HDR 直出、降级/软解 |
| 4 | 3–4 周 | TV feature 与焦点 |
| 5 | 持续 | 资料、下载、平板、诊断、治理 |

> 估算用于评估进度偏差，不构成交付承诺；任一阶段超出上限 50% 时，优先缩减范围，不增加人力。

---

## 6. 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| logicalId 迁移错误 | 用户进度/收藏错位或丢失 | 保留旧列、迁移测试、真机抽查、迁移前备份 |
| 一文件多集 | 进度/已看判定错误 | `file_episodes` + 文件级进度 + 片尾整段标记 |
| 未匹配文件移动 | 进度/收藏断链 | 疑似移动建议 + UI 提示先刮削 |
| 源根路径变更 | 未匹配身份整体失效 | `root_generation` + FULL 扫描 + 关联提示 |
| 扫描请求过多触发 OpenList 锁定 | 全站 WebDAV 不可用 | 单源串行、限速、401/429 全局封锁、绝不重试 |
| 20k 条 TMDB 刮削耗时/配额 | 刮削长期不完成 | 持久队列、预算、优先级、暂停/恢复 |
| 全量列表/内存分组 | 卡顿、OOM | Paging 3 + works 表 + SQL 过滤 |
| WorkManager 前台限制 | 长扫描被杀 | `dataSync` 前台类型 + 通知 + 可续跑 |
| 中文搜索分词 | 搜不到 | 专门的 search_text / trigram 方案，基准测试 |
| 多模块过度拆分 | 构建/维护成本 | 控制在 8–12 个模块 |
| FFmpeg 包体/许可证 | APK 变大、合规风险 | 后置，评估 LGPL/GPL 与体积 |
| HDR / DV 设备碎片化 | 黑屏、偏色、错误 HDR | 能力探测 + 解码器实际初始化 + 明确降级 |
| 投屏无 GMS | Cast 不可用 | DLNA 为主，Cast 可选 |
| DLNA 无鉴权 / 本地代理 | 局域网可访问代理流 | 仅可信 LAN、随机会话路径、结束即停、用户确认 |
| jUPnP/Jetty 依赖 | Android 启动失败/包体增大 | 自研 SSDP/SOAP，不引入 Jetty |
| SMB 实网未验 | 认证/流式播放问题 | 需要用户提供 SMB 测试共享后补验 |
| 明文主机白名单 | 静态配置无法覆盖用户主机 | OkHttp 主机批准清单 + 明确警告 |
| 字幕无持久模型 | 改名后字幕丢失 | `subtitles` 表 + 重新关联 |
| SMB 客户端维护 | 认证/流式播放不稳定 | 选成熟库（SMBJ/jcifs-ng），封装 `MediaSource`，可替换 |
| Emby/Jellyfin 转码 | HDR/Atmos 被降级 | 优先 Direct Play；转码明确提示；能力协商 |
| 一次性迁移失败 | 用户数据错位/丢失 | 自动备份 + 事务 + 行数校验 + 回滚演练 |
| 4K 软件色调映射 | 卡顿、发热、掉帧 | 硬件优先，软解时提示并允许降级 |
| 音频直通设备差异 | 无声、只剩立体声 | 探测输出能力 + 下混回退 + UI 标明 |
| PGS / 图形字幕 | 字幕不显示 | 能力检测 + 提示，必要时接 FFmpeg |
| 流信息探测成本 | 大库全探测慢 | 按需探测 + 缓存 + 后台低优先级补齐 |
| TV/同步范围蔓延 | 架构分叉 | 先定决策，单独立项 |

---

## 7. 网易爆米花功能对照

| 功能 | 现状 | 目标阶段 |
|---|---|---|
| WebDAV / NAS 媒体库 | 有（单源） | Phase 2 多源 |
| SMB | 无 | Phase 2 |
| Emby / Jellyfin | 无 | Phase 2（Direct Play 优先） |
| 本地媒体 | 有（SAF / MediaStore） | 保留 |
| TMDB 刮削 | 有（同步、内存任务） | Phase 1 持久队列 |
| 分类 / 筛选 / 搜索 | 有（内存全量） | Phase 1 SQL + 分页 |
| 详情 / 演员 / 分集 | 有 | 保留 + works 表 |
| 收藏 / 历史 / 片单 | 有（按 path） | Phase 0 logicalId |
| 续播 / 播放进度 | 有（按 path） | Phase 0 logicalId |
| 字幕（内嵌/在线/本地） | 有 | 保留 + 样式 |
| 音轨 / 倍速 / 截图 | 有 | 保留 |
| 多版本切换 | 有（按 tmdbId） | Phase 2 多源版本 |
| 4K / 10-bit | 部分（硬解） | Phase 3 |
| HDR10 / HDR10+ / HLG | 部分（设备相关） | Phase 3 直出 / 降级 |
| Dolby Vision | 未承诺 | Phase 3（按设备能力） |
| Atmos / DTS 直通 | 无 | Phase 3 |
| 刷新率匹配 | 无 | Phase 3 |
| PGS / 图形字幕 | 未验证 | Phase 3 |
| 画中画 | 无 | Phase 3 |
| 投屏 | 无 | Phase 3（DLNA 优先，Cast 可选） |
| 跳过片头/片尾 | 无 | Phase 3 |
| 多端同步 | 无 | 暂不做，预留 `SyncProvider` |
| TV 端 | 暂不做 | 单列 Phase 4（已定） |
| 分享给朋友 | 有（明文凭据） | 保留，加确认 |

---

## 8. 接口草图

> **评审修订（2026-10-18）**：接口中的身份类型以 **15.2** 的 `ContentId` / `LogicalKey` 为准；
> `LogicalId` 仅作为兼容名称保留。

```kotlin
// core:model
@JvmInline value class SourceId(val value: String)
@JvmInline value class LogicalId(val value: String)   // tmdb:... / file:...

object LogicalIdCodec {
    fun forMovie(tmdbId: Int): LogicalId
    fun forEpisode(tmdbId: Int, season: Int, episode: Int): LogicalId
    fun forFile(sourceId: SourceId, remoteKey: String): LogicalId
}

enum class SourceType { WEBDAV, LOCAL, SMB, EMBY, JELLYFIN }
enum class ScanMode { INCREMENTAL, FULL }

data class Source(
    val id: SourceId,
    val type: SourceType,
    val name: String,
    val enabled: Boolean,
    val priority: Int,
)

// core:network
interface MediaSource {
    val id: SourceId
    val type: SourceType
    suspend fun probe(): SourceHealth
    suspend fun resolvePlayback(fileId: String): PlaybackVariant
}

interface FileSource : MediaSource {
    suspend fun list(parentKey: String): List<RemoteEntry>
}

interface CatalogSource : MediaSource {
    suspend fun sync(cursor: String?): CatalogDelta
}

data class CatalogDelta(val items: List<RemoteEntry>, val nextCursor: String?)

fun normalizeRemoteKey(raw: String): String

// core:data
interface ScanScheduler {
    fun enqueue(sourceId: SourceId, mode: ScanMode)
    fun cancel(sourceId: SourceId)
}

interface ScrapeScheduler {
    fun enqueue(ids: List<LogicalId>)
    fun pause()
    fun resume()
}

interface PlaybackResolver {
    suspend fun candidates(id: LogicalId): List<PlaybackVariant>
}

interface SubtitleProvider {
    suspend fun search(query: SubtitleQuery): List<SubtitleCandidate>
    suspend fun download(candidate: SubtitleCandidate): SubtitleFile
}

data class SubtitleQuery(
    val title: String, val season: Int?, val episode: Int?, val language: String,
)
data class SubtitleCandidate(
    val id: String, val provider: String, val language: String, val releaseName: String?,
)
data class SubtitleFile(val path: String, val encoding: String?)

// 高规格播放
enum class HdrFormat { SDR, HDR10, HDR10_PLUS, HLG, DOLBY_VISION }
enum class HdrMode { DIRECT, TONE_MAP, DV_BASE_LAYER, HLG_TO_SDR }
enum class AudioMode { PASSTHROUGH, DECODE, DOWNMIX }
enum class DecoderChoice { HARDWARE, FFMPEG_SOFTWARE }

data class StreamInfo(
    val fileId: String,
    val videoCodec: String?,
    val profile: Int?,
    val bitDepth: Int?,
    val hdrFormat: HdrFormat?,
    val dolbyVisionProfile: Int?,
    val width: Int?,
    val height: Int?,
    val frameRate: Float?,
    val bitrate: Int?,
    val audioTracks: List<AudioTrackInfo>,
    val subtitleTracks: List<SubtitleTrackInfo>,
)

data class DeviceCapabilities(
    val displayHdrFormats: Set<HdrFormat>,
    val maxWidth: Int,
    val maxHeight: Int,
    val maxRefresh: Float,
    val videoCodecs: Set<String>,
    val passthroughCodecs: Set<String>,
)

data class RendererCapabilities(   // DLNA / Cast 接收端
    val hdrFormats: Set<HdrFormat>,
    val maxWidth: Int,
    val maxHeight: Int,
    val passthroughCodecs: Set<String>,
)

data class PlaybackPlan(
    val fileId: String,
    val decoder: DecoderChoice,
    val hdrMode: HdrMode,
    val audioMode: AudioMode,
    val preferredDisplayModeId: Int?,
    val fallback: List<PlaybackPlan> = emptyList(),
)

interface StreamProbe {
    suspend fun probe(fileId: String): StreamInfo?
}

interface CapabilityRepository {
    fun current(): DeviceCapabilities
}

interface PlaybackPlanner {
    suspend fun plan(id: LogicalId): PlaybackPlan?
}

interface SyncProvider {
    suspend fun pull(since: Long): SyncDelta
    suspend fun push(delta: SyncDelta)
}

// feature
data class LibraryFilter(
    val type: MediaType?,
    val genre: String?,
    val year: IntRange?,
    val unwatched: Boolean,
)
```

---

## 9. 第一个 PR 的范围

> **评审后调整（2026-10-18）**：先按 **15.10** 提交基线并完成 S1–S4，再开始 0a；
> 0a 采用 15.2–15.4 的 UUID / 完整多集模型 / 兼容 VIEW。

不做功能，只做地基，保证可回滚：

1. `ARCHITECTURE.md` 入库（本文件，含第二版修订）。
2. 开启 `exportSchema`，生成并提交 `app/schemas/8.json`。
3. 新建 `:core:model` 模块：`SourceId` / `LogicalId` / `LogicalIdCodec` / `SourceType` / `normalizeRemoteKey` / 纯函数（不搬现有代码）。
4. v9 迁移：按 4.2 的映射表建表与回填；`media_items` 转只读；`file_episodes` / `subtitles` / `media_fts` 一并建好。
5. `MigrationTestHelper`（v8→v9）+ 脱敏 fixture + 逐键校验 + 回滚演练。
6. **单独发一版**：只切数据层，UI 与仓库结构不动；真机验证现有 38 集、进度、收藏、片单。
7. 验证通过后再进入 0b / Phase 1。

> 约束不变：401/429 不重试、真迁移、版本单一来源、改完真机验证。

---

## 10. 决策记录（ADR）

| 编号 | 决策 | 理由 | 后果 |
|---|---|---|---|
| ADR-001 | 保持单 Activity + Compose，不重写跨端 | 团队规模与 Media3 生态 | TV 需单独 feature |
| ADR-002 | 用户数据引用 `logicalId`，物理文件用 `fileId` | 改名/移动/多源不丢进度 | 需要 v9 迁移与身份合并 |
| ADR-003 | 模块控制在 8–12 个，暂用手写 DI | 降低构建与认知成本 | 模块边界靠约定与评审 |
| ADR-004 | Room 是唯一媒体库数据源，列表用 Paging 3 | 20k 级不能全量加载 | 需要 `works` 查询与 SQL 过滤 |
| ADR-005 | 扫描/刮削用 WorkManager + 持久队列 | 长任务要可续跑、可取消 | 需要 FGS 类型与通知 |
| ADR-006 | Media3 + 能力探测，HDR 按设备直出/降级 | 设备碎片化，不能假设全支持 | DV 不承诺全设备 |
| ADR-007 | 不引入后端，除非确认多端同步 | 控制运维成本与攻击面 | 同步先做 WebDAV JSON |
| ADR-008 | 401/429 全局封锁，任何入口不重试 | OpenList 按 IP 锁定的硬约束 | 需要统一 SourceGate |
| ADR-009 | 升级方向只做真迁移，绝不 destructive | 用户数据不可恢复 | 迁移测试是发布门槛 |
| ADR-010 | 不做“目录即剧集”的粗暴分组 | 合集目录会误合并 | 只按集数 + 季根分组 |
| ADR-011 | 先手机 + 投屏，TV 单列 Phase 4 | 控制当前 UI 复杂度 | TV 需独立 feature 与焦点模型 |
| ADR-012 | 暂不做多端同步，预留 `SyncProvider` | 避免后端运维 | 先做导出/导入 |
| ADR-013 | 多源包含 WebDAV / 本地 / SMB / Emby / Jellyfin | 用户已确认“都要” | Phase 2 复杂度与测试面最大 |
| ADR-014 | 身份迁移一次性切换 + 迁移守卫 | 用户选择工期优先 | 必须自动备份、校验、回滚演练 |
| ADR-015 | v9 迁移单独发版，与重构解耦 | 失败时能定位是迁移还是重构 | 发布节奏多一版 |
| ADR-016 | 投屏 DLNA 优先，Cast 可选 | 国产设备普遍无 GMS | Cast 只作为加分项 |
| ADR-017 | 明文流量用 OkHttp 主机批准清单 | `network_security_config` 是编译期静态配置 | 需在网络层做运行时拦截 |
| ADR-018 | 文件型源与目录型源拆分契约 | Emby/Jellyfin 是 item 同步，不是目录 BFS | 扫描器两条路径 |
| ADR-019 | 多集文件用 `file_episodes`，进度按文件 | 无法可靠推断集边界 | 片尾整段标记完成 |
| ADR-020 | 身份主键用不可变 UUID，`logical_key` 仅作唯一键 | 匹配/改名不级联用户数据 | 需要 `MergeContents` 与 v9 迁移 |
| ADR-021 | v9 兼容采用只读 VIEW | 现有 UI 读路径不变 | 写路径改新表；Room VIEW 由 S1 验证 |
| ADR-022 | 多集/版本/进度用完整模型 | 避免边界推断错误 | `movie_versions` / `episode_versions` / `file_segments` / 分表进度 |
| ADR-023 | 开工前修 B1–B6 + 4 个 spike | 降低迁移与返工风险 | 0a 延后，先完成 S1–S4 |
| ADR-024 | 中文搜索用 FTS4 + `search_text` | 目标机不支持 FTS5 trigram；FTS4 p95 3.44 ms | 查询构造需把 CJK 转为空格短语；索引随标题/演员重建 |
| ADR-025 | SMB 用 SMBJ；DLNA 自研 SSDP/SOAP + 本地 HTTP 代理 | jUPnP Android 模块缺 Jetty；SMBJ Apache 2.0；DLNA 不依赖 GMS | 需自建代理与 SOAP；只在可信 LAN 使用 |

> 新增决策时在表格末尾追加，并在 WORKLOG 记录背景与影响。

---

## 11. 非目标

- 不做内容托管、内容分发、DRM、广告、社交功能。
- 不自建账号体系/云端后端（除非多端同步成为明确需求）。
- 不做整库视频缓存；离线下载按用户选择单集/单片，配额管理。
- TV 单列 Phase 4（已定），当前不按 TV 重构手机 UI。
- 默认不做远端遥测；崩溃上报可选、默认关闭、需用户同意。
- 不做每帧 4K 软件色调映射；硬件优先，软解必须提示性能代价。
- 不做没有迁移测试的数据库变更。
- 不做只对某个具体文件名生效的解析特判；命名问题按通用规则 + 用户改名解决。
- 不用“全量加载 / 全表扫描 / 主线程 IO”换开发速度。

---

## 12. 文档维护

- 本文件记录**长期架构与决策**；具体实现进展、问题与验证结果写入 `WORKLOG.md`。
- 新增/修改 ADR 时同步更新相关章节；废弃决策保留编号并标注状态。
- 任何与安全、凭据、401/429、迁移相关的改动，必须先对照本文件再动手。

---

## 13. 质量属性评估与改进清单

> 目标：不只“功能能跑”，而是在可靠性、性能、安全、兼容、可维护、可测试、可部署、可伸缩等维度
> 达到 10TB / 长期维护的要求。本节同时覆盖**已完成功能的改善项**和**后续功能的约束**。

### 13.1 质量属性矩阵

| 属性 | 现状 | 主要差距 | 改进方向 | 验收 |
|---|---|---|---|---|
| 功能性 | 浏览/播放/媒体库/刮削/字幕/收藏/片单基本完整 | 多源、HDR 高规格、投屏、同步、画中画、跳过片头尾 | 按 Phase 2–4 | 功能对照表逐项通过 |
| 可靠性 | 401 防线、真迁移、进度持久化 | 全局源封锁、任务续跑、迁移测试、备份恢复 | SourceGate + 持久任务 + 迁移测试 | 断网/401/进程死亡后行为可预期 |
| 易用性 | 界面已按参考重做、二次确认 | 命名问题提示、错误文案、空态、无障碍、中文搜索 | 统一文案 + 引导 + 搜索优化 | 关键流程无需猜 |
| 性能 | 媒体链路调优、R8、扫描有界 | 全量列表/内存分组、主线程日志、无分页 | Paging/FTS + 异步日志 + 20k 压测 | 预算表达标 |
| 安全性 | Keystore 凭据、日志脱敏、主机限定的鉴权头 | 明文流量、分享明文凭据、备份规则、供应链 | 按主机白名单 + 二次确认 + 备份排除 | 威胁清单逐项关闭 |
| 兼容性 | minSdk 26 / targetSdk 36 / arm64 | Android 14 部分媒体访问、HDR/DV/音频碎片化、预测返回 | 权限适配 + 能力探测 + 设备矩阵 | 设备矩阵通过 |
| 可扩展性 | 字幕源接口、手写容器 | 数据源适配、元数据源、feature 模块 | `MediaSource` / `MetadataProvider` 接口 | 新源不改核心 |
| 可维护性 | 注释与文档好、分层清楚 | 上帝仓库、UI 直接拿容器、状态不一致 | 拆仓库 + VM 统一 + 模块化 | 单文件 <500 行、无跨层依赖 |
| 可测试性 | 98 条纯逻辑单测 | 仓库/DB/UI/WorkManager 测试缺失 | 假服务端 + MigrationTestHelper + work-testing | 关键状态机有测试 |
| 可移植性 | 纯 Android、E 盘构建 | 开发环境绑定本机路径、无 Linux 开发说明 | 文档化环境 + CI 校验 | 新机器可按文档构建 |
| 可部署性 | CI 签字包 + tag Release | 无 lint 门禁、无迁移门禁、无回滚演练 | CI 加 lint/迁移/性能门禁 | 发布可重复、可回滚 |
| 可伸缩性 | 单源、全量模型 | 10TB/多源/20k 列表 | Phase 0–2 | 20k 合成库达标 |
| 可观测性 | 落盘日志、崩溃捕获 | 结构化指标、播放诊断、任务进度 | 统一诊断面板 + 指标 | 问题可定位到层 |
| 内聚/耦合 | 数据层职责较清楚 | `LibraryRepository` 低内聚、UI 耦合容器、全局状态 | 见 13.5 | 依赖规则自动检查 |
| 数据生命周期 | 缓存与用户数据分开、迁移链 | 导出/备份、完整性检查、损坏恢复 | 见 13.2 / 3.19 | 迁移/恢复演练通过 |

### 13.2 现有代码改进清单（P0）

| 项 | 位置 / 原因 | 动作 | 验收 |
|---|---|---|---|
| 全局源封锁 | `BrowseViewModel` / `LibraryRepository` / `SourceViewModel` / `PlayerViewModel` 各有 flag；字幕探针会吞 401 | 建 `SourceHealthRepository` + `SourceGate`；所有网络入口统一检查；typed error | 任一入口 401/429 → 全源停；单测覆盖；无重试 |
| 迁移可验证（一次性切换） | `exportSchema=false`、`app/schemas/` 被 gitignore、只有链连续性测试 | 导出 v8 schema；`MigrationTestHelper`；自动备份 + 事务 + 行数校验 + 回滚演练 | 迁移前后一致；失败恢复备份并阻止启动 |
| 凭据与备份 | `allowBackup=true`；分享一键输出明文密码/TMDB key | `dataExtractionRules` 排除凭据；分享二次确认、默认不含 TMDB key；解密失败强制重登（已做） | 恢复后不会发空密码；分享有确认 |
| 用户数据导出 | 换机/迁移只能靠数据库文件 | 导出/导入进度、收藏、片单、改名（JSON） | 换机可恢复 |
| 任务持久化 | 扫描/刮削是内存 job | WorkManager + `scan_runs` / `scrape_tasks` + 续跑 | 进程死亡后续跑 |
| 主线程 IO | `AppLog.write` 在亮度/音量手势循环里同步落盘 | 异步队列 + 节流；StrictMode 检查 | 手势无卡顿 |
| Android 14 媒体权限 | 只声明 `READ_MEDIA_VIDEO` | 补 `READ_MEDIA_VISUAL_USER_SELECTED` + 部分授权 UI | Android 14/15 可扫本地 |
| 明文流量 | `network_security_config` 全局放行；静态配置无法覆盖用户新增主机 | OkHttp 拦截器 + 用户批准的主机清单；添加源时明确警告 | 未批准主机拒绝 `http://` |
| 数据层缺陷 | `PlaylistRepository.delete` 无事务；`SourceViewModel.test` 允许空密码；`DetailViewModel.init` 的 `first{}` 可能永久挂起 | 事务、非空校验、加超时/条件 | 对应单测或代码审查 |
| 401 字幕探针 | `PlayerViewModel.findSubtitles` catch 所有异常返回空 | 走 `SourceGate`；401/429 标记封锁并提示 | 不再静默吞 401 |
| 深链校验 | `mediadock://` 可被外部应用触发 | 只接受 DB 中存在的 `logicalId`，永不接受 URL 参数 | 外部无法触发任意播放 |

### 13.3 现有代码改进清单（P1）

| 项 | 位置 / 原因 | 动作 | 验收 |
|---|---|---|---|
| 拆 `LibraryRepository` | 约 650 行：扫描/刮削/状态机/映射 | `MediaScanner`（WebDAV/本地/MediaStore）、`MetadataScraper`、`LibraryQueryRepository` | 单类 <300 行；职责单一 |
| UI 状态统一 | Mine/Search/Playlist/History 直接拿 `AppContainer` | 全部 ViewModel + UiState；composable 不碰容器 | 无 `AppContainer` 出现在 `ui/` |
| 全量列表 | `observeAll()` + 内存 `groupWall` / 搜索 | `works` 表 + Paging 3 + SQL 筛选 + FTS | 20k 列表不卡 |
| 错误模型 | 字符串 message + `catch(Exception)` | typed error + 统一映射 + `CancellationException` 规则 | UI 不再解析字符串 |
| 模块化 | 单 `:app` | `:core:*` + `:feature:*`（8–12 个） | 依赖方向单向 |
| Lint / 依赖 / 文档 | 34 个 lint error（多为 Media3 UnstableApi）；未用依赖；proguard 注释过时 | `@OptIn`、删依赖、修文档、lint 进 CI | lint 无新增错误 |
| 轮询/功耗 | 播放页 200ms、迷你播放条 1s 常驻 | 仅在可见或播放时轮询；后台停止 | 待机电量可接受 |
| 备份/完整性 | 无导出、无完整性检查 | 见 P0；设置页调试入口 | 可自检 |
| 供应链 | 依赖版本集中，但无自动更新/校验 | 定期升级；CI 校验；密钥只在 secrets | 可重复构建 |
| 字幕持久化 | 仅靠 `filesDir/subtitles` 约定 | `subtitles` 表 + 编码探测（GBK/GB18030/UTF-16） | 改名后仍可关联 |

### 13.4 现有代码改进清单（P2）

| 项 | 内容 |
|---|---|
| 中文搜索 | FTS4 + `search_text` 或 FTS5 trigram；拼音首字母；20k 基准 |
| 多源/合并 | `sources`、`MediaSource`、`work_versions`、播放失败切换 |
| 高规格播放 | 3A–3D：能力探测、HDR 直出、降级/软解、PiP/投屏/字幕样式 |
| 同步 | `SyncProvider` + WebDAV JSON；冲突规则 |
| 易用性 | 空态、错误文案、初始化引导、命名问题提示、无障碍、i18n |
| 性能 | Baseline Profile、Macrobenchmark、20k 压测、图片缓存策略 |
| 部署 | CI 加 lint/迁移门禁；发布清单；回滚演练；构建变体（dev/prod） |
| 可移植 | 新机器构建文档；Windows/Linux 双环境校验；路径不写死 |
| 测试 | 假 WebDAV/TMDB、Room 内存库、WorkManager、少量 Compose UI |
| TV/投屏 | 仅在决策确认后立项 |

### 13.5 内聚与耦合专项

**目标依赖规则**：

```text
:core:model   ← 所有模块（纯 Kotlin，不依赖 Android）
:core:database / :core:network / :core:datastore ← :core:model
:core:ui      ← :core:model（只放共享 Compose 组件与主题 token）
:core:data    ← 上述 core 模块
:feature:*    ← :core:data / :core:model / :core:ui
:app          ← 所有 feature + core
feature 之间禁止互相依赖；跨页导航由 :app 负责。
```

**当前问题与治理顺序**：

1. `LibraryRepository` 低内聚：先抽 `MediaScanner` 接口与实现（WebDAV/本地/MediaStore），再抽 `MetadataScraper`，最后留查询仓库。
2. UI 耦合容器：先给 4 个直接拿 `AppContainer` 的页面补 ViewModel，再把容器参数从 composable 移除。
3. 全局可变状态：`PlaybackSelection` / `DetailSelection` → `SavedStateHandle` 或持久化 `PlaybackRequest`。
4. 数据边界：仓库对外返回 domain model，不把 Room 实体直接抛给 UI；实体变化不触发无谓重组。
5. 错误边界：网络层抛 typed error，仓库翻译成用户可见状态，UI 只渲染。
6. 重复逻辑：搜索、分组与解析保持纯函数单测；不得在多个页面重复实现过滤逻辑。

**判断标准**：

- 一个类只对一类变化负责；单文件超过 ~500 行必须拆。
- 跨层调用只走接口；feature 不知道数据库/网络实现。
- 新增一个数据源/字幕源/元数据源时，不改核心 UI。

### 13.6 质量门禁（Definition of Done）

任何改动合入前：

1. 编译 + 单测通过；纯逻辑改动同步补单测。
2. 涉及数据库：schema 导出、迁移测试、真实库副本验证；禁止 destructive 升级。
3. 涉及网络/凭据：不得新增 401/429 自动重试；日志不含凭据；分享/备份有确认。
4. 涉及 UI/播放：进行真机验证（说明验证项并提供日志证据；不要求提供截图）。
5. 不引入全量加载、全表扫描、主线程 IO；性能敏感改动跑 20k 基线。
6. lint 无新增错误；未使用依赖不进入主干。
7. WORKLOG 记录「背景 / 变更 / 验证 / 备注」；架构决策更新本文件。

### 13.7 待决策项

**已定（2026-10-18）**：
- D1 TV/投屏：先手机 + 投屏；TV 单列 Phase 4。
- D2 多端同步：暂不做，预留 `SyncProvider`。
- D3 数据源：WebDAV + 本地 + SMB + Emby/Jellyfin 都要。
- D4 身份迁移：一次性切换 + 自动备份/校验/回滚守卫。
- D8 崩溃上报：本地日志 + **可选远端**（默认关闭，需用户同意）。
- D11 多用户/儿童模式：**做**（Phase 5）。
- D13 离线下载：**做**（Phase 5，按用户选择、配额管理）。
- D14 平板/折叠屏：**正式支持**（Phase 5，`WindowSizeClass`）。
- D5 中文搜索：**FTS4 + `search_text`**（CJK 逐字空格 + 短语查询）；FTS5 trigram 在目标设备不可用。
- B1/B2/B3 设计：**UUID + `logical_key`；完整多集/版本模型；v9 兼容 VIEW**（见 15.2–15.4）。

> 上述“全部纳入”指剩余主题均纳入架构与路线图；多端同步仍按 D2 暂缓，TV 按 D1 单列。

| 编号 | 决策 | 选项 | 建议 | 影响 |
|---|---|---|---|---|
| D6 | DI | 手写容器 / Hilt | 现阶段手写，模块化后再评估 | 构建时间与测试便利 |
| D7 | HDR/DV 承诺 | 能力探测+降级 / 承诺全设备 | 能力探测，不承诺 DV | 用户预期与兼容性 |
| D9 | 数据库加密 | 不加密 / SQLCipher | 不加密（凭据已单独加密） | 性能与复杂度 |
| D10 | minSdk / targetSdk | 保持 26 / 提高 minSdk / 尽快上 37 | 保持 26；targetSdk 稳定后升 37 | 设备覆盖与适配成本 |
| D12 | 开发环境 | 仅 Windows E 盘 / 文档化跨平台 | 文档化 + CI 校验 | 可移植性 |

---

## 14. 剩余架构主题（全量落地）

> 产品决策：以下主题全部纳入架构与路线图，按 Phase 0–5 排期；不要求单次发版完成。
> 多端同步仍按 D2 暂缓（预留 `SyncProvider`）；TV 按 D1 单列 Phase 4。

### 14.1 设置与凭据分层

- **拆分存储**：
  - `CredentialStore`：每个 source 的 WebDAV/SMB/Emby/Jellyfin 凭据；TMDB / OpenSubtitles 凭据；全部 Keystore 加密。
  - `SettingsStore`：主题、扫描策略、播放偏好、语言、缓存预算、通知、下载配额。
  - `LocalMediaStore`：SAF 树 URI 等非机密配置。
- **迁移**：从现有 `credentials` DataStore 一次性映射；旧键保留一个版本；退出登录只清对应 source。
- **备份**：凭据 DataStore 排除出 Auto Backup；设置可备份；恢复后凭据缺失 → 重新登录。
- **测试**：解密失败、备份恢复、迁移、按源清理。

### 14.2 发现与首页信息架构

- **`HomeSectionProvider` 接口**：每个 section 返回分页数据 + 标题 + 空态；`HomeFeed` 只负责顺序与渲染。
- **Section 类型**：
  - 继续观看（进度）；最近添加（扫描时间）；最近播放；
  - 基于观看或收藏记录的相似推荐（依据本地类型/演员，推荐理由须可解释）；
  - 高分推荐（TMDB）；合集（Collection）；片单；收藏；
  - 分类行；来源分区（多源时可切换）。
- **规则**：去重、优先级、可配置顺序、隐藏空 section；推荐理由必须可解释；不做假数据。
- **实现**：纯排序/筛选函数 + 单测；每个 section 独立 Paging；首页不一次性加载全库。
- **验收**：20k 库首页首屏 < 1s；下滑不卡；空态正确。

### 14.3 元数据提供方架构

- **`MetadataProvider` 接口**：search / details / episodes / images / externalIds；实现：
  - TMDB（默认）；Emby / Jellyfin（服务端元数据）；NFO（本地，用户已整理）；
  - 预留 Bangumi / AniDB（动漫别名、绝对集数）。
- **NFO 解析**：禁用 XML 外部实体（XXE），限制文件大小与嵌套深度。
- **优先级与合并**：用户手动覆盖 > NFO > Emby/Jellyfin > TMDB（可配置）；记录 `metadata_source` 与 `external_ids`，不静默覆盖。
- **字段**：本地化标题/别名、简介、海报/背景、分集组、绝对集数、演员/导演/类型、合集、预告片。
- **刷新**：按条目/整库；失败不阻塞；手动重匹配；冲突诊断（显示来源与候选）。
- **刮削减耦**：抓取队列引用 provider + logicalId；Emby/Jellyfin 源不重复 TMDB 刮削。

### 14.4 播放会话生命周期

- **状态机**：`Idle → Preparing → Playing ⇄ Paused/Buffering → Ended/Error`；所有 UI 从状态机读，不直接问 ExoPlayer。
- **生命周期**：音频焦点（gain/duck/loss）、耳机拔出、蓝牙切换、来电、PiP 进入/退出、前后台、进程死亡恢复（logicalId + 位置 + 队列 + 源）。
- **队列**：自动下一集、上一集/下一集、插入/移除/移动、随机/循环、从片单/合集播放。
- **播放能力**：章节、书签、预告片、倍速持久化、字幕/音轨选择持久化、跳过片头/片尾。
- **投屏移交**：手机 ↔ 接收端会话移交；接收端能力协商（HDR/音频/分辨率）；移交失败回本地。
- **接收端模型**：`RendererCapabilities` 单独建模，投屏时重新计算 PlaybackPlan；不支持 HDR 的接收端明确降级。
- **队列持久化**：当前队列、索引、源、字幕/音轨选择写入 `PlaybackRequest`（DataStore/DB），进程死亡可恢复。
- **架构**：`PlaybackSessionController` 门面 + `PlaybackRequest/State`；UI 不持有 ExoPlayer。
- **测试**：状态机单测 + 设备矩阵（耳机、来电、PiP、进程死亡）。

### 14.5 网络层与源适配契约

- **统一网络策略**：超时、重试矩阵、代理（Clash/系统代理）、DNS、HTTP/2、重定向、Range、连接池；
  WebDAV / SMB / Emby / Jellyfin / TMDB / 字幕各自实现，但错误语义统一。
- **`MediaSource` 契约**：`list / resolvePlayback / probe / health`；认证刷新、API 版本协商、Direct Play/转码。
- **源隔离**：每源独立 `SourceHealth`；401/429 只封锁该源；不重试风暴。
- **错误可见**：网络层抛 typed error，仓库翻译成用户可读状态；诊断日志不含凭据。
- **契约测试**：假 WebDAV / SMB / Emby / Jellyfin / TMDB 服务端；覆盖断网、401、429、404、超时、重定向。

### 14.6 安全、供应链与发布

- **明文流量落地**：`network_security_config` 是编译期静态配置，无法按运行时新增主机放行；实际策略是 OkHttp 拦截器维护“用户批准的主机清单”，未批准主机拒绝 `http://`。
- **威胁模型**：
  - 资产：凭据、媒体库、进度/收藏、片单、下载内容；
  - 对手：网络窃听、恶意服务端、丢失设备、其它 App、供应链；
  - 对策：Keystore、HTTPS、备份排除、日志脱敏、分享确认、按主机明文策略。
- **更新完整性**：APK 签名由系统校验；`version.json` 走 HTTPS；可选 SHA-256；不静默安装。
- **供应链**：Gradle 依赖校验/锁定、CI secrets 最小权限、签名构建、定期升级；R8 keep 规则复核。
- **许可证**：TMDB / OpenSubtitles 署名；FFmpeg LGPL/GPL 义务；Media3/AndroidX 许可证页。
  SMBJ 为 Apache 2.0；jUPnP（CDDL-1.0）已评估但不采用；自研 DLNA 控制器无额外许可证。
- **Android 15 兼容**：FFmpeg 等原生库必须支持 **16KB page size**；ABI 拆分/App Bundle；包体预算。
- **发布**：迁移门禁、灰度/分阶段、回滚演练、版本说明、可选崩溃监控（默认关闭）。

### 14.7 测试、可观测性与诊断

- **测试金字塔**：
  - 纯逻辑单测（现有）；仓库 + 内存 Room；
  - 契约测试（WebDAV/SMB/Emby/Jellyfin/TMDB/字幕）；
  - 迁移测试（真实 v8 fixture）；WorkManager 测试；
  - 少量 Compose UI 测试；性能基准（Macrobenchmark）。
- **故障注入**：断网、401/429、进程死亡、低存储、DB 损坏、慢速网络、超时。
- **可观测性**：结构化日志 + 关联 ID；扫描/刮削/播放指标（时长、数量、错误、丢帧）；诊断包（设备、DB 统计、源健康、解码器信息）。
- **崩溃上报**：本地优先；远端可选、默认关闭、需用户同意；上报内容不含凭据/路径。
- **调试入口**：开发者页显示 SourceHealth、任务队列、DB 统计、HDR/音频能力；可导出诊断包。

### 14.8 自适应 UI、用户资料与家庭

- **`WindowSizeClass`**：手机 / 平板 / 折叠屏；竖屏/横屏；列表/详情多栏；键盘与鼠标；不做 TV 的触摸假设。
- **用户资料**：`user_id` 贯穿进度/收藏/片单/改名；资料切换；儿童模式（内容分级/PIN/隐藏成人内容）；资料隔离。
- **无障碍**：TalkBack、字体缩放、对比度、字幕可读性、焦点顺序、色盲友好。
- **国际化**：UI 文案、TMDB 元数据语言、字幕语言、日期/数字格式、RTL。
- **验收**：平板双栏；儿童模式不能看到成人内容；TalkBack 完成主流程。

### 14.9 离线下载与缓存

- **下载队列**：`DownloadTask` 持久化；单集/单片；断点续传；仅 Wi-Fi 可选；前台 `dataSync`；通知进度。
- **存储**：配额、可用空间检查、LRU/手动删除、按资料/源统计；下载内容视为 `LOCAL` 版本。
- **播放**：优先本地版本；删除下载不影响进度/收藏；跨源切换保持续播。
- **权限与合规**：只下载用户自有/有权访问的内容；不做 DRM 绕过。
- **验收**：断网可播下载内容；删除后回源；配额提示准确。

### 14.10 仍未展开 / 持续改善

| 主题 | 内容 | 阶段 |
|---|---|---|
| 架构治理 | 模块依赖检查（Konsist/ArchUnit）、ADR、废弃策略、代码所有权 | 持续 |
| Feature flag / 灰度 | 高风险迁移、HDR、下载、TV 的开关与回滚 | Phase 0+ |
| 崩溃恢复 / 安全模式 | DB 恢复、重置缓存但不丢用户数据、启动诊断 | Phase 1 |
| 数据保留 / 隐私 | 日志、字幕、下载、tombstone 的保留与清理 | Phase 1 |
| 性能 | 冷启动、Baseline Profile、电量/发热、网络吞吐 | 持续 |
| 兼容矩阵 | Android 版本、16KB page、预测返回、FGS 类型、通知渠道、存储权限、折叠屏 | 持续 |
| 构建/部署 | flavors、可重复构建、CI 门禁、ABI 拆分、依赖锁定、版本策略 | Phase 0+ |
| 文档 | 用户指南、FAQ、支持手册、迁移说明 | 持续 |
| 插件/扩展 | 数据源、元数据、字幕、播放器的可替换实现 | Phase 2+ |
| 备份/导出 | 用户数据 JSON、数据库备份、换机恢复 | Phase 0 |
| 许可证页 | 第三方库与数据源署名 | Phase 5 |
| 发布列车 | 版本节奏、灰度、回滚、Release Notes | 持续 |

### 14.11 主题 → 阶段映射

| 主题 | 阶段 |
|---|---|
| 设置/凭据分层、备份/导出 | Phase 0 / 1 |
| 发现/首页、搜索、元数据提供方 | Phase 1 / 2 |
| 多源（WebDAV/本地/SMB/Emby/Jellyfin）、网络契约 | Phase 2 |
| 播放会话生命周期、HDR/高规格、字幕样式 | Phase 3 |
| TV | Phase 4 |
| 用户资料/儿童模式、离线下载、平板/折叠、诊断/可选上报、许可证、架构治理 | Phase 5 |
| 多端同步 | 暂缓（Phase 6，如恢复） |

---

## 15. 企业级评审整改与启动门槛（2026-10-18）

### 15.1 结论与已定决策

第二版架构可作为实施基础，但开工前仍有 6 个阻塞项（B1–B6）。按评审结论，先修阻塞、跑完 spike，再进入 Phase 0a。

已定决策：

| 项 | 决策 |
|---|---|
| 逻辑身份 | **不可变 UUID + 唯一 `logical_key`**（tmdb/file） |
| v9 兼容 | **兼容 VIEW**（旧 `media_items` 读路径不变；写路径改新表） |
| 多集/版本/进度 | **完整模型**：`movie_versions` / `episode_versions` / `file_segments` / 分表进度 |
| 开工顺序 | **修 B1–B6 + 4 个 spike 后开工**；0a 只做数据层与兼容桥 |

当前状态：工作区有 54 个未提交文件（第二版文档 + 注释/文案）；单测 98/98 通过；release 构建通过。开工前先提交为基线。

### 15.2 B1：身份模型修订（覆盖 3.3.1 / 3.3.2）

**术语**：

- `WorkId` / `EpisodeId`：UUID，不可变；用户数据只引用它们。
- `LogicalKey`：`tmdb:movie:<id>` / `tmdb:tv:<id>` / `tmdb:tv:<id>:s<S>:e<E>` / `file:<sourceId>:<key>`；
  唯一，但**允许变化**（未匹配 → 匹配）。
- `ContentId`：`WorkId`（电影/剧集作品）或 `EpisodeId`（剧集分集）的统称。

**关键表**（替换原草案中 `works.id` / `episodes.id` 直接用 logical key、`work_versions`、`file_episodes`）：

```text
works(
  id TEXT PK,                 -- UUID，不可变
  logical_key TEXT NOT NULL UNIQUE,
  tmdb_id INTEGER, type TEXT, title TEXT, sort_title TEXT, year INTEGER,
  poster_path TEXT, backdrop_path TEXT, overview TEXT, rating REAL,
  total_seasons INTEGER, total_episodes INTEGER, collection_id TEXT,
  metadata_source TEXT, external_ids_json TEXT,
  added_at INTEGER, latest_file_mtime INTEGER, updated_at INTEGER
)

episodes(
  id TEXT PK,                 -- UUID，不可变
  logical_key TEXT NOT NULL UNIQUE,
  work_id TEXT NOT NULL, season INTEGER, episode INTEGER,
  title TEXT, still_path TEXT, runtime INTEGER, air_date TEXT,
  UNIQUE(work_id, season, episode)
)

media_files(
  id TEXT PK,                 -- UUID，物理文件
  source_id TEXT NOT NULL,
  remote_key TEXT NOT NULL,   -- 归一化键
  legacy_path TEXT,           -- 兼容 VIEW 用的旧 path 原值
  provider_item_id TEXT,
  media_kind TEXT,            -- main / sample / trailer / extra
  name TEXT, size_bytes INTEGER, mtime TEXT, etag TEXT,
  scan_run_id INTEGER, missing_at INTEGER,
  UNIQUE(source_id, remote_key)
)

movie_versions(
  work_id TEXT, file_id TEXT, source_id TEXT, quality_label TEXT,
  size_bytes INTEGER, pinned INTEGER, PRIMARY KEY(work_id, file_id)
)

episode_versions(
  episode_id TEXT, file_id TEXT, source_id TEXT, quality_label TEXT,
  size_bytes INTEGER, pinned INTEGER, PRIMARY KEY(episode_id, file_id)
)

file_segments(
  file_id TEXT, episode_id TEXT, segment_index INTEGER,
  start_ms INTEGER, end_ms INTEGER,
  PRIMARY KEY(file_id, segment_index)
)                             -- 一文件多集 / 多碟分段的权威边界

movie_progress(work_id TEXT, user_id TEXT, position_ms INTEGER, duration_ms INTEGER,
               completed INTEGER, updated_at INTEGER, PRIMARY KEY(work_id, user_id))
episode_progress(episode_id TEXT, user_id TEXT, position_ms INTEGER, duration_ms INTEGER,
                 completed INTEGER, updated_at INTEGER, PRIMARY KEY(episode_id, user_id))
file_progress(file_id TEXT, user_id TEXT, position_ms INTEGER, duration_ms INTEGER,
              updated_at INTEGER, PRIMARY KEY(file_id, user_id))  -- 多集文件

favorites(work_id TEXT, user_id TEXT, added_at INTEGER, PRIMARY KEY(work_id, user_id))
playlist_items(playlist_id INTEGER, work_id TEXT, added_at INTEGER,
               PRIMARY KEY(playlist_id, work_id))
title_overrides(work_id TEXT, title TEXT, year INTEGER, PRIMARY KEY(work_id))
```

**规则**：

- 未匹配文件：创建临时 `works` / `episodes` 行（UUID），`logical_key = file:<sourceId>:<key>`；匹配后只更新 `logical_key`，用户数据不动。- 合并到已存在作品：执行 `MergeContents` 事务——迁移 `movie_versions` / `episode_versions` / `file_segments` / 进度 / 收藏 / 片单 / 改名；冲突规则同 3.3.1；写入审计报告；旧 UUID 标记 tombstone，不物理删除。
- 不再存在“改 logical_id 级联所有外键”的路径；所有用户数据只引用 UUID。
- `LogicalKeyCodec` 只生成/解析 key；`ContentId` 类型用于接口。

> **S1 发现（2026-10-18）**：兼容 VIEW 需要 `works.scrape_state` / `scraped_at`；
> `title_details` 的 `release_date` / `runtime_minutes` / `country` / `genres` / `cast`
> 可放在 `works` 或独立 `work_details` 表。S1 原型已使用这些列，0a schema 必须补齐。

### 15.3 B2：多集、版本与进度（覆盖 3.3.2 / 3.15）

- **电影版本**：`movie_versions(work_id, file_id)`；一部电影多个文件/来源是版本。
- **剧集版本**：`episode_versions(episode_id, file_id)`；每集独立选版本，不能把整部剧的文件挂在作品级。
- **多集文件**：`file_segments(file_id, episode_id, segment_index, start_ms, end_ms)` 记录边界；每个覆盖的集都有一条 `episode_versions`。
- **进度**：
  - 普通单集/电影 → `episode_progress` / `movie_progress`（按内容）；
  - 多集文件 → `file_progress`（按文件）；播放位置越过某段 `end_ms` 时，将该段 `episode_progress.completed = 1`；暂停时不猜测边界。
  - 已看判定：`position >= 90%` 或距片尾 < 2 分钟；多集文件按段判定。
- **队列**：多集文件内的“下一集”按 `file_segments`/`episode` 顺序；文件结束后进入下一内容。
- **权威关系**：`file_segments` + `episode_versions` 是权威；`media_items` 兼容 VIEW 由此投影。
- **测试**：一文件多集、单集、多版本、跨集边界、暂停/续播、片尾标记。

### 15.4 B3：v9 兼容 VIEW（覆盖 4.2）

- v9 将旧 `media_items` 表重命名为 `media_items_legacy`，创建只读 VIEW `media_items`，列名与原表一致；
  `legacy_path` 保留旧 `path` 原值，保证现有 UI / 用户数据按旧 path 读取仍正确。
- 现有 UI 与 `MediaDao` 的**读**查询不变；**写**路径改为写新表（扫描/刮削/删除），删除对 `media_items` 的
  `insertAll` / `deleteAll` / `markScraped` 写入。
- v10 在 Phase 1 UI 迁到 `works` 后移除 VIEW 与 legacy 表。
- **Spike 必须验证**：Room 是否能通过现有 `MediaDao` 的 `@Query SELECT * FROM media_items` 读取 VIEW；
  `observeByPaths` / `observeMatchedCount` 是否正常；若 Room 校验阻止，则退回“仓库读适配层”，并在 ADR 中记录。
- 验收：VIEW 行数/关键列与 legacy 逐项一致；UI 现有页面无回归；扫描新数据能出现在现有海报墙。

### 15.5 B4：Provider health 分离（覆盖 3.13）

- `SourceHealth`：WebDAV / SMB / Emby / Jellyfin / LOCAL；只有源健康阻塞媒体库、播放、扫描。
- `ProviderHealth`：TMDB / OpenSubtitles / NFO；独立 `provider_health(provider, state, last_error, updated_at)`。
- 错误表修订：
  - 源 401/429 → 源 `BLOCKED`；
  - TMDB/字幕 401 → 对应 provider `AUTH_REQUIRED`，暂停其任务，**不阻塞源**；
  - TMDB/字幕 429 → provider 退避，不阻塞源。
- `SourceGate` 与 `ProviderGate` 分开；测试覆盖“TMDB 凭据错误时播放/扫描仍正常”。

### 15.6 B5：`scrape_tasks` 主键（覆盖 3.3.2）

```text
scrape_tasks(
  id TEXT PK,
  content_id TEXT NOT NULL, content_kind TEXT NOT NULL,
  provider TEXT NOT NULL, task_kind TEXT NOT NULL,   -- search/details/episodes
  state TEXT, attempts INTEGER, next_attempt_at INTEGER,
  priority INTEGER, last_error TEXT, updated_at INTEGER,
  UNIQUE(content_id, content_kind, provider, task_kind)
)
```

同一内容可有多个 provider / 多种任务；任务幂等，重复入队按唯一键合并。

### 15.7 B6：迁移完整性与备份（覆盖 4.2 / 4.3）

**完整映射矩阵**（必须逐表）：

| v8 表 | v9 去向 | 校验 |
|---|---|---|
| `media_items` | `media_files` + `movie_versions` / `episode_versions` + `file_segments` + `works` / `episodes` | 行数、path/name/size、作品/集数 |
| `playback_progress` | `movie_progress` / `episode_progress` / `file_progress` | 行数、position/duration/completed 校验和 |
| `favorites` | `favorites(work_id, user_id=default)` | 行数、work 映射 |
| `playlists` | `playlists(user_id=default)` | 行数 |
| `playlist_items` | `playlist_items(work_id)` | 行数、work 映射 |
| `title_overrides` | `title_overrides(work_id)` | 行数、title/year |
| `title_details` | `works` + `work_genres` + `people` / `work_cast` | 行数、tmdbId、演职员数 |
| `episode_meta` | `episodes` | 行数、季/集 |
| `filesDir/subtitles` | `subtitles` 表 + 文件索引 | 文件数、语言/来源 |

- **迁移状态文件**：`filesDir/migration/v9_state.json`，记录阶段、开始/结束、校验和、失败原因；在打开 Room 之前读取。
- **用户可访问备份**：除应用私有备份外，通过 SAF/MediaStore 导出到用户选择的位置；JSON 导出含 `schemaVersion`、校验和、冲突策略。
- **恢复**：迁移失败 → 恢复备份并阻止进入主界面；提供“导出诊断包/恢复”入口。
- **fixture**：脱敏 v8 种子库，覆盖多集、无 tmdbId、多版本、空密码/坏凭据、空收藏/片单。
- **shadow compare**：v9 保留 1–2 个版本，新旧读路径对比差异写日志；v10 删旧列。

### 15.8 SLO、错误预算与 DoD

| 指标 | 目标 |
|---|---|
| 401/429 自动重试 | **0 次/版本** |
| 迁移数据丢失 | **0 行/0 用户** |
| 冷启动 p95（S21 Ultra） | ≤ 2 s |
| 海报墙首屏 p95（20k 库） | ≤ 1 s |
| 搜索 p95（20k 库） | ≤ 300 ms |
| 起播 p95（局域网/VPS） | ≤ 3 s |
| crash-free sessions | ≥ 99.5%（可选上报或本地统计） |
| 扫描中断续跑成功率 | ≥ 99%（可达源） |
| 20k 库内存 | 无 OOM；< 300 MB |

- **错误预算**：认证重试与迁移数据丢失为零容忍；扫描/刮削失败预算按源统计，超预算先停任务并提示。
- **RPO/RTO**：RPO = 最近一次自动备份/导出；RTO = 重装 + 导入/恢复 ≤ 1 小时；迁移前必须备份。
- **DoD**：编译 + 98+ 单测；迁移测试；lint 无新增；真机验证；性能基线；日志无凭据；文档更新。

### 15.9 开工前四项 Spike

| # | Spike | 退出标准 |
|---|---|---|
| S1 | v8 fixture → v9 新 schema → 兼容 VIEW；Room DAO 读 VIEW | 迁移/回滚/逐键校验通过；VIEW 读查询正常；失败有 fallback 结论 |
| S2 | 中文搜索：FTS4+search_text / FTS5 trigram，20k 合成库 | p95 ≤ 300 ms；选定方案写入 ADR |
| S3 | DLNA / SMB 客户端评估（维护性、许可证、16KB、API） | 选定库与最小可运行 demo |
| S4 | FFmpeg 16KB page size + HDR/DV/Atmos 真机能力（S21 + 一台 DV/TV） | 可播放/降级矩阵与包体/许可证结论 |

**S1 结果（2026-10-18）**：SQL 迁移 spike 全部通过（VIEW 与 legacy 逐列一致、用户数据映射/去重正确）；
Room `@DatabaseView` 在 SM-G998U1 真机通过（含 `IN (:paths)`）。详见 WORKLOG 同名条目。

**S2 结果（2026-10-18）**：目标机 SQLite **不支持 FTS5 trigram**（`no such module: fts5`）；
FTS4（CJK 逐字空格 + 短语查询）20k 条 p50 1.20 ms / p95 3.44 ms；LIKE 基线 p50 7.85 ms / p95 8.11 ms；
DB 约 5.7 MB。**选定 FTS4 + `search_text`**，详见 WORKLOG 与 ADR-024。

**S3 结果（2026-10-18）**：jUPnP 3.0.5 因 Android 模块未声明 Jetty
（`NoClassDefFoundError: org.eclipse.jetty.server.Server`）不采用；SMBJ 0.15.0（Apache 2.0）
在 Android 可构造；原始 SSDP + 组播锁运行正常，但当前 Wi-Fi 返回 0 个设备
（无 DLNA 渲染器或网络隔离），真机投屏需在有电视的环境补验。
选定：**SMBJ + 自研 DLNA 控制器 + 本地 HTTP 代理**。


### 15.10 启动门槛

1. 提交当前 54 个文件为基线（文档第二版 + 注释/文案）；确认 98 单测 + release。
2. 按 15.2–15.7 修订 schema 与迁移计划，冻结 v9 设计。
3. 完成 S1–S4，记录结果与 ADR。
4. 确认 SLO/DoD/CI 门禁。
5. 开始 0a：只做数据层 + 兼容 VIEW，不混重构。
6. 0a 真机验证通过后，进入 0b / Phase 1。

### 15.11 仍需确认

- 是否同意先提交当前 54 个文件为基线。
- S3 后选择 DLNA/SMB 库；S2 后确定 FTS 方案；S4 后确定 HDR/DV 承诺。
- SLO 目标是否按 15.8 执行。
