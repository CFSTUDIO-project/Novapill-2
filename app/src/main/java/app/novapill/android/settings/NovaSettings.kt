package app.novapill.android.settings

import app.novapill.android.util.MiniJson
import app.novapill.android.util.asJsonList
import app.novapill.android.util.asJsonMap
import app.novapill.android.util.jsonBool
import app.novapill.android.util.jsonInt
import app.novapill.android.util.jsonString

enum class NotificationMode { ALL_EXCEPT_MUTED, ONLY_ALLOWED }

/**
 * What a peek is allowed to show: APP_ONLY = app name, SUMMARY = app + sender/title, FULL = app + sender + message.
 * V2 defaults to FULL because the island is only useful when it says who wrote and what; nothing is stored or sent.
 */
enum class PreviewPrivacy { APP_ONLY, SUMMARY, FULL }

enum class QueueBehavior { QUEUE, LATEST_ONLY }

enum class PauseBehavior { KEEP_VISIBLE, COLLAPSE, HIDE }

enum class LyricsProviderChoice { LOCAL_ONLY, LRCLIB }

enum class LyricsHighlight { SOFT, ACCENT, BAR }

enum class ClockFormat { SYSTEM, H12, H24 }

/** What the idle pill shows next to the clock. */
enum class IdleInfo { CLOCK, CLOCK_BATTERY, FULL }

/** AUTO follows the country of the phone's locale (Fahrenheit only where it is the everyday unit). */
enum class TempUnit { AUTO, CELSIUS, FAHRENHEIT }

/** Screen recorder resolution: the short side of the video, the long side follows the screen's aspect. */
enum class RecQuality(val shortSide: Int) { P720(720), P1080(1080) }

/** Keys of the tiles in the quick panel (stable strings, stored in settings). */
object TileKeys {
    const val FLASH = "flash"
    const val STOPWATCH = "stopwatch"
    const val RECORD = "record"
    const val ALARMS = "alarms"
    const val CALENDAR = "calendar"
    const val PHONE = "phone"
    const val INTERNET = "internet"
    const val VOLUME = "volume"
    const val CAMERA = "camera"
    const val CALCULATOR = "calculator"
    const val SETTINGS = "settings"
    const val MANUAL_TIMER = "manual_timer"
    val ALL: Set<String> = setOf(FLASH, STOPWATCH, RECORD, ALARMS, CALENDAR, PHONE, INTERNET, VOLUME, CAMERA, CALCULATOR, SETTINGS, MANUAL_TIMER)
}

object TempUnits {
    private val FAHRENHEIT_COUNTRIES = setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW", "FM", "MH")

    /** [country] is an ISO 3166 region such as "ID" or "US" (may be empty). */
    fun useFahrenheit(unit: TempUnit, country: String): Boolean = when (unit) {
        TempUnit.CELSIUS -> false
        TempUnit.FAHRENHEIT -> true
        TempUnit.AUTO -> country.uppercase() in FAHRENHEIT_COUNTRIES
    }
}

/**
 * Every user-facing preference. Persisted as a single JSON string (see [NovaSettingsCodec]).
 * Ranges are enforced on decode so a corrupted or hand-edited file can never produce an
 * unreadable island (for example zero opacity or a 5 px wide pill).
 */
