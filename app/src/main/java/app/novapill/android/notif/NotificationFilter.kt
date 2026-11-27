package app.novapill.android.notif

import app.novapill.android.settings.NotificationMode
import app.novapill.android.settings.PreviewPrivacy

/** Everything the filter needs to know about a notification, without any Android type. */
data class NotifFacts(
    val packageName: String,
    val category: String?,
    val isOngoing: Boolean,
    val isGroupSummary: Boolean,
    val hasTitle: Boolean,
    val hasText: Boolean,
    val isMediaStyle: Boolean,
    val isOwnApp: Boolean,
)

data class FilterSettings(
    val peekEnabled: Boolean,
    val mode: NotificationMode,
    val allowedApps: Set<String>,
    val mutedApps: Set<String>,
    val mutedCategories: Set<String>,
)

enum class DropReason {
    DISABLED, OWN_APP, GROUP_SUMMARY, ONGOING, MEDIA, EMPTY, MUTED_APP, NOT_ALLOWED, MUTED_CATEGORY,
}

data class FilterResult(
    val show: Boolean,
    val reason: DropReason? = null,
    /** Calls and alarms jump the queue. */
    val high: Boolean = false,
    /** An incoming call stays until the system removes the notification. */
    val sticky: Boolean = false,
)

object NotificationFilter {
    const val CAT_CALL = "call"
    const val CAT_ALARM = "alarm"
    const val CAT_MESSAGE = "msg"
    const val CAT_EMAIL = "email"
    const val CAT_TRANSPORT = "transport"

    /** Categories offered in the "mute categories" setting (the values Android reports). */
    val MUTABLE_CATEGORIES: List<Pair<String, String>> = listOf(
        "msg" to "Messages",
        "email" to "Email",
        "social" to "Social",
        "promo" to "Promotions",
        "recommendation" to "Recommendations",
        "event" to "Calendar events",
        "reminder" to "Reminders",
        "progress" to "Progress",
        "status" to "Device status",
        "sys" to "System",
        "err" to "Errors",
    )

    fun decide(f: NotifFacts, s: FilterSettings): FilterResult {
        if (!s.peekEnabled) return FilterResult(false, DropReason.DISABLED)
        if (f.isOwnApp) return FilterResult(false, DropReason.OWN_APP)
        if (f.isGroupSummary) return FilterResult(false, DropReason.GROUP_SUMMARY)
        if (f.isMediaStyle || f.category == CAT_TRANSPORT) return FilterResult(false, DropReason.MEDIA)

        val isCall = f.category == CAT_CALL
        val isAlarm = f.category == CAT_ALARM
        if (f.isOngoing && !isCall && !isAlarm) return FilterResult(false, DropReason.ONGOING)
        if (!f.hasTitle && !f.hasText) return FilterResult(false, DropReason.EMPTY)

        if (s.mode == NotificationMode.ONLY_ALLOWED && f.packageName !in s.allowedApps) {
            return FilterResult(false, DropReason.NOT_ALLOWED)
        }
        if (f.packageName in s.mutedApps) return FilterResult(false, DropReason.MUTED_APP)
        if (f.category != null && f.category in s.mutedCategories) return FilterResult(false, DropReason.MUTED_CATEGORY)

        return FilterResult(true, high = isCall || isAlarm, sticky = isCall && f.isOngoing)
    }
}

data class PreviewInputs(
    val appLabel: String,
    val title: String,
    val text: String,
    val category: String?,
    val keyguardLocked: Boolean,
    /** VISIBILITY_SECRET (or private while locked) hides the content regardless of settings. */
    val secret: Boolean,
    val hideSensitive: Boolean,
    val privacy: PreviewPrivacy,
)

data class PreviewText(val appName: String, val summary: String)

object PreviewBuilder {
    const val GENERIC = "New notification"
    private const val SUMMARY_MAX = 40
    private const val FULL_MAX = 96

    fun effectivePrivacy(i: PreviewInputs): PreviewPrivacy {
        if (i.keyguardLocked || i.secret) return PreviewPrivacy.APP_ONLY
        var p = i.privacy
        val sensitive = i.category == NotificationFilter.CAT_MESSAGE || i.category == NotificationFilter.CAT_EMAIL
        if (i.hideSensitive && sensitive && p == PreviewPrivacy.FULL) p = PreviewPrivacy.SUMMARY
        return p
    }

    fun build(i: PreviewInputs): PreviewText {
        val title = i.title.trim()
        val text = i.text.trim()
        return when (effectivePrivacy(i)) {
            PreviewPrivacy.APP_ONLY -> PreviewText(i.appLabel, GENERIC)
            PreviewPrivacy.SUMMARY -> PreviewText(i.appLabel, truncate(title.ifEmpty { GENERIC }, SUMMARY_MAX))
            PreviewPrivacy.FULL -> {
                val joined = when {
                    title.isNotEmpty() && text.isNotEmpty() -> "$title: $text"
                    title.isNotEmpty() -> title
                    text.isNotEmpty() -> text
                    else -> GENERIC
                }
                PreviewText(i.appLabel, truncate(joined, FULL_MAX))
            }
        }
    }

    fun truncate(s: String, max: Int): String {
        val oneLine = s.replace('\n', ' ').replace('\r', ' ').trim()
        return if (oneLine.length <= max) oneLine else oneLine.substring(0, max - 1).trimEnd() + "…"
    }
}
