package com.lovelev.mediadock.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.lovelev.mediadock.data.MediaTypes
import com.lovelev.mediadock.data.local.EpisodeMeta
import com.lovelev.mediadock.data.local.MediaItemEntity
import com.lovelev.mediadock.data.local.PlaybackProgress
import com.lovelev.mediadock.data.tmdb.CastMember
import com.lovelev.mediadock.data.tmdb.TitleParser
import com.lovelev.mediadock.data.tmdb.TmdbClient
import com.lovelev.mediadock.ui.HeartButton
import com.lovelev.mediadock.ui.formatClock
import com.lovelev.mediadock.ui.formatDurationWords
import com.lovelev.mediadock.ui.formatSize
import java.util.Locale

/**
 * **详情页是「海报 + 它自己的颜色延伸」的一整块面，不能看着割裂成两块。**
 *
 * 做法：
 *  - 顶部海报按原始比例（2:3）`ContentScale.Fit` **完整显示、不裁剪**，标题 / 元数据 /
 *    文件信息 / 继续播放 / 收藏压在它下半部；
 *  - 取**海报底部一条的平均色**当页面主色（见 `PosterTint`），大图下半部的压暗**渐变成这个色**
 *    而不是渐变成黑，图以下再用同一个色往下铺（略微渐暗，保证白字读得清）——
 *    于是海报和它下面的内容是同一个色调，视觉上连成一片。
 *  - 取不到色（没图 / 没网）时退回一块中性的深色渐变，不影响功能。
 *
 * 为什么用海报而不是横版背景图：横版 16:9 铺满竖屏要被放大约 4 倍、只剩中间一条，用户明确说
 * 「只能看到部分」；海报是竖版，按原比例铺宽度正好是一块高画面。（`Fit` 而非 `Crop`：万一只有
 * 横版图也完整显示，接得上。）
 *
 * 没做的两件事（参考图里有、这里刻意不放）：多资源切换的「资源」胶囊、片单 / 举报图标 ——
 * 都没有真实功能，不做点着没反应的假按钮。见 WORKLOG。
 */
