package app.novapill.android.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.novapill.android.NovaGraph
import app.novapill.android.settings.ClockFormat
import app.novapill.android.settings.NovaSettings
import app.novapill.android.settings.TempUnit
import app.novapill.android.ui.components.Gap
import app.novapill.android.ui.components.Hairline
import app.novapill.android.ui.components.InfoRow
import app.novapill.android.ui.components.IntSliderRow
import app.novapill.android.ui.components.NText
import app.novapill.android.ui.components.NavRow
import app.novapill.android.ui.components.NovaTextField
import app.novapill.android.ui.components.ScreenScaffold
import app.novapill.android.ui.components.SectionLabel
import app.novapill.android.ui.components.SegmentedRow
import app.novapill.android.ui.components.SwitchRow
import app.novapill.android.ui.theme.NovaType

// -------------------------------------------------------------------------------------------------
// F. Record indicator
// -------------------------------------------------------------------------------------------------

@Composable
internal fun RecordPage(nav: SettingsNav) {
    val s = rememberSettings()
    val osSupported = NovaGraph.record.osSupported
    ScreenScaffold(title = "Record indicator", onBack = nav.back) {
        InfoRow(
            "Android's own signal",
            if (osSupported) "Available on this phone (Android 15 or newer)" else "Not available (needs Android 15 or newer)",
        )
        Note(
            "Android 15 added a callback that tells an app when its own windows are visible in a screen recording. " +
                "That is the only automatic signal NOVA PILL uses. It does not watch other apps, and older Android versions have nothing like it. " +
                "The pill labels what it shows: OS means Android reported it, Manual means you started the timer, Unsupported means neither is available.",
        )

        SectionLabel("Detection")
        SwitchRow(
            "Use Android's recording signal",
            s.recordUseOs && osSupported,
            { v -> save { it.copy(recordUseOs = v) } },
            if (osSupported) "Shows a recording card while Android reports a recording." else "Not available on this Android version.",
            enabled = osSupported,
        )
        Hairline()
        SwitchRow(
            "Manual Start / Pause / Stop timer",
            s.recordManualEnabled,
            { v -> save { it.copy(recordManualEnabled = v) } },
            "A stopwatch you control from the tools card, for recordings Android does not report.",
        )

        SectionLabel("Display")
        SwitchRow(
            "Show duration",
            s.recordShowDuration,
            { v -> save { it.copy(recordShowDuration = v) } },
        )
        Hairline()
        SwitchRow(
            "Reset the timer on Stop",
            s.recordResetOnStop,
            { v -> save { it.copy(recordResetOnStop = v) } },
            "Off keeps the last time on screen until you press Reset.",
        )
    }
}

// -------------------------------------------------------------------------------------------------
// G. Landscape
// -------------------------------------------------------------------------------------------------

@Composable
internal fun LandscapePage(nav: SettingsNav) {
    val s = rememberSettings()
    ScreenScaffold(title = "Landscape", onBack = nav.back) {
        SectionLabel("Adapting")
        SwitchRow(
            "Adapt when rotated",
            s.lsAutoAdapt,
            { v -> save { it.copy(lsAutoAdapt = v) } },
            "Keeps the pill clear of the camera cutout and system bars in landscape.",
        )
        Hairline()
        SwitchRow(
            "Compact cards in landscape",
            s.lsCompact,
            { v -> save { it.copy(lsCompact = v) } },
            "A slightly smaller pill and a shorter music card, so it fits the lower screen height.",
        )
        Hairline()
        SwitchRow(
            "Hide in landscape fullscreen",
            s.hideInLandscapeFullscreen,
            { v -> save { it.copy(hideInLandscapeFullscreen = v) } },
            "Videos and games usually hide the status bar when rotated.",
        )

        SectionLabel("Position")
        SwitchRow(
            "Separate landscape position",
            s.lsIndependent,
            { v -> save { it.copy(lsIndependent = v) } },
            "Use the values below in landscape instead of the portrait ones.",
        )
        if (s.lsIndependent) {
            IntSliderRow("Width", s.lsWidthDp, 120, 320, { v -> save { it.copy(lsWidthDp = v) } }, unit = " dp")
            IntSliderRow("Distance from top", s.lsTopOffsetDp, -24, 160, { v -> save { it.copy(lsTopOffsetDp = v) } }, unit = " dp")
            IntSliderRow("Horizontal offset", s.lsXOffsetDp, -400, 400, { v -> save { it.copy(lsXOffsetDp = v) } }, unit = " dp")
        } else {
            Note("Portrait values are used in both orientations.")
        }
    }
}

