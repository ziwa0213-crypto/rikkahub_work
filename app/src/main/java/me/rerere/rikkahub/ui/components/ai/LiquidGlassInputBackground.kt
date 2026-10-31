package me.rerere.rikkahub.ui.components.ai

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.view.Gravity
import androidx.core.view.doOnPreDraw
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.rememberCompositionContext
import com.qmdeve.liquidglass.widget.LiquidGlassView
import me.rerere.rikkahub.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.data.datastore.DisplaySetting
import java.util.WeakHashMap
import kotlin.math.roundToInt

private const val TAG = "LiquidGlassInput"
private val lastGlassSizeDiagnostics = WeakHashMap<LiquidGlassView, String>()

object LiquidGlassCircuitBreaker {
    private val state = MutableStateFlow(false)
    val unavailable: StateFlow<Boolean> = state.asStateFlow()

    fun disable(error: RuntimeException) {
        if (state.value) return
        Log.e(TAG, "Liquid glass disabled for this process; using Haze fallback", error)
        state.value = true
    }
}

internal fun shouldUseNativeLiquidGlass(
    apiLevel: Int,
    setting: DisplaySetting,
    unavailable: Boolean,
): Boolean = apiLevel >= 33 &&
    !unavailable &&
    setting.enableBlurEffect &&
    setting.backgroundEffectType == BackgroundEffectType.LIQUID

data class LiquidGlassInputBounds(
    val x: Float,
    val y: Float,
    val width: Int,
    val height: Int,
)

@Composable
fun LiquidGlassChatHost(
    sourceContent: @Composable () -> Unit,
    inputContent: @Composable () -> Unit,
    inputBounds: LiquidGlassInputBounds?,
    tintColor: Color,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
) {
    val parentCompositionContext = rememberCompositionContext()
    val sourceContentState = remember { mutableStateOf<@Composable () -> Unit>({}) }
    val inputContentState = remember { mutableStateOf<@Composable () -> Unit>({}) }
    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }
    SideEffect {
        sourceContentState.value = sourceContent
        inputContentState.value = inputContent
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val sourceView = ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setParentCompositionContext(parentCompositionContext)
                setContent { sourceContentState.value() }
            }
            val inputView = ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setParentCompositionContext(parentCompositionContext)
                setContent { inputContentState.value() }
            }
            FrameLayout(context).apply {
                clipChildren = false
                clipToPadding = false
                addView(
                    sourceView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                val glassView = try {
                    LiquidGlassView(context).apply {
                        isClickable = false
                        isFocusable = false
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        setDraggableEnabled(false)
                        setElasticEnabled(false)
                        setTouchEffectEnabled(false)
                        setCornerRadius(cornerRadiusPx)
                        setTintAlpha(0.18f)
                        setTintColorRed(tintColor.red)
                        setTintColorGreen(tintColor.green)
                        setTintColorBlue(tintColor.blue)
                        setTag(R.id.liquid_glass_tint, floatArrayOf(tintColor.red, tintColor.green, tintColor.blue))
                        setRenderFailureListener(LiquidGlassCircuitBreaker::disable)
                        visibility = View.GONE
                        bind(sourceView)
                    }
                } catch (error: RuntimeException) {
                    LiquidGlassCircuitBreaker.disable(error)
                    null
                }
                if (glassView != null) {
                    addView(glassView, FrameLayout.LayoutParams(0, 0))
                    setTag(R.id.liquid_glass_surface, glassView)
                }
                addView(
                    inputView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM,
                    ),
                )
                setTag(R.id.liquid_glass_source, sourceView)
                setTag(R.id.liquid_glass_input, inputView)
            }
        },
        update = { host ->
            val sourceView = host.getTag(R.id.liquid_glass_source) as? ViewGroup
            val glassView = host.getTag(R.id.liquid_glass_surface) as? LiquidGlassView
            if (sourceView != null && glassView != null && !LiquidGlassCircuitBreaker.unavailable.value) {
                try {
                    val tint = floatArrayOf(tintColor.red, tintColor.green, tintColor.blue)
                    val previousTint = glassView.getTag(R.id.liquid_glass_tint) as? FloatArray
                    if (previousTint == null || !previousTint.contentEquals(tint)) {
                        glassView.setTintColorRed(tint[0])
                        glassView.setTintColorGreen(tint[1])
                        glassView.setTintColorBlue(tint[2])
                        glassView.setTag(R.id.liquid_glass_tint, tint)
                    }
                    updateGlassBounds(host, glassView, inputBounds)
                } catch (error: RuntimeException) {
                    LiquidGlassCircuitBreaker.disable(error)
                    glassView.visibility = View.GONE
                }
            }
        },
    )
}

private fun updateGlassBounds(
    host: FrameLayout,
    glass: LiquidGlassView,
    bounds: LiquidGlassInputBounds?,
) {
    if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
        glass.visibility = View.GONE
        return
    }
    val hostLocation = IntArray(2)
    host.getLocationInWindow(hostLocation)
    val left = bounds.x.roundToInt() - hostLocation[0]
    val top = bounds.y.roundToInt() - hostLocation[1]
    val params = (glass.layoutParams as? FrameLayout.LayoutParams)
        ?: FrameLayout.LayoutParams(bounds.width, bounds.height)
    params.width = bounds.width
    params.height = bounds.height
    params.leftMargin = left
    params.topMargin = top
    glass.layoutParams = params

    val density = glass.resources.displayMetrics.density
    val heightDp = bounds.height / density
    glass.setRefractionHeight((heightDp * 0.45f).coerceIn(16f, 32f) * density)
    glass.setRefractionOffset((heightDp * 0.35f).coerceIn(14f, 28f) * density)
    glass.setBlurRadius(0f)
    glass.visibility = View.VISIBLE
    glass.refreshConfig()
    glass.doOnPreDraw {
        val diagnostic = "bounds=${bounds.width}x${bounds.height} glass=${glass.width}x${glass.height} " +
            "conf=${glass.debugConfigSize()} boundsW=${params.width} boundsH=${params.height}"
        if (lastGlassSizeDiagnostics[glass] != diagnostic) {
            lastGlassSizeDiagnostics[glass] = diagnostic
            Log.d(TAG, diagnostic)
        }
    }
}
