package app.novapill.android.core

/** What one flash unit (one camera id that owns a flash) last reported to the app. */
enum class UnitTorch { UNKNOWN, OFF, ON, BUSY }

/**
 * What the UI is allowed to show. It is derived only from reports of the system, never from a button
 * press and never from a default value: until the system has reported, the answer is UNKNOWN, not On.
 */
enum class TorchStatus { NO_FLASH, UNKNOWN, OFF, ON, BUSY }

/** A command was sent to the system and the system has not confirmed it yet. */
enum class TorchPending { NONE, TO_ON, TO_OFF }

/**
 * Per-camera torch bookkeeping. The platform reports torch state per camera id, and many phones expose
 * more than one id with a flash, so a single "is it on" boolean is not enough:
 *  - any unit reporting ON means the torch is on;
 *  - a unit that becomes unavailable (the camera was opened) has already been switched off by the system
 *    before that report arrives, so BUSY replaces whatever was there, ON included;
 *  - nothing is ON until a report says so.
 */
class TorchTracker {
    private val units = LinkedHashMap<String, UnitTorch>()

    val ids: List<String> get() = units.keys.toList()

    fun reset() {
        units.clear()
    }

    /** Registers an id as a flash unit without claiming anything about its state. */
    fun track(id: String) {
        if (!units.containsKey(id)) units[id] = UnitTorch.UNKNOWN
    }

    fun onChanged(id: String, enabled: Boolean) {
        units[id] = if (enabled) UnitTorch.ON else UnitTorch.OFF
    }

    fun onUnavailable(id: String) {
        units[id] = UnitTorch.BUSY
    }

    fun state(id: String): UnitTorch = units[id] ?: UnitTorch.UNKNOWN

    fun idsOn(): List<String> = units.entries.filter { it.value == UnitTorch.ON }.map { it.key }

    /** Aggregate over all tracked units. An empty tracker is UNKNOWN; "no flash" is decided by the caller. */
    fun status(): TorchStatus {
        if (units.isEmpty()) return TorchStatus.UNKNOWN
        val values = units.values
        return when {
            values.any { it == UnitTorch.ON } -> TorchStatus.ON
            values.any { it == UnitTorch.OFF } -> TorchStatus.OFF
            values.any { it == UnitTorch.BUSY } -> TorchStatus.BUSY
            else -> TorchStatus.UNKNOWN
        }
    }

    /** The unit to switch on: the first in [order] that reported OFF, else the first that is still unknown. */
    fun pickForOn(order: List<String>): String? {
        for (id in order) if (state(id) == UnitTorch.OFF) return id
        for (id in order) if (state(id) == UnitTorch.UNKNOWN) return id
        return null
    }
}

object TorchConfirm {
    /** True when a report proves that the command in [pending] has taken effect. */
    fun confirmed(pending: TorchPending, status: TorchStatus): Boolean = when (pending) {
        TorchPending.NONE -> false
        TorchPending.TO_ON -> status == TorchStatus.ON
        TorchPending.TO_OFF -> status == TorchStatus.OFF || status == TorchStatus.BUSY
    }

    /** Maps a 1..100 brightness percentage to a platform strength level 1..[maxLevel]. */
    fun levelFor(percent: Int, maxLevel: Int): Int {
        if (maxLevel <= 1) return 1
        val raw = Math.round(maxLevel * (percent.coerceIn(1, 100) / 100.0)).toInt()
        return raw.coerceIn(1, maxLevel)
    }
}
