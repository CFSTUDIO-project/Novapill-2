package app.novapill.android.ui.screens

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.novapill.android.BuildConfig
import app.novapill.android.NovaGraph
import app.novapill.android.ServiceStatus
import app.novapill.android.perm.Permissions
import app.novapill.android.perm.ShizukuState
import app.novapill.android.service.ListenerBridge
import app.novapill.android.ui.components.AppButton
import app.novapill.android.ui.components.ButtonKind
import app.novapill.android.ui.components.Gap
import app.novapill.android.ui.components.Hairline
import app.novapill.android.ui.components.InfoRow
import app.novapill.android.ui.components.NText
import app.novapill.android.ui.components.NavRow
import app.novapill.android.ui.components.NovaIcon
import app.novapill.android.ui.components.ScreenScaffold
import app.novapill.android.ui.components.SectionLabel
import app.novapill.android.ui.components.StatusBadge
import app.novapill.android.ui.theme.NovaColors
import app.novapill.android.ui.theme.NovaType
import app.novapill.android.util.IntentLauncher
import java.text.DateFormat
import java.util.Date

// -------------------------------------------------------------------------------------------------
// I. Privacy & Permissions
// -------------------------------------------------------------------------------------------------

@Composable
internal fun PrivacyPage(nav: SettingsNav) {
    val context = LocalContext.current
    val p = remember(nav.resumeTick) { Permissions.snapshot(context) }
    var message by remember { mutableStateOf<String?>(null) }

    ScreenScaffold(title = "Privacy & Permissions", onBack = nav.back) {
        SectionLabel("Status")
        PermissionLine("Display over other apps", "Required", p.overlay)
        Hairline()
        PermissionLine("Notification access", "Required", p.notificationAccess)
        if (Permissions.postNotificationsNeedsRuntimeRequest) {
            Hairline()
            PermissionLine("Show notifications", "Optional", p.postNotifications)
        }
        Hairline()
        PermissionLine("Usage access", "Optional", p.usageAccess)
        Hairline()
        PermissionLine("Approximate location", "Optional", p.location)
        Hairline()
        PermissionLine("Battery optimisation exempt", "Optional guidance", p.batteryOptimizationIgnored)
        Hairline()
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                NText("Shizuku", style = NovaType.BodyStrong)
                NText("Optional / Not required", Modifier.padding(top = 2.dp), NovaType.Caption.copy(fontSize = 13.sp, lineHeight = 18.sp))
            }
            StatusBadge(
                when (p.shizuku) {
                    ShizukuState.READY -> "Ready"
                    ShizukuState.PERMISSION_NEEDED -> "Permission needed"
                    ShizukuState.NOT_RUNNING -> "Not running"
                    ShizukuState.NOT_INSTALLED -> "Not installed"
                },
                if (p.shizuku == ShizukuState.READY) NovaColors.Green else NovaColors.Text3,
            )
        }
        NavRow(
            title = "Open permission wizard",
            icon = NovaIcon.SHIELD,
            onClick = nav.openPermissions,
        )

        SectionLabel("What NOVA PILL reads")
        Fact(
            "Media sessions",
            "Title, artist, album, artwork, position and the controls a player offers. Used to draw the music card and kept in memory only.",
        )
        Fact(
            "Notifications",
            "The app name and, depending on your privacy level, a short title or text. A peek is shown once and dropped. Nothing is written to disk or sent anywhere.",
        )
        Fact(
            "Calls",
            "The caller line of the system call notification, shown in the peek. Answer and Decline use the buttons that notification already has.",
        )
        Fact(
            "Foreground app",
            "Only if you pick apps to hide in and turn on Usage access. The package name is compared with your list and not stored.",
        )
        Fact(
            "Location",
            "Only the last known coarse location, and only if you turn on weather by location. Coordinates are rounded before they are sent.",
        )
        Fact(
            "Network",
            "Open-Meteo for weather, and LRCLIB for lyrics only if you switched it on. No accounts, no ads, no analytics.",
        )

        SectionLabel("Clear stored data")
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppButton(
                "Clear cached app names and icons",
                {
                    NovaGraph.appInfo.clear()
                    message = "Cleared cached app names and icons."
                },
                Modifier.fillMaxWidth(),
                ButtonKind.SECONDARY,
                height = 48.dp,
            )
            AppButton(
                "Clear notification previews",
                {
                    NovaGraph.notifications.clearPreviews()
                    message = "Cleared queued and visible notification previews."
                },
                Modifier.fillMaxWidth(),
                ButtonKind.SECONDARY,
                height = 48.dp,
            )
            AppButton(
                "Clear LRCLIB lyrics cache",
                {
                    NovaGraph.lyrics.clearCache()
                    message = "Cleared the LRCLIB cache."
                },
                Modifier.fillMaxWidth(),
                ButtonKind.SECONDARY,
                height = 48.dp,
            )
        }
        val note = message
        if (note != null) Note(note, NovaColors.Green)
        Note("Imported .lrc files and settings are not touched here. Manage them in Music and General.")
    }
}

