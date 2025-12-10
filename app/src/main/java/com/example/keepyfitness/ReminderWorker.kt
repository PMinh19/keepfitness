package com.example.keepyfitness

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

class ReminderWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("reminder_settings", Context.MODE_PRIVATE)
        val workoutEnabled = prefs.getBoolean("workout_reminder", false)
        val waterEnabled = prefs.getBoolean("water_reminder", false)

        if (workoutEnabled) {
            showNotification("Nhắc nhở tập luyện", "Đến giờ tập luyện rồi!")
        }

        if (waterEnabled) {
            showNotification("Nhắc nhở uống nước", "Hãy uống một ly nước!")
        }

        return Result.success()
    }

    private fun showNotification(title: String, message: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("reminders", "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, "reminders")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use appropriate icon
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
