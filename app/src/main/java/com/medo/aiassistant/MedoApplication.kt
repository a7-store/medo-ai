package com.medo.aiassistant

import android.app.Application
import com.medo.aiassistant.data.AppDatabase
import com.medo.aiassistant.service.TaskAlertManager
import com.medo.aiassistant.utils.PrefsManager

class MedoApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var prefsManager: PrefsManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getDatabase(this)
        prefsManager = PrefsManager(this)

        // Initialize Android notification channels for task alerts
        TaskAlertManager.createNotificationChannels(this)
    }

    companion object {
        lateinit var instance: MedoApplication
            private set
    }
}
