package app.lawnchair.ui.preferences.destinations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.glass.GlassSupported
import app.lawnchair.ui.preferences.components.WallpaperAccessPermissionDialog
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.util.FileAccessManager
import app.lawnchair.util.FileAccessState
import com.android.launcher3.R

/** Settings of the liquid glass look (OpenLiquidLauncher-2). */
@Composable
fun GlassPreferenceGroup() {
    val context = LocalContext.current
    val prefs = preferenceManager()
    val fileAccess = remember { FileAccessManager.getInstance(context) }
    val wallpaperAccess by fileAccess.wallpaperAccessState.collectAsStateWithLifecycle()
    val allFilesAccess by fileAccess.allFilesAccessState.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }

    PreferenceGroup(heading = stringResource(R.string.glass_label)) {
        SwitchPreference(
            label = stringResource(R.string.glass_enable),
            description = stringResource(R.string.glass_enable_description),
            adapter = prefs.glassEnabled.getAdapter(),
            enabled = GlassSupported,
        )
        SliderPreference(
            label = stringResource(R.string.glass_blur),
            adapter = prefs.glassBlur.getAdapter(),
            step = 0.1f,
            valueRange = 0f..2f,
            showAsPercentage = true,
        )
        SliderPreference(
            label = stringResource(R.string.glass_tint),
            adapter = prefs.glassTint.getAdapter(),
            step = 0.1f,
            valueRange = 0f..2f,
            showAsPercentage = true,
        )
        if (wallpaperAccess != FileAccessState.Full) {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            ) {
                FilledTonalButton(onClick = { showDialog = true }) {
                    Text(stringResource(R.string.glass_wallpaper_access))
                }
            }
        }
    }

    if (showDialog) {
        WallpaperAccessPermissionDialog(
            managedFilesChecked = allFilesAccess != FileAccessState.Denied,
            onDismiss = { showDialog = false },
            onPermissionRequest = { fileAccess.refresh() },
        )
    }

    LifecycleResumeEffect(Unit) {
        showDialog = false
        fileAccess.refresh()
        onPauseOrDispose { }
    }
}
