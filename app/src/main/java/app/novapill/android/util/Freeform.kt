package app.novapill.android.util

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import kotlin.math.roundToInt

/** What happened when an app was opened with the "floating" option. Shown to the user, never hidden. */
enum class FreeformOutcome { FREEFORM_REQUESTED, OPENED_NORMALLY, FAILED }

/**
 * Asks the system to open another app in a freeform window, using only public API. Android decides: it honours
 * the launch bounds only when the device has freeform window management switched on, which most phones do not.
 * The caller therefore reports "requested", never "done".
 */
object Freeform {
    fun supported(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)

    fun launch(
        context: Context,
        intent: Intent,
        widthPct: Int,
        heightPct: Int,
        screenWidthPx: Int,
        screenHeightPx: Int,
    ): FreeformOutcome {
        if (!supported(context)) {
            return if (IntentLauncher.start(context, intent)) FreeformOutcome.OPENED_NORMALLY else FreeformOutcome.FAILED
        }
        val w = (screenWidthPx * widthPct / 100f).roundToInt()
        val h = (screenHeightPx * heightPct / 100f).roundToInt()
        val left = (screenWidthPx - w) / 2
        val top = (screenHeightPx - h) / 2
        val options = ActivityOptions.makeBasic().setLaunchBounds(Rect(left, top, left + w, top + h))
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options.toBundle())
            FreeformOutcome.FREEFORM_REQUESTED
        } catch (_: Exception) {
            if (IntentLauncher.start(context, intent)) FreeformOutcome.OPENED_NORMALLY else FreeformOutcome.FAILED
        }
    }
}
