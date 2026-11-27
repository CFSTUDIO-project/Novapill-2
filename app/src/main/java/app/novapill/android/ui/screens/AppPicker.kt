package app.novapill.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.novapill.android.NovaGraph
import app.novapill.android.perm.Permissions
import app.novapill.android.settings.NovaSettings
import app.novapill.android.ui.components.AppButton
import app.novapill.android.ui.components.AppIconView
import app.novapill.android.ui.components.ButtonKind
import app.novapill.android.ui.components.Gap
import app.novapill.android.ui.components.NText
import app.novapill.android.ui.components.NovaIcon
import app.novapill.android.ui.components.NovaIconView
import app.novapill.android.ui.components.NovaTextField
import app.novapill.android.ui.components.ScreenScaffold
import app.novapill.android.ui.components.StatusBadge
import app.novapill.android.ui.components.pressable
import app.novapill.android.ui.theme.NovaColors
import app.novapill.android.ui.theme.NovaType
import app.novapill.android.util.AppEntry
import app.novapill.android.util.IntentLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Searchable list of installed apps that have a launcher icon. Used for every per-app setting. */
@Composable
fun AppPickerScreen(purpose: PickerPurpose, resumeTick: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by NovaGraph.settings.settings.collectAsState()
    val activePlayers by NovaGraph.media.players.collectAsState()
    var query by remember { mutableStateOf("") }
    val apps by produceState<List<AppEntry>?>(initialValue = null) {
        value = withContext(Dispatchers.Default) { NovaGraph.appInfo.listLaunchable() }
    }

    val selected: Set<String> = when (purpose) {
        PickerPurpose.HIDDEN_APPS -> settings.hiddenApps
        PickerPurpose.ALLOWED_APPS -> settings.allowedApps
        PickerPurpose.MUTED_APPS -> settings.mutedApps
        PickerPurpose.PREFERRED_PLAYER -> if (settings.preferredPlayer.isBlank()) emptySet() else setOf(settings.preferredPlayer)
        PickerPurpose.SHORTCUTS -> settings.shortcutApps.toSet()
    }
    // Selected apps float to the top when the screen opens, but rows do not jump around while you tap.
    val initiallySelected = remember { selected }
    val usageAccess = remember(resumeTick) { Permissions.usageAccess(context) }

    fun toggle(packageName: String) {
        NovaGraph.settings.update { s ->
            when (purpose) {
                PickerPurpose.HIDDEN_APPS -> s.copy(hiddenApps = s.hiddenApps.toggled(packageName))
                PickerPurpose.ALLOWED_APPS -> s.copy(allowedApps = s.allowedApps.toggled(packageName))
                PickerPurpose.MUTED_APPS -> s.copy(mutedApps = s.mutedApps.toggled(packageName))
                PickerPurpose.PREFERRED_PLAYER -> s.copy(preferredPlayer = if (s.preferredPlayer == packageName) "" else packageName)
                PickerPurpose.SHORTCUTS -> {
                    val list = s.shortcutApps
                    if (packageName in list) {
                        s.copy(shortcutApps = list - packageName)
                    } else if (list.size < NovaSettings.MAX_SHORTCUTS) {
                        s.copy(shortcutApps = list + packageName)
                    } else {
                        s
                    }
                }
            }
        }
    }

    ScreenScaffold(title = purpose.title, onBack = onBack, scroll = false) {
        val hint = when (purpose) {
            PickerPurpose.HIDDEN_APPS -> "The pill hides while one of these apps is in front."
            PickerPurpose.ALLOWED_APPS -> "Used when notifications are set to \"Only allowed apps\"."
            PickerPurpose.MUTED_APPS -> "Notifications from these apps never peek."
            PickerPurpose.PREFERRED_PLAYER -> "The music card follows this player when it has a session. Otherwise it picks automatically, Spotify first."
            PickerPurpose.SHORTCUTS -> "Up to ${NovaSettings.MAX_SHORTCUTS} apps appear as icons in the tools card."
        }
        NText(hint, Modifier.padding(horizontal = 20.dp, vertical = 4.dp), NovaType.Caption.copy(fontSize = 13.sp, lineHeight = 18.sp))
        if (purpose == PickerPurpose.HIDDEN_APPS && !usageAccess) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                NText(
                    "Usage access is off, so the pill cannot tell which app is in front. Hiding by app does nothing until you turn it on.",
                    style = NovaType.Caption.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = NovaColors.Yellow,
                )
                Gap(8)
                AppButton(
                    "Open Usage access",
                    { IntentLauncher.start(context, Permissions.usageAccessIntent()) },
                    Modifier.fillMaxWidth(),
                    ButtonKind.SECONDARY,
                    height = 44.dp,
                )
            }
        }
        NovaTextField(query, { query = it }, "Search apps", Modifier.padding(horizontal = 20.dp, vertical = 10.dp))

        val all = apps
        if (all == null) {
            NText("Loading apps…", Modifier.padding(20.dp), NovaType.Caption)
        } else {
            val q = query.trim()
            val visible = remember(all, q) {
                all.filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
                    .sortedWith(compareByDescending<AppEntry> { it.packageName in initiallySelected }.thenBy { it.label.lowercase() })
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                if (purpose == PickerPurpose.PREFERRED_PLAYER && q.isEmpty()) {
                    item(key = "auto") {
                        PickerRow(
                            title = "Automatic",
                            subtitle = "Spotify first, then whichever player is active",
                            packageName = null,
                            selected = settings.preferredPlayer.isBlank(),
                            active = false,
                            onClick = { NovaGraph.settings.update { it.copy(preferredPlayer = "") } },
                        )
                    }
                }
                items(visible, key = { it.packageName }) { app ->
                    PickerRow(
                        title = app.label,
                        subtitle = app.packageName,
                        packageName = app.packageName,
                        selected = app.packageName in selected,
                        active = purpose == PickerPurpose.PREFERRED_PLAYER && app.packageName in activePlayers,
                        onClick = { toggle(app.packageName) },
                    )
                }
                if (visible.isEmpty()) {
                    item(key = "empty") { NText("No app matches \"$q\".", Modifier.padding(20.dp), NovaType.Caption) }
                }
            }
        }
    }
}

private fun Set<String>.toggled(value: String): Set<String> = if (value in this) this - value else this + value

@Composable
private fun PickerRow(
    title: String,
    subtitle: String,
    packageName: String?,
    selected: Boolean,
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(label = title, pressedScale = 0.99f, onClick = onClick)
            .defaultMinSize(minHeight = 64.dp)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (packageName != null) {
            AppIconView(packageName, 40.dp)
        } else {
            Box(Modifier.size(40.dp).clip(CircleShape).background(NovaColors.Surface3), contentAlignment = Alignment.Center) {
                NovaIconView(NovaIcon.PILL, NovaColors.Text2, size = 22.dp)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            NText(title, style = NovaType.BodyStrong, maxLines = 1)
            NText(subtitle, style = NovaType.Caption.copy(fontSize = 12.sp, lineHeight = 16.sp), maxLines = 1)
        }
        if (active) {
            StatusBadge("Active", NovaColors.Green)
            Box(Modifier.size(10.dp))
        }
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (selected) NovaColors.Crimson else Color.Transparent)
                .border(1.5.dp, if (selected) NovaColors.Crimson else NovaColors.Text4, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) NovaIconView(NovaIcon.CHECK, Color.White, size = 16.dp)
        }
    }
}
