package app.novapill.android.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutesTest {
    private val everyRoute: List<Route> = buildList {
        add(Route.Intro)
        add(Route.Welcome)
        add(Route.Permissions(onboarding = true))
        add(Route.Permissions(onboarding = false))
        add(Route.Dashboard)
        add(Route.Settings)
        SettingsSection.entries.forEach { add(Route.SettingsPage(it)) }
        PickerPurpose.entries.forEach { add(Route.AppPicker(it)) }
    }

    @Test
    fun everyRouteSurvivesEncodingAndDecoding() {
        for (route in everyRoute) {
            assertEquals(route, decodeRoute(route.encode()))
        }
    }

    @Test
    fun encodedKeysAreUnique() {
        val keys = everyRoute.map { it.encode() }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun unknownOrDamagedKeysDecodeToNull() {
        assertNull(decodeRoute(""))
        assertNull(decodeRoute("nonsense"))
        assertNull(decodeRoute("page:NOT_A_SECTION"))
        assertNull(decodeRoute("picker:NOT_A_PURPOSE"))
    }

    @Test
    fun everySettingsSectionHasATitleAndSummary() {
        for (section in SettingsSection.entries) {
            assertTrue(section.title.isNotBlank())
            assertTrue(section.summary.isNotBlank())
        }
    }

    @Test
    fun onlyTheAppPickerForPlayerAllowsASingleChoice() {
        val single = PickerPurpose.entries.filter { !it.multiple }
        assertEquals(listOf(PickerPurpose.PREFERRED_PLAYER), single)
    }
}
