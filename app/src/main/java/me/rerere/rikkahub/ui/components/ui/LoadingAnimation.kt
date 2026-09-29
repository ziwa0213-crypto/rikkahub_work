package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import me.rerere.ai.provider.LoadingAnimationMode
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.utils.computeAIIconByName

sealed interface ResolvedLoadingAnimation {
    data class Custom(val uri: String) : ResolvedLoadingAnimation
    data class Preset(val presetId: String) : ResolvedLoadingAnimation
    data class Brand(val iconFile: String) : ResolvedLoadingAnimation
    data object Material : ResolvedLoadingAnimation
}

fun resolveLoadingAnimation(provider: ProviderSetting?): ResolvedLoadingAnimation {
    val config = provider?.loadingAnimation
    when (config?.mode) {
        LoadingAnimationMode.CUSTOM -> {
            config.customUri?.takeIf { it.isNotBlank() }?.let {
                return ResolvedLoadingAnimation.Custom(it)
            }
        }

        LoadingAnimationMode.PRESET -> {
            config.presetId?.takeIf { it.isNotBlank() }?.let {
                return ResolvedLoadingAnimation.Preset(it)
            }
        }

        LoadingAnimationMode.AUTO, null -> Unit
    }

    if (provider == null) return ResolvedLoadingAnimation.Material

    val iconFile = computeAutoBrandIcon(provider)
    if (iconFile != null) return ResolvedLoadingAnimation.Brand(iconFile)

    return ResolvedLoadingAnimation.Brand(
        when (provider) {
            is ProviderSetting.OpenAI -> "openai.svg"
            is ProviderSetting.Google -> "gemini-color.svg"
            is ProviderSetting.Claude -> "claude-color.svg"
        }
    )
}

private fun computeAutoBrandIcon(provider: ProviderSetting): String? {
    return when (val iconFile = computeAIIconByName(provider.name)) {
        // These matcher aliases represent the same model channels used by the
        // dedicated Claude and Gemini loading animations.
        "google-color.svg" -> "gemini-color.svg"
        "anthropic.svg" -> "claude-color.svg"
        else -> iconFile
    }
}

@Composable
fun LoadingAnimationIndicator(
    modifier: Modifier = Modifier,
    provider: ProviderSetting? = null,
) {
    when (val resolved = resolveLoadingAnimation(provider)) {
        is ResolvedLoadingAnimation.Custom -> CustomLoadingAnimation(resolved.uri, modifier)
        is ResolvedLoadingAnimation.Preset -> PresetLoadingAnimation(
            presetId = resolved.presetId,
            provider = provider,
            modifier = modifier,
        )

        is ResolvedLoadingAnimation.Brand -> BrandLoadingAnimation(resolved.iconFile, modifier)
        ResolvedLoadingAnimation.Material -> ContainedLoadingIndicator(modifier = modifier)
    }
}

@Composable
private fun CustomLoadingAnimation(uri: String, modifier: Modifier) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(uri)
            .size(96)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

@Composable
private fun PresetLoadingAnimation(
    presetId: String,
    provider: ProviderSetting?,
    modifier: Modifier,
) {
    when (presetId) {
        "material" -> ContainedLoadingIndicator(modifier = modifier)
        "brand_openai" -> BrandLoadingAnimation("openai.svg", modifier)
        "brand_claude" -> BrandLoadingAnimation("claude-color.svg", modifier)
        "brand_gemini" -> BrandLoadingAnimation("gemini-color.svg", modifier)
        "brand_deepseek" -> BrandLoadingAnimation("deepseek-color.svg", modifier)
        "brand_generic" -> {
            val iconFile = provider?.let { computeAIIconByName(it.name) }
                ?: when (provider) {
                    is ProviderSetting.OpenAI -> "openai.svg"
                    is ProviderSetting.Google -> "gemini-color.svg"
                    is ProviderSetting.Claude -> "claude-color.svg"
                    null -> null
                }
            if (iconFile == null) {
                ContainedLoadingIndicator(modifier = modifier)
            } else {
                BrandLoadingAnimation(iconFile, modifier, forceAnimation = BrandAnimation.BREATHE)
            }
        }

        else -> ContainedLoadingIndicator(modifier = modifier)
    }
}

