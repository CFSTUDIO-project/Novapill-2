package app.novapill.android.perm

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

enum class ShizukuState { NOT_INSTALLED, NOT_RUNNING, PERMISSION_NEEDED, READY }

/**
 * Status only. No feature in NOVA PILL depends on Shizuku, so every call is guarded and a missing or
 * stopped Shizuku is a normal, non-error state.
 */
object ShizukuStatus {
    private const val PACKAGE = "moe.shizuku.privileged.api"

    fun read(context: Context): ShizukuState {
        val installed = try {
            context.packageManager.getPackageInfo(PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }
        val running = try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
        if (!running) return if (installed) ShizukuState.NOT_RUNNING else ShizukuState.NOT_INSTALLED
        return try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) ShizukuState.READY else ShizukuState.PERMISSION_NEEDED
        } catch (_: Throwable) {
            ShizukuState.PERMISSION_NEEDED
        }
    }
}
