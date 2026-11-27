package app.novapill.android.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.novapill.android.BuildConfig
import app.novapill.android.NovaGraph
import app.novapill.android.overlay.PillKind
import app.novapill.android.perm.Permissions
import app.novapill.android.perm.ShizukuState
import app.novapill.android.ui.components.AppButton
import app.novapill.android.ui.components.ButtonKind
import app.novapill.android.ui.components.Gap
import app.novapill.android.ui.components.GapW
import app.novapill.android.ui.components.NText
import app.novapill.android.ui.components.NovaCard
import app.novapill.android.ui.components.ScreenScaffold
import app.novapill.android.ui.components.StatusBadge
import app.novapill.android.ui.theme.NovaColors
import app.novapill.android.ui.theme.NovaType
import app.novapill.android.util.IntentLauncher
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// 1. Intro: a real start-up screen. It leaves as soon as the settings are loaded (after the animation
//    has had a moment to play); there is no progress bar because there is nothing to measure.
// ---------------------------------------------------------------------------------------------

@Composable
fun IntroScreen(onReady: () -> Unit) {
    val loaded by NovaGraph.settings.loaded.collectAsState()
    var minimumShown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1400)
        minimumShown = true
    }
    LaunchedEffect(loaded, minimumShown) {
        if (loaded && minimumShown) onReady()
    }

    val grow = remember { Animatable(0f) }
    val label = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, spring(dampingRatio = 0.66f, stiffness = 90f)) }
    LaunchedEffect(Unit) {
        delay(420)
        label.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    val sweep by rememberInfiniteTransition(label = "sweep").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "sweepPhase",
    )

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size((18f + 172f * grow.value).dp, (18f + 28f * grow.value).dp)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .drawBehind {
                        val stroke = 2.dp.toPx()
                        val stops = Array(37) { i ->
                            val pos = i / 36f
                            val d = ((pos - sweep) % 1f + 1f) % 1f
                            val bump = if (d < 0.22f) sin(PI.toFloat() * d / 0.22f) else 0f
                            pos to NovaColors.Crimson.copy(alpha = 0.12f + 0.88f * bump)
                        }
                        drawRoundRect(
                            brush = Brush.sweepGradient(*stops),
                            topLeft = Offset(stroke / 2f, stroke / 2f),
                            size = Size(size.width - stroke, size.height - stroke),
                            cornerRadius = CornerRadius(size.height / 2f),
                            style = Stroke(stroke),
                        )
                    },
            )
            Gap(30)
            Column(Modifier.graphicsLayer { alpha = label.value }, horizontalAlignment = Alignment.CenterHorizontally) {
                NText(
                    "NOVA PILL",
                    style = NovaType.Headline.copy(fontSize = 26.sp, letterSpacing = (10f - 7f * label.value).sp),
                )
                Gap(6)
                NText("LIVE ISLAND", style = NovaType.Label)
                Gap(16)
                // The loading screen says which version this is, so an updated install is recognisable at once.
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(NovaColors.Crimson)
                        .padding(horizontal = 16.dp, vertical = 5.dp),
                ) {
                    NText("V2", style = NovaType.Label.copy(letterSpacing = 3.sp), color = Color.White)
                }
                Gap(10)
                NText("Updated to V2 · ${BuildConfig.VERSION_NAME}", style = NovaType.Caption)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 2. Welcome
