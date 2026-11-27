package app.novapill.android.notif

import app.novapill.android.settings.NotificationMode
import app.novapill.android.settings.PreviewPrivacy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationFilterTest {
    private val facts = NotifFacts(
        packageName = "com.chat.app", category = "msg", isOngoing = false, isGroupSummary = false,
        hasTitle = true, hasText = true, isMediaStyle = false, isOwnApp = false,
    )
    private val settings = FilterSettings(true, NotificationMode.ALL_EXCEPT_MUTED, emptySet(), emptySet(), emptySet())

    @Test
    fun ordinaryNotificationIsShown() {
        val r = NotificationFilter.decide(facts, settings)
        assertTrue(r.show)
        assertFalse(r.high)
        assertFalse(r.sticky)
    }

    @Test
    fun noiseIsDropped() {
        assertEquals(DropReason.DISABLED, NotificationFilter.decide(facts, settings.copy(peekEnabled = false)).reason)
        assertEquals(DropReason.OWN_APP, NotificationFilter.decide(facts.copy(isOwnApp = true), settings).reason)
        assertEquals(DropReason.GROUP_SUMMARY, NotificationFilter.decide(facts.copy(isGroupSummary = true), settings).reason)
        assertEquals(DropReason.ONGOING, NotificationFilter.decide(facts.copy(isOngoing = true), settings).reason)
        assertEquals(DropReason.MEDIA, NotificationFilter.decide(facts.copy(isMediaStyle = true), settings).reason)
        assertEquals(DropReason.MEDIA, NotificationFilter.decide(facts.copy(category = "transport"), settings).reason)
        assertEquals(DropReason.EMPTY, NotificationFilter.decide(facts.copy(hasTitle = false, hasText = false), settings).reason)
    }

    @Test
    fun mutingAndAllowList() {
        assertEquals(DropReason.MUTED_APP, NotificationFilter.decide(facts, settings.copy(mutedApps = setOf("com.chat.app"))).reason)
        assertEquals(DropReason.MUTED_CATEGORY, NotificationFilter.decide(facts, settings.copy(mutedCategories = setOf("msg"))).reason)
        val only = settings.copy(mode = NotificationMode.ONLY_ALLOWED, allowedApps = setOf("com.other"))
        assertEquals(DropReason.NOT_ALLOWED, NotificationFilter.decide(facts, only).reason)
        assertTrue(NotificationFilter.decide(facts.copy(packageName = "com.other"), only).show)
    }

    @Test
    fun callsAndAlarmsAreHighPriority() {
        val call = NotificationFilter.decide(facts.copy(category = "call", isOngoing = true), settings)
        assertTrue(call.show && call.high && call.sticky)
        val alarm = NotificationFilter.decide(facts.copy(category = "alarm", isOngoing = true), settings)
        assertTrue(alarm.show && alarm.high && !alarm.sticky)
        val missed = NotificationFilter.decide(facts.copy(category = "call", isOngoing = false), settings)
        assertTrue(missed.show && missed.high && !missed.sticky)
    }

    private fun inputs(
        privacy: PreviewPrivacy, locked: Boolean = false, secret: Boolean = false,
        hideSensitive: Boolean = true, category: String? = "msg",
    ) = PreviewInputs("Chat", "Alice", "Are we still on for dinner tonight?", category, locked, secret, hideSensitive, privacy)

    @Test
    fun defaultSummaryShowsAppNameAndTitleOnly() {
        val p = PreviewBuilder.build(inputs(PreviewPrivacy.SUMMARY))
        assertEquals("Chat", p.appName)
        assertEquals("Alice", p.summary)
    }

    @Test
    fun appOnlyHidesEverything() {
        assertEquals(PreviewBuilder.GENERIC, PreviewBuilder.build(inputs(PreviewPrivacy.APP_ONLY)).summary)
    }

    @Test
    fun fullShowsBodyUnlessSensitiveHidden() {
        val full = PreviewBuilder.build(inputs(PreviewPrivacy.FULL, hideSensitive = false, category = "social"))
        assertEquals("Alice: Are we still on for dinner tonight?", full.summary)
        val capped = PreviewBuilder.build(inputs(PreviewPrivacy.FULL, hideSensitive = true))
        assertEquals("Alice", capped.summary)
    }

    @Test
    fun lockScreenAndSecretAlwaysHideContent() {
        assertEquals(PreviewBuilder.GENERIC, PreviewBuilder.build(inputs(PreviewPrivacy.FULL, locked = true, hideSensitive = false)).summary)
        assertEquals(PreviewBuilder.GENERIC, PreviewBuilder.build(inputs(PreviewPrivacy.SUMMARY, secret = true)).summary)
    }

    @Test
    fun truncationAddsEllipsisAndStaysOneLine() {
        val long = "x".repeat(100)
        val t = PreviewBuilder.truncate(long, 40)
        assertEquals(40, t.length)
        assertTrue(t.endsWith("…"))
        assertEquals("a b", PreviewBuilder.truncate("a\nb", 10))
    }
}
