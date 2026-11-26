package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.util.Redactor

object DubbingNotificationHelper {
    const val CHANNEL_ID = "livedub_dubbing"
    const val NOTIFICATION_ID = 1

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.notif_channel_desc)
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    fun buildNotification(
        context: Context,
        statusText: String,
        detectedLanguageText: String,
        isPaused: Boolean,
        targetLanguageLabel: String = "Türkçe"
    ): Notification {
        ensureChannel(context)

        val safeStatus = Redactor.redact(statusText)
        val safeLang = Redactor.redact(detectedLanguageText)
        val safeTarget = Redactor.redact(targetLanguageLabel)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            100,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseIntent = Intent(context, DubbingService::class.java).apply {
            action = DubbingService.ACTION_PAUSE_TOGGLE
        }
        val pausePendingIntent = PendingIntent.getService(
            context,
            101,
            pauseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(context, DubbingService::class.java).apply {
            action = DubbingService.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            103,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentText = "$safeStatus • $safeLang → $safeTarget"

        val pauseLabel = if (isPaused) {
            context.getString(R.string.notif_action_resume)
        } else {
            context.getString(R.string.notif_action_pause)
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_livedub)
            .setContentTitle(context.getString(R.string.notif_title_running))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, pauseLabel, pausePendingIntent)
            .addAction(0, context.getString(R.string.notif_action_stop), stopPendingIntent)
            .build()
    }

    fun updateNotification(
        context: Context,
        statusText: String,
        detectedLanguageText: String,
        isPaused: Boolean,
        targetLanguageLabel: String = "Türkçe"
    ) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notif = buildNotification(
                context = context,
                statusText = statusText,
                detectedLanguageText = detectedLanguageText,
                isPaused = isPaused,
                targetLanguageLabel = targetLanguageLabel
            )
            nm.notify(NOTIFICATION_ID, notif)
        } catch (_: Throwable) {
        }
    }
}