@Composable
private fun PermissionLine(title: String, tag: String, enabled: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            NText(title, style = NovaType.BodyStrong)
            NText(tag, Modifier.padding(top = 2.dp), NovaType.Caption.copy(fontSize = 13.sp, lineHeight = 18.sp))
        }
        StatusBadge(if (enabled) "Enabled" else "Not enabled", if (enabled) NovaColors.Green else NovaColors.Yellow)
    }
}

@Composable
private fun Fact(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        NText(title, style = NovaType.BodyStrong)
        NText(body, Modifier.padding(top = 2.dp), NovaType.Body.copy(fontSize = 14.sp, lineHeight = 20.sp), color = NovaColors.Text2)
    }
}

// -------------------------------------------------------------------------------------------------
// J. About & Debug
// -------------------------------------------------------------------------------------------------

@Composable
internal fun AboutPage(nav: SettingsNav) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    var refresh by remember { mutableIntStateOf(0) }
    val settings = rememberSettings()
    val status by NovaGraph.serviceStatus.collectAsState()
    val serviceError by NovaGraph.serviceError.collectAsState()
    val listener by ListenerBridge.connected.collectAsState()
    val lastError by NovaGraph.diagnostics.lastError.collectAsState()
    val powerSave by NovaGraph.system.powerSave.collectAsState()
    val p = remember(nav.resumeTick, refresh) { Permissions.snapshot(context) }
    val metrics = context.resources.displayMetrics

    val lines: List<Pair<String, String>> = remember(
        refresh,
        nav.resumeTick,
        status,
        serviceError,
        listener,
        lastError,
        powerSave,
        settings.lyricsProvider,
        configuration.orientation,
        configuration.screenWidthDp,
        configuration.screenHeightDp,
    ) {
        buildList {
            add("App version" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})${if (BuildConfig.DEBUG) " debug" else ""}")
            add("Package" to context.packageName)
            add("Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            add("Device" to "${Build.MANUFACTURER} ${Build.MODEL}")
            add("Screen" to "${metrics.widthPixels} x ${metrics.heightPixels} px, ${configuration.screenWidthDp} x ${configuration.screenHeightDp} dp")
            add("Density" to "${metrics.density} (${metrics.densityDpi} dpi), font scale ${configuration.fontScale}")
            add("Orientation" to if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) "Landscape" else "Portrait")
            add("Island service" to when (status) {
                ServiceStatus.RUNNING -> "Running"
                ServiceStatus.STOPPED -> "Stopped"
                ServiceStatus.FAILED -> "Failed${serviceError?.let { ": $it" } ?: ""}"
            })
            add("Overlay permission" to if (p.overlay) "Allowed" else "Not allowed")
            add("Notification access" to if (p.notificationAccess) "Allowed" else "Not allowed")
            add("Notification listener" to if (listener) "Connected" else "Not connected")
            add("Media sessions" to NovaGraph.media.debugSummary())
            add("Lyrics provider" to settings.lyricsProvider.name)
            add("Lyrics state" to NovaGraph.lyrics.debugSummary())
            add("Battery saver" to if (powerSave) "On" else "Off")
            add("System animations" to if (NovaGraph.system.systemAnimationsOff()) "Off" else "On")
            add("Last error" to (lastError?.let { "${it.area}: ${it.message} (${DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(it.atMs))})" } ?: "None"))
        }
    }

    ScreenScaffold(title = "About & Debug", onBack = nav.back) {
        SectionLabel("This build")
        lines.forEachIndexed { index, (label, value) ->
            InfoRow(label, value, mono = label == "Package")
            if (index != lines.lastIndex) Hairline()
        }

        Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
            AppButton("Refresh", { refresh++ }, Modifier.fillMaxWidth(), ButtonKind.SECONDARY, height = 48.dp)
            Gap(10)
            AppButton(
                "Export diagnostics",
                {
                    val report = buildString {
                        appendLine("NOVA PILL diagnostics")
                        for ((label, value) in lines) appendLine("$label: $value")
                    }
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "NOVA PILL diagnostics")
                        .putExtra(Intent.EXTRA_TEXT, report)
                    IntentLauncher.start(context, Intent.createChooser(send, "Share diagnostics"))
                },
                Modifier.fillMaxWidth(),
                ButtonKind.PRIMARY,
                height = 48.dp,
            )
            Gap(10)
            NText(
                "The report holds versions, permission states and error classes only. It never contains notification text, song titles, lyrics or contact names.",
                style = NovaType.Caption.copy(fontSize = 12.sp, lineHeight = 17.sp),
            )
        }

        SectionLabel("Credits")
        Fact("Open-Meteo", "Weather data by Open-Meteo.com, licensed CC BY 4.0. Used only when you open the weather in the tools card.")
        Fact("LRCLIB", "Optional lyrics lookup at lrclib.net, only if you switch it on in Music. Check their terms before relying on it.")
        Fact("Shizuku", "Optional. NOVA PILL only detects whether Shizuku is installed and running. No feature needs it.")
        Fact("Built with", "Kotlin, Jetpack Compose (without Material), DataStore, OkHttp and the Shizuku API.")
    }
}
