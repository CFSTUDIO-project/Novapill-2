package app.novapill.android.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.novapill.android.NovaGraph
import app.novapill.android.notif.NotificationFilter
import app.novapill.android.perm.Permissions
import app.novapill.android.settings.LyricsHighlight
import app.novapill.android.settings.LyricsProviderChoice
import app.novapill.android.settings.NotificationMode
import app.novapill.android.settings.NovaSettings
import app.novapill.android.settings.PauseBehavior
import app.novapill.android.settings.PreviewPrivacy
import app.novapill.android.settings.QueueBehavior
import app.novapill.android.ui.components.AppButton
import app.novapill.android.ui.components.ButtonKind
import app.novapill.android.ui.components.Gap
import app.novapill.android.ui.components.GapW
import app.novapill.android.ui.components.Hairline
import app.novapill.android.ui.components.IntSliderRow
import app.novapill.android.ui.components.NText
import app.novapill.android.ui.components.NavRow
import app.novapill.android.ui.components.ScreenScaffold
import app.novapill.android.ui.components.SectionLabel
import app.novapill.android.ui.components.SegmentedRow
import app.novapill.android.ui.components.SwitchRow
import app.novapill.android.ui.theme.NovaColors
import app.novapill.android.ui.theme.NovaType
import app.novapill.android.util.IntentLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// -------------------------------------------------------------------------------------------------
// D. Notifications
// -------------------------------------------------------------------------------------------------

@Composable
internal fun NotificationsPage(nav: SettingsNav) {
    val s = rememberSettings()
    val context = LocalContext.current
    val access = remember(nav.resumeTick) { Permissions.notificationAccess(context) }
    val durations = NovaSettings.PEEK_DURATIONS_SEC

    ScreenScaffold(title = "Notifications", onBack = nav.back) {
        if (!access) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                NText(
                    "Notification access is off, so nothing can peek yet.",
                    style = NovaType.Caption.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = NovaColors.Yellow,
                )
                Gap(8)
                AppButton(
                    "Open Notification access",
                    { IntentLauncher.start(context, Permissions.notificationAccessIntent(context)) },
                    Modifier.fillMaxWidth(),
                    ButtonKind.SECONDARY,
                    height = 44.dp,
                )
            }
        }

        SectionLabel("Peek")
        SwitchRow(
            "Show notification peeks",
            s.peekEnabled,
            { v -> save { it.copy(peekEnabled = v) } },
            "A short banner with the app name, who wrote and a one-line preview. Media notifications and group summaries never peek.",
        )
        SegmentedRow(
            title = "Peek length",
            options = durations.map { "$it s" },
            selected = durations.indexOf(s.peekDurationSec).coerceAtLeast(0),
            onSelect = { i -> save { it.copy(peekDurationSec = durations[i]) } },
            subtitle = "Incoming calls stay until you answer, decline or the call ends.",
        )
        SegmentedRow(
            title = "When several arrive",
            options = listOf("Queue them", "Latest only"),
            selected = s.queueBehavior.ordinal,
            onSelect = { i -> save { it.copy(queueBehavior = QueueBehavior.entries[i]) } },
            subtitle = "Calls and alarms always go to the front.",
        )
        SwitchRow(
            "Quick reply from the peek",
            s.peekReply,
            { v -> save { it.copy(peekReply = v) } },
            "Shows a Reply button only when the app itself offers a text reply, as chat apps usually do. The reply is sent through that app. NOVA PILL keeps nothing.",
        )

        SectionLabel("Which apps")
        SegmentedRow(
            title = "Mode",
            options = listOf("All except muted", "Only allowed"),
            selected = s.notifMode.ordinal,
            onSelect = { i -> save { it.copy(notifMode = NotificationMode.entries[i]) } },
        )
        NavRow(
            title = "Allowed apps",
            subtitle = "Used in \"Only allowed\" mode",
            trailing = countLabel(s.allowedApps.size),
            onClick = { nav.openPicker(PickerPurpose.ALLOWED_APPS) },
        )
        Hairline()
        NavRow(
            title = "Muted apps",
            subtitle = "Never peek, in either mode",
            trailing = countLabel(s.mutedApps.size),
            onClick = { nav.openPicker(PickerPurpose.MUTED_APPS) },
        )

        SectionLabel("Mute categories")
        NotificationFilter.MUTABLE_CATEGORIES.forEachIndexed { index, (key, label) ->
            SwitchRow(
                title = label,
                checked = key in s.mutedCategories,
                onChange = { on ->
                    save { cur -> cur.copy(mutedCategories = if (on) cur.mutedCategories + key else cur.mutedCategories - key) }
                },
            )
            if (index != NotificationFilter.MUTABLE_CATEGORIES.lastIndex) Hairline()
        }
        Note("A muted category never peeks. Calls and alarms are not part of this list.")

        SectionLabel("Privacy")
        SegmentedRow(
            title = "What a peek shows",
            options = listOf("App name", "Summary", "Full"),
            selected = s.privacy.ordinal,
            onSelect = { i -> save { it.copy(privacy = PreviewPrivacy.entries[i]) } },
            subtitle = "App name: only the app. Summary: the app and who wrote. Full: also a one-line preview of the message.",
        )
        SwitchRow(
            "Keep messages and email short",
            s.hideSensitiveCategories,
            { v -> save { it.copy(hideSensitiveCategories = v) } },
            "Even on Full, messages and email show only the short summary.",
        )
        Note(
            "While the phone is locked, or when an app marks a notification as private, only the app name is shown, whatever you choose here. " +
                "Full text on screen can end up in screenshots and screen recordings.",
        )
    }
}