// ---------------------------------------------------------------------------------------------

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    val context = LocalContext.current
    val model = remember { PreviewModel(context.applicationContext) }
    LaunchedEffect(Unit) {
        // A slow, looping tour of the real pill renderer with sample data.
        while (true) {
            delay(2200)
            model.kind = PillKind.EXPANDED
            delay(3000)
            model.collapse()
            delay(1600)
            model.showPeek(false)
            delay(3800)
            model.showPeek(true)
            delay(6600)
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Gap(20)
        NText("NOVA PILL", style = NovaType.Label)
        PreviewIsland(model, Modifier.padding(top = 10.dp), height = 300.dp)
        NText("Sample data. This tour never reads your media or notifications.", style = NovaType.Caption)
        Gap(28)
        NText("Your screen,\nin the flow.", style = NovaType.Display)
        Gap(16)
        NText(
            "A Dynamic-Island-style pill for Android. Music, timers, calls and notification peeks, drawn natively and kept private.",
            style = NovaType.Body,
        )
        Gap(24)
        Rule()
        FeatureLine("01", "Music with real lyrics", "Synced lyrics only from files you import or from LRCLIB. Never invented.")
        Rule()
        FeatureLine("02", "Timers and tools", "Timer, stopwatch, flashlight, volume, weather and a record indicator.")
        Rule()
        FeatureLine("03", "Calls and notifications", "Short peeks that show an app name and a summary, nothing more by default.")
        Gap(28)
        AppButton("Get Started", onStart, Modifier.fillMaxWidth())
        Gap(24)
    }
}

@Composable
private fun Rule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(NovaColors.Hairline))
}