data class NovaSettings(
    // A. General
    val islandEnabled: Boolean = false,
    val restoreOnLaunch: Boolean = true,
    val autoStartOnBoot: Boolean = false,
    val onboardingDone: Boolean = false,

    // B. Appearance
    val widthDp: Int = 176,
    val heightDp: Int = 38,
    val topOffsetDp: Int = 0,
    val xOffsetDp: Int = 0,
    val cornerPercent: Int = 100,
    val borderIntensity: Int = 30,
    val artworkSizeDp: Int = 56,
    val fontScalePercent: Int = 100,
    val opacityPercent: Int = 100,
    val accentIntensity: Int = 70,
    val haptics: Boolean = true,

    // C. Behavior
    val tapToExpand: Boolean = true,
    val longPressReposition: Boolean = false,
    val swipeGestures: Boolean = true,
    val idleTimeoutSec: Int = 6,
    val showMinimalIdlePill: Boolean = false,
    val hideAfterMediaStops: Boolean = true,
    val hideInFullscreen: Boolean = true,
    val hideWithKeyboard: Boolean = true,
    val hiddenApps: Set<String> = emptySet(),
    val reduceMotionOnBatterySaver: Boolean = true,
    val reducedMotion: Boolean = false,

    // D. Notifications
    val peekEnabled: Boolean = true,
    val peekDurationSec: Int = 2,
    val notifMode: NotificationMode = NotificationMode.ALL_EXCEPT_MUTED,
    val allowedApps: Set<String> = emptySet(),
    val mutedApps: Set<String> = emptySet(),
    val mutedCategories: Set<String> = emptySet(),
    val privacy: PreviewPrivacy = PreviewPrivacy.FULL,
    val hideSensitiveCategories: Boolean = false,
    val queueBehavior: QueueBehavior = QueueBehavior.QUEUE,
    val peekReply: Boolean = true,

    // E. Music
    val preferredPlayer: String = "",
    val autoOpenLyrics: Boolean = false,
    val showArtwork: Boolean = true,
    val showTitleArtist: Boolean = true,
    val showProgress: Boolean = true,
    val visualizerEnabled: Boolean = true,
    val visualizerIntensity: Int = 60,
    val pauseBehavior: PauseBehavior = PauseBehavior.COLLAPSE,
    val pauseGraceSec: Int = 8,
    val lyricsProvider: LyricsProviderChoice = LyricsProviderChoice.LOCAL_ONLY,
    val lyricsFontPercent: Int = 100,
    val lyricsSpacingPercent: Int = 100,
    val lyricsHighlight: LyricsHighlight = LyricsHighlight.SOFT,
    val lyricsOffsetMs: Int = 0,
    val trackOffsets: Map<String, Int> = emptyMap(),

    // F. Record indicator
    val recordUseOs: Boolean = true,
    val recordManualEnabled: Boolean = false,
    val recordShowDuration: Boolean = true,
    val recordResetOnStop: Boolean = true,
    val recQuality: RecQuality = RecQuality.P720,
    val recFps: Int = 30,
    val recMic: Boolean = false,
    val recCountdownSec: Int = 3,
    val recHidePill: Boolean = false,

    // G. Landscape
    val lsAutoAdapt: Boolean = true,
    val lsCompact: Boolean = true,
    val lsIndependent: Boolean = false,
    val lsTopOffsetDp: Int = 0,
    val lsXOffsetDp: Int = 0,
    val lsWidthDp: Int = 176,
    val hideInLandscapeFullscreen: Boolean = true,

    // H. Utilities
    val clockFormat: ClockFormat = ClockFormat.SYSTEM,
    val timerPresetsMin: List<Int> = listOf(1, 5, 10, 25),
    val volumeInMusicCard: Boolean = true,
    val weatherCity: String = "",
    val weatherUseLocation: Boolean = false,
    val weatherUnit: TempUnit = TempUnit.AUTO,
    val weatherRefreshMin: Int = 60,
    val shortcutApps: List<String> = emptyList(),

    // I. V2: idle, quick panel, flashlight, floating windows
    val idleInfo: IdleInfo = IdleInfo.FULL,
    val panelShowDate: Boolean = true,
    val hiddenTiles: Set<String> = emptySet(),
    val flashShowInPill: Boolean = true,
    val flashStrengthPercent: Int = 100,
    val floatingTools: Boolean = true,
    val floatingExternal: Boolean = false,
    val floatingWidthPct: Int = 70,
    val floatingHeightPct: Int = 60,
    val whatsNewSeen: Int = 0,
) {
    fun tileVisible(key: String): Boolean = key !in hiddenTiles

    fun effectiveWidthDp(landscape: Boolean): Int = if (landscape && lsIndependent) lsWidthDp else widthDp
    fun effectiveTopOffsetDp(landscape: Boolean): Int = if (landscape && lsIndependent) lsTopOffsetDp else topOffsetDp
    fun effectiveXOffsetDp(landscape: Boolean): Int = if (landscape && lsIndependent) lsXOffsetDp else xOffsetDp

    /** Offset in ms for one track: per-track value wins, else the global one. */
    fun offsetFor(trackOffsetKey: String?): Int =
        (if (trackOffsetKey != null) trackOffsets[trackOffsetKey] else null) ?: lyricsOffsetMs

    companion object {
        val PEEK_DURATIONS_SEC = listOf(2, 3, 4, 6)
        const val MAX_TRACK_OFFSETS = 200
        const val MAX_SHORTCUTS = 4
        const val CURRENT_WHATS_NEW = 2
        val REC_FPS_CHOICES = listOf(30, 60)
        val REC_COUNTDOWN_CHOICES = listOf(0, 3, 5)
        const val OFFSET_MIN_MS = -1500
        const val OFFSET_MAX_MS = 1500
        const val OFFSET_STEP_MS = 50
    }
}

