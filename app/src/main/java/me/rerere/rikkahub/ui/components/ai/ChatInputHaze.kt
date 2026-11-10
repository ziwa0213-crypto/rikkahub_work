package me.rerere.rikkahub.ui.components.ai

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.material3.Material3
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.data.datastore.DisplaySetting

/** View-only fallback; never writes the selected background mode back to settings. */
internal fun DisplaySetting.chatBackgroundSetting(nativeLiquidGlass: Boolean): DisplaySetting =
    if (!nativeLiquidGlass && backgroundEffectType == BackgroundEffectType.LIQUID) {
        copy(backgroundEffectType = BackgroundEffectType.BLUR)
    } else this

/** Blur mode. Native liquid glass is drawn by LiquidGlassChatHost. */
@Composable
@Suppress("UNUSED_PARAMETER")
internal fun Modifier.chatInputHaze(
    hazeState: HazeState,
    displaySetting: DisplaySetting,
    containerColor: Color = Color.Unspecified,
    shape: Shape = RectangleShape,
): Modifier {
    if (!displaySetting.enableBlurEffect) return this
    if (displaySetting.backgroundEffectType == BackgroundEffectType.LIQUID) return this
    return hazeBlur(
        input = HazeInput.Sources(hazeState),
        style = HazeBlurStyle.Material3 { blurRadius(12.dp) },
    )
}
