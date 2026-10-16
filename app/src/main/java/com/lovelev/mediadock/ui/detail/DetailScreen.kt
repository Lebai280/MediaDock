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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lovelev.mediadock.data.MediaTypes
import com.lovelev.mediadock.data.local.MediaItemEntity
import com.lovelev.mediadock.data.local.PlaybackProgress
import com.lovelev.mediadock.data.tmdb.CastMember
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

    if (entries.isEmpty()) {
        Box(
            modifier.fillMaxSize().background(NeutralPageTone),
            contentAlignment = Alignment.Center,
        ) {
            Text("没有可显示的内容", style = MaterialTheme.typography.bodyMedium, color = OnBackdropFaint)
        }
        return
    }

    val sample = entries.first()
    val queue = entries.map { it.path }
    val isSeries = entries.size >= 2

    // 「继续播放」的目标 = 最近看过的那一集；没看过就从第一集开始
    val resumable = entries
        .filter { (progress[it.path]?.positionMs ?: 0L) > 0L }
        .maxByOrNull { progress.getValue(it.path).updatedAt }
    val target = resumable ?: sample

    // 海报底部的主色（取不到就用中性深色），大图与它以下的背景都围绕它做
    val tone = (vm.heroTint?.readablePageTone()) ?: NeutralPageTone

    BoxWithConstraints(modifier.fillMaxSize().background(tone)) {
        val art = sample.heroArt()
        // 海报底边在页面上的高度占比（海报高 = 宽 ÷ (2/3) = 宽 × 1.5）——
        // 背景的色带要从这里开始接，才不会有分界
        val viewportH = maxHeight.value
        val heroBottom = if (art != null && viewportH.isFinite() && viewportH > 0f) {
            ((maxWidth.value * (1f / HERO_ASPECT)) / viewportH).coerceIn(0f, 1f)
        } else {
            0f
        }

        // 海报以下：同一个色往下铺，略微渐暗（内容区够暗，白字才读得清）
        PageBackground(tone, heroBottom)

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            HeroArt(item = sample, tone = tone) {
                Text(
                    text = sample.parsedTitle.ifBlank { sample.name },
                    // 艺术字：衬线体 + 加粗 + 拉开字距。系统自带衬线，不用打包字体。
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    style = MaterialTheme.typography.headlineMedium.copy(shadow = HeroTextShadow),
                    color = OnBackdrop,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )

                val meta = buildList {
                    sample.rating?.let { add(String.format(Locale.US, "★%.1f", it)) }
                    detail?.releaseDate?.takeIf { it.isNotBlank() }?.let { add(it.replace('-', '/')) }
                        ?: sample.year?.let { add(it.toString()) }
                    detail?.country?.takeIf { it.isNotBlank() }?.let(::add)
                    detail?.genres?.takeIf { it.isNotBlank() }?.let { add(it.replace(",", "，")) }
                    if (isSeries) add("共 ${entries.size} 集")
                }
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta.joinToString("  |  "),
                        style = MaterialTheme.typography.bodyMedium.copy(shadow = HeroTextShadow),
                        color = OnBackdropDim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }

                val fileParts = buildList {
                    // 时长优先用 TMDB 给的官方时长（不用播过就有），没有才退回播放进度里记的
                    detail?.runtimeMinutes?.takeIf { it > 0 }?.let { add("${it}分钟") }
                        ?: progress[target.path]?.durationMs?.let { formatDurationWords(it) }
                            ?.takeIf { it.isNotEmpty() }?.let(::add)
                    formatSize(sample.sizeBytes).takeIf { it.isNotEmpty() }?.let(::add)
                    MediaTypes.resolutionLabel(sample.name)?.let(::add)
                }
                if (fileParts.isNotEmpty()) {
                    Text(
                        text = fileParts.joinToString("  |  "),
                        style = MaterialTheme.typography.bodySmall.copy(shadow = HeroTextShadow),
                        color = OnBackdropFaint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }

                Spacer(Modifier.height(22.dp))

                Button(
                    onClick = { onPlay(target.path, queue) },
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (resumable != null) "继续播放" else "播放",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        resumable?.let {
                            Text(
                                text = "上次播放到 ${formatClock(progress.getValue(it.path).positionMs)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    HeartButton(
                        isFavorite = sample.path in favorites,
                        onToggle = { vm.toggleFavorite(sample.path) },
                        // 压在图上，描边心得用浅色才看得见
                        outlineTint = Color.White,
                    )
                }
            }

            // 简介、分集、演职人员落在海报延伸出来的同一片底色上
            sample.overview?.takeIf { it.isNotBlank() }?.let { OverviewSection(it) }

            vm.notice?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = NoticeTint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }

            if (isSeries) {
                HorizontalDivider(
                    color = HairLine,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp),
                )
                EpisodeList(entries, progress, resumable?.path, queue, onPlay)
            }

            CastRow(cast)

            Spacer(Modifier.height(28.dp))
        }

        // 返回 / 更多：钉在顶部（不随内容滚），随时够得着
        TopBar(
            item = sample,
            vm = vm,
            targetPath = target.path,
            targetDurationMs = progress[target.path]?.durationMs,
            onBack = onBack,
        )
    }
}