private enum class BrandAnimation {
    SPIN,
    CLAUDE_BLOOM,
    SPARKLE,
    PULSE,
    BREATHE,
}

private fun animationForIconFile(iconFile: String): BrandAnimation = when (iconFile) {
    "openai.svg" -> BrandAnimation.SPIN
    "claude-color.svg" -> BrandAnimation.CLAUDE_BLOOM
    "gemini-color.svg" -> BrandAnimation.SPARKLE
    "deepseek-color.svg" -> BrandAnimation.PULSE
    else -> BrandAnimation.BREATHE
}

@Composable
private fun BrandLoadingAnimation(
    iconFile: String,
    modifier: Modifier,
    forceAnimation: BrandAnimation? = null,
) {
    val animation = forceAnimation ?: animationForIconFile(iconFile)
    when (animation) {
        BrandAnimation.CLAUDE_BLOOM -> ClaudeBloomAnimation(modifier)
        else -> {
            val transition = rememberInfiniteTransition(label = "loading_" + iconFile)
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = when (animation) {
                            BrandAnimation.SPIN -> 1200
                            BrandAnimation.SPARKLE -> 2400
                            BrandAnimation.PULSE -> 1800
                            BrandAnimation.BREATHE -> 2500
                            BrandAnimation.CLAUDE_BLOOM -> 2000
                        },
                        easing = when (animation) {
                            BrandAnimation.SPIN -> LinearEasing
                            else -> FastOutSlowInEasing
                        },
                    ),
                    repeatMode = if (animation == BrandAnimation.SPIN) {
                        RepeatMode.Restart
                    } else {
                        RepeatMode.Reverse
                    },
                ),
                label = "loading_progress",
            )
            val rotation = when (animation) {
                BrandAnimation.SPIN -> progress * 360f
                BrandAnimation.SPARKLE -> progress * 90f
                BrandAnimation.BREATHE -> -5f + progress * 10f
                else -> 0f
            }
            val scale = when (animation) {
                BrandAnimation.SPARKLE -> 0.9f + progress * 0.1f
                BrandAnimation.PULSE -> 0.9f + progress * 0.15f
                BrandAnimation.BREATHE -> 0.95f + progress * 0.1f
                else -> 1f
            }
            val alpha = if (animation == BrandAnimation.SPARKLE) {
                0.7f + progress * 0.3f
            } else {
                1f
            }
            BrandIcon(
                iconFile = iconFile,
                modifier = modifier.graphicsLayer {
                    rotationZ = rotation
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                },
            )
        }
    }
}

@Composable
private fun BrandIcon(
    iconFile: String,
    modifier: Modifier,
) {
    val colorFilter = if (iconFile.contains("-color")) {
        null
    } else {
        ColorFilter.tint(MaterialTheme.colorScheme.primary)
    }
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data("file:///android_asset/icons/$iconFile")
            .size(96)
            .build(),
        contentDescription = null,
        colorFilter = colorFilter,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

private val ClaudeRayRingShape = GenericShape { size, _ ->
    addOval(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height), Path.Direction.Clockwise)
    addOval(
        androidx.compose.ui.geometry.Rect(
            left = size.width / 2f - size.minDimension * 0.19f,
            top = size.height / 2f - size.minDimension * 0.19f,
            right = size.width / 2f + size.minDimension * 0.19f,
            bottom = size.height / 2f + size.minDimension * 0.19f,
        ),
        Path.Direction.CounterClockwise,
    )
}

private val ClaudeBrandOrange = Color(0xFFD97757)

@Composable
private fun ClaudeBloomAnimation(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "claude_bloom")
    val outerScale by transition.animateFloat(
        initialValue = 0.50f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "claude_scale",
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        BrandIcon(
            iconFile = "claude-color.svg",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = outerScale
                    scaleY = outerScale
                    transformOrigin = TransformOrigin.Center
                }
                .clip(ClaudeRayRingShape),
        )
        Box(
            modifier = Modifier
                .fillMaxSize(0.39f)
                .background(ClaudeBrandOrange, CircleShape),
        )
    }
}
