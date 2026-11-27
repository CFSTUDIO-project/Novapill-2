package app.novapill.android.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings

enum class SystemAppKind { ALARMS, CALENDAR, PHONE, INTERNET, VOLUME, CAMERA, SETTINGS }

/**
 * Alarm, calendar, call, camera and settings actions are owned by system apps, so the island opens them instead of
 * pretending to be them. Internet and Volume use Android's own panels (API 29+) and fall back to the full settings
 * page: an app may not switch Wi-Fi or mobile data itself, and this one does not try.
 */
object SystemApps {
    fun open(context: Context, kind: SystemAppKind): Boolean = when (kind) {
        SystemAppKind.ALARMS -> IntentLauncher.start(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))
        SystemAppKind.CALENDAR ->
            IntentLauncher.start(context, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
        SystemAppKind.PHONE -> IntentLauncher.start(context, Intent(Intent.ACTION_DIAL))
        SystemAppKind.INTERNET -> internet(context)
        SystemAppKind.VOLUME -> volume(context)
        SystemAppKind.CAMERA -> IntentLauncher.start(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        SystemAppKind.SETTINGS -> IntentLauncher.start(context, Intent(Settings.ACTION_SETTINGS))
    }

    private fun internet(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            IntentLauncher.start(context, Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
        ) {
            return true
        }
        return IntentLauncher.start(context, Intent(Settings.ACTION_WIRELESS_SETTINGS))
    }

    private fun volume(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            IntentLauncher.start(context, Intent(Settings.Panel.ACTION_VOLUME))
        ) {
            return true
        }
        return IntentLauncher.start(context, Intent(Settings.ACTION_SOUND_SETTINGS))
    }
}
