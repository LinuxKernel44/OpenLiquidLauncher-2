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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
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

/**
 * A Compose view that draws the dock as liquid glass over the wallpaper.
 * Returns null when glass can't be drawn (API < 33 or no wallpaper access), so callers keep the stock background.
 */
@SuppressLint("ViewConstructor")
fun createGlassDockView(context: Context, cornerRadiusPx: Float): View? {
    if (!GlassSupported || !GlassWallpaper.canRead(context)) return null
    GlassWallpaper.refresh(context)
    val dm = context.resources.displayMetrics
    // Capsule-like by default; a larger custom radius from settings wins.
    val radius = maxOf(cornerRadiusPx / dm.density, 36f).dp
    return ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        isClickable = false
        isFocusable = false
        setContent {
            val wallpaper by GlassWallpaper.bitmap.collectAsState()
            val backdrop = rememberLayerBackdrop()
            var origin by remember { mutableStateOf(Offset.Zero) }
            Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
                // The wallpaper, drawn at its on-screen position so the glass refracts what is really behind it.
                Box(
                    Modifier.fillMaxSize()
                        .layerBackdrop(backdrop)
                        .drawBehind {
                            wallpaper?.let { wp ->
                                val x = -origin.x + (dm.widthPixels - wp.width) / 2f
                                val y = -origin.y + (dm.heightPixels - wp.height) / 2f
                                drawImage(wp, dstOffset = IntOffset(x.toInt(), y.toInt()), dstSize = IntSize(wp.width, wp.height))
                            }
                        },
                )
                Box(Modifier.fillMaxSize().liquidGlass(backdrop, DockStyle, RoundedCornerShape(radius), small = true))
            }
        }
    }
}
