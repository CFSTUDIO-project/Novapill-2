package app.novapill.android.record

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.novapill.android.MainActivity
import app.novapill.android.NovaGraph
import app.novapill.android.R
import app.novapill.android.core.RecError
import app.novapill.android.core.RecNote
import app.novapill.android.core.RecPhase
import app.novapill.android.core.RecorderMachine
import app.novapill.android.core.RecordingNames
import app.novapill.android.core.RecordingProfile
import app.novapill.android.core.VideoProfile
import app.novapill.android.service.NotificationChannels
import app.novapill.android.settings.NovaSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.IOException

/**
 * Records the screen. Order matters and follows the platform rules:
 * 1. the consent dialog already ran (see [RecordConsentActivity]);
 * 2. this service enters the foreground with type mediaProjection (plus microphone only when allowed);
 * 3. only then the projection is created and its callback registered, before the virtual display exists;
 * 4. MediaRecorder + VirtualDisplay write to MediaStore (Android 10+) or an app folder (older Android).
 * Nothing is reported as recording until the encoder has really started, and an empty file is deleted.
 */
class RecorderService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val hub get() = NovaGraph.recorder

    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var recorder: MediaRecorder? = null
    private var display: VirtualDisplay? = null
    private var output: Output? = null
    private var countdownJob: Job? = null
    private var finishing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> finishRecording(byUser = true)
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        countdownJob?.cancel()
        releaseEncoder()
        releaseProjection()
        scope.cancel()
        super.onDestroy()
    }

    // ---- start ----

    private fun handleStart(intent: Intent) {
        val withMic = intent.getBooleanExtra(EXTRA_MIC, false) &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        // A foreground notification must exist within seconds of startForegroundService(): do it first.
        if (!enterForeground(withMic)) {
            fail(RecError.START_FAILED)
            return
        }
        if (projection != null || recorder != null) return // a second start while recording is ignored

        val code = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val data = readResultData(intent)
        if (code != Activity.RESULT_OK || data == null) {
            fail(RecError.START_FAILED)
            return
        }
        val settings = NovaGraph.settings.settings.value
        val manager = getSystemService(MediaProjectionManager::class.java)
        val proj = try {
            manager?.getMediaProjection(code, data)
        } catch (e: SecurityException) {
            NovaGraph.diagnostics.error("recorder", e)
            null
        } catch (e: IllegalStateException) {
            NovaGraph.diagnostics.error("recorder", e)
            null
        }
        if (proj == null) {
            fail(RecError.START_FAILED)
            return
        }
        projection = proj
        // Android 14+ requires the callback to be registered before the virtual display is created.
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                main.post { finishRecording(byUser = false) }
            }
        }
        proj.registerCallback(callback, main)
        projectionCallback = callback

        hub.update { if (it.phase == RecPhase.PREPARING) it else RecorderMachine.requested(it) }
        hub.update { RecorderMachine.consentGranted(it, settings.recCountdownSec, withMic) }
        if (settings.recCountdownSec <= 0) {
            begin(settings, withMic)
        } else {
            countdownJob = scope.launch {
                for (left in settings.recCountdownSec downTo 1) {
                    hub.update { RecorderMachine.countdown(it, left) }
                    delay(1_000L)
                }
                begin(settings, withMic)
            }
        }
    }

    private fun enterForeground(withMic: Boolean): Boolean {
        val notification = buildNotification(startedAtWallMs = null, stopping = false)
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                if (withMic && Build.VERSION.SDK_INT >= 30) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: SecurityException) {
            NovaGraph.diagnostics.error("recorder", e)
            false
        } catch (e: IllegalStateException) {
            NovaGraph.diagnostics.error("recorder", e)
            false
        }
    }

    private fun begin(settings: NovaSettings, withMic: Boolean) {
        val proj = projection ?: return
        val (width, height, dpi) = screenSize()
        var profile = RecordingProfile.compute(width, height, dpi, settings.recQuality, settings.recFps)
        var error = startEncoder(proj, profile, withMic)
        if (error != null) {
            val fallback = RecordingProfile.fallback(width, height, dpi, settings.recQuality, settings.recFps)
            if (fallback != null) {
                profile = fallback
                hub.update { RecorderMachine.withNote(it, RecNote.LOWER_QUALITY_USED) }
                error = startEncoder(proj, profile, withMic)
            }
        }
        if (error != null) {
            fail(error)
            return
        }
        hub.update { RecorderMachine.started(it, SystemClock.elapsedRealtime()) }
        notifyRecording(startedAtWallMs = System.currentTimeMillis())
    }

    /** Returns null when the encoder is running; otherwise the reason it could not start (everything is cleaned up). */
    private fun startEncoder(proj: MediaProjection, p: VideoProfile, withMic: Boolean): RecError? {
        val target = try {
            Output.create(this, RecordingNames.fileName(System.currentTimeMillis()))
        } catch (e: IOException) {
            NovaGraph.diagnostics.error("recorder", e)
            return RecError.STORAGE_FAILED
        } catch (e: SecurityException) {
            NovaGraph.diagnostics.error("recorder", e)
            return RecError.STORAGE_FAILED
        }
        val rec = createRecorder()
        try {
            // Sources must be set before the output format.
            if (withMic) rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (withMic) {
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                rec.setAudioEncodingBitRate(AUDIO_BITRATE)
                rec.setAudioSamplingRate(AUDIO_SAMPLE_RATE)
            }
            rec.setVideoSize(p.width, p.height)
            rec.setVideoFrameRate(p.fps)
            rec.setVideoEncodingBitRate(p.bitrate)
            rec.setOutputFile(target.descriptor)
            rec.prepare()
            val virtual = proj.createVirtualDisplay(
                "NOVA-PILL",
                p.width,
                p.height,
                p.dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                rec.surface,
                null,
                null,
            ) ?: throw IllegalStateException("The system did not create the virtual display")
            rec.start()
            recorder = rec
            display = virtual
            output = target
            return null
        } catch (e: IOException) {
            return abortAttempt(rec, target, e)
        } catch (e: RuntimeException) {
            // MediaRecorder reports native encoder problems as IllegalStateException / RuntimeException.
            return abortAttempt(rec, target, e)
        }
    }

    private fun abortAttempt(rec: MediaRecorder, target: Output, cause: Exception): RecError {
        NovaGraph.diagnostics.error("recorder", cause)
        try {
            rec.reset()
        } catch (e: IllegalStateException) {
            NovaGraph.diagnostics.error("recorder", e)
        }
        rec.release()
        target.closeStreams()
        target.discard(this)
        return RecError.ENCODER_FAILED
    }

    @Suppress("DEPRECATION")
    private fun createRecorder(): MediaRecorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()

    // ---- stop ----

    private fun finishRecording(byUser: Boolean) {
        if (finishing) return
        countdownJob?.cancel()
        val rec = recorder
        if (rec == null) {
            // Still counting down (or never started): nothing was recorded, so there is nothing to save.
            if (byUser) {
                hub.update { RecorderMachine.stopping(it) }
            } else {
                hub.update { RecorderMachine.failed(it, RecError.START_FAILED) }
                hub.autoDismissLater()
            }
            releaseProjection()
            leave()
            return
        }
        finishing = true
        hub.update { RecorderMachine.stopping(it) }
        notifyStopping()

        var stoppedCleanly = true
        try {
            rec.stop()
        } catch (e: RuntimeException) {
            // "stop failed": the recorder got no data (for example the projection ended at once).
            NovaGraph.diagnostics.error("recorder", e)
            stoppedCleanly = false
        }
        val target = output
        releaseEncoder()
        releaseProjection()
        target?.closeStreams()

        val saved = if (target != null && stoppedCleanly) target.publishIfValid(this) else null
        if (saved == null) target?.discard(this)
        if (!byUser) hub.update { RecorderMachine.withNote(it, RecNote.STOPPED_BY_SYSTEM) }
        if (saved != null && target != null) {
            hub.update { RecorderMachine.saved(it, target.name, saved.toString()) }
        } else {
            hub.update { RecorderMachine.failed(it, RecError.EMPTY_FILE) }
        }
        hub.autoDismissLater()
        leave()
    }

    private fun releaseEncoder() {
        try {
            display?.release()
        } catch (e: RuntimeException) {
            NovaGraph.diagnostics.error("recorder", e)
        }
        display = null
        val rec = recorder
        recorder = null
        if (rec != null) {
            try {
                rec.reset()
            } catch (e: IllegalStateException) {
                NovaGraph.diagnostics.error("recorder", e)
            }
            rec.release()
        }
        output = null
    }

    private fun releaseProjection() {
        val proj = projection ?: return
        projection = null
        projectionCallback?.let {
            try {
                proj.unregisterCallback(it)
            } catch (e: RuntimeException) {
                NovaGraph.diagnostics.error("recorder", e)
            }
        }
        projectionCallback = null
        try {
            proj.stop()
        } catch (e: RuntimeException) {
            NovaGraph.diagnostics.error("recorder", e)
        }
    }

    private fun fail(error: RecError) {
        countdownJob?.cancel()
        val target = output
        releaseEncoder()
        releaseProjection()
        target?.closeStreams()
        target?.discard(this)
        hub.update { RecorderMachine.failed(it, error) }
        hub.autoDismissLater()
        leave()
    }

    private fun leave() {
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    // ---- notification ----

    private fun notifyRecording(startedAtWallMs: Long) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification(startedAtWallMs, stopping = false))
    }

    private fun notifyStopping() {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification(null, stopping = true))
    }

    private fun buildNotification(startedAtWallMs: Long?, stopping: Boolean): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val stop = PendingIntent.getService(this, 1, Intent(this, RecorderService::class.java).setAction(ACTION_STOP), flags)
        val open = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            flags,
        )
        val text = when {
            stopping -> R.string.rec_notification_stopping
            startedAtWallMs != null -> R.string.rec_notification_recording
            else -> R.string.rec_notification_preparing
        }
        val builder = NotificationCompat.Builder(this, NotificationChannels.RECORDING)
            .setSmallIcon(R.drawable.ic_stat_nova)
            .setContentTitle(getString(R.string.rec_notification_title))
            .setContentText(getString(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (!stopping) builder.addAction(0, getString(R.string.rec_stop), stop)
        if (startedAtWallMs != null) builder.setUsesChronometer(true).setShowWhen(true).setWhen(startedAtWallMs)
        return builder.build()
    }

    // ---- helpers ----

    private fun readResultData(intent: Intent): Intent? = if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(EXTRA_DATA)
    }

    private fun screenSize(): Triple<Int, Int, Int> {
        val wm = getSystemService(WindowManager::class.java)
        val dpi = resources.configuration.densityDpi
        if (Build.VERSION.SDK_INT >= 30) {
            val bounds = wm.maximumWindowMetrics.bounds
            return Triple(bounds.width(), bounds.height(), dpi)
        }
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        return Triple(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
    }

    /** Where the video goes: a pending MediaStore row on Android 10+, a file in the app's movie folder before. */
    private class Output(
        val name: String,
        private val uri: Uri?,
        private val file: File?,
        private val pfd: ParcelFileDescriptor?,
        private val stream: FileOutputStream?,
    ) {
        val descriptor: FileDescriptor = pfd?.fileDescriptor ?: requireNotNull(stream) { "no output stream" }.fd

        fun closeStreams() {
            try {
                pfd?.close()
                stream?.close()
            } catch (e: IOException) {
                NovaGraph.diagnostics.error("recorder", e)
            }
        }

        /** Makes the video visible and checks that it has a duration. Returns its Uri, or null when it is empty. */
        fun publishIfValid(context: Context): Uri? {
            if (uri != null) {
                val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                context.contentResolver.update(uri, values, null, null)
            }
            if (!hasDuration(context)) return null
            if (file != null) {
                MediaScannerConnection.scanFile(context, arrayOf(file.path), arrayOf("video/mp4"), null)
            }
            return uri ?: Uri.fromFile(file)
        }

        fun discard(context: Context) {
            if (uri != null) {
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (e: SecurityException) {
                    NovaGraph.diagnostics.error("recorder", e)
                }
            }
            if (file != null && file.exists() && !file.delete()) {
                NovaGraph.diagnostics.note("recorder", "could not delete the empty file")
            }
        }

        private fun hasDuration(context: Context): Boolean {
            val retriever = MediaMetadataRetriever()
            return try {
                if (uri != null) retriever.setDataSource(context, uri) else retriever.setDataSource(requireNotNull(file).path)
                (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) > 0L
            } catch (e: RuntimeException) {
                NovaGraph.diagnostics.error("recorder", e)
                false
            } finally {
                retriever.release()
            }
        }

        companion object {
            fun create(context: Context, name: String): Output {
                if (Build.VERSION.SDK_INT >= 29) {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, name)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/${RecordingNames.FOLDER}")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                        ?: throw IOException("MediaStore refused the new video")
                    val pfd = resolver.openFileDescriptor(uri, "w")
                    if (pfd == null) {
                        resolver.delete(uri, null, null)
                        throw IOException("MediaStore did not open the output")
                    }
                    return Output(name, uri, null, pfd, null)
                }
                val root = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
                val dir = File(root, RecordingNames.FOLDER)
                if (!dir.exists() && !dir.mkdirs()) throw IOException("Could not create ${dir.path}")
                val file = File(dir, name)
                return Output(name, null, file, null, FileOutputStream(file))
            }
        }
    }

    companion object {
        private const val ACTION_START = "app.novapill.android.record.START"
        private const val ACTION_STOP = "app.novapill.android.record.STOP"
        private const val EXTRA_RESULT_CODE = "resultCode"
        private const val EXTRA_DATA = "data"
        private const val EXTRA_MIC = "mic"
        private const val NOTIFICATION_ID = 4107
        private const val AUDIO_BITRATE = 128_000
        private const val AUDIO_SAMPLE_RATE = 44_100

        /** Starts the foreground service with the consent result. False when Android refuses to start it. */
        fun start(context: Context, resultCode: Int, data: Intent, withMic: Boolean): Boolean {
            val intent = Intent(context, RecorderService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
                .putExtra(EXTRA_MIC, withMic)
            return try {
                ContextCompat.startForegroundService(context, intent)
                true
            } catch (e: IllegalStateException) {
                NovaGraph.diagnostics.error("recorder", e)
                false
            } catch (e: SecurityException) {
                NovaGraph.diagnostics.error("recorder", e)
                false
            }
        }

        /** Asks a running service to finish the file. False when it could not be reached. */
        fun stop(context: Context): Boolean {
            val intent = Intent(context, RecorderService::class.java).setAction(ACTION_STOP)
            return try {
                context.startService(intent) != null
            } catch (e: IllegalStateException) {
                NovaGraph.diagnostics.error("recorder", e)
                false
            } catch (e: SecurityException) {
                NovaGraph.diagnostics.error("recorder", e)
                false
            }
        }
    }
}