private fun countLabel(count: Int): String = if (count == 0) "None" else "$count"

// -------------------------------------------------------------------------------------------------
// E. Music
// -------------------------------------------------------------------------------------------------

@Composable
internal fun MusicPage(nav: SettingsNav) {
    val s = rememberSettings()
    var confirmDelete by remember { mutableStateOf(false) }
    var countsRefresh by remember { mutableIntStateOf(0) }
    val live by produceState(initialValue = 0 to 0, countsRefresh, nav.resumeTick) {
        value = withContext(Dispatchers.IO) { NovaGraph.lyrics.importedCount() to NovaGraph.lyrics.cachedCount() }
    }
    val playerLabel = remember(s.preferredPlayer) {
        if (s.preferredPlayer.isBlank()) "Automatic" else NovaGraph.appInfo.label(s.preferredPlayer)
    }

    ScreenScaffold(title = "Music", onBack = nav.back) {
        SectionLabel("Player")
        NavRow(
            title = "Preferred player",
            subtitle = "The music card follows it when it has a session",
            trailing = playerLabel,
            onClick = { nav.openPicker(PickerPurpose.PREFERRED_PLAYER) },
        )
        Note("Automatic picks Spotify first, then whichever player is active. NOVA PILL only ever controls the session it is showing.")

        SectionLabel("Card")
        SwitchRow("Show artwork", s.showArtwork, { v -> save { it.copy(showArtwork = v) } })
        Hairline()
        SwitchRow("Show title and artist", s.showTitleArtist, { v -> save { it.copy(showTitleArtist = v) } })
        Hairline()
        SwitchRow("Show progress bar", s.showProgress, { v -> save { it.copy(showProgress = v) } })
        Hairline()
        SwitchRow(
            "Open lyrics automatically",
            s.autoOpenLyrics,
            { v -> save { it.copy(autoOpenLyrics = v) } },
            "Opening the music card shows the lyrics view first.",
        )

        SectionLabel("Visualizer")
        SwitchRow(
            "Show visualizer",
            s.visualizerEnabled,
            { v -> save { it.copy(visualizerEnabled = v) } },
            "Four small bars next to the title.",
        )
        IntSliderRow("Intensity", s.visualizerIntensity, 10, 100, { v -> save { it.copy(visualizerIntensity = v) } }, unit = "%")
        Note("The bars are decorative: they move while a track is playing and stop when it pauses. They are not an analysis of the audio.")

        SectionLabel("When paused")
        SegmentedRow(
            title = "After the grace time",
            options = listOf("Keep", "Collapse", "Hide"),
            selected = s.pauseBehavior.ordinal,
            onSelect = { i -> save { it.copy(pauseBehavior = PauseBehavior.entries[i]) } },
            subtitle = "Keep: stays as it is. Collapse: shrinks to the compact pill. Hide: leaves the screen.",
        )
        IntSliderRow("Grace time", s.pauseGraceSec, 2, 120, { v -> save { it.copy(pauseGraceSec = v) } }, unit = " s")

        SectionLabel("Lyrics source")
        SegmentedRow(
            title = "Where lyrics come from",
            options = listOf("Local files only", "LRCLIB"),
            selected = s.lyricsProvider.ordinal,
            onSelect = { i -> save { it.copy(lyricsProvider = LyricsProviderChoice.entries[i]) } },
        )
        Note(
            if (s.lyricsProvider == LyricsProviderChoice.LRCLIB) {
                "LRCLIB is on. For the track you play, its title, artist, album and length are sent to lrclib.net to look for lyrics. Nothing else is sent. " +
                    "LRCLIB is a community project, so check its terms before relying on it. Results are cached on this phone."
            } else {
                "Nothing is sent anywhere. Lyrics appear only for tracks where you imported an .lrc file from the lyrics view. " +
                    "Lyrics are never invented: if there is no source, the card says so."
            },
        )

        SectionLabel("Lyrics look")
        IntSliderRow("Text size", s.lyricsFontPercent, 80, 140, { v -> save { it.copy(lyricsFontPercent = v) } }, unit = "%")
        IntSliderRow("Line spacing", s.lyricsSpacingPercent, 80, 140, { v -> save { it.copy(lyricsSpacingPercent = v) } }, unit = "%")
        SegmentedRow(
            title = "Current line",
            options = listOf("Soft", "Accent", "Bar"),
            selected = s.lyricsHighlight.ordinal,
            onSelect = { i -> save { it.copy(lyricsHighlight = LyricsHighlight.entries[i]) } },
        )

        SectionLabel("Lyrics timing")
        OffsetRow(
            value = s.lyricsOffsetMs,
            onChange = { v -> save { it.copy(lyricsOffsetMs = v) } },
        )
        Note(
            "Moves every lyric line earlier (minus) or later (plus). An offset you set for one track in the lyrics view wins over this one. " +
                "Line-synced files stay line-synced: nothing here turns them into word-by-word lyrics.",
        )

        SectionLabel("Stored lyrics")
        NavRowLike(
            title = "Imported files",
            value = "${live.first}",
        )
        NavRowLike(
            title = "LRCLIB cache",
            value = "${live.second}",
        )
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            AppButton(
                "Clear LRCLIB cache",
                {
                    NovaGraph.lyrics.clearCache()
                    countsRefresh++
                },
                Modifier.fillMaxWidth(),
                ButtonKind.SECONDARY,
                height = 48.dp,
                enabled = live.second > 0,
            )
            Gap(10)
            if (!confirmDelete) {
                AppButton(
                    "Delete imported lyrics",
                    { confirmDelete = true },
                    Modifier.fillMaxWidth(),
                    ButtonKind.DANGER,
                    height = 48.dp,
                    enabled = live.first > 0,
                )
            } else {
                NText("Delete all ${live.first} imported lyric files? Your original files are not touched.", style = NovaType.Body)
                Gap(10)
                Row(Modifier.fillMaxWidth()) {
                    AppButton("Cancel", { confirmDelete = false }, Modifier.weight(1f), ButtonKind.SECONDARY, height = 48.dp)
                    GapW(10)
                    AppButton(
                        "Delete",
                        {
                            NovaGraph.lyrics.deleteImported()
                            confirmDelete = false
                            countsRefresh++
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

@Composable
private fun NavRowLike(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        NText(title, Modifier.weight(1f), NovaType.BodyStrong)
        NText(value, style = NovaType.NumberMid)
    }
}

/** Minus / plus / reset in 50 ms steps, within ±1500 ms. */
@Composable
private fun OffsetRow(value: Int, onChange: (Int) -> Unit) {
    val step = NovaSettings.OFFSET_STEP_MS
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NText("Global offset", Modifier.weight(1f), NovaType.BodyStrong)
            NText(signedMs(value), style = NovaType.NumberMid)
        }
        Gap(12)
        Row(Modifier.fillMaxWidth()) {
            AppButton(
                "− $step ms",
                { onChange((value - step).coerceAtLeast(NovaSettings.OFFSET_MIN_MS)) },
                Modifier.weight(1f),
                ButtonKind.SECONDARY,
                height = 48.dp,
                enabled = value > NovaSettings.OFFSET_MIN_MS,
            )
            GapW(8)
            AppButton(
                "+ $step ms",
                { onChange((value + step).coerceAtMost(NovaSettings.OFFSET_MAX_MS)) },
                Modifier.weight(1f),
                ButtonKind.SECONDARY,
                height = 48.dp,
                enabled = value < NovaSettings.OFFSET_MAX_MS,
            )
            GapW(8)
            AppButton("Reset", { onChange(0) }, Modifier.weight(1f), ButtonKind.GHOST, height = 48.dp, enabled = value != 0)
        }
    }
}
