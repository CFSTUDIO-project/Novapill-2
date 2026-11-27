package app.novapill.android.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import java.util.concurrent.ConcurrentHashMap

data class AppEntry(val packageName: String, val label: String)

/** App labels, launcher icons and launch intents, cached so the overlay never blocks on PackageManager twice. */
class AppInfoCache(context: Context) {
    private val pm: PackageManager = context.packageManager
    private val labels = ConcurrentHashMap<String, String>()
    private val icons = LruCache<String, Bitmap>(64)

    fun label(packageName: String): String {
        labels[packageName]?.let { return it }
        val label = try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) {
            prettify(packageName)
        }
        labels[packageName] = label
        return label
    }

    fun iconBitmap(packageName: String, sizePx: Int = 96): Bitmap? {
        val key = "$packageName@$sizePx"
        icons.get(key)?.let { return it }
        return try {
            val bmp = pm.getApplicationIcon(packageName).toBitmap(sizePx, sizePx)
            icons.put(key, bmp)
            bmp
        } catch (_: Exception) {
            null
        }
    }

    fun launchIntent(packageName: String): Intent? = try {
        pm.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } catch (_: Exception) {
        null
    }

    fun listLaunchable(): List<AppEntry> {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = try {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.queryIntentActivities(query, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(query, 0)
            }
        } catch (_: Exception) {
            emptyList()
        }
        return infos
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun clear() {
        labels.clear()
        icons.evictAll()
    }

    private fun prettify(packageName: String): String {
        val last = packageName.substringAfterLast('.')
        return last.replaceFirstChar { it.uppercase() }.ifEmpty { packageName }
    }
}
