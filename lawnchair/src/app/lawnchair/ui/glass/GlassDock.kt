@file:JvmName("GlassDock")

package app.lawnchair.ui.glass

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.util.Log
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.util.FileAccessManager
import app.lawnchair.util.FileAccessState
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Holds the system wallpaper as a bitmap, the source the glass refracts.
 * Needs the wallpaper-access permission Lawnchair already asks for; without it [bitmap] stays null.
 */
private const val TAG = "GlassWallpaper"

object GlassWallpaper {
    val bitmap = MutableStateFlow<ImageBitmap?>(null)

    /** True when the system wallpaper can be read, i.e. real glass can be drawn. */
    fun canRead(context: Context): Boolean {
        val access = FileAccessManager.getInstance(context)
        access.refresh() // the cached state can be stale at launcher start
        return access.wallpaperAccessState.value == FileAccessState.Full
    }

    fun refresh(context: Context) {
        if (!canRead(context)) return
        val app = context.applicationContext
        thread(name = "glass-wallpaper") {
            runCatching {
                val dm = app.resources.displayMetrics
                val drawable = WallpaperManager.getInstance(app).drawable
                if (drawable == null) {
                    Log.w(TAG, "WallpaperManager.drawable is null (live wallpaper or no access)")
                    return@runCatching
                }
                // Cover-fit the wallpaper to the screen once, so drawing it later is a plain blit.
                val scale = maxOf(
                    dm.widthPixels / drawable.intrinsicWidth.toFloat(),
                    dm.heightPixels / drawable.intrinsicHeight.toFloat(),
                )
                val w = (drawable.intrinsicWidth * scale).toInt().coerceAtLeast(1)
                val h = (drawable.intrinsicHeight * scale).toInt().coerceAtLeast(1)
                bitmap.value = drawable.toBitmap(w, h).asImageBitmap()
                Log.i(TAG, "wallpaper loaded ${w}x$h")
            }.onFailure { Log.e(TAG, "wallpaper load failed", it) }
        }
    }
}

/** Glass material used by the dock. */
private val DockStyle = GlassStyle(blur = 6f, refractionHeight = 16f, refractionAmount = 28f, surfaceAlpha = 0.16f, quality = 2, droplet = 0.2f)

/** Glass material of the full-screen app drawer sheet: heavy blur, no lens (a lens on a screen-sized sheet only bends the edges). */
private val SheetStyle = GlassStyle(blur = 28f, refractionHeight = 0f, refractionAmount = 0f, surfaceAlpha = 0.10f, quality = 1)

/**
 * A ComposeView that tracks its own on-screen position every frame (the host may be translated by an animation,
 * which does not trigger a Compose layout pass) so the glass keeps refracting the right part of the wallpaper.
 */
private class GlassHost(context: Context) : AbstractComposeView(context) {
    val origin = mutableStateOf(Offset.Zero)
    private val glassContent = mutableStateOf<@Composable () -> Unit>({})

    fun showGlass(content: @Composable () -> Unit) {
        glassContent.value = content
    }

    @Composable
    override fun Content() = glassContent.value()

    private val loc = IntArray(2)
    private val preDraw = android.view.ViewTreeObserver.OnPreDrawListener {
        getLocationOnScreen(loc)
        val o = Offset(loc[0].toFloat(), loc[1].toFloat())
        if (o != origin.value) origin.value = o
        true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(preDraw)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(preDraw)
        super.onDetachedFromWindow()
    }
}

