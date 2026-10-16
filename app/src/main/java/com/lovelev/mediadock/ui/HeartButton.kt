package com.lovelev.mediadock.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** 收藏按钮的红：主流应用（X / Instagram）那个心都是红色，不跟主题强调色走。 */
private val LikeRed = Color(0xFFE0245E)

private const val GLYPH_DP = 30
private const val TOUCH_TARGET_DP = 48

/**
 * 收藏心形。用 Material 的 favorite / favoriteBorder 两个图标。
 *
 * **不要用 `♡` / `♥` 字形** —— 实测被用户指出「太扁、点击时大小会变」：
 * 那是两个不同字形，宽高都不一样，一点下去整块布局都会跳。
 * 图标是矢量、尺寸恒定，填充态和描边态形状一致，只在颜色和填充上变。
 *
 * 点击时按主流做法弹一下（缩放），只影响视觉、不影响布局。
 */
@Composable
fun HeartButton(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    /** 未收藏时的描边颜色。压在图片上时要传浅色，否则深灰描边看不见。 */
    outlineTint: Color? = null,
) {
    val scale = remember { Animatable(1f) }
    // 只在「变成已收藏」时弹；取消收藏不弹（主流应用也是这样）
    LaunchedEffect(isFavorite) {
        if (isFavorite) {
            scale.animateTo(
                targetValue = 1.32f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
            scale.animateTo(targetValue = 1f, animationSpec = spring())
        }
    }

    Box(
        modifier = modifier
            .size(TOUCH_TARGET_DP.dp)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (isFavorite) "取消收藏" else "收藏",
            tint = if (isFavorite) LikeRed else (outlineTint ?: MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier
                .size(GLYPH_DP.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
        )
    }
}