@Composable
fun DetailScreen(
    vm: DetailViewModel,
    onPlay: (path: String, queue: List<String>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries by vm.entries.collectAsState()
    val progress by vm.progress.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val detail by vm.detail.collectAsState()
    val cast by vm.cast.collectAsState()
    val versions by vm.versions.collectAsState()

    if (entries.isEmpty()) {
        Box(
            modifier.fillMaxSize().background(NeutralPageTone),
            contentAlignment = Alignment.Center,
        ) {
            Text("暂无内容", style = MaterialTheme.typography.bodyMedium, color = OnBackdropFaint)
        }
        return
    }

    val sample = entries.first()
    // 队列：剧集用这几集；单片用同目录的视频（见 DetailViewModel.playQueue 的注释）
    val queue by vm.playQueue.collectAsState()
    val isSeries = entries.size >= 2

    // 「继续播放」的目标 = 最近看过的那一集；没看过就从第一集开始
    val resumable = entries
        .filter { (progress[it.path]?.positionMs ?: 0L) > 0L }
        .maxByOrNull { progress.getValue(it.path).updatedAt }
    val target = resumable ?: sample

    var chosenVersion by remember(entries) { mutableStateOf<MediaItemEntity?>(null) }
    val playItem = chosenVersion ?: target
    val playPosition = progress[playItem.path]?.positionMs ?: 0L
    val playQueue = if (chosenVersion != null) listOf(playItem.path) else queue

    fun seasonOf(e: MediaItemEntity): Int? = e.season ?: TitleParser.seasonOfPath(e.path)
    val seasons = entries.map { seasonOf(it) }.filterNotNull().distinct().sorted()
    val multiSeason = seasons.size >= 2
    // 没有季信息时也给一个「第 1 季」标签，这样也能点它展开全部
    val seasonTabs = seasons.ifEmpty { listOf(1) }
    var selectedSeason by remember(entries) {
        mutableStateOf(seasonOf(target) ?: seasonTabs.first())
    }
    // 默认横滑；再点一次当前季标签 → 展开成双列全量（用户要求结合两种）
    var showAllEpisodes by remember(entries) { mutableStateOf(false) }
    val currentSeason = selectedSeason.takeIf { it in seasonTabs } ?: seasonTabs.first()
    val episodeMeta = vm.episodeMeta

    LaunchedEffect(sample.tmdbId, currentSeason, isSeries) {
        val tmdbId = sample.tmdbId
        if (isSeries && tmdbId != null) vm.loadSeason(tmdbId, currentSeason)
    }

    val titleLine = buildString {
        append(sample.parsedTitle.ifBlank { sample.name })
        // 与爆米花一致：剧集标题始终带季号（单季也显示「第 1 季」）
        if (isSeries) append(" 第 $currentSeason 季")
    }

    // 海报底部的主色（取不到就用中性深色），大图与它以下的背景都围绕它做
    val tone = (vm.heroTint?.readablePageTone()) ?: NeutralPageTone

    BoxWithConstraints(modifier.fillMaxSize().background(tone)) {
        val art = sample.heroArt()
        // 用户要求：详情页横幅约占半屏
        val heroHeight = if (art != null) maxHeight * 0.5f else HERO_NO_ART_HEIGHT
        val heroBottom = if (art != null) 0.5f else 0f

        // 海报以下：同一个色往下铺，略微渐暗（内容区够暗，白字才读得清）
        PageBackground(tone, heroBottom)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            HeroArt(item = sample, tone = tone, height = heroHeight)

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                // ---- 标题 + 元数据（爆米花式：图下、左对齐）----
                Text(
                    text = titleLine,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = OnBackdrop,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))

                val metaText = buildList {
                    detail?.releaseDate?.takeIf { it.isNotBlank() }?.let { add(it.replace('-', '/')) }
                        ?: sample.year?.let { add(it.toString()) }
                    if (isSeries) {
                        val total = detail?.totalEpisodes
                        add(
                            if (total != null && total > 0) {
                                "共 $total 集（库中 ${entries.size} 集）"
                            } else {
                                "共 ${entries.size} 集"
                            },
                        )
                    } else {
                        detail?.runtimeMinutes?.takeIf { it > 0 }?.let { add("${it}分钟") }
                            ?: progress[target.path]?.durationMs?.let { formatDurationWords(it) }
                                ?.takeIf { it.isNotEmpty() }?.let(::add)
                        formatSize(sample.sizeBytes).takeIf { it.isNotEmpty() }?.let(::add)
                        MediaTypes.resolutionLabel(sample.name)?.let(::add)
                    }
                }.joinToString("  ")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sample.rating?.let { RatingBadge(it) }
                    if (metaText.isNotBlank()) {
                        Text(metaText, style = MaterialTheme.typography.bodyMedium, color = OnBackdropDim)
                    }
                }

                val genreText = detail?.genres?.takeIf { it.isNotBlank() }?.replace(",", "  ")
                if (genreText != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(genreText, style = MaterialTheme.typography.bodySmall, color = OnBackdropFaint)
                }

                Spacer(Modifier.height(18.dp))

                // ---- 白色播放按钮（爆米花样式）----
                Surface(
                    onClick = { onPlay(playItem.path, playQueue) },
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFF141414),
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (playPosition > 0L) "继续播放" else "播放",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF141414),
                        )
                    }
                }

                if (versions.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "版本",
                            style = MaterialTheme.typography.labelLarge,
                            color = OnBackdropFaint,
                        )
                        VersionChip(
                            text = versionLabel(sample),
                            selected = chosenVersion == null,
                        ) { chosenVersion = null }
                        versions.forEach { version ->
                            VersionChip(
                                text = versionLabel(version),
                                selected = chosenVersion?.path == version.path,
                            ) { chosenVersion = version }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    HeartButton(
                        isFavorite = sample.path in favorites,
                        onToggle = { vm.toggleFavorite(sample.path) },
                        outlineTint = Color.White,
                    )
                }

                if (isSeries) {
                    Spacer(Modifier.height(20.dp))
                    SeasonTabs(
                        seasons = seasonTabs,
                        selected = currentSeason,
                        onSelect = { picked ->
                            if (picked == currentSeason) {
                                showAllEpisodes = !showAllEpisodes
                            } else {
                                selectedSeason = picked
                                showAllEpisodes = false
                            }
                        },
                    )
                    EpisodeGrid(
                        entries = entries,
                        season = currentSeason,
                        multiSeason = multiSeason,
                        expanded = showAllEpisodes,
                        metaByEpisode = episodeMeta,
                        progress = progress,
                        currentPath = resumable?.path,
                        onPlay = onPlay,
                        queue = queue,
                    )
                }

                sample.overview?.takeIf { it.isNotBlank() }?.let { OverviewSection(it) }

                vm.notice?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = NoticeTint,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    )
                }

                CastRow(cast)

                Spacer(Modifier.height(28.dp))
            }
        }

        // 返回 / 更多：钉在顶部（不随内容滚），随时够得着
        TopBar(
            item = sample,
            vm = vm,
            targetPath = playItem.path,
            targetDurationMs = progress[playItem.path]?.durationMs ?: progress[target.path]?.durationMs,
            paths = entries.map { it.path },
            isTv = isSeries,
            onBack = onBack,
        )
    }
}

