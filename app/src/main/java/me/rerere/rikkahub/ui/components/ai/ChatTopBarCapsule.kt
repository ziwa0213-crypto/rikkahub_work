package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.rerere.rikkahub.data.datastore.DisplaySetting

// 顶部栏按钮的浮动胶囊容器，背景效果与底部输入框保持一致；
// 背景单独一层，按 [showBackground] 淡入淡出，按钮本身始终显示
@Composable
fun ChatTopBarCapsule(
    hazeState: HazeState,
    displaySetting: DisplaySetting,
    showBackground: Boolean,
    forceLiquidGlass: Boolean = false,
    onGlassBoundsChanged: ((LiquidGlassInputBounds?, Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    val effectiveDisplaySetting = displaySetting.chatBackgroundSetting(forceLiquidGlass)
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (showBackground) 1f else 0f,
        label = "ChatTopBarCapsuleBackground",
    )
    var glassBounds by remember { mutableStateOf<LiquidGlassInputBounds?>(null) }
    val currentOnGlassBoundsChanged by rememberUpdatedState(onGlassBoundsChanged)
    DisposableEffect(Unit) {
        onDispose { currentOnGlassBoundsChanged?.invoke(null, 0f) }
    }
    SideEffect {
        val alpha = if (forceLiquidGlass && backgroundAlpha > 0.01f) backgroundAlpha else 0f
        onGlassBoundsChanged?.invoke(if (alpha > 0f) glassBounds else null, alpha)
    }
    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            // Cache layout even while transparent: an alpha change need not trigger another layout.
            if (forceLiquidGlass) {
                val position = coordinates.positionInRoot()
                val bounds = LiquidGlassInputBounds(
                    x = position.x,
                    y = position.y,
                    width = coordinates.size.width,
                    height = coordinates.size.height,
                )
                if (glassBounds != bounds) glassBounds = bounds
            } else if (glassBounds != null) {
                glassBounds = null
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        if (backgroundAlpha > 0f) {
            Surface(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = backgroundAlpha }
                    .clip(CircleShape)
                    .then(
                        if (forceLiquidGlass) Modifier else Modifier.chatInputHaze(
                            hazeState = hazeState,
                            displaySetting = effectiveDisplaySetting,
                            containerColor = containerColor,
                            shape = CircleShape,
                        )
                    ),
                shape = CircleShape,
                border = if (forceLiquidGlass) null else
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                color = if (effectiveDisplaySetting.enableBlurEffect) Color.Transparent else containerColor,
                content = {},
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