// -------------------------------------------------------------------------------------------------
// H. Utilities
// -------------------------------------------------------------------------------------------------

@Composable
internal fun UtilitiesPage(nav: SettingsNav) {
    val s = rememberSettings()
    ScreenScaffold(title = "Utilities", onBack = nav.back) {
        SectionLabel("Clock")
        SegmentedRow(
            title = "Time format",
            options = listOf("System", "12 h", "24 h"),
            selected = s.clockFormat.ordinal,
            onSelect = { i -> save { it.copy(clockFormat = ClockFormat.entries[i]) } },
        )

        SectionLabel("Timer presets (minutes)")
        for (slot in 0 until 4) {
            val value = s.timerPresetsMin.getOrNull(slot) ?: DEFAULT_PRESETS[slot]
            IntSliderRow(
                title = "Preset ${slot + 1}",
                value = value,
                min = 1,
                max = 180,
                onChange = { v ->
                    save { cur ->
                        val list = (0 until 4).map { i -> cur.timerPresetsMin.getOrNull(i) ?: DEFAULT_PRESETS[i] }.toMutableList()
                        list[slot] = v
                        cur.copy(timerPresetsMin = list)
                    }
                },
                unit = " min",
            )
        }

        SectionLabel("Tools")
        SwitchRow(
            "Volume slider in the music card",
            s.volumeInMusicCard,
            { v -> save { it.copy(volumeInMusicCard = v) } },
            "Controls the media volume of the phone.",
        )
        Hairline()
        NavRow(
            title = "Shortcut apps",
            subtitle = "Up to ${NovaSettings.MAX_SHORTCUTS} icons in the tools card",
            trailing = if (s.shortcutApps.isEmpty()) "None" else "${s.shortcutApps.size}",
            onClick = { nav.openPicker(PickerPurpose.SHORTCUTS) },
        )

        SectionLabel("Weather")
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            NText("City", style = NovaType.BodyStrong)
            Gap(8)
            NovaTextField(
                value = s.weatherCity,
                onChange = { v -> save { it.copy(weatherCity = v.take(80)) } },
                placeholder = "For example: Jakarta",
            )
        }
        SwitchRow(
            "Use approximate location",
            s.weatherUseLocation,
            { v -> save { it.copy(weatherUseLocation = v) } },
            "Uses the last known coarse location when the location permission is on. The city above is the fallback.",
        )
        SegmentedRow(
            title = "Temperature unit",
            options = listOf("Automatic", "°C", "°F"),
            selected = s.weatherUnit.ordinal,
            onSelect = { i -> save { it.copy(weatherUnit = TempUnit.entries[i]) } },
            subtitle = "Automatic follows the region of your phone.",
        )
        IntSliderRow(
            title = "Refresh no more often than",
            value = s.weatherRefreshMin,
            min = 15,
            max = 720,
            onChange = { v -> save { it.copy(weatherRefreshMin = v) } },
            step = 15,
            format = { m -> if (m % 60 == 0) "${m / 60} h" else if (m > 60) "${m / 60} h ${m % 60} min" else "$m min" },
        )
        Note(
            "Weather is fetched when the quick panel opens, and while the idle pill shows it, never more often than the limit above. " +
                "The city name or rounded coordinates go to Open-Meteo. The panel shows where the reading is from and when it was taken, " +
                "and dims it when it is old. Weather data by Open-Meteo.com, licensed CC BY 4.0.",
        )
    }
}

private val DEFAULT_PRESETS = listOf(1, 5, 10, 25)
