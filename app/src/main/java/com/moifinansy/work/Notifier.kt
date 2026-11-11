package com.moifinansy.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.moifinansy.MainActivity
import com.moifinansy.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Показ системных уведомлений одного канала «Напоминания». */
@Singleton
class Notifier @Inject constructor(@param:ApplicationContext private val context: Context) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = context.getString(R.string.notif_channel_desc)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(key: String, title: String, text: String) {
        if (!canNotify()) return
        ensureChannel()
        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(key.hashCode(), notification)
        } catch (e: SecurityException) {
            // Разрешение отозвали между проверкой и показом — просто не показываем.
        }
    }

    private companion object {
        const val CHANNEL = "reminders"
    }
}
