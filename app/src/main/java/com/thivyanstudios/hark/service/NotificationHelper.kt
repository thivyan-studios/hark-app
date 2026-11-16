package com.thivyanstudios.hark.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.thivyanstudios.hark.MainActivity
import com.thivyanstudios.hark.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "hark_channel"

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val name = context.getString(R.string.notification_channel_name)
        val descriptionText = context.getString(R.string.notification_channel_description)
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun createNotification(): Notification {
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )

        // The Stop button: ends streaming without opening the app
        val stop = PendingIntent.getService(
            context, 1,
            Intent(context, AudioStreamingService::class.java).setAction(AudioStreamingService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, channelId)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_mic_on)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_mic_off, context.getString(R.string.notification_action_stop), stop)
            .setOngoing(true)
            .setWhen(System.currentTimeMillis())
            .setUsesChronometer(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
