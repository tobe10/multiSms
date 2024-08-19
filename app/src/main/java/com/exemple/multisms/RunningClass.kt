
package com.exemple.multisms

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context


class RunningClass: Application() {
    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            "sms_sending_channel",
            "SMS Sending",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notification for SMS sending service"
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}


