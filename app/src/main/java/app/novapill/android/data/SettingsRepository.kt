package app.novapill.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.novapill.android.settings.NovaSettings
import app.novapill.android.settings.NovaSettingsCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val Context.novaDataStore: DataStore<Preferences> by preferencesDataStore(name = "nova_settings")

/**
 * Settings live in one JSON string inside DataStore Preferences. After the first read the in-memory
 * state is the source of truth (this process is the only writer), writes are debounced and coalesced,
 * and decoding can never throw, so a damaged file simply falls back to defaults.
 */
class SettingsRepository(context: Context, private val scope: CoroutineScope) {
    private val store: DataStore<Preferences> = context.applicationContext.novaDataStore
    private val key = stringPreferencesKey("settings_json")

    private val _settings = MutableStateFlow(NovaSettings())
    val settings: StateFlow<NovaSettings> = _settings.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    private var persistJob: Job? = null

    init {
        scope.launch {
            try {
                val prefs = store.data.first()
                _settings.value = NovaSettingsCodec.decode(prefs[key])
            } catch (e: Exception) {
                _loadError.value = e.javaClass.simpleName
            } finally {
                _loaded.value = true
            }
        }
    }

    /** Applies [transform] atomically and persists the result shortly after. */
    fun update(transform: (NovaSettings) -> NovaSettings) {
        if (_loaded.value) {
            apply(transform)
        } else {
            scope.launch {
                _loaded.first { it }
                apply(transform)
            }
        }
    }

    /** Back to defaults, but the app keeps its onboarding flag and island on/off state. */
    fun resetAll() {
        update { cur -> NovaSettings(islandEnabled = cur.islandEnabled, onboardingDone = cur.onboardingDone) }
    }

    suspend fun flush() {
        persistJob?.cancel()
        store.edit { it[key] = NovaSettingsCodec.encode(_settings.value) }
    }

    private fun apply(transform: (NovaSettings) -> NovaSettings) {
        _settings.update(transform)
        persist()
    }

    private fun persist() {
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(150)
            try {
                store.edit { it[key] = NovaSettingsCodec.encode(_settings.value) }
            } catch (e: Exception) {
                _loadError.value = e.javaClass.simpleName
            }
        }
    }
}
