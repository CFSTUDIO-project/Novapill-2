package app.novapill.android.record

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import app.novapill.android.NovaGraph
import app.novapill.android.core.RecError
import app.novapill.android.core.RecorderMachine

/**
 * Invisible helper that shows the system's screen-capture consent dialog (Android requires it for every
 * recording session) and, when the user agrees, hands the result to [RecorderService]. When the optional
 * microphone is on and not yet allowed, it asks for that first.
 */
class RecordConsentActivity : ComponentActivity() {
    private var withMic = false

    private val micRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        askForCapture(granted)
    }

    private val captureRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        onCaptureResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            // Recreated while a dialog is open: the pending launcher delivers its result to this instance.
            withMic = savedInstanceState.getBoolean(STATE_MIC, false)
            return
        }
        val wantsMic = NovaGraph.settings.settings.value.recMic
        val hasMic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        when {
            !wantsMic -> askForCapture(false)
            hasMic -> askForCapture(true)
            else -> micRequest.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_MIC, withMic)
    }

    private fun askForCapture(micAllowed: Boolean) {
        withMic = micAllowed
        val manager = getSystemService(MediaProjectionManager::class.java)
        if (manager == null) {
            NovaGraph.recorder.update { RecorderMachine.failed(it, RecError.START_FAILED) }
            NovaGraph.recorder.autoDismissLater()
            finish()
            return
        }
        // Android 14+ lets the user pick a single app; the recorder only supports the whole screen.
        val intent = if (Build.VERSION.SDK_INT >= 34) {
            manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            manager.createScreenCaptureIntent()
        }
        captureRequest.launch(intent)
    }

    private fun onCaptureResult(code: Int, data: Intent?) {
        val hub = NovaGraph.recorder
        if (code == Activity.RESULT_OK && data != null) {
            if (!RecorderService.start(this, code, data, withMic)) {
                hub.update { RecorderMachine.failed(it, RecError.START_FAILED) }
                hub.autoDismissLater()
            }
        } else {
            hub.update { RecorderMachine.consentDenied(it) }
            hub.autoDismissLater()
        }
        finish()
    }

    private companion object {
        const val STATE_MIC = "mic"
    }
}
