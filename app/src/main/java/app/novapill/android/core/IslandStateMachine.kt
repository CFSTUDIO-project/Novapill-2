package app.novapill.android.core

import app.novapill.android.settings.PauseBehavior

enum class CardKind { MEDIA, TIMER, STOPWATCH, RECORD, TOOLS }

enum class IslandPhase {
    OFF,
    IDLE,
    MEDIA_PLAYING,
    MEDIA_PAUSED,
    MEDIA_BUFFERING,
    NOTIFICATION_PEEK,
    RECORDING_INDICATOR,
    TIMER_RUNNING,
    TIMER_PAUSED,
    TIMER_FINISHED,
    UTILITY_ACTIVE,
}

enum class MediaPlayState { NONE, PLAYING, PAUSED, BUFFERING, STOPPED }

enum class TimerPhase { NONE, RUNNING, PAUSED, FINISHED }

enum class StopwatchPhase { NONE, RUNNING, PAUSED }

/** Everything the reducer needs. All durations are plain milliseconds so tests need no clock. */
data class StateInputs(
    val serviceRunning: Boolean,
    val media: MediaPlayState = MediaPlayState.NONE,
    /** Time since the media became paused/stopped (0 while playing). */
    val mediaInactiveForMs: Long = 0,
    val peekActive: Boolean = false,
    val recordingActive: Boolean = false,
    val timer: TimerPhase = TimerPhase.NONE,
    val stopwatch: StopwatchPhase = StopwatchPhase.NONE,
    val flashOn: Boolean = false,
    /** The user explicitly opened the tools card (for example from the idle pill). */
    val toolsRequested: Boolean = false,
    /** Time since nothing was worth showing. */
    val idleForMs: Long = 0,
    val idleTimeoutMs: Long = 6_000,
    val pausedGraceMs: Long = 8_000,
    val pauseBehavior: PauseBehavior = PauseBehavior.COLLAPSE,
    val hideAfterMediaStops: Boolean = true,
    val showMinimalIdlePill: Boolean = false,
)

data class StateOutput(
    val phase: IslandPhase,
    val visible: Boolean,
    /** Swipe order. TOOLS is appended whenever the island is visible and at least one other card exists. */
    val cards: List<CardKind>,
    /** The card the island shows by default (the peek, when active, is layered on top of it). */
    val primary: CardKind?,
    /** Other running things summarised as small badges on a compact pill. */
    val badges: List<CardKind>,
    /** True once a paused/stopped media card has outlived its grace period. */
    val graceExpired: Boolean,
    /** When the reducer must be run again even if no input changes, or null. */
    val recheckInMs: Long?,
)

/**
 * Pure presentation state machine: inputs in, phase + visible cards out.
 * No clocks, no Android: the coordinator feeds it elapsed times and re-runs it at [StateOutput.recheckInMs].
 */
object IslandStateMachine {
    private val ORDER = listOf(CardKind.MEDIA, CardKind.TIMER, CardKind.RECORD, CardKind.STOPWATCH)

    fun resolve(i: StateInputs): StateOutput {
        if (!i.serviceRunning) {
            return StateOutput(IslandPhase.OFF, false, emptyList(), null, emptyList(), false, null)
        }

        val graceLeft = i.pausedGraceMs - i.mediaInactiveForMs
        val inactiveMedia = i.media == MediaPlayState.PAUSED || i.media == MediaPlayState.STOPPED
        val mediaCard = when (i.media) {
            MediaPlayState.NONE -> false
            MediaPlayState.PLAYING, MediaPlayState.BUFFERING -> true
            MediaPlayState.PAUSED -> when (i.pauseBehavior) {
                PauseBehavior.HIDE -> graceLeft > 0
                else -> true
            }
            MediaPlayState.STOPPED -> if (i.hideAfterMediaStops) false else when (i.pauseBehavior) {
                PauseBehavior.HIDE -> graceLeft > 0
                else -> true
            }
        }
        val graceExpired = inactiveMedia && mediaCard && graceLeft <= 0

        val timerCard = i.timer != TimerPhase.NONE
        val stopwatchCard = i.stopwatch != StopwatchPhase.NONE
        val recordCard = i.recordingActive
        val toolsCard = i.flashOn || i.toolsRequested

        val active = ArrayList<CardKind>()
        for (kind in ORDER) {
            val on = when (kind) {
                CardKind.MEDIA -> mediaCard
                CardKind.TIMER -> timerCard
                CardKind.RECORD -> recordCard
                CardKind.STOPWATCH -> stopwatchCard
                CardKind.TOOLS -> false
            }
            if (on) active.add(kind)
        }

        val primary: CardKind? = when {
            i.timer == TimerPhase.FINISHED -> CardKind.TIMER
            mediaCard -> CardKind.MEDIA
            timerCard -> CardKind.TIMER
            recordCard -> CardKind.RECORD
            stopwatchCard -> CardKind.STOPWATCH
            toolsCard -> CardKind.TOOLS
            else -> null
        }

        val anyActive = active.isNotEmpty() || toolsCard

        val idleLeft = i.idleTimeoutMs - i.idleForMs
        val visible = when {
            i.peekActive -> true
            anyActive -> true
            i.showMinimalIdlePill -> true
            else -> idleLeft > 0
        }

        val cards = ArrayList<CardKind>(active)
        if (visible && (active.isNotEmpty() || toolsCard || i.showMinimalIdlePill)) cards.add(CardKind.TOOLS)

        val phase = when {
            i.peekActive -> IslandPhase.NOTIFICATION_PEEK
            primary == null -> IslandPhase.IDLE
            primary == CardKind.MEDIA -> when (i.media) {
                MediaPlayState.PLAYING -> IslandPhase.MEDIA_PLAYING
                MediaPlayState.BUFFERING -> IslandPhase.MEDIA_BUFFERING
                else -> IslandPhase.MEDIA_PAUSED
            }
            primary == CardKind.TIMER -> when (i.timer) {
                TimerPhase.RUNNING -> IslandPhase.TIMER_RUNNING
                TimerPhase.FINISHED -> IslandPhase.TIMER_FINISHED
                else -> IslandPhase.TIMER_PAUSED
            }
            primary == CardKind.RECORD -> IslandPhase.RECORDING_INDICATOR
            else -> IslandPhase.UTILITY_ACTIVE
        }

        val badges = ArrayList<CardKind>()
        if (primary != CardKind.RECORD && recordCard) badges.add(CardKind.RECORD)
        if (primary != CardKind.TIMER && timerCard && i.timer != TimerPhase.FINISHED) badges.add(CardKind.TIMER)

        var recheck: Long? = null
        fun consider(ms: Long) {
            if (ms > 0 && (recheck == null || ms < recheck!!)) recheck = ms
        }
        if (!anyActive && !i.showMinimalIdlePill && !i.peekActive) consider(idleLeft)
        if (inactiveMedia && graceLeft > 0) {
            if (i.pauseBehavior == PauseBehavior.HIDE || i.pauseBehavior == PauseBehavior.COLLAPSE) consider(graceLeft)
        }

        return StateOutput(phase, visible, cards, primary, badges, graceExpired, recheck)
    }
}