/** 顶部大图：爆米花式横版横幅（16:9），完整铺满宽度。 */
private const val HERO_ASPECT = 16f / 9f

/** 没有海报/背景图时顶部留的高度。 */
private val HERO_NO_ART_HEIGHT = 200.dp

/** 简介收起时的行数。超出的靠「展开」看。 */
private const val OVERVIEW_COLLAPSED_LINES = 4

/**
 * 取不到海报色（没图 / 没网 / 解码失败）时的中性底色 —— 一块深色竖向渐变。
 * 用纯黑会让整页是一片死黑、看着像坏了。
 */
private val NeutralPageTone = Color(0xFF1B2126)

/** 页面底色亮度上限：白字压上去要读得清，同时还能看出是海报那个色。 */
private const val PAGE_TONE_LUMINANCE = 0.30f

private val OnBackdrop = Color.White
private val OnBackdropDim = Color.White.copy(alpha = 0.82f)
private val OnBackdropFaint = Color.White.copy(alpha = 0.60f)
private val NoticeTint = Color(0xFFFFB4AB)
private val HairLine = Color.White.copy(alpha = 0.14f)

/** 压在图上那几行白字的投影：亮底色上也读得清（配合大图下半部的压暗）。 */
private val HeroTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.60f),
    offset = Offset(0f, 1.5f),
    blurRadius = 8f,
)

/** 详情页用哪张图：**横版背景图优先**（爆米花式横幅），没有才退回竖版海报。 */
internal fun MediaItemEntity.heroArt(): String? = backdropPath ?: posterPath

/**
 * 把海报底部那个平均色压成**能当页面底色**的色调：保留色相，把亮度压到
 * [PAGE_TONE_LUMINANCE] 以下 —— 白字压上去读得清，同时还能看出是海报那个颜色。
 * 只压暗、不提亮（本来就是深色的海报不会被提亮成灰）。
 */
private fun Color.readablePageTone(): Color {
    val luminance = 0.2126f * red + 0.7152f * green + 0.0722f * blue
    val factor = if (luminance <= PAGE_TONE_LUMINANCE) 1f else PAGE_TONE_LUMINANCE / luminance
    return Color(red * factor, green * factor, blue * factor, 1f)
}

/** 按比例调暗（保留色相），用来做「往下略微渐暗」。 */
private fun Color.mul(factor: Float): Color =
    Color(red * factor, green * factor, blue * factor, alpha)

/**
 * 海报以下的页面底色：从海报底边（[heroBottom]）开始是同一个色调，往下略微渐暗。
 * 图以上是透明的（那块被海报盖住）。这样图与下方同色，不会有分界线。
 */
