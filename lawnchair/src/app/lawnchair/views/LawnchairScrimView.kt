package app.lawnchair.views

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import app.lawnchair.ui.glass.createGlassSheetView
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.R
import com.android.launcher3.util.SystemUiController
import com.android.launcher3.util.Themes
import com.android.launcher3.views.ScrimView

class LawnchairScrimView(context: Context, attrs: AttributeSet?) : ScrimView(context, attrs) {

    private var drawerOpacity = 0f

    init {
        val pm = PreferenceManager.getInstance(context)
        pm.drawerOpacity.subscribeValues(this) {
            drawerOpacity = it
        }
    }

    private var glassSheet: View? = null

    /** Drives the liquid glass sheet behind the app drawer: 0 = hidden (home), 1 = fully open. */
    fun setGlassProgress(progress: Float) {
        if (progress <= 0.01f && glassSheet == null) return
        val sheet = glassSheet ?: createGlassSheetView(context)?.also { view ->
            (parent as? ViewGroup)?.addView(view, (parent as ViewGroup).indexOfChild(this), ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            glassSheet = view
        } ?: return
        sheet.alpha = progress.coerceIn(0f, 1f)
        sheet.visibility = if (progress <= 0.01f) GONE else VISIBLE
    }

    override fun updateSysUiColors() {
        val threshold = STATUS_BAR_COLOR_FORCE_UPDATE_THRESHOLD
        val forceChange = visibility == VISIBLE &&
            alpha > threshold &&
            Color.alpha(mBackgroundColor) / (255f * drawerOpacity) > threshold
        with(systemUiController) {
            if (forceChange) {
                updateUiState(SystemUiController.UI_STATE_SCRIM_VIEW, !isScrimDark)
            } else {
                updateUiState(SystemUiController.UI_STATE_SCRIM_VIEW, 0)
            }
        }
    }

    override fun isScrimDark() = if (drawerOpacity <= 0.3f) {
        !Themes.getAttrBoolean(context, R.attr.isWorkspaceDarkText)
    } else {
        super.isScrimDark()
    }
}
