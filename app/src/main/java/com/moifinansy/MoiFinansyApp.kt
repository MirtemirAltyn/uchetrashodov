package com.moifinansy

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.moifinansy.work.DailyWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MoiFinansyApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // В unit-тестах (Robolectric) фоновые задачи не запускаем.
        if (android.os.Build.FINGERPRINT != "robolectric") DailyWorker.schedule(this)
    }
}