@Composable
private fun BoxScope.PageBackground(tone: Color, heroBottom: Float) {
    val bottom = heroBottom.coerceIn(0f, 0.98f)
    val stops = buildList {
        if (bottom > 0.02f) add(0f to Color.Transparent)
        add(bottom to tone)
        add(bottom + (1f - bottom) * 0.30f to tone.mul(0.74f))
        add(1f to tone.mul(0.55f))
    }
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(Brush.verticalGradient(*stops.toTypedArray())),
    )
}

/**
 * 顶部大图：**整张按原比例显示（不裁剪）**，标题等信息压在它的下半部。
 * 底边的压暗**渐变成页面主色 [tone]**（不是渐变成黑），这样大图和下面的背景同色、连成一片。
 */
@Composable
private fun HeroArt(
    item: MediaItemEntity,
    tone: Color,
    height: Dp? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val art = item.heroArt()
    // 横版背景图铺满横幅没问题；只有竖版海报时用 Fit 完整显示 ——
    // 半屏高的横幅比 2:3 海报更宽扁，Crop 会把海报上下裁掉（用户反馈「被局部放大」）。
    val isBackdrop = !item.backdropPath.isNullOrBlank()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(tone)
            .then(
                if (height != null) {
                    Modifier.height(height)
                } else if (art != null) {
                    Modifier.aspectRatio(HERO_ASPECT)
                } else {
                    Modifier.height(HERO_NO_ART_HEIGHT)
                },
            ),
    ) {
        if (art != null) {
            AsyncImage(
                model = TmdbClient.posterUrl(art, width = 780),
                contentDescription = null,
                contentScale = if (isBackdrop) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.matchParentSize(),
            )
            // 下半部压暗给白字回退；**颜色是页面主色不是黑** —— 压完正好和底下的背景同色
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.28f to Color.Transparent,
                            0.58f to tone.copy(alpha = 0.50f),
                            1.00f to tone.copy(alpha = 0.96f),
                        ),
                    ),
            )
        }
        if (content != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
    }
}

/** 顶部固定的一条：返回 + 更多操作（修改信息 / 标记已看未看 / 重新刮削）。 */
@Composable
private fun BoxScope.TopBar(
    item: MediaItemEntity,
    vm: DetailViewModel,
    targetPath: String,
    targetDurationMs: Long?,
    paths: List<String>,
    isTv: Boolean,
    onBack: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var matching by remember { mutableStateOf(false) }
    var addingToList by remember { mutableStateOf(false) }
    val playlists by vm.playlists.collectAsState()

    Row(
        modifier = Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
        Spacer(Modifier.weight(1f))
        Box {
            RoundIconButton(Icons.Filled.MoreVert, "更多操作") { menuOpen = true }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("修改信息") },
                    onClick = { menuOpen = false; editing = true },
                )
                DropdownMenuItem(
                    text = { Text("标记为已看") },
                    onClick = {
                        menuOpen = false
                        vm.setWatched(targetPath, targetDurationMs ?: 0L, watched = true)
                    },
                )
                DropdownMenuItem(
                    text = { Text("标记为未看") },
                    onClick = {
                        menuOpen = false
                        vm.setWatched(targetPath, 0L, watched = false)
                    },
                )
                DropdownMenuItem(
                    text = { Text("加入片单") },
                    onClick = { menuOpen = false; addingToList = true },
                )
                DropdownMenuItem(
                    text = { Text("在线匹配") },
                    onClick = { menuOpen = false; matching = true },
                )
            }
        }
    }

    if (editing) {
        var title by remember(item.path) { mutableStateOf(item.parsedTitle) }
        var yearText by remember(item.path) {
            mutableStateOf(item.year?.toString().orEmpty())
        }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("修改信息") },
            text = {
                Column {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("片名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = yearText,
                        onValueChange = { yearText = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("年份") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    editing = false
                    vm.saveOverrideAndScrape(paths, title, yearText.toIntOrNull())
                }) { Text("保存并匹配") }
            },
            dismissButton = {
                TextButton(onClick = {
                    editing = false
                    vm.clearOverride(paths)
                }) { Text("清除") }
            },
        )
    }

    if (matching) {
        MatchDialog(
            vm = vm,
            item = item,
            paths = paths,
            isTv = isTv,
            onDismiss = { matching = false },
        )
    }

    if (addingToList) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingToList = false },
            title = { Text("加入片单") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("新建片单") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = {
                            vm.createPlaylistAndAdd(newName, targetPath)
                            addingToList = false
                        },
                        enabled = newName.isNotBlank(),
                    ) { Text("新建并加入") }
                    if (playlists.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        playlists.forEach { playlist ->
                            TextButton(
                                onClick = {
                                    vm.addToPlaylist(playlist.id, targetPath)
                                    addingToList = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = playlist.name,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Start,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { addingToList = false }) { Text("关闭") }
            },
        )
    }
}