/** 顶部大图的比例：海报是 2:3 —— 按原比例铺宽度，整张可见、不裁剪。 */
private const val HERO_ASPECT = 2f / 3f

/** 没有海报/背景图时顶部留的高度（别占满 2:3，免得一大片空底）。 */
private val HERO_NO_ART_HEIGHT = 260.dp

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

/** 详情页用哪张图：优先海报（竖版，铺满竖屏不放大），没有才退回横版背景图。 */
internal fun MediaItemEntity.heroArt(): String? = posterPath ?: backdropPath

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
    content: @Composable ColumnScope.() -> Unit,
) {
    val art = item.heroArt()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (art != null) {
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
                // Fit：完整显示不裁剪。海报是 2:3，正好铺满这块；万一只有横版背景图，
                // 也完整显示、上下空档露出页面底色，照样接得上。
                contentScale = ContentScale.Fit,
                modifier = Modifier.matchParentSize(),
            )
            // 下半部压暗给白字兜底；**颜色是页面主色不是黑** —— 压完正好和底下的背景同色
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

/** 顶部固定的一条：返回 + 更多操作（修改信息 / 标记已看未看 / 重新刮削）。 */
@Composable
private fun BoxScope.TopBar(
    item: MediaItemEntity,
    vm: DetailViewModel,
    targetPath: String,
    targetDurationMs: Long?,
    onBack: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth()
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
                    text = { Text("重新刮削") },
                    onClick = { menuOpen = false; vm.rescrape(targetPath) },
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
                        label = { Text("年份（可空）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    editing = false
                    vm.saveOverride(item.path, title, yearText.toIntOrNull())
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = {
                    editing = false
                    vm.saveOverride(item.path, "", null)
                }) { Text("清除") }
            },
        )
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

@Composable
private fun EpisodeList(
    entries: List<MediaItemEntity>,
    progress: Map<String, PlaybackProgress>,
    resumablePath: String?,
    queue: List<String>,
    onPlay: (path: String, queue: List<String>) -> Unit,
) {
    // 只有真的分了好几季才打季标题，否则纯属噪音
    val seasons = entries.mapNotNull { it.season }.distinct().sorted()
    val showSeasonHeader = seasons.size >= 2

    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        entries
            .groupBy { it.season }
            .toSortedMap(compareBy { it ?: 0 })
            .forEach { (season, group) ->
                if (showSeasonHeader && season != null) {
                    Text(
                        text = "第 $season 季",
                        style = MaterialTheme.typography.titleSmall,
                        color = OnBackdrop,
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                group.forEach { episode ->
                    EpisodeRow(episode, progress[episode.path], episode.path == resumablePath) {
                        onPlay(episode.path, queue)
                    }
                }
            }
    }
}

@Composable
private fun EpisodeRow(
    episode: MediaItemEntity,
    progress: PlaybackProgress?,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = episode.episode?.let { "第 $it 集" } ?: episode.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.SemiBold else null,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else OnBackdrop,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            progress?.let {
                Text(
                    text = formatClock(it.positionMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnBackdropFaint,
                )
            }
        }

        val fraction = progress?.takeIf { it.durationMs > 0L }
            ?.let { (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) }
        if (fraction != null && fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
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
