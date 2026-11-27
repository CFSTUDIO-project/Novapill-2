package app.novapill.android.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovaSettingsCodecTest {
    @Test
    fun defaultsRoundTrip() {
        val d = NovaSettings()
        assertEquals(d, NovaSettingsCodec.decode(NovaSettingsCodec.encode(d)))
    }

    @Test
    fun customValuesRoundTrip() {
        val s = NovaSettings(
            islandEnabled = true, widthDp = 200, heightDp = 40, topOffsetDp = 12, xOffsetDp = -30,
            peekDurationSec = 6, privacy = PreviewPrivacy.FULL, notifMode = NotificationMode.ONLY_ALLOWED,
            allowedApps = setOf("com.a", "com.b"), mutedApps = setOf("com.c"), mutedCategories = setOf("promo", "social"),
            preferredPlayer = "com.spotify.music", lyricsProvider = LyricsProviderChoice.LRCLIB,
            lyricsOffsetMs = -250, trackOffsets = mapOf("artist|title" to 350), pauseBehavior = PauseBehavior.HIDE,
            clockFormat = ClockFormat.H24, timerPresetsMin = listOf(2, 7, 15, 45), weatherCity = "Jakarta",
            shortcutApps = listOf("com.x", "com.y"), lsIndependent = true, lsWidthDp = 220, hiddenApps = setOf("com.game"),
            idleInfo = IdleInfo.CLOCK_BATTERY, weatherUnit = TempUnit.FAHRENHEIT, hiddenTiles = setOf(TileKeys.CAMERA, TileKeys.PHONE),
            flashStrengthPercent = 40, recQuality = RecQuality.P1080, recFps = 60, recMic = true, recCountdownSec = 5,
            floatingExternal = true, floatingWidthPct = 55, peekReply = false, hideSensitiveCategories = true,
        )
        assertEquals(s, NovaSettingsCodec.decode(NovaSettingsCodec.encode(s)))
    }

    @Test
    fun garbageFallsBackToDefaultsWithoutThrowing() {
        assertEquals(NovaSettings(), NovaSettingsCodec.decode(null))
        assertEquals(NovaSettings(), NovaSettingsCodec.decode(""))
        assertEquals(NovaSettings(), NovaSettingsCodec.decode("{not json"))
        assertEquals(NovaSettings(), NovaSettingsCodec.decode("[1,2,3]"))
    }

    @Test
    fun outOfRangeNumbersAreClamped() {
        val s = NovaSettingsCodec.decode("""{"widthDp":9999,"heightDp":1,"opacityPercent":0,"idleTimeoutSec":999,"lyricsOffsetMs":99999}""")
        assertEquals(260, s.widthDp)
        assertEquals(30, s.heightDp)
        assertEquals(70, s.opacityPercent)
        assertEquals(60, s.idleTimeoutSec)
        assertEquals(1_500, s.lyricsOffsetMs)
    }

    @Test
    fun peekDurationOnlyAcceptsTheFourAllowedValues() {
        for (ok in listOf(2, 3, 4, 6)) {
            assertEquals(ok, NovaSettingsCodec.decode("""{"peekDurationSec":$ok}""").peekDurationSec)
        }
        for (bad in listOf(0, 1, 5, 7, 10, -3)) {
            assertEquals(2, NovaSettingsCodec.decode("""{"peekDurationSec":$bad}""").peekDurationSec)
        }
        assertEquals(2, NovaSettings().peekDurationSec)
    }

    @Test
    fun unknownEnumValuesFallBack() {
        val s = NovaSettingsCodec.decode("""{"privacy":"NOPE","pauseBehavior":"???","clockFormat":"H99"}""")
        assertEquals(PreviewPrivacy.FULL, s.privacy)
        assertEquals(PauseBehavior.COLLAPSE, s.pauseBehavior)
        assertEquals(ClockFormat.SYSTEM, s.clockFormat)
    }

    @Test
    fun offsetSnapsToFiftyMillisecondGrid() {
        assertEquals(150, NovaSettingsCodec.snapOffset(130))
        assertEquals(100, NovaSettingsCodec.snapOffset(-(-100)))
        assertEquals(-100, NovaSettingsCodec.snapOffset(-125))
        assertEquals(50, NovaSettingsCodec.snapOffset(74))
        assertEquals(100, NovaSettingsCodec.snapOffset(75))
        assertEquals(1_500, NovaSettingsCodec.snapOffset(9_999))
        assertEquals(-1_500, NovaSettingsCodec.snapOffset(-9_999))
        assertEquals(0, NovaSettingsCodec.snapOffset(0))
    }

    @Test
    fun trackOffsetsAreSnappedAndCapped() {
        val entries = (0 until 300).joinToString(",") { "\"k$it\":${(it % 7) * 130}" }
        val s = NovaSettingsCodec.decode("""{"trackOffsets":{$entries}}""")
        assertEquals(NovaSettings.MAX_TRACK_OFFSETS, s.trackOffsets.size)
        assertTrue(s.trackOffsets.values.all { it % 50 == 0 && it in -1_500..1_500 })
    }

    @Test
    fun perTrackOffsetWinsOverGlobal() {
        val s = NovaSettings(lyricsOffsetMs = 100, trackOffsets = mapOf("a|b" to -400))
        assertEquals(-400, s.offsetFor("a|b"))
        assertEquals(100, s.offsetFor("x|y"))
        assertEquals(100, s.offsetFor(null))
    }

    @Test
    fun listsAreBoundedAndDeduplicated() {
        val s = NovaSettingsCodec.decode(
            """{"shortcutApps":["a","b","a","c","d","e"],"timerPresetsMin":[0,500,5,6,7,8]}""",
        )
        assertEquals(listOf("a", "b", "c", "d"), s.shortcutApps)
        assertEquals(listOf(1, 180, 5, 6), s.timerPresetsMin)
    }

    @Test
    fun landscapeValuesOnlyApplyWhenIndependent() {
        val shared = NovaSettings(widthDp = 180, lsWidthDp = 220, topOffsetDp = 5, lsTopOffsetDp = 9)
        assertEquals(180, shared.effectiveWidthDp(true))
        assertEquals(5, shared.effectiveTopOffsetDp(true))
        val own = shared.copy(lsIndependent = true)
        assertEquals(220, own.effectiveWidthDp(true))
        assertEquals(9, own.effectiveTopOffsetDp(true))
        assertEquals(180, own.effectiveWidthDp(false))
    }

    @Test
    fun versionOneFileIsMigratedToV2Defaults() {
        val v1 = """{"schema":1,"privacy":"SUMMARY","hideSensitiveCategories":true,"recordManualEnabled":true,""" +
            """"flashShortcut":false,"weatherFahrenheit":true,"widthDp":190}"""
        val s = NovaSettingsCodec.decode(v1)
        assertEquals(PreviewPrivacy.FULL, s.privacy)
        assertEquals(false, s.hideSensitiveCategories)
        assertEquals(false, s.recordManualEnabled)
        assertTrue(TileKeys.FLASH in s.hiddenTiles)
        assertEquals(TempUnit.FAHRENHEIT, s.weatherUnit)
        assertEquals(190, s.widthDp)
    }

    @Test
    fun versionOneExplicitChoicesSurvive() {
        assertEquals(PreviewPrivacy.APP_ONLY, NovaSettingsCodec.decode("""{"schema":1,"privacy":"APP_ONLY"}""").privacy)
        assertEquals(PreviewPrivacy.FULL, NovaSettingsCodec.decode("""{"schema":1,"privacy":"FULL"}""").privacy)
        assertEquals(TempUnit.AUTO, NovaSettingsCodec.decode("""{"schema":1,"weatherFahrenheit":false}""").weatherUnit)
    }

    @Test
    fun versionTwoFileKeepsStoredChoices() {
        val s = NovaSettingsCodec.decode(
            """{"schema":2,"privacy":"SUMMARY","hideSensitiveCategories":true,"recordManualEnabled":true,"weatherUnit":"CELSIUS"}""",
        )
        assertEquals(PreviewPrivacy.SUMMARY, s.privacy)
        assertEquals(true, s.hideSensitiveCategories)
        assertEquals(true, s.recordManualEnabled)
        assertEquals(TempUnit.CELSIUS, s.weatherUnit)
    }

    @Test
    fun encodedFileCarriesTheCurrentSchema() {
        assertTrue(NovaSettingsCodec.encode(NovaSettings()).contains("\"schema\":2"))
    }

    @Test
    fun recorderChoicesAreValidated() {
        val s = NovaSettingsCodec.decode("""{"recFps":45,"recCountdownSec":4,"flashStrengthPercent":3,"floatingWidthPct":5}""")
        assertEquals(30, s.recFps)
        assertEquals(3, s.recCountdownSec)
        assertEquals(10, s.flashStrengthPercent)
        assertEquals(40, s.floatingWidthPct)
        assertEquals(60, NovaSettingsCodec.decode("""{"recFps":60}""").recFps)
        assertEquals(0, NovaSettingsCodec.decode("""{"recCountdownSec":0}""").recCountdownSec)
    }

    @Test
    fun unknownTileKeysAreDropped() {
        val s = NovaSettingsCodec.decode("""{"hiddenTiles":["camera","nonsense","flash"]}""")
        assertEquals(setOf("camera", "flash"), s.hiddenTiles)
        assertTrue(s.tileVisible("phone"))
        assertEquals(false, s.tileVisible("camera"))
    }

    @Test
    fun temperatureUnitFollowsTheLocaleOnlyInAuto() {
        assertEquals(false, TempUnits.useFahrenheit(TempUnit.AUTO, "ID"))
        assertEquals(true, TempUnits.useFahrenheit(TempUnit.AUTO, "us"))
        assertEquals(false, TempUnits.useFahrenheit(TempUnit.AUTO, ""))
        assertEquals(true, TempUnits.useFahrenheit(TempUnit.FAHRENHEIT, "ID"))
        assertEquals(false, TempUnits.useFahrenheit(TempUnit.CELSIUS, "US"))
    }
}
