package app.novapill.android.perm

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.novapill.android.service.NovaNotificationListener

/** A point-in-time reading of everything the app can ask for. Always read from the system. */
data class PermissionSnapshot(
    val overlay: Boolean,
    val notificationAccess: Boolean,
    val postNotifications: Boolean,
    val location: Boolean,
    val usageAccess: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val flashAvailable: Boolean,
    val shizuku: ShizukuState,
) {
    /** The two permissions the island cannot work without. */
    val coreReady: Boolean get() = overlay && notificationAccess
}

object Permissions {
    fun snapshot(c: Context): PermissionSnapshot = PermissionSnapshot(
        overlay = canDrawOverlays(c),
        notificationAccess = notificationAccess(c),
        postNotifications = postNotifications(c),
        location = location(c),
        usageAccess = usageAccess(c),
        batteryOptimizationIgnored = batteryOptimizationIgnored(c),
        flashAvailable = flashAvailable(c),
        shizuku = ShizukuStatus.read(c),
    )

    fun canDrawOverlays(c: Context): Boolean = try {
        Settings.canDrawOverlays(c)
    } catch (_: Exception) {
        false
    }

    fun notificationAccess(c: Context): Boolean = try {
        NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)
    } catch (_: Exception) {
        false
    }

    /** Android 13+ asks at runtime; older versions only have the per-app notification switch. */
    fun postNotifications(c: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(c).areNotificationsEnabled()
        }

    val postNotificationsNeedsRuntimeRequest: Boolean get() = Build.VERSION.SDK_INT >= 33

    fun location(c: Context): Boolean =
        ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun usageAccess(c: Context): Boolean = try {
        val ops = c.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        }
        if (mode == AppOpsManager.MODE_DEFAULT) {
            c.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    } catch (_: Exception) {
        false
    }

    fun batteryOptimizationIgnored(c: Context): Boolean = try {
        c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)
    } catch (_: Exception) {
        false
    }

    fun flashAvailable(c: Context): Boolean = c.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)

    fun overlayIntent(c: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${c.packageName}"))

    fun notificationAccessIntent(c: Context): Intent =
        if (Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(c, NovaNotificationListener::class.java).flattenToString(),
            )
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }

    fun notificationAccessListIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun appDetailsIntent(c: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}"))

    fun usageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /** Opens the list of battery optimisation exemptions; the user decides, nothing is requested silently. */
    fun batterySettingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun appNotificationSettingsIntent(c: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName)
}
