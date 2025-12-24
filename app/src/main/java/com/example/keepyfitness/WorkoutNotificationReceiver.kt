package com.example.keepyfitness

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import android.app.AlarmManager
import java.util.Calendar

class WorkoutNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Kiểm tra quyền thông báo cho Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val channelId = "workout_reminder_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Nhắc nhở tập luyện",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo nhắc nhở tập luyện hàng ngày"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(context, HomeScreen::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Thời gian tập luyện!")
            .setContentText("Đã đến giờ tập luyện. Hãy bắt đầu ngay!")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        notificationManager.notify(1001, notification)

        // Đặt lại alarm cho tuần sau cùng ngày
        val day = intent.getStringExtra("day")
        val hour = intent.getIntExtra("hour", -1)
        val minute = intent.getIntExtra("minute", -1)
        if (day != null && hour != -1 && minute != -1) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val dayMap = mapOf(
                "Chủ Nhật" to Calendar.SUNDAY,
                "Thứ Hai" to Calendar.MONDAY,
                "Thứ Ba" to Calendar.TUESDAY,
                "Thứ Tư" to Calendar.WEDNESDAY,
                "Thứ Năm" to Calendar.THURSDAY,
                "Thứ Sáu" to Calendar.FRIDAY,
                "Thứ Bảy" to Calendar.SATURDAY
            )
            val calendarDay = dayMap[day]
            if (calendarDay != null) {
                val nextWeek = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.DAY_OF_WEEK, calendarDay)
                    add(Calendar.WEEK_OF_YEAR, 1)
                }
                val nextIntent = Intent(context, WorkoutNotificationReceiver::class.java).apply {
                    putExtra("day", day)
                    putExtra("hour", hour)
                    putExtra("minute", minute)
                }
                val nextPendingIntent = PendingIntent.getBroadcast(
                    context,
                    0, // Sử dụng requestCode 0 cho reschedule
                    nextIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextWeek.timeInMillis,
                    nextPendingIntent
                )
            }
        }
    }
}
