package app.novapill.android.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.novapill.android.BuildConfig
import app.novapill.android.NovaGraph
import app.novapill.android.ServiceStatus
import app.novapill.android.perm.Permissions
import app.novapill.android.service.IslandService
import app.novapill.android.service.ListenerBridge
import app.novapill.android.settings.LyricsProviderChoice
import app.novapill.android.settings.NovaSettings
import app.novapill.android.ui.components.AppButton
import app.novapill.android.ui.components.ButtonKind
import app.novapill.android.ui.components.Gap
import app.novapill.android.ui.components.GapW
import app.novapill.android.ui.components.IconAction
import app.novapill.android.ui.components.NText
import app.novapill.android.ui.components.NovaCard
import app.novapill.android.ui.components.NovaIcon
import app.novapill.android.ui.components.StatusBadge
import app.novapill.android.ui.theme.NovaColors
import app.novapill.android.ui.theme.NovaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DashboardScreen(
    resumeTick: Int,
    onOpenSettings: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenMusicSettings: () -> Unit,
) {
    val context = LocalContext.current
    val settings by NovaGraph.settings.settings.collectAsState()
    val status by NovaGraph.serviceStatus.collectAsState()
    val serviceError by NovaGraph.serviceError.collectAsState()
    val listenerConnected by ListenerBridge.connected.collectAsState()
    val permissions = remember(resumeTick) { Permissions.snapshot(context) }
    val running = status == ServiceStatus.RUNNING
    val model = remember { PreviewModel(context.applicationContext) }
    val counts by produceState(initialValue = 0 to 0, resumeTick) {
        value = withContext(Dispatchers.IO) { NovaGraph.lyrics.importedCount() to NovaGraph.lyrics.cachedCount() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            NText("NOVA PILL", Modifier.weight(1f), NovaType.Label)
            IconAction(NovaIcon.SLIDERS, "Settings", onOpenSettings)
        }

        // --- Hero -------------------------------------------------------------------------------
        Column(Modifier.padding(horizontal = 24.dp).padding(top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveDot(active = running)
                GapW(10)
                NText(
                    when (status) {
                        ServiceStatus.RUNNING -> "RUNNING"
                        ServiceStatus.FAILED -> "COULD NOT START"
                        ServiceStatus.STOPPED -> "OFF"
                    },
                    style = NovaType.Label,
                    color = when (status) {
                        ServiceStatus.RUNNING -> NovaColors.Crimson
                        ServiceStatus.FAILED -> NovaColors.Danger
                        ServiceStatus.STOPPED -> NovaColors.Text3
                    },
                )
            }
            Gap(12)
            NText(if (running) "The island\nis live." else "The island\nis off.", style = NovaType.Display)
            Gap(12)
            NText(
                when {
                    status == ServiceStatus.FAILED && serviceError != null -> serviceError.orEmpty()
                    running -> "It sits at the top of your screen. Tap it to open, swipe to change cards."
                    !permissions.overlay -> "START will first take you to the one permission the pill needs: Display over other apps."
                    else -> "Start it when you want it. Stop removes the pill and its notification completely."
                },
                style = NovaType.Body,
                color = if (status == ServiceStatus.FAILED) NovaColors.Danger else Color.Unspecified,
            )
            Gap(22)
            if (running) {
                AppButton("STOP ISLAND", { IslandService.stop(context) }, Modifier.fillMaxWidth(), ButtonKind.DANGER)
            } else {
                AppButton(
                    "START ISLAND",
                    {
                        if (permissions.overlay) IslandService.start(context) else onOpenPermissions()
                    },
                    Modifier.fillMaxWidth(),
                    ButtonKind.PRIMARY,
                )
            }
        }

        if (settings.whatsNewSeen < NovaSettings.CURRENT_WHATS_NEW) {
            Gap(28)
            WhatsNewCard()
        }

        // --- System check -----------------------------------------------------------------------
        Gap(32)
        NText("SYSTEM", Modifier.padding(horizontal = 24.dp), NovaType.Label)
        Gap(10)
        NovaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            StatusLine(
                title = "Island service",
                detail = if (status == ServiceStatus.FAILED) serviceError else null,
                ok = running,
                onText = "Running",
                offText = if (status == ServiceStatus.FAILED) "Failed" else "Stopped",
                neutralWhenOff = status != ServiceStatus.FAILED,
            )
            StatusDivider()
            StatusLine(
                title = "Display over other apps",
                detail = "Required. Draws the pill.",
                ok = permissions.overlay,
                onText = "Allowed",
                offText = "Not allowed",
            )
            StatusDivider()
            StatusLine(
                title = "Notification access",
                detail = "Required for music and peeks.",
                ok = permissions.notificationAccess,
                onText = "Allowed",
                offText = "Not allowed",
            )
            StatusDivider()
            StatusLine(
                title = "Media listener",
                detail = if (permissions.notificationAccess) null else "Starts once Notification access is on.",
                ok = listenerConnected,
                onText = "Connected",
                offText = "Not connected",
                neutralWhenOff = true,
            )
            if (!permissions.coreReady) {
                Box(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 18.dp, top = 4.dp)) {
                    AppButton("Fix permissions", onOpenPermissions, Modifier.fillMaxWidth(), ButtonKind.SECONDARY, height = 48.dp)
                }
            }
        }

        // --- Preview ----------------------------------------------------------------------------
        Gap(32)
        NText("PREVIEW", Modifier.padding(horizontal = 24.dp), NovaType.Label)
        Gap(10)
        NovaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NText("The real renderer, sample data", Modifier.weight(1f), NovaType.BodyStrong)
                    StatusBadge("Sample", NovaColors.Text3)
                }
                PreviewIsland(model, Modifier.padding(top = 8.dp), height = 330.dp)
                Gap(6)
                Row(Modifier.fillMaxWidth()) {
                    AppButton("Try a peek", { model.showPeek(false) }, Modifier.weight(1f), ButtonKind.SECONDARY, height = 48.dp)
                    GapW(10)
                    AppButton("Try a call", { model.showPeek(true) }, Modifier.weight(1f), ButtonKind.SECONDARY, height = 48.dp)
                }
                Gap(12)
                NText(
                    "Tap the pill, swipe it, use its buttons. This runs the same drawing code as the real island, but it never reads your media or notifications, and the flashlight button here does not touch the real torch.",
                    style = NovaType.Caption.copy(fontSize = 12.sp, lineHeight = 17.sp),
                )
            }
        }

        // --- Lyrics -----------------------------------------------------------------------------
        Gap(32)
        NText("LYRICS", Modifier.padding(horizontal = 24.dp), NovaType.Label)
        Gap(10)
        NovaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Column(Modifier.padding(20.dp)) {
                NText(
                    if (settings.lyricsProvider == LyricsProviderChoice.LRCLIB) "LRCLIB is on" else "Local files only",
                    style = NovaType.Title,
                )
                Gap(6)
                NText(
                    "Synced lyrics come only from .lrc files you import, or from LRCLIB when you turn it on. Nothing is invented. " +
                        "If a track has none, the card says so.",
                    style = NovaType.Body.copy(fontSize = 14.sp, lineHeight = 21.sp),
                )
                Gap(12)
                NText("${counts.first} imported · ${counts.second} cached", style = NovaType.Mono)
                Gap(16)
                AppButton("Music settings", onOpenMusicSettings, Modifier.fillMaxWidth(), ButtonKind.SECONDARY, height = 48.dp)
            }
        }

        Gap(28)
        NText(
            "NOVA PILL ${BuildConfig.VERSION_NAME}",
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            NovaType.Caption,
        )
        Gap(32)
    }
}