/** The wallpaper drawn at its on-screen position into a backdrop, with a glass panel over it. */
@Composable
private fun GlassPanel(origin: State<Offset>, style: GlassStyle, shape: Shape, small: Boolean, edges: Boolean, tint: Color = Color.Unspecified) {
    val dm = LocalContext.current.resources.displayMetrics
    val wallpaper by GlassWallpaper.bitmap.collectAsState()
    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        // The wallpaper, drawn at its on-screen position so the glass refracts what is really behind it.
        Box(
            Modifier.fillMaxSize()
                .layerBackdrop(backdrop)
                .drawBehind {
                    wallpaper?.let { wp ->
                        val o = origin.value
                        val x = -o.x + (dm.widthPixels - wp.width) / 2f
                        val y = -o.y + (dm.heightPixels - wp.height) / 2f
                        drawImage(wp, dstOffset = IntOffset(x.toInt(), y.toInt()), dstSize = IntSize(wp.width, wp.height))
                    }
                },
        )
        Box(Modifier.fillMaxSize().liquidGlass(backdrop, style, shape, small = small, edges = edges, tint = tint))
    }
}

private fun glassComposeView(context: Context, content: @Composable (State<Offset>) -> Unit): View {
    GlassWallpaper.refresh(context)
    return GlassHost(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        isClickable = false
        isFocusable = false
        showGlass { content(origin) }
    }
}

/** True when real glass can be drawn: API 33+ and the wallpaper is readable. */
fun isGlassAvailable(context: Context): Boolean =
    GlassSupported && PreferenceManager.getInstance(context).glassEnabled.get() && GlassWallpaper.canRead(context)

/** Applies the user's blur / darkness settings to a style and tint. */
private fun GlassStyle.userScaled(context: Context): GlassStyle =
    copy(blur = blur * PreferenceManager.getInstance(context).glassBlur.get())

private fun Context.glassTint(base: Float): Color =
    Color.Black.copy(alpha = (base * PreferenceManager.getInstance(this).glassTint.get()).coerceIn(0f, 0.8f))

/**
 * A Compose view that draws the dock as liquid glass over the wallpaper.
 * Returns null when glass can't be drawn, so callers keep the stock background.
 */
fun createGlassDockView(context: Context, cornerRadiusPx: Float): View? {
    if (!isGlassAvailable(context)) return null
    val dm = context.resources.displayMetrics
    // Capsule-like by default; a larger custom radius from settings wins.
    val radius = maxOf(cornerRadiusPx / dm.density, 36f).dp
    return glassComposeView(context) { o -> GlassPanel(o, DockStyle.userScaled(context), RoundedCornerShape(radius), small = true, edges = true) }
}

/** A full-screen glass sheet for behind the app drawer. Returns null when glass can't be drawn. */
fun createGlassSheetView(context: Context): View? {
    if (!isGlassAvailable(context)) return null
    return glassComposeView(context) { o ->
        GlassPanel(o, SheetStyle.userScaled(context), RoundedCornerShape(0.dp), small = false, edges = false, tint = context.glassTint(0.30f))
    }
}

/** The glass bottom sheet of the app drawer (rounded top corners). Returns null when glass can't be drawn. */
fun createGlassDrawerSheetView(context: Context): View? {
    if (!isGlassAvailable(context)) return null
    return glassComposeView(context) { o ->
        GlassPanel(
            o, SheetStyle.userScaled(context), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            small = false, edges = true, tint = context.glassTint(0.20f),
        )
    }
}

/** The glass panel behind an opened folder (all corners rounded). Returns null when glass can't be drawn. */
fun createGlassFolderView(context: Context): View? {
    if (!isGlassAvailable(context)) return null
    return glassComposeView(context) { o ->
        GlassPanel(
            o, SheetStyle.copy(blur = 22f).userScaled(context), RoundedCornerShape(28.dp),
            small = false, edges = true, tint = context.glassTint(0.22f),
        )
    }
}

/** The glass panel behind a long-press / options popup menu. Returns null when glass can't be drawn. */
fun createGlassMenuView(context: Context): View? {
    if (!isGlassAvailable(context)) return null
    return glassComposeView(context) { o ->
        GlassPanel(
            o, SheetStyle.copy(blur = 18f).userScaled(context), RoundedCornerShape(24.dp),
            small = false, edges = true, tint = context.glassTint(0.28f),
        )
    }
}
