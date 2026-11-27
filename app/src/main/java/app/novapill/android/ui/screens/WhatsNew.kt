package app.novapill.android.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.novapill.android.BuildConfig
import app.novapill.android.settings.NovaSettings
import app.novapill.android.ui.components.AppButton
import app.novapill.android.ui.components.ButtonKind
import app.novapill.android.ui.components.Gap
import app.novapill.android.ui.components.NText
import app.novapill.android.ui.components.NovaCard
import app.novapill.android.ui.theme.NovaColors
import app.novapill.android.ui.theme.NovaType

/** What changed in V2, in plain words. Shown once on the dashboard; "Got it" remembers that it was seen. */
internal val WHATS_NEW_V2: List<String> = listOf(
    "The flashlight tile now shows what Android reports. A tap that is not confirmed says so instead of pretending.",
    "A quick panel with a real clock, date, weather and battery, and a second page of tools.",
    "Notifications show the app, who wrote and a one-line preview, with a Reply button where the app allows it.",
    "A real screen recorder that saves videos to Movies/NOVA-PILL, and a floating calculator.",
    "Many more settings: idle pill, tiles, recorder, floating windows.",
)

@Composable
internal fun WhatsNewCard() {
    NovaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            NText("NEW IN V2 · ${BuildConfig.VERSION_NAME}", style = NovaType.Label, color = NovaColors.Crimson)
            Gap(10)
            for (line in WHATS_NEW_V2) {
                NText(line, Modifier.padding(bottom = 8.dp), NovaType.Body.copy(fontSize = 14.sp, lineHeight = 20.sp))
            }
            Gap(6)
            AppButton(
                "Got it",
                { save { it.copy(whatsNewSeen = NovaSettings.CURRENT_WHATS_NEW) } },
                Modifier.fillMaxWidth(),
                ButtonKind.SECONDARY,
                height = 46.dp,
            )
        }
    }
}