@Composable
private fun FeatureLine(number: String, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        NText(number, Modifier.width(40.dp), NovaType.Label, color = NovaColors.Crimson)
        Column(Modifier.weight(1f)) {
            NText(title, style = NovaType.BodyStrong)
            Gap(2)
            NText(body, style = NovaType.Caption.copy(fontSize = 13.sp, lineHeight = 18.sp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 3. Permission wizard: one card per permission, live status, "Next" always works.
// ---------------------------------------------------------------------------------------------

@Composable
fun PermissionsScreen(resumeTick: Int, onboarding: Boolean, onDone: () -> Unit, onBack: (() -> Unit)?) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val snapshot = remember(resumeTick, refresh) { Permissions.snapshot(context) }
    var message by remember { mutableStateOf<String?>(null) }
    var askedNotifications by remember { mutableStateOf(false) }
    var askedLocation by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    fun open(intent: Intent) {
        message = if (IntentLauncher.start(context, intent)) null
        else "Android could not open that settings page. Open it by hand from the system Settings app."
    }

    ScreenScaffold(title = "Permissions", onBack = onBack) {
        NText(
            "Turn on only what you want. Two permissions are needed for the pill itself; everything else is optional and the app works without it.",
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            NovaType.Body,
        )
        val note = message
        if (note != null) NText(note, Modifier.padding(horizontal = 20.dp, vertical = 4.dp), NovaType.Caption, color = NovaColors.Yellow)

        PermissionCard(
            number = "01",
            title = "Display over other apps",
            body = "Draws the pill above other apps. Without it nothing can appear on screen.",
            required = true,
            enabled = snapshot.overlay,
            primaryLabel = if (snapshot.overlay) "Open Settings" else "Grant Access",
            onPrimary = { open(Permissions.overlayIntent(context)) },
        )
        PermissionCard(
            number = "02",
            title = "Notification access",
            body = "Lets NOVA PILL see media sessions (for the music card) and show short notification peeks. " +
                "Everything is processed on your phone and nothing is uploaded. You choose which apps may peek in Settings.",
            required = true,
            enabled = snapshot.notificationAccess,
            primaryLabel = if (snapshot.notificationAccess) "Open Settings" else "Grant Access",
            onPrimary = { open(Permissions.notificationAccessIntent(context)) },
            note = if (!snapshot.notificationAccess && Build.VERSION.SDK_INT >= 33) {
                "If Android says the setting is restricted: open App info, tap the three-dot menu, choose Allow restricted settings, then come back."
            } else {
                null
            },
            secondaryLabel = if (!snapshot.notificationAccess && Build.VERSION.SDK_INT >= 33) "Open App info" else null,
            onSecondary = { open(Permissions.appDetailsIntent(context)) },
        )
        if (Permissions.postNotificationsNeedsRuntimeRequest) {
            PermissionCard(
                number = "03",
                title = "Show notifications from this app",
                body = "Needed for the \"NOVA PILL is running\" notice and for \"Timer finished\".",
                required = false,
                enabled = snapshot.postNotifications,
                primaryLabel = if (snapshot.postNotifications) "Open Settings" else "Grant Access",
                onPrimary = {
                    if (snapshot.postNotifications || askedNotifications) {
                        open(Permissions.appNotificationSettingsIntent(context))
                    } else {
                        askedNotifications = true
                        if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
        }
        PermissionCard(
            number = "04",
            title = "Usage access",
            body = "Only used by \"Hide in selected apps\". Without it the pill is simply never hidden per app.",
            required = false,
            enabled = snapshot.usageAccess,
            primaryLabel = "Open Settings",
            onPrimary = { open(Permissions.usageAccessIntent()) },
        )
        PermissionCard(
            number = "05",
            title = "Approximate location",
            body = "Optional. Weather can use your last known coarse location. You can type a city in Settings instead and never grant this.",
            required = false,
            enabled = snapshot.location,
            primaryLabel = if (snapshot.location) "Open Settings" else "Grant Access",
            onPrimary = {
                if (snapshot.location || askedLocation) {
                    open(Permissions.appDetailsIntent(context))
                } else {
                    askedLocation = true
                    locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            },
        )
        PermissionCard(
            number = "06",
            title = "Battery optimisation",
            body = "Guidance only. Some phones stop background services to save power; if the pill disappears, " +
                "set NOVA PILL to \"Unrestricted\" or exempt it. The app never asks for this on its own.",
            required = false,
            enabled = snapshot.batteryOptimizationIgnored,
            primaryLabel = "Open Settings",
            onPrimary = { open(Permissions.batterySettingsIntent()) },
        )
        PermissionCard(
            number = "07",
            title = "Shizuku",
            body = "Optional / Not required. NOVA PILL only detects whether Shizuku is installed and running. No feature depends on it.",
            required = false,
            enabled = snapshot.shizuku == ShizukuState.READY,
            statusText = when (snapshot.shizuku) {
                ShizukuState.READY -> "Ready"
                ShizukuState.PERMISSION_NEEDED -> "Permission needed"
                ShizukuState.NOT_RUNNING -> "Not running"
                ShizukuState.NOT_INSTALLED -> "Not installed"
            },
            statusColor = if (snapshot.shizuku == ShizukuState.READY) NovaColors.Green else NovaColors.Text3,
            primaryLabel = null,
            onPrimary = null,
        )
        NText(
            "The flashlight uses Android's torch switch and needs no camera permission.",
            Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            NovaType.Caption,
        )
        AppButton(
            text = if (onboarding) "Next" else "Done",
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
    }
}

@Composable
private fun PermissionCard(
    number: String,
    title: String,
    body: String,
    required: Boolean,
    enabled: Boolean,
    primaryLabel: String?,
    onPrimary: (() -> Unit)?,
    note: String? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    statusText: String? = null,
    statusColor: Color? = null,
) {
    NovaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NText(number, style = NovaType.Label)
                GapW(10)
                NText(if (required) "REQUIRED" else "OPTIONAL", style = NovaType.Label, color = if (required) NovaColors.Crimson else NovaColors.Text3)
                Box(Modifier.weight(1f))
                StatusBadge(statusText ?: if (enabled) "Enabled" else "Not enabled", statusColor ?: if (enabled) NovaColors.Green else NovaColors.Yellow)
            }
            Gap(14)
            NText(title, style = NovaType.Title)
            Gap(6)
            NText(body, style = NovaType.Body.copy(fontSize = 14.sp, lineHeight = 21.sp))
            if (note != null) {
                Gap(10)
                NText(note, style = NovaType.Caption.copy(fontSize = 13.sp, lineHeight = 18.sp), color = NovaColors.Yellow)
            }
            if (primaryLabel != null && onPrimary != null) {
                Gap(16)
                AppButton(
                    text = primaryLabel,
                    onClick = onPrimary,
                    modifier = Modifier.fillMaxWidth(),
                    kind = if (enabled) ButtonKind.SECONDARY else ButtonKind.PRIMARY,
                    height = 48.dp,
                )
            }
            if (secondaryLabel != null && onSecondary != null) {
                Gap(8)
                AppButton(secondaryLabel, onSecondary, Modifier.fillMaxWidth(), ButtonKind.GHOST, height = 44.dp)
            }
        }
    }
}
