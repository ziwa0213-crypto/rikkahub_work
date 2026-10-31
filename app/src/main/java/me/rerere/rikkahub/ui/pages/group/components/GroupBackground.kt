package me.rerere.rikkahub.ui.pages.group.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import me.rerere.rikkahub.data.model.Group
import me.rerere.rikkahub.ui.pages.chat.MeshGradientBackground

@Composable
fun GroupBackground(group: Group?, modifier: Modifier = Modifier) {
    if (group == null) return
    if (group.useGradientBackground) {
        MeshGradientBackground(modifier = modifier)
        return
    }
    val background = group.background ?: return
    val backgroundColor = MaterialTheme.colorScheme.background
    Box(modifier) {
        AsyncImage(
            model = background, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(group.backgroundOpacity.coerceIn(0f, 1f)),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            backgroundColor.copy(alpha = 0.2f), backgroundColor.copy(alpha = 0.5f),
        ))))
    }
}