object NovaSettingsCodec {
    private const val SCHEMA = 2

    fun encode(s: NovaSettings): String {
        val m = LinkedHashMap<String, Any?>()
        m["schema"] = SCHEMA
        m["islandEnabled"] = s.islandEnabled
        m["restoreOnLaunch"] = s.restoreOnLaunch
        m["autoStartOnBoot"] = s.autoStartOnBoot
        m["onboardingDone"] = s.onboardingDone

        m["widthDp"] = s.widthDp
        m["heightDp"] = s.heightDp
        m["topOffsetDp"] = s.topOffsetDp
        m["xOffsetDp"] = s.xOffsetDp
        m["cornerPercent"] = s.cornerPercent
        m["borderIntensity"] = s.borderIntensity
        m["artworkSizeDp"] = s.artworkSizeDp
        m["fontScalePercent"] = s.fontScalePercent
        m["opacityPercent"] = s.opacityPercent
        m["accentIntensity"] = s.accentIntensity
        m["haptics"] = s.haptics

        m["tapToExpand"] = s.tapToExpand
        m["longPressReposition"] = s.longPressReposition
        m["swipeGestures"] = s.swipeGestures
        m["idleTimeoutSec"] = s.idleTimeoutSec
        m["showMinimalIdlePill"] = s.showMinimalIdlePill
        m["hideAfterMediaStops"] = s.hideAfterMediaStops
        m["hideInFullscreen"] = s.hideInFullscreen
        m["hideWithKeyboard"] = s.hideWithKeyboard
        m["hiddenApps"] = s.hiddenApps.sorted()
        m["reduceMotionOnBatterySaver"] = s.reduceMotionOnBatterySaver
        m["reducedMotion"] = s.reducedMotion

        m["peekEnabled"] = s.peekEnabled
        m["peekDurationSec"] = s.peekDurationSec
        m["notifMode"] = s.notifMode
        m["allowedApps"] = s.allowedApps.sorted()
        m["mutedApps"] = s.mutedApps.sorted()
        m["mutedCategories"] = s.mutedCategories.sorted()
        m["privacy"] = s.privacy
        m["hideSensitiveCategories"] = s.hideSensitiveCategories
        m["queueBehavior"] = s.queueBehavior
        m["peekReply"] = s.peekReply

        m["preferredPlayer"] = s.preferredPlayer
        m["autoOpenLyrics"] = s.autoOpenLyrics
        m["showArtwork"] = s.showArtwork
        m["showTitleArtist"] = s.showTitleArtist
        m["showProgress"] = s.showProgress
        m["visualizerEnabled"] = s.visualizerEnabled
        m["visualizerIntensity"] = s.visualizerIntensity
        m["pauseBehavior"] = s.pauseBehavior
        m["pauseGraceSec"] = s.pauseGraceSec
        m["lyricsProvider"] = s.lyricsProvider
        m["lyricsFontPercent"] = s.lyricsFontPercent
        m["lyricsSpacingPercent"] = s.lyricsSpacingPercent
        m["lyricsHighlight"] = s.lyricsHighlight
        m["lyricsOffsetMs"] = s.lyricsOffsetMs
        m["trackOffsets"] = s.trackOffsets

        m["recordUseOs"] = s.recordUseOs
        m["recordManualEnabled"] = s.recordManualEnabled
        m["recordShowDuration"] = s.recordShowDuration
        m["recordResetOnStop"] = s.recordResetOnStop
        m["recQuality"] = s.recQuality
        m["recFps"] = s.recFps
        m["recMic"] = s.recMic
        m["recCountdownSec"] = s.recCountdownSec
        m["recHidePill"] = s.recHidePill

        m["lsAutoAdapt"] = s.lsAutoAdapt
        m["lsCompact"] = s.lsCompact
        m["lsIndependent"] = s.lsIndependent
        m["lsTopOffsetDp"] = s.lsTopOffsetDp
        m["lsXOffsetDp"] = s.lsXOffsetDp
        m["lsWidthDp"] = s.lsWidthDp
        m["hideInLandscapeFullscreen"] = s.hideInLandscapeFullscreen

        m["clockFormat"] = s.clockFormat
        m["timerPresetsMin"] = s.timerPresetsMin
        m["volumeInMusicCard"] = s.volumeInMusicCard
        m["weatherCity"] = s.weatherCity
        m["weatherUseLocation"] = s.weatherUseLocation
        m["weatherUnit"] = s.weatherUnit
        m["weatherRefreshMin"] = s.weatherRefreshMin
        m["shortcutApps"] = s.shortcutApps

        m["idleInfo"] = s.idleInfo
        m["panelShowDate"] = s.panelShowDate
        m["hiddenTiles"] = s.hiddenTiles.sorted()
        m["flashShowInPill"] = s.flashShowInPill
        m["flashStrengthPercent"] = s.flashStrengthPercent
        m["floatingTools"] = s.floatingTools
        m["floatingExternal"] = s.floatingExternal
        m["floatingWidthPct"] = s.floatingWidthPct
        m["floatingHeightPct"] = s.floatingHeightPct
        m["whatsNewSeen"] = s.whatsNewSeen
        return MiniJson.stringify(m)
    }

