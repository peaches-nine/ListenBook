package com.tz.audiobook.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tz.audiobook.R
import com.tz.audiobook.presentation.settings.SettingsPrefs

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "audiobook_reminder"
        const val ACTION_REMINDER = "com.tz.audiobook.REMINDER"

        fun scheduleReminder(context: Context) {
            val hour = SettingsPrefs.getReminderHour(context)
            val minute = SettingsPrefs.getReminderMinute(context)
            if (hour < 0) return // Reminder disabled

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_REMINDER
            }
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, hour)
                set(java.util.Calendar.MINUTE, minute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
            }

            alarmManager.setRepeating(
                android.app.AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                android.app.AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        }

        fun cancelReminder(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_REMINDER
            }
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMINDER) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "阅读提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "每日阅读提醒"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("该听书啦")
            .setContentText("今天还没听书，来一段吧~")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(
                android.app.PendingIntent.getActivity(
                    context, 0,
                    context.packageManager.getLaunchIntentForPackage(context.packageName),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        notificationManager.notify(1001, notification)
    }
}
