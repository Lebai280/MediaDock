package com.lovelev.mediadock.ui.favorites

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lovelev.mediadock.ui.library.PosterGrid

@Composable
fun FavoritesScreen(
    vm: FavoritesViewModel,
    onOpenDetail: (paths: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items by vm.items.collectAsState()
    val progress by vm.progress.collectAsState()

    if (items.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无收藏", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    PosterGrid(
        items = items,
        progress = progress,
        onOpenDetail = onOpenDetail,
        modifier = modifier.padding(top = 4.dp),
    )
}
