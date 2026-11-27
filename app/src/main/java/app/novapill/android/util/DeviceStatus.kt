package app.novapill.android.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.os.StatFs

enum class NetworkKind { WIFI, CELLULAR, ETHERNET, OTHER, OFFLINE, UNKNOWN }

/** Two facts for the quick panel's status strip. Read when the panel opens, never polled. */
data class DeviceStatus(val network: NetworkKind, val freeBytes: Long) {
    companion object {
        val UNKNOWN = DeviceStatus(NetworkKind.UNKNOWN, -1L)
    }
}

class DeviceStatusReader(private val context: Context, private val diagnostics: Diagnostics) {
    fun read(): DeviceStatus = DeviceStatus(network(), freeBytes())

    private fun network(): NetworkKind {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return NetworkKind.UNKNOWN
        return try {
            val active = cm.activeNetwork ?: return NetworkKind.OFFLINE
            val caps = cm.getNetworkCapabilities(active) ?: return NetworkKind.OFFLINE
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.CELLULAR
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.ETHERNET
                else -> NetworkKind.OTHER
            }
        } catch (e: SecurityException) {
            diagnostics.error("status", e)
            NetworkKind.UNKNOWN
        }
    }

    private fun freeBytes(): Long = try {
        StatFs(Environment.getDataDirectory().path).availableBytes
    } catch (e: IllegalArgumentException) {
        diagnostics.error("status", e)
        -1L
    }
}
