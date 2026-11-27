package app.novapill.android.ui.screens

import androidx.compose.runtime.Composable
import app.novapill.android.settings.IdleInfo
import app.novapill.android.settings.NovaSettings
import app.novapill.android.settings.RecQuality
import app.novapill.android.settings.TempUnit
import app.novapill.android.settings.TileKeys
import app.novapill.android.ui.components.Hairline
import app.novapill.android.ui.components.IntSliderRow
import app.novapill.android.ui.components.ScreenScaffold
import app.novapill.android.ui.components.SectionLabel
import app.novapill.android.ui.components.SegmentedRow
import app.novapill.android.ui.components.SwitchRow

// -------------------------------------------------------------------------------------------------
// Quick panel: what the resting pill shows, the flashlight, and which tiles exist
// -------------------------------------------------------------------------------------------------

@Composable
internal fun QuickPage(nav: SettingsNav) {
    val s = rememberSettings()
    ScreenScaffold(title = "Quick panel", onBack = nav.back) {
        SectionLabel("Resting pill")
        SegmentedRow(
            title = "What the idle pill shows",
            options = listOf("Clock", "+ Battery", "+ Weather"),
            selected = s.idleInfo.ordinal,
            onSelect = { i -> save { it.copy(idleInfo = IdleInfo.entries[i]) } },
            subtitle = "Clock only. Clock and battery. Clock, weather and battery. Weather needs a city in Utilities and the internet; " +
                "when it is missing the pill shows a dash, never a made-up number.",
        )
        Note("With a camera hole in the middle of the pill, the clock sits on one side and the rest on the other. The pill gets a little wider to make room.")

        SectionLabel("Quick panel")
        SwitchRow(
            "Show the date",
            s.panelShowDate,
            { v -> save { it.copy(panelShowDate = v) } },
            "Under the clock at the top of the panel.",
        )
        SegmentedRow(
            title = "Temperature unit",
            options = listOf("Automatic", "°C", "°F"),
            selected = s.weatherUnit.ordinal,
            onSelect = { i -> save { it.copy(weatherUnit = TempUnit.entries[i]) } },
            subtitle = "Automatic follows the region of your phone.",
        )

        SectionLabel("Flashlight")
        SwitchRow(
            "Show it in the pill while it is on",
            s.flashShowInPill,
            { v -> save { it.copy(flashShowInPill = v) } },
            "The pill only says On when Android reports the torch as on. The tile in the quick panel always shows the real state.",
        )
        IntSliderRow(
            title = "Brightness",
            value = s.flashStrengthPercent,
            min = 10,
            max = 100,
            step = 10,
            onChange = { v -> save { it.copy(flashStrengthPercent = v) } },
            unit = " %",
            subtitle = "Only phones with Android 13 or newer and a dimmable torch use this. On other phones the torch is simply on or off.",
        )

        SectionLabel("Tiles")
        TileSwitch("Flashlight", TileKeys.FLASH, s)
        Hairline()
        TileSwitch("Stopwatch", TileKeys.STOPWATCH, s)
        Hairline()
        TileSwitch("Screen recorder", TileKeys.RECORD, s)
        Hairline()
        TileSwitch("Alarms", TileKeys.ALARMS, s)
        Hairline()
        TileSwitch("Calendar", TileKeys.CALENDAR, s)
        Hairline()
        TileSwitch("Phone", TileKeys.PHONE, s)
        Hairline()
        TileSwitch("Internet", TileKeys.INTERNET, s, "Opens Android's own Internet panel. An app may not switch Wi-Fi or mobile data itself.")
        Hairline()
        TileSwitch("Volume", TileKeys.VOLUME, s, "Opens Android's own volume panel.")
        Hairline()
        TileSwitch("Camera", TileKeys.CAMERA, s)
        Hairline()
        TileSwitch("Calculator", TileKeys.CALCULATOR, s, "The floating calculator. Needs Floating windows to be on.")
        Hairline()
        TileSwitch("Settings", TileKeys.SETTINGS, s, "Opens Android's settings.")
        Hairline()
        TileSwitch("Manual recording timer", TileKeys.MANUAL_TIMER, s, "Only appears when the manual timer is on in Record indicator.")
        Note("A hidden tile is only hidden. Nothing is switched off or uninstalled.")
    }
}

