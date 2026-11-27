package app.novapill.android.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.novapill.android.NovaGraph
import app.novapill.android.overlay.PillKind
import app.novapill.android.settings.NovaSettings
import app.novapill.android.ui.components.AppButton
import app.novapill.android.ui.components.ButtonKind
import app.novapill.android.ui.components.GapW
import app.novapill.android.ui.components.Hairline
import app.novapill.android.ui.components.IntSliderRow
import app.novapill.android.ui.components.NText
import app.novapill.android.ui.components.NavRow
import app.novapill.android.ui.components.NovaIcon
import app.novapill.android.ui.components.ScreenScaffold
import app.novapill.android.ui.components.SectionLabel
import app.novapill.android.ui.components.SwitchRow
import app.novapill.android.ui.theme.NovaType

// -------------------------------------------------------------------------------------------------
// A. General
// -------------------------------------------------------------------------------------------------

@Composable
internal fun GeneralPage(nav: SettingsNav) {
    val s = rememberSettings()
    var confirmReset by remember { mutableStateOf(false) }
    ScreenScaffold(title = "General", onBack = nav.back) {
        SectionLabel("Start-up")
        SwitchRow(
            "Restore island on launch",
            s.restoreOnLaunch,
            { v -> save { it.copy(restoreOnLaunch = v) } },
            "If the island was running when you left, opening NOVA PILL starts it again.",
        )
        Hairline()
        SwitchRow(
            "Start after reboot",
            s.autoStartOnBoot,
            { v -> save { it.copy(autoStartOnBoot = v) } },
            "Starts the island again once the phone has booted, if it was running. Some phone makers block this unless the app is allowed to auto-start.",
        )

        SectionLabel("Setup")
        NavRow(
            title = "Permission wizard",
            subtitle = "See what is enabled and change it",
            icon = NovaIcon.SHIELD,
            onClick = nav.openPermissions,
        )

        SectionLabel("Reset")
        if (!confirmReset) {
            NavRow(
                title = "Reset all settings",
                subtitle = "Back to the defaults. Imported lyrics are kept.",
                icon = NovaIcon.RESET,
                danger = true,
                chevron = false,
                onClick = { confirmReset = true },
            )
        } else {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                NText("Every setting goes back to its default. This cannot be undone.", style = NovaType.Body)
                Box(Modifier.padding(top = 14.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        AppButton("Cancel", { confirmReset = false }, Modifier.weight(1f), ButtonKind.SECONDARY, height = 48.dp)
                        GapW(10)
                        AppButton(
                            "Reset",
                            {
                                NovaGraph.settings.resetAll()
                                confirmReset = false
                            },
                            Modifier.weight(1f),
                            ButtonKind.DANGER,
                            height = 48.dp,
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// B. Appearance (the preview stays pinned above the controls)
// -------------------------------------------------------------------------------------------------

@Composable
internal fun AppearancePage(nav: SettingsNav) {
    val s = rememberSettings()
    val context = LocalContext.current
    val model = remember { PreviewModel(context.applicationContext) }
    val expanded = model.kind == PillKind.EXPANDED
    val previewHeight by animateDpAsState(if (expanded) 300.dp else 104.dp, spring(dampingRatio = 0.9f, stiffness = 420f), label = "previewHeight")

    ScreenScaffold(title = "Appearance", onBack = nav.back, scroll = false) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            PreviewIsland(model, height = previewHeight)
        }
        NText(
            "Live preview with sample data. Tap it to open.",
            Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            NovaType.Caption.copy(fontSize = 12.sp, lineHeight = 16.sp),
        )
        Hairline(Modifier.padding(top = 4.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SectionLabel("Size")
            IntSliderRow("Width", s.widthDp, 120, 260, { v -> save { it.copy(widthDp = v) } }, unit = " dp")
            IntSliderRow("Height", s.heightDp, 30, 52, { v -> save { it.copy(heightDp = v) } }, unit = " dp")
            IntSliderRow("Corner roundness", s.cornerPercent, 50, 100, { v -> save { it.copy(cornerPercent = v) } }, unit = "%")

            SectionLabel("Position")
            IntSliderRow(
                "Distance from top",
                s.topOffsetDp,
                -24,
                160,
                { v -> save { it.copy(topOffsetDp = v) } },
                unit = " dp",
                subtitle = "Added to the default spot under the status bar or camera cutout.",
            )
            IntSliderRow("Horizontal offset", s.xOffsetDp, -300, 300, { v -> save { it.copy(xOffsetDp = v) } }, unit = " dp")
            Note("To drag the pill with your finger, turn on Long-press to reposition in Behavior.")

            SectionLabel("Look")
            IntSliderRow("Border glow", s.borderIntensity, 0, 100, { v -> save { it.copy(borderIntensity = v) } }, unit = "%")
            IntSliderRow(
                "Accent strength",
                s.accentIntensity,
                0,
                100,
                { v -> save { it.copy(accentIntensity = v) } },
                unit = "%",
                subtitle = "How strongly the album colour tints buttons and highlights.",
            )
            IntSliderRow(
                "Opacity",
                s.opacityPercent,
                70,
                100,
                { v -> save { it.copy(opacityPercent = v) } },
                unit = "%",
                subtitle = "Never below 70% so text stays readable.",
            )
            IntSliderRow("Artwork size", s.artworkSizeDp, 40, 72, { v -> save { it.copy(artworkSizeDp = v) } }, unit = " dp")
            IntSliderRow("Text size", s.fontScalePercent, 80, 130, { v -> save { it.copy(fontScalePercent = v) } }, unit = "%")

            SectionLabel("Feel")
            SwitchRow(
                "Haptics",
                s.haptics,
                { v -> save { it.copy(haptics = v) } },
                "A light tick on buttons and gestures. Follows the system touch-feedback setting.",
            )

            Box(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                AppButton(
                    "Reset appearance",
                    {
                        val d = NovaSettings()
                        save {
                            it.copy(
                                widthDp = d.widthDp,
                                heightDp = d.heightDp,
                                topOffsetDp = d.topOffsetDp,
                                xOffsetDp = d.xOffsetDp,
                                cornerPercent = d.cornerPercent,
                                borderIntensity = d.borderIntensity,
                                artworkSizeDp = d.artworkSizeDp,
                                fontScalePercent = d.fontScalePercent,
                                opacityPercent = d.opacityPercent,
                                accentIntensity = d.accentIntensity,
                            )
                        }
                    },
                    Modifier.fillMaxWidth(),
                    ButtonKind.SECONDARY,
                    height = 48.dp,
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// C. Behavior
// -------------------------------------------------------------------------------------------------

@Composable
internal fun BehaviorPage(nav: SettingsNav) {
    val s = rememberSettings()
    ScreenScaffold(title = "Behavior", onBack = nav.back) {
        SectionLabel("Gestures")
        SwitchRow(
            "Tap to open",
            s.tapToExpand,
            { v -> save { it.copy(tapToExpand = v) } },
            "Tap the pill to open the music, timer or tools card. Tap again to close.",
        )
        Hairline()
        SwitchRow(
            "Swipe gestures",
            s.swipeGestures,
            { v -> save { it.copy(swipeGestures = v) } },
            "Swipe sideways to change card, down to open, up to close.",
        )
        Hairline()
        SwitchRow(
            "Long-press to reposition",
            s.longPressReposition,
            { v -> save { it.copy(longPressReposition = v) } },
            "Hold the pill, then drag it where you want. Done saves the spot and Reset puts it back.",
        )

        SectionLabel("When nothing is playing")
        IntSliderRow(
            "Idle pill stays for",
            s.idleTimeoutSec,
            2,
            60,
            { v -> save { it.copy(idleTimeoutSec = v) } },
            unit = " s",
            subtitle = "The small clock pill that shows after start-up before it hides.",
        )
        SwitchRow(
            "Always show a minimal pill",
            s.showMinimalIdlePill,
            { v -> save { it.copy(showMinimalIdlePill = v) } },
            "Keeps a small clock pill on screen even when nothing is happening.",
        )
        Hairline()
        SwitchRow(
            "Hide when playback stops",
            s.hideAfterMediaStops,
            { v -> save { it.copy(hideAfterMediaStops = v) } },
            "Removes the music card as soon as the player reports Stopped.",
        )

        SectionLabel("Hiding")
        SwitchRow(
            "Hide in fullscreen apps",
            s.hideInFullscreen,
            { v -> save { it.copy(hideInFullscreen = v) } },
            "Videos, games and anything else that hides the status bar.",
        )
        Hairline()
        SwitchRow(
            "Hide while the keyboard is open",
            s.hideWithKeyboard,
            { v -> save { it.copy(hideWithKeyboard = v) } },
        )
        Hairline()
        NavRow(
            title = "Hide in selected apps",
            subtitle = "Needs Usage access, which is optional",
            trailing = if (s.hiddenApps.isEmpty()) "None" else "${s.hiddenApps.size}",
            onClick = { nav.openPicker(PickerPurpose.HIDDEN_APPS) },
        )

        SectionLabel("Motion")
        SwitchRow(
            "Reduce motion",
            s.reducedMotion,
            { v -> save { it.copy(reducedMotion = v) } },
            "Replaces springs, glows and the visualizer with short fades. Also applied when Android's animations are turned off.",
        )
        Hairline()
        SwitchRow(
            "Reduce motion in battery saver",
            s.reduceMotionOnBatterySaver,
            { v -> save { it.copy(reduceMotionOnBatterySaver = v) } },
            "Applies Reduce motion automatically while Battery saver is on.",
        )
    }
}
