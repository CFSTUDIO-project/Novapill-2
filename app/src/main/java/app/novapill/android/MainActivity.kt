package app.novapill.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.novapill.android.perm.Permissions
import app.novapill.android.service.IslandService
import app.novapill.android.ui.screens.AppPickerScreen
import app.novapill.android.ui.screens.DashboardScreen
import app.novapill.android.ui.screens.IntroScreen
import app.novapill.android.ui.screens.PermissionsScreen
import app.novapill.android.ui.screens.Route
import app.novapill.android.ui.screens.SettingsHubScreen
import app.novapill.android.ui.screens.SettingsNav
import app.novapill.android.ui.screens.SettingsPageScreen
import app.novapill.android.ui.screens.SettingsSection
import app.novapill.android.ui.screens.WelcomeScreen
import app.novapill.android.ui.screens.decodeRoute
import app.novapill.android.ui.screens.encode
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    /** Bumped on every resume so permission readings are taken again after the user comes back from Settings. */
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { NovaPillApp(resumeTick) }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}

private val RouteStackSaver = listSaver<SnapshotStateList<Route>, String>(
    save = { stack -> stack.map { it.encode() } },
    restore = { saved -> saved.mapNotNull { decodeRoute(it) }.ifEmpty { listOf(Route.Intro) }.toMutableStateList() },
)

@Composable
private fun NovaPillApp(resumeTick: Int) {
    val stack = rememberSaveable(saver = RouteStackSaver) { mutableStateListOf<Route>(Route.Intro) }
    val context = LocalContext.current

    // Bring the island back if it was running when the app was last used (never before the user finished setup).
    LaunchedEffect(Unit) {
        NovaGraph.settings.loaded.first { it }
        val s = NovaGraph.settings.settings.value
        if (s.onboardingDone &&
            s.restoreOnLaunch &&
            s.islandEnabled &&
            Permissions.canDrawOverlays(context) &&
            NovaGraph.serviceStatus.value != ServiceStatus.RUNNING
        ) {
            IslandService.start(context.applicationContext)
        }
    }

    fun push(route: Route) {
        if (stack.lastOrNull() != route) stack.add(route)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun replaceAll(route: Route) {
        stack.clear()
        stack.add(route)
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(targetState = stack.lastOrNull() ?: Route.Intro, animationSpec = tween(220), label = "route") { route ->
            when (route) {
                Route.Intro -> IntroScreen(
                    onReady = {
                        val done = NovaGraph.settings.settings.value.onboardingDone
                        replaceAll(if (done) Route.Dashboard else Route.Welcome)
                    },
                )

                Route.Welcome -> WelcomeScreen(onStart = { push(Route.Permissions(onboarding = true)) })

                is Route.Permissions -> PermissionsScreen(
                    resumeTick = resumeTick,
                    onboarding = route.onboarding,
                    onDone = {
                        if (route.onboarding) {
                            NovaGraph.settings.update { it.copy(onboardingDone = true) }
                            replaceAll(Route.Dashboard)
                        } else {
                            pop()
                        }
                    },
                    onBack = { pop() },
                )

                Route.Dashboard -> DashboardScreen(
                    resumeTick = resumeTick,
                    onOpenSettings = { push(Route.Settings) },
                    onOpenPermissions = { push(Route.Permissions(onboarding = false)) },
                    onOpenMusicSettings = { push(Route.SettingsPage(SettingsSection.MUSIC)) },
                )

                Route.Settings -> SettingsHubScreen(
                    onBack = { pop() },
                    onOpen = { push(Route.SettingsPage(it)) },
                )

                is Route.SettingsPage -> SettingsPageScreen(
                    section = route.section,
                    nav = SettingsNav(
                        back = { pop() },
                        openPicker = { push(Route.AppPicker(it)) },
                        openPermissions = { push(Route.Permissions(onboarding = false)) },
                        resumeTick = resumeTick,
                    ),
                )

                is Route.AppPicker -> AppPickerScreen(
                    purpose = route.purpose,
                    resumeTick = resumeTick,
                    onBack = { pop() },
                )
            }
        }
    }
}
