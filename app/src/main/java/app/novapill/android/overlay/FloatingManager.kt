package app.novapill.android.overlay

import android.content.Context
import androidx.compose.runtime.Composable
import app.novapill.android.core.WindowBox
import app.novapill.android.overlay.ui.FloatingCalculatorWindow
import app.novapill.android.overlay.ui.FloatingBrowserWindow
import app.novapill.android.overlay.ui.FloatingLauncherWindow
import app.novapill.android.overlay.ui.FloatingNotesWindow
import app.novapill.android.overlay.ui.GameHudWindow
import app.novapill.android.settings.NovaSettings
import app.novapill.android.util.Diagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Floating tool windows that live inside NOVA PILL's own overlay permission (no system freeform needed): the
 * calculator, notes, a small browser, an app launcher and the game HUD. At most one window of each kind is
 * open, and a window that is closed and opened again comes back where it was. Everything runs on the main thread.
 */
class FloatingManager(private val app: Context, private val diagnostics: Diagnostics) {
    private val windows = HashMap<FloatingKind, FloatingWindow>()
    private val lastBoxes = HashMap<FloatingKind, WindowBox>()

    private val _open = MutableStateFlow<Set<FloatingKind>>(emptySet())
    val open: StateFlow<Set<FloatingKind>> = _open.asStateFlow()

    private val _calculatorOpen = MutableStateFlow(false)
    val calculatorOpen: StateFlow<Boolean> = _calculatorOpen.asStateFlow()

    fun isOpen(kind: FloatingKind): Boolean = windows.containsKey(kind)

    /** Opens the window, or closes it when it is already open. Returns false when Android refuses the window. */
    fun toggle(kind: FloatingKind, settings: NovaSettings): Boolean {
        val existing = windows[kind]
        if (existing != null) {
            existing.close()
            return true
        }
        return open(kind, settings)
    }

    fun toggleCalculator(settings: NovaSettings): Boolean = toggle(FloatingKind.CALCULATOR, settings)

    fun open(kind: FloatingKind, settings: NovaSettings): Boolean {
        if (windows.containsKey(kind)) return true
        val window = FloatingWindow(app, diagnostics, specFor(kind, settings), lastBoxes[kind], ::gone, contentFor(kind))
        if (!window.open()) return false
        windows[kind] = window
        publish()
        return true
    }

    fun close(kind: FloatingKind) {
        windows[kind]?.close()
    }

    fun closeAll() {
        for (window in windows.values.toList()) window.close()
    }

    /** A rotation or fold changed the screen: pull every window back inside. */
    fun onConfigurationChanged() {
        for (window in windows.values) window.reclamp()
    }

    private fun gone(kind: FloatingKind, box: WindowBox) {
        windows.remove(kind)
        lastBoxes[kind] = box
        publish()
    }

    private fun publish() {
        _open.value = windows.keys.toSet()
        _calculatorOpen.value = windows.containsKey(FloatingKind.CALCULATOR)
    }

    private fun specFor(kind: FloatingKind, s: NovaSettings): FloatingSpec = when (kind) {
        FloatingKind.CALCULATOR -> FloatingSpec(kind, "Calculator", false, 232, 330, s.floatingWidthPct, s.floatingHeightPct)
        FloatingKind.NOTES -> FloatingSpec(kind, "Notes", true, 260, 320, 86, 66, dragStripDp = 48, buttonsZoneDp = 150)
        FloatingKind.BROWSER -> FloatingSpec(kind, "Browser", true, 280, 380, 94, 76, dragStripDp = 44, buttonsZoneDp = 56)
        FloatingKind.LAUNCHER -> FloatingSpec(kind, "Apps", false, 260, 340, 80, 64, dragStripDp = 44, buttonsZoneDp = 56)
        FloatingKind.GAME_HUD -> FloatingSpec(kind, "Game HUD", false, 148, 86, 1, 1, dragStripDp = -1, buttonsZoneDp = 0, resizable = false, startAtDp = 12 to 96)
    }

    private fun contentFor(kind: FloatingKind): @Composable (FloatingHandle) -> Unit = when (kind) {
        FloatingKind.CALCULATOR -> { h -> FloatingCalculatorWindow(h) }
        FloatingKind.NOTES -> { h -> FloatingNotesWindow(h) }
        FloatingKind.BROWSER -> { h -> FloatingBrowserWindow(h) }
        FloatingKind.LAUNCHER -> { h -> FloatingLauncherWindow(h) }
        FloatingKind.GAME_HUD -> { h -> GameHudWindow(h) }
    }
}
