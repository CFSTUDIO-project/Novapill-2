package app.novapill.android.record

import android.content.Context
import android.content.Intent
import app.novapill.android.core.RecError
import app.novapill.android.core.RecPhase
import app.novapill.android.core.RecorderMachine
import app.novapill.android.core.RecorderUi
import app.novapill.android.util.Diagnostics
import app.novapill.android.util.IntentLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The one place that knows what the screen recorder is doing. The consent activity, the foreground service
 * and the island all talk to it; every change goes through the pure [RecorderMachine], so a late callback
 * cannot leave the UI in an impossible state. In memory only: a restart of the process starts at Idle.
 */
class RecorderHub(
    private val context: Context,
    private val scope: CoroutineScope,
    private val diagnostics: Diagnostics,
) {
    private val _state = MutableStateFlow(RecorderUi())
    val state: StateFlow<RecorderUi> = _state.asStateFlow()

    private var dismissJob: Job? = null

    fun update(transform: (RecorderUi) -> RecorderUi) {
        _state.update(transform)
    }

    /** Opens the system consent dialog. False when a recording is already in progress or the screen cannot open. */
    fun requestStart(): Boolean {
        if (_state.value.busy) return false
        dismissJob?.cancel()
        update { RecorderMachine.requested(it) }
        val opened = IntentLauncher.start(context, Intent(context, RecordConsentActivity::class.java))
        if (!opened) update { RecorderMachine.failed(it, RecError.START_FAILED) }
        return opened
    }

    /** Stop pressed in the island: the service finishes the file. Before the encoder started nothing is saved. */
    fun requestStop() {
        val phase = _state.value.phase
        if (phase != RecPhase.RECORDING && phase != RecPhase.PREPARING) return
        if (!RecorderService.stop(context)) {
            diagnostics.note("recorder", "stop request could not reach the service")
            update { RecorderMachine.failed(it, RecError.START_FAILED) }
            autoDismissLater()
        }
    }

    fun dismiss() {
        dismissJob?.cancel()
        update { RecorderMachine.dismissed(it) }
    }

    /** A saved or failed result stays visible for a while, then the card leaves on its own. */
    fun autoDismissLater() {
        dismissJob?.cancel()
        dismissJob = scope.launch {
            delay(AUTO_DISMISS_MS)
            update { RecorderMachine.dismissed(it) }
        }
    }

    private companion object {
        const val AUTO_DISMISS_MS = 12_000L
    }
}