@Composable
private fun TileSwitch(title: String, key: String, s: NovaSettings, subtitle: String? = null) {
    SwitchRow(
        title = title,
        checked = s.tileVisible(key),
        onChange = { on -> save { cur -> cur.copy(hiddenTiles = if (on) cur.hiddenTiles - key else cur.hiddenTiles + key) } },
        subtitle = subtitle,
    )
}

// -------------------------------------------------------------------------------------------------
// Screen recorder
// -------------------------------------------------------------------------------------------------

@Composable
internal fun ScreenRecorderPage(nav: SettingsNav) {
    val s = rememberSettings()
    ScreenScaffold(title = "Screen recorder", onBack = nav.back) {
        Note(
            "Records the screen into a video. Android asks you to allow screen capture every single time, and shows its own recording " +
                "indicator while it runs. Videos are saved in Movies/NOVA-PILL.",
        )

        SectionLabel("Video")
        SegmentedRow(
            title = "Quality",
            options = listOf("720p", "1080p"),
            selected = s.recQuality.ordinal,
            onSelect = { i -> save { it.copy(recQuality = RecQuality.entries[i]) } },
            subtitle = "Size of the shorter side. 720p makes smaller files and is the safer choice on older phones. If the phone refuses a size, the recorder retries at a lower one and says so.",
        )
        SegmentedRow(
            title = "Frame rate",
            options = NovaSettings.REC_FPS_CHOICES.map { "$it fps" },
            selected = NovaSettings.REC_FPS_CHOICES.indexOf(s.recFps).coerceAtLeast(0),
            onSelect = { i -> save { it.copy(recFps = NovaSettings.REC_FPS_CHOICES[i]) } },
        )

        SectionLabel("Sound")
        SwitchRow(
            "Record the microphone",
            s.recMic,
            { v -> save { it.copy(recMic = v) } },
            "Asks for the microphone permission when you start. If it is refused, the video is recorded without sound and the pill says so.",
        )
        Note("Sound from other apps cannot be recorded by NOVA PILL. That would need a different, more restricted Android feature.")

        SectionLabel("Start and stop")
        SegmentedRow(
            title = "Countdown before recording",
            options = NovaSettings.REC_COUNTDOWN_CHOICES.map { if (it == 0) "None" else "$it s" },
            selected = NovaSettings.REC_COUNTDOWN_CHOICES.indexOf(s.recCountdownSec).coerceAtLeast(0),
            onSelect = { i -> save { it.copy(recCountdownSec = NovaSettings.REC_COUNTDOWN_CHOICES[i]) } },
            subtitle = "Time to close the quick panel before the first frame.",
        )
        SwitchRow(
            "Hide the pill while recording",
            s.recHidePill,
            { v -> save { it.copy(recHidePill = v) } },
            "Keeps the pill out of the video. Stop the recording from its notification.",
        )
    }
}

// -------------------------------------------------------------------------------------------------
// Floating windows
// -------------------------------------------------------------------------------------------------

@Composable
internal fun FloatingPage(nav: SettingsNav) {
    val s = rememberSettings()
    ScreenScaffold(title = "Floating windows", onBack = nav.back) {
        SectionLabel("NOVA PILL's own tools")
        SwitchRow(
            "Floating tools",
            s.floatingTools,
            { v -> save { it.copy(floatingTools = v) } },
            "A small calculator you can drag, resize and close, above other apps. It uses the same \"Display over other apps\" permission as the pill.",
        )

        SectionLabel("Other apps")
        SwitchRow(
            "Open shortcut apps in a floating window",
            s.floatingExternal,
            { v -> save { it.copy(floatingExternal = v) } },
            "Asks Android for a freeform window. Many phones ignore the request. When that happens the app opens normally and the pill tells you.",
        )
        Note("NOVA PILL only uses the public Android API for this. It cannot force another app into a window, and it does not use hidden system features.")

        SectionLabel("Size")
        IntSliderRow(
            title = "Width",
            value = s.floatingWidthPct,
            min = 40,
            max = 95,
            step = 5,
            onChange = { v -> save { it.copy(floatingWidthPct = v) } },
            unit = " % of the screen",
        )
        IntSliderRow(
            title = "Height",
            value = s.floatingHeightPct,
            min = 30,
            max = 90,
            step = 5,
            onChange = { v -> save { it.copy(floatingHeightPct = v) } },
            unit = " % of the screen",
        )
    }
}
