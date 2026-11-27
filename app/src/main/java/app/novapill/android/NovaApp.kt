package app.novapill.android

import android.app.Application
import app.novapill.android.service.NotificationChannels

class NovaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)
        NovaGraph.init(this)
    }
}