    /** Never throws. Unknown/invalid fields fall back to defaults, numbers are clamped. */
    fun decode(json: String?): NovaSettings {
        val root = MiniJson.parseOrNull(json).asJsonMap() ?: return NovaSettings()
        val d = NovaSettings()
        fun int(key: String, def: Int, min: Int, max: Int): Int = (root.jsonInt(key) ?: def).coerceIn(min, max)
        fun bool(key: String, def: Boolean): Boolean = root.jsonBool(key) ?: def
        fun strSet(key: String): Set<String> =
            root[key].asJsonList()?.mapNotNull { it as? String }?.filter { it.isNotBlank() && it.length <= 200 }?.toSet() ?: emptySet()
        val peek = root.jsonInt("peekDurationSec")
        val peekSec = if (peek != null && peek in NovaSettings.PEEK_DURATIONS_SEC) peek else d.peekDurationSec

        val offsets = LinkedHashMap<String, Int>()
        root["trackOffsets"].asJsonMap()?.let { map ->
            for ((k, v) in map) {
                if (offsets.size >= NovaSettings.MAX_TRACK_OFFSETS) break
                val n = (v as? Long)?.toInt() ?: (v as? Double)?.toInt() ?: continue
                if (k.isNotBlank() && k.length <= 300) {
                    offsets[k] = snapOffset(n)
                }
            }
        }

        val presets = root["timerPresetsMin"].asJsonList()
            ?.mapNotNull { (it as? Long)?.toInt() ?: (it as? Double)?.toInt() }
            ?.map { it.coerceIn(1, 180) }
            ?.take(4)
            ?.takeIf { it.isNotEmpty() }
            ?: d.timerPresetsMin

        val shortcuts = root["shortcutApps"].asJsonList()
            ?.mapNotNull { it as? String }
            ?.filter { it.isNotBlank() && it.length <= 200 }
            ?.distinct()
            ?.take(NovaSettings.MAX_SHORTCUTS)
            ?: emptyList()

        val refresh = int("weatherRefreshMin", d.weatherRefreshMin, 15, 720)

        // Files written by version 1 carry "schema":1. A missing key means "current" (hand-written JSON, tests).
        val fromV1 = (root.jsonInt("schema") ?: SCHEMA) < 2
        val privacy = root.enumOf("privacy", d.privacy).let { stored ->
            if (fromV1 && stored == PreviewPrivacy.SUMMARY) PreviewPrivacy.FULL else stored
        }
        val tiles = strSet("hiddenTiles").filter { it in TileKeys.ALL }.toMutableSet()
        if (fromV1 && root.jsonBool("flashShortcut") == false) tiles.add(TileKeys.FLASH)
        val unit = if (fromV1) {
            if (root.jsonBool("weatherFahrenheit") == true) TempUnit.FAHRENHEIT else TempUnit.AUTO
        } else {
            root.enumOf("weatherUnit", d.weatherUnit)
        }
        val fps = root.jsonInt("recFps")?.takeIf { it in NovaSettings.REC_FPS_CHOICES } ?: d.recFps
        val countdown = root.jsonInt("recCountdownSec")?.takeIf { it in NovaSettings.REC_COUNTDOWN_CHOICES } ?: d.recCountdownSec

        return NovaSettings(
            islandEnabled = bool("islandEnabled", d.islandEnabled),
            restoreOnLaunch = bool("restoreOnLaunch", d.restoreOnLaunch),
            autoStartOnBoot = bool("autoStartOnBoot", d.autoStartOnBoot),
            onboardingDone = bool("onboardingDone", d.onboardingDone),

            widthDp = int("widthDp", d.widthDp, 120, 260),
            heightDp = int("heightDp", d.heightDp, 30, 52),
            topOffsetDp = int("topOffsetDp", d.topOffsetDp, -24, 160),
            xOffsetDp = int("xOffsetDp", d.xOffsetDp, -300, 300),
            cornerPercent = int("cornerPercent", d.cornerPercent, 50, 100),
            borderIntensity = int("borderIntensity", d.borderIntensity, 0, 100),
            artworkSizeDp = int("artworkSizeDp", d.artworkSizeDp, 40, 72),
            fontScalePercent = int("fontScalePercent", d.fontScalePercent, 80, 130),
            opacityPercent = int("opacityPercent", d.opacityPercent, 70, 100),
            accentIntensity = int("accentIntensity", d.accentIntensity, 0, 100),
            haptics = bool("haptics", d.haptics),

            tapToExpand = bool("tapToExpand", d.tapToExpand),
            longPressReposition = bool("longPressReposition", d.longPressReposition),
            swipeGestures = bool("swipeGestures", d.swipeGestures),
            idleTimeoutSec = int("idleTimeoutSec", d.idleTimeoutSec, 2, 60),
            showMinimalIdlePill = bool("showMinimalIdlePill", d.showMinimalIdlePill),
            hideAfterMediaStops = bool("hideAfterMediaStops", d.hideAfterMediaStops),
            hideInFullscreen = bool("hideInFullscreen", d.hideInFullscreen),
            hideWithKeyboard = bool("hideWithKeyboard", d.hideWithKeyboard),
            hiddenApps = strSet("hiddenApps"),
            reduceMotionOnBatterySaver = bool("reduceMotionOnBatterySaver", d.reduceMotionOnBatterySaver),
            reducedMotion = bool("reducedMotion", d.reducedMotion),

            peekEnabled = bool("peekEnabled", d.peekEnabled),
            peekDurationSec = peekSec,
            notifMode = root.enumOf("notifMode", d.notifMode),
            allowedApps = strSet("allowedApps"),
            mutedApps = strSet("mutedApps"),
            mutedCategories = strSet("mutedCategories"),
            privacy = privacy,
            hideSensitiveCategories = if (fromV1) false else bool("hideSensitiveCategories", d.hideSensitiveCategories),
            queueBehavior = root.enumOf("queueBehavior", d.queueBehavior),
            peekReply = bool("peekReply", d.peekReply),

            preferredPlayer = (root.jsonString("preferredPlayer") ?: "").take(200),
            autoOpenLyrics = bool("autoOpenLyrics", d.autoOpenLyrics),
            showArtwork = bool("showArtwork", d.showArtwork),
            showTitleArtist = bool("showTitleArtist", d.showTitleArtist),
            showProgress = bool("showProgress", d.showProgress),
            visualizerEnabled = bool("visualizerEnabled", d.visualizerEnabled),
            visualizerIntensity = int("visualizerIntensity", d.visualizerIntensity, 10, 100),
            pauseBehavior = root.enumOf("pauseBehavior", d.pauseBehavior),
            pauseGraceSec = int("pauseGraceSec", d.pauseGraceSec, 2, 120),
            lyricsProvider = root.enumOf("lyricsProvider", d.lyricsProvider),
            lyricsFontPercent = int("lyricsFontPercent", d.lyricsFontPercent, 80, 140),
            lyricsSpacingPercent = int("lyricsSpacingPercent", d.lyricsSpacingPercent, 80, 140),
            lyricsHighlight = root.enumOf("lyricsHighlight", d.lyricsHighlight),
            lyricsOffsetMs = snapOffset(int("lyricsOffsetMs", d.lyricsOffsetMs, NovaSettings.OFFSET_MIN_MS, NovaSettings.OFFSET_MAX_MS)),
            trackOffsets = offsets,

            recordUseOs = bool("recordUseOs", d.recordUseOs),
            recordManualEnabled = if (fromV1) false else bool("recordManualEnabled", d.recordManualEnabled),
            recordShowDuration = bool("recordShowDuration", d.recordShowDuration),
            recordResetOnStop = bool("recordResetOnStop", d.recordResetOnStop),
            recQuality = root.enumOf("recQuality", d.recQuality),
            recFps = fps,
            recMic = bool("recMic", d.recMic),
            recCountdownSec = countdown,
            recHidePill = bool("recHidePill", d.recHidePill),

            lsAutoAdapt = bool("lsAutoAdapt", d.lsAutoAdapt),
            lsCompact = bool("lsCompact", d.lsCompact),
            lsIndependent = bool("lsIndependent", d.lsIndependent),
            lsTopOffsetDp = int("lsTopOffsetDp", d.lsTopOffsetDp, -24, 160),
            lsXOffsetDp = int("lsXOffsetDp", d.lsXOffsetDp, -400, 400),
            lsWidthDp = int("lsWidthDp", d.lsWidthDp, 120, 320),
            hideInLandscapeFullscreen = bool("hideInLandscapeFullscreen", d.hideInLandscapeFullscreen),

            clockFormat = root.enumOf("clockFormat", d.clockFormat),
            timerPresetsMin = presets,
            volumeInMusicCard = bool("volumeInMusicCard", d.volumeInMusicCard),
            weatherCity = (root.jsonString("weatherCity") ?: "").trim().take(80),
            weatherUseLocation = bool("weatherUseLocation", d.weatherUseLocation),
            weatherUnit = unit,
            weatherRefreshMin = refresh,
            shortcutApps = shortcuts,

            idleInfo = root.enumOf("idleInfo", d.idleInfo),
            panelShowDate = bool("panelShowDate", d.panelShowDate),
            hiddenTiles = tiles,
            flashShowInPill = bool("flashShowInPill", d.flashShowInPill),
            flashStrengthPercent = int("flashStrengthPercent", d.flashStrengthPercent, 10, 100),
            floatingTools = bool("floatingTools", d.floatingTools),
            floatingExternal = bool("floatingExternal", d.floatingExternal),
            floatingWidthPct = int("floatingWidthPct", d.floatingWidthPct, 40, 95),
            floatingHeightPct = int("floatingHeightPct", d.floatingHeightPct, 30, 90),
            whatsNewSeen = int("whatsNewSeen", d.whatsNewSeen, 0, 1000),
        )
    }

    private inline fun <reified E : Enum<E>> Map<String, Any?>.enumOf(key: String, def: E): E {
        val name = this.jsonString(key) ?: return def
        return enumValues<E>().firstOrNull { it.name == name } ?: def
    }

    /** Snap to the 50 ms grid used by the calibration control. */
    fun snapOffset(value: Int): Int {
        val clamped = value.coerceIn(NovaSettings.OFFSET_MIN_MS, NovaSettings.OFFSET_MAX_MS)
        val step = NovaSettings.OFFSET_STEP_MS
        val q = Math.floorDiv(clamped + step / 2, step)
        return (q * step).coerceIn(NovaSettings.OFFSET_MIN_MS, NovaSettings.OFFSET_MAX_MS)
    }
}
