package app.novapill.android.ui.screens

import androidx.compose.runtime.Composable
import app.novapill.android.ui.components.Hairline
import app.novapill.android.ui.components.NavRow
import app.novapill.android.ui.components.NovaIcon
import app.novapill.android.ui.components.ScreenScaffold

@Composable
fun SettingsHubScreen(onBack: () -> Unit, onOpen: (SettingsSection) -> Unit) {
    ScreenScaffold(title = "Settings", onBack = onBack) {
        SettingsSection.entries.forEachIndexed { index, section ->
            NavRow(
                title = section.title,
                subtitle = section.summary,
                icon = iconFor(section),
                onClick = { onOpen(section) },
            )
            if (index != SettingsSection.entries.lastIndex) Hairline()
        }
    }
}

private fun iconFor(section: SettingsSection): NovaIcon = when (section) {
    SettingsSection.GENERAL -> NovaIcon.SLIDERS
    SettingsSection.APPEARANCE -> NovaIcon.PILL
    SettingsSection.BEHAVIOR -> NovaIcon.TOUCH
    SettingsSection.NOTIFICATIONS -> NovaIcon.BELL
    SettingsSection.MUSIC -> NovaIcon.MUSIC
    SettingsSection.QUICK -> NovaIcon.APPS
    SettingsSection.SCREEN_RECORDER -> NovaIcon.RECORD
    SettingsSection.RECORD -> NovaIcon.DOT
    SettingsSection.FLOATING -> NovaIcon.WINDOW
    SettingsSection.LANDSCAPE -> NovaIcon.ROTATE
    SettingsSection.UTILITIES -> NovaIcon.TIMER
    SettingsSection.PRIVACY -> NovaIcon.SHIELD
    SettingsSection.ABOUT -> NovaIcon.INFO
}

/** Routes a settings section to its page. */
@Composable
internal fun SettingsPageScreen(section: SettingsSection, nav: SettingsNav) {
    when (section) {
        SettingsSection.GENERAL -> GeneralPage(nav)
        SettingsSection.APPEARANCE -> AppearancePage(nav)
        SettingsSection.BEHAVIOR -> BehaviorPage(nav)
        SettingsSection.NOTIFICATIONS -> NotificationsPage(nav)
        SettingsSection.MUSIC -> MusicPage(nav)
        SettingsSection.QUICK -> QuickPage(nav)
        SettingsSection.SCREEN_RECORDER -> ScreenRecorderPage(nav)
        SettingsSection.RECORD -> RecordPage(nav)
        SettingsSection.FLOATING -> FloatingPage(nav)
        SettingsSection.LANDSCAPE -> LandscapePage(nav)
        SettingsSection.UTILITIES -> UtilitiesPage(nav)
        SettingsSection.PRIVACY -> PrivacyPage(nav)
        SettingsSection.ABOUT -> AboutPage(nav)
    }
}
