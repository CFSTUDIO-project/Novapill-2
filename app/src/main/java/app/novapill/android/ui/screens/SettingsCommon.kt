package app.novapill.android.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.novapill.android.NovaGraph
import app.novapill.android.settings.NovaSettings
import app.novapill.android.ui.components.NText
import app.novapill.android.ui.theme.NovaColors
import app.novapill.android.ui.theme.NovaType

/** What every settings page needs from the surrounding navigation. */
internal class SettingsNav(
    val back: () -> Unit,
    val openPicker: (PickerPurpose) -> Unit,
    val openPermissions: () -> Unit,
    /** Changes whenever the app comes back to the foreground, so permission readings refresh. */
    val resumeTick: Int,
)

@Composable
internal fun rememberSettings(): NovaSettings {
    val state by NovaGraph.settings.settings.collectAsState()
    return state
}

internal fun save(transform: (NovaSettings) -> NovaSettings) = NovaGraph.settings.update(transform)

/** Small explanatory paragraph under a control. */
@Composable
internal fun Note(text: String, color: Color = NovaColors.Text3) {
    NText(
        text,
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        NovaType.Caption.copy(fontSize = 13.sp, lineHeight = 18.sp),
        color = color,
    )
}

internal fun signedMs(value: Int): String = when {
    value > 0 -> "+$value ms"
    else -> "$value ms"
}
