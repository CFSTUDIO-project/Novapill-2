package app.novapill.android.ui.screens

/** Every screen of the app. Navigation is a plain back stack kept by MainActivity. */
sealed interface Route {
    data object Intro : Route
    data object Welcome : Route
    data class Permissions(val onboarding: Boolean) : Route
    data object Dashboard : Route
    data object Settings : Route
    data class SettingsPage(val section: SettingsSection) : Route
    data class AppPicker(val purpose: PickerPurpose) : Route
}

enum class SettingsSection(val title: String, val summary: String) {
    GENERAL("General", "Start-up and reset"),
    APPEARANCE("Appearance", "Size, position, shape, motion feel"),
    BEHAVIOR("Behavior", "Gestures, idle, hiding"),
    NOTIFICATIONS("Notifications", "Peeks, filters, privacy"),
    MUSIC("Music", "Player, lyrics, visualizer"),
    QUICK("Quick panel", "Idle pill, flashlight, tiles"),
    SCREEN_RECORDER("Screen recorder", "Quality, sound, countdown"),
    RECORD("Record indicator", "Android's signal and manual timer"),
    FLOATING("Floating windows", "Calculator and app windows"),
    LANDSCAPE("Landscape", "Position and size when rotated"),
    UTILITIES("Utilities", "Clock, timer, weather, shortcuts"),
    PRIVACY("Privacy & Permissions", "What is read, what is stored"),
    ABOUT("About & Debug", "Versions, state, diagnostics"),
}

enum class PickerPurpose(val title: String, val multiple: Boolean) {
    HIDDEN_APPS("Hide in these apps", true),
    ALLOWED_APPS("Allowed apps", true),
    MUTED_APPS("Muted apps", true),
    PREFERRED_PLAYER("Preferred player", false),
    SHORTCUTS("Shortcut apps", true),
}

/** Back-stack entries are saved as short strings so they survive process recreation. */
fun Route.encode(): String = when (this) {
    Route.Intro -> "intro"
    Route.Welcome -> "welcome"
    is Route.Permissions -> "permissions:$onboarding"
    Route.Dashboard -> "dashboard"
    Route.Settings -> "settings"
    is Route.SettingsPage -> "page:${section.name}"
    is Route.AppPicker -> "picker:${purpose.name}"
}

fun decodeRoute(value: String): Route? = when {
    value == "intro" -> Route.Intro
    value == "welcome" -> Route.Welcome
    value.startsWith("permissions:") -> Route.Permissions(value.removePrefix("permissions:") == "true")
    value == "dashboard" -> Route.Dashboard
    value == "settings" -> Route.Settings
    value.startsWith("page:") -> SettingsSection.entries.firstOrNull { it.name == value.removePrefix("page:") }?.let { Route.SettingsPage(it) }
    value.startsWith("picker:") -> PickerPurpose.entries.firstOrNull { it.name == value.removePrefix("picker:") }?.let { Route.AppPicker(it) }
    else -> null
}
