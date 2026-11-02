package app.lawnchair.ui.glass

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/**
 * Liquid glass needs RuntimeShader (API 33+). Below that, [liquidGlass] falls back to a flat translucent tint.
 */
val GlassSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** Tunable parameters of the liquid glass material. */
@Immutable
data class GlassStyle(
    val blur: Float = 4f,
    val refractionHeight: Float = 14f,
    val refractionAmount: Float = 24f,
    val surfaceAlpha: Float = 0.18f,
    val dispersion: Boolean = false,
    /** 0 Minimum, 1 Low, 2 Current, 3 High, 4 Maximum. */
    val quality: Int = 2,
    /** 0..1 droplet look for small elements. */
    val droplet: Float = 0f,
)

/**
 * Draws [backdrop] (the wallpaper) through a refracting, blurred, highlighted glass layer
 * (Kyant0's AndroidLiquidGlass). [shape] must be a corner-based shape for the lens effect.
 */
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    style: GlassStyle,
    shape: Shape,
    surfaceAlpha: Float = style.surfaceAlpha,
    tint: Color = Color.Unspecified,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    /** Rim light and drop shadow; turn off for full-screen sheets. */
    edges: Boolean = true,
    /** Small element (dock, folder, button): the droplet setting makes it clearer and more refractive. */
    small: Boolean = false,
): Modifier = if (!GlassSupported) {
    (if (layerBlock != null) Modifier.graphicsLayer(layerBlock) else Modifier)
        .background(Color.White.copy(alpha = surfaceAlpha + 0.06f), shape)
} else {
    val d = if (small) style.droplet else 0f
    drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            val q = style.quality
            if (q >= 1) vibrancy()
            val blurK = (if (q >= 4) 1.25f else 1f) * (1f - 0.7f * d)
            if (style.blur * blurK > 0f) blur((style.blur * blurK).dp.toPx())
            if (q >= 1 && style.refractionHeight > 0f) {
                // The lens ring is capped to 30% of the shorter side, or the ends of wide capsules smear.
                val maxH = minOf(size.width, size.height) * 0.30f
                val h = minOf((style.refractionHeight * (1f + 0.8f * d)).dp.toPx(), maxH)
                lens(
                    h,
                    minOf((style.refractionAmount * (1f + 0.5f * d)).dp.toPx(), h * 1.9f),
                    depthEffect = small && (q >= 3 || d > 0.5f) && size.width <= size.height * 1.6f,
                    chromaticAberration = style.dispersion || q >= 4,
                )
            }
        },
        highlight = if (edges && style.quality >= 1) {
            { if (d > 0f) Highlight(width = (0.5f + 1.5f * d).dp, alpha = 1f) else Highlight.Default }
        } else {
            null
        },
        shadow = if (edges && style.quality >= 2) {
            { if (d > 0f) Shadow(radius = (24f + 10f * d).dp, color = Color.Black.copy(alpha = 0.1f + 0.1f * d)) else Shadow.Default }
        } else {
            null
        },
        innerShadow = if (small && (style.quality >= 3 || d > 0f)) {
            {
                val radius = (10f + 8f * d).dp
                InnerShadow(radius = radius, offset = DpOffset(0.dp, radius), color = Color.Black.copy(alpha = 0.08f + 0.10f * d))
            }
        } else {
            null
        },
        layerBlock = layerBlock,
        onDrawSurface = {
            drawRect(Color.Black.copy(alpha = 0.10f * (1f - 0.5f * d))) // keeps white text readable on bright wallpapers
            if (tint != Color.Unspecified) drawRect(tint)
            drawRect(Color.White.copy(alpha = surfaceAlpha * (1f - 0.55f * d)))
        },
    )
}

/** Percent-based corner shape; 50 = capsule or circle. */
fun glassShape(percent: Int): RoundedCornerShape = RoundedCornerShape(percent = percent.coerceIn(0, 50))
