package app.novapill.android

import android.app.Application
import android.content.Context
import app.novapill.android.data.SettingsRepository
import app.novapill.android.lyrics.LyricsRepository
import app.novapill.android.media.MediaMonitor
import app.novapill.android.notif.NotificationHub
import app.novapill.android.overlay.FloatingManager
import app.novapill.android.record.RecorderHub
import app.novapill.android.system.SystemFlags
import app.novapill.android.util.AppInfoCache
import app.novapill.android.util.BatteryMonitor
import app.novapill.android.util.ClockTools
import app.novapill.android.util.DeviceStatusReader
import app.novapill.android.util.Diagnostics
import app.novapill.android.util.Flashlight
import app.novapill.android.util.ForegroundAppMonitor
import app.novapill.android.util.RecordIndicator
import app.novapill.android.util.VolumeController
import app.novapill.android.util.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

enum class ServiceStatus { STOPPED, RUNNING, FAILED }

/**
 * Tiny service locator. One instance per process, created in [NovaApp.onCreate] before any activity,
 * service or receiver runs. Components only hold references while they are started; everything that
 * registers callbacks has an explicit start()/stop() driven by the island service.
 */
object NovaGraph {
    private lateinit var app: Application

    val context: Context get() = app

    /** Background work (network, disk). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Work that must touch the main thread (media callbacks, timers). */
    val mainScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var diagnostics: Diagnostics
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var http: OkHttpClient
        private set
    lateinit var appInfo: AppInfoCache
        private set
    lateinit var system: SystemFlags
        private set
    lateinit var notifications: NotificationHub
        private set
    lateinit var media: MediaMonitor
        private set
    lateinit var lyrics: LyricsRepository
        private set
    lateinit var flashlight: Flashlight
        private set
    lateinit var volume: VolumeController
        private set
    lateinit var battery: BatteryMonitor
        private set
    lateinit var weather: WeatherRepository
        private set
    lateinit var clocks: ClockTools
        private set
    lateinit var record: RecordIndicator
        private set
    lateinit var foreground: ForegroundAppMonitor
        private set
    lateinit var recorder: RecorderHub
        private set
    lateinit var floating: FloatingManager
        private set
    lateinit var deviceStatus: DeviceStatusReader
        private set

    private val _serviceStatus = MutableStateFlow(ServiceStatus.STOPPED)
    val serviceStatus: StateFlow<ServiceStatus> = _serviceStatus.asStateFlow()
    private val _serviceError = MutableStateFlow<String?>(null)
    val serviceError: StateFlow<String?> = _serviceError.asStateFlow()

    fun setServiceStatus(status: ServiceStatus, error: String? = null) {
        _serviceStatus.value = status
        _serviceError.value = error
    }

    fun init(application: Application) {
        app = application
        diagnostics = Diagnostics()
        http = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .followSslRedirects(false)
            .build()
        settings = SettingsRepository(application, appScope)
        appInfo = AppInfoCache(application)
        system = SystemFlags(application)
        notifications = NotificationHub(application, settings, appInfo, system)
        media = MediaMonitor(application, settings, appInfo, http, diagnostics)
        lyrics = LyricsRepository(application, settings, http, diagnostics, appScope)
        flashlight = Flashlight(application, diagnostics)
        volume = VolumeController(application)
        battery = BatteryMonitor(application)
        weather = WeatherRepository(application, settings, http, appScope, diagnostics)
        clocks = ClockTools(application, mainScope, diagnostics)
        record = RecordIndicator(application, settings, diagnostics)
        foreground = ForegroundAppMonitor(application, settings, system)
        recorder = RecorderHub(application, mainScope, diagnostics)
        floating = FloatingManager(application, diagnostics)
        deviceStatus = DeviceStatusReader(application, diagnostics)
    }
}
