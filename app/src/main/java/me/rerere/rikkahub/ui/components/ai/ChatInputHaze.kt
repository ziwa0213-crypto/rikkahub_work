package me.rerere.rikkahub.ui.components.ai

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.material3.Material3
import me.rerere.rikkahub.data.datastore.DisplaySetting

/** Blur mode, and the fallback when native liquid glass cannot be used. */
@Composable
internal fun Modifier.chatInputHaze(
    hazeState: HazeState,
    displaySetting: DisplaySetting,
): Modifier {
    if (!displaySetting.enableBlurEffect) return this
    return hazeBlur(
        input = HazeInput.Sources(hazeState),
        style = HazeBlurStyle.Material3 { blurRadius(12.dp) },
    )
}