/** 全屏「在线匹配」：列出所有 TMDB 候选，点一条应用到整部剧/整部片。 */
@Composable
private fun MatchDialog(
    vm: DetailViewModel,
    item: MediaItemEntity,
    paths: List<String>,
    isTv: Boolean,
    onDismiss: () -> Unit,
) {
    var query by remember(item.path) { mutableStateOf(item.parsedTitle) }

    LaunchedEffect(Unit) {
        if (vm.matchCandidates.isEmpty()) {
            vm.searchMatches(query, item.year, isTv)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .widthIn(max = 560.dp)
                .heightIn(max = 720.dp)
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f),
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "在线匹配",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        onClick = onDismiss,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.height(32.dp),
                    ) {
                        Text(
                            text = "关闭",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("片名") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { vm.searchMatches(query, item.year, isTv) },
                        enabled = query.isNotBlank() && !vm.matchBusy,
                    ) {
                        Text(if (vm.matchBusy) "搜索中" else "搜索")
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (vm.matchBusy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                vm.notice?.takeIf { vm.matchCandidates.isEmpty() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(vm.matchCandidates, key = { it.tmdbId }) { match ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.applyMatch(paths, match)
                                    onDismiss()
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (match.posterPath != null) {
                                AsyncImage(
                                    model = TmdbClient.posterUrl(match.posterPath, width = 92),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(48.dp)
                                        .height(72.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = match.title.orEmpty(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = listOfNotNull(
                                        match.year?.toString(),
                                        match.rating?.let { String.format(Locale.US, "★%.1f", it) },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                match.overview?.takeIf { it.isNotBlank() }?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** 压在背景图上的圆形图标按钮：半透明深色圆底 + 白图标（图上放纯图标会看不见）。 */
@Composable
private fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * 简介。**只有真的超出 [OVERVIEW_COLLAPSED_LINES] 行才出现「展开」** —— 短简介不该白挂一个开关。
 * 展开时不再回写 [overflows]（那时 `hasVisualOverflow` 必然是 false，会把开关又藏起来）。
 */
@Composable
private fun OverviewSection(overview: String) {
    var expanded by remember(overview) { mutableStateOf(false) }
    var overflows by remember(overview) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp),
    ) {
        Text(
            text = overview,
            style = MaterialTheme.typography.bodyMedium,
            color = OnBackdropDim,
            maxLines = if (expanded) Int.MAX_VALUE else OVERVIEW_COLLAPSED_LINES,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout -> if (!expanded) overflows = layout.hasVisualOverflow },
        )
        if (overflows || expanded) {
            // 深色背景上柠檬绿（primary）读得清，直接用品牌色
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    text = if (expanded) "收起" else "展开",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** 版本标签（分辨率 / 体积）。 */
private fun versionLabel(item: MediaItemEntity): String {
    val resolution = MediaTypes.resolutionLabel(item.name)
    val size = formatSize(item.sizeBytes).takeIf { it.isNotBlank() }
    return listOfNotNull(resolution, size).joinToString(" · ").ifBlank { item.name }
}

@Composable
private fun VersionChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) Color.White else Color.White.copy(alpha = 0.14f),
        contentColor = if (selected) Color(0xFF141414) else OnBackdrop,
        modifier = Modifier.height(28.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

/** 季标签：爆米花式的文字 tab + 选中下划线。 */
@Composable
private fun SeasonTabs(seasons: List<Int>, selected: Int?, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        seasons.forEach { season ->
            val active = season == selected
            Column(
                modifier = Modifier.clickable { onSelect(season) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "第 $season 季",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (active) OnBackdrop else OnBackdropFaint,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(2.dp)
                        .background(if (active) OnBackdrop else Color.Transparent),
                )
            }
        }
    }
}

/** 双列分集卡片（剧照 + 时长 + 分集标题）。 */
@Composable
private fun EpisodeGrid(
    entries: List<MediaItemEntity>,
    season: Int,
    multiSeason: Boolean,
    expanded: Boolean,
    metaByEpisode: Map<Int, EpisodeMeta>,
    progress: Map<String, PlaybackProgress>,
    currentPath: String?,
    onPlay: (path: String, queue: List<String>) -> Unit,
    queue: List<String>,
) {
    val shown = if (multiSeason) {
        entries.filter { (it.season ?: TitleParser.seasonOfPath(it.path)) == season }
    } else {
        entries
    }.sortedBy { it.episode ?: 0 }

    if (!expanded) {
        // 默认：左右滑动的分集行（爆米花式）
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(shown, key = { it.path }) { episode ->
                EpisodeCard(
                    episode = episode,
                    meta = metaByEpisode[episode.episode],
                    progress = progress[episode.path],
                    isCurrent = episode.path == currentPath,
                    onClick = { onPlay(episode.path, queue) },
                    modifier = Modifier.width(150.dp),
                )
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        shown.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { episode ->
                    EpisodeCard(
                        episode = episode,
                        meta = metaByEpisode[episode.episode],
                        progress = progress[episode.path],
                        isCurrent = episode.path == currentPath,
                        onClick = { onPlay(episode.path, queue) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: MediaItemEntity,
    meta: EpisodeMeta?,
    progress: PlaybackProgress?,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.10f)),
        ) {
            meta?.stillPath?.let { still ->
                AsyncImage(
                    model = TmdbClient.stillUrl(still),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            val metaRuntime = meta?.runtimeMinutes
            val durationLabel = when {
                metaRuntime != null && metaRuntime > 0 ->
                    formatClock(metaRuntime * 60_000L)
                progress != null && progress.durationMs > 0L -> formatClock(progress.durationMs)
                else -> null
            }
            durationLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.60f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
            val fraction = progress?.takeIf { it.durationMs > 0L }
                ?.let { (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) }
            if (fraction != null && fraction > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(fraction)
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = buildString {
                episode.episode?.let { append("$it. ") }
                append(meta?.name?.takeIf { it.isNotBlank() } ?: episode.name.substringBeforeLast('.'))
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isCurrent) FontWeight.SemiBold else null,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else OnBackdrop,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 评分徽标（爆米花用豆瓣；我们用 TMDB 分，保持同样的绿色块样式）。 */
@Composable
private fun RatingBadge(rating: Double) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF2BB24C))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = String.format(Locale.US, "★%.1f", rating),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
    }
}

/** 演职人员：横滑的圆形头像 + 名字 + 角色。头像没有就用名字首字占位，不编图。 */
@Composable
private fun CastRow(cast: List<CastMember>) {
    if (cast.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Text(
            text = "演职人员",
            style = MaterialTheme.typography.titleSmall,
            color = OnBackdrop,
            modifier = Modifier.padding(start = 20.dp, bottom = 10.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(cast) { member -> CastItem(member) }
        }
    }
}

@Composable
private fun CastItem(member: CastMember) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(76.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            if (member.profilePath != null) {
                AsyncImage(
                    model = TmdbClient.posterUrl(member.profilePath, width = 185),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = member.name.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    color = OnBackdropDim,
                )
            }
        }
        Text(
            text = member.name,
            style = MaterialTheme.typography.bodySmall,
            color = OnBackdrop,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        member.role?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = OnBackdropFaint,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