@Composable
private fun StatusDivider() {
    Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(1.dp).background(NovaColors.Hairline))
}

@Composable
private fun StatusLine(
    title: String,
    detail: String?,
    ok: Boolean,
    onText: String,
    offText: String,
    neutralWhenOff: Boolean = false,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            NText(title, style = NovaType.BodyStrong)
            if (!detail.isNullOrBlank()) NText(detail, Modifier.padding(top = 2.dp), NovaType.Caption.copy(fontSize = 13.sp, lineHeight = 18.sp))
        }
        GapW(12)
        StatusBadge(
            if (ok) onText else offText,
            if (ok) NovaColors.Green else if (neutralWhenOff) NovaColors.Text3 else NovaColors.Yellow,
        )
    }
}

/** A dot that breathes while the island is live. Static when off or when system animations are disabled. */
@Composable
private fun LiveDot(active: Boolean) {
    val calm = remember { NovaGraph.system.systemAnimationsOff() }
    val transition = rememberInfiniteTransition(label = "live")
    val ring by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing)),
        label = "ring",
    )
    Canvas(Modifier.size(22.dp)) {
        val c = center
        val core = 4.dp.toPx()
        if (active && !calm) {
            drawCircle(NovaColors.Crimson.copy(alpha = (1f - ring) * 0.55f), core + 7.dp.toPx() * ring, c)
        }
        drawCircle(if (active) NovaColors.Crimson else NovaColors.Text4, core, c)
    }
}
