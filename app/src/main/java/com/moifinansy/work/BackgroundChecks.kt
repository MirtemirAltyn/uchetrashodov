package com.moifinansy.work

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Запуск фоновой проверки (бюджеты, напоминания) из ViewModel без зависимости от WorkManager в тестах. */
class BackgroundChecks @Inject constructor(@ApplicationContext private val context: Context) {
    fun requestCheck() {
        if (Build.FINGERPRINT == "robolectric") return
        DailyWorker.runNow(context)
    }
}
