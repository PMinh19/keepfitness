package com.example.keepyfitness

import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class NotificationSettingsActivity : AppCompatActivity() {

    private lateinit var switchWorkoutReminder: Switch
    private lateinit var switchWaterReminder: Switch
    private lateinit var btnSaveSettings: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_settings)

        switchWorkoutReminder = findViewById(R.id.switchWorkoutReminder)
        switchWaterReminder = findViewById(R.id.switchWaterReminder)
        btnSaveSettings = findViewById(R.id.btnSaveSettings)

        loadSettings()

        btnSaveSettings.setOnClickListener {
            saveSettings()
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("reminder_settings", MODE_PRIVATE)
        switchWorkoutReminder.isChecked = prefs.getBoolean("workout_reminder", false)
        switchWaterReminder.isChecked = prefs.getBoolean("water_reminder", false)
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("reminder_settings", MODE_PRIVATE).edit()
        prefs.putBoolean("workout_reminder", switchWorkoutReminder.isChecked)
        prefs.putBoolean("water_reminder", switchWaterReminder.isChecked)
        prefs.apply()

        // Schedule or cancel reminders
        val workManager = WorkManager.getInstance(this)
        if (switchWorkoutReminder.isChecked || switchWaterReminder.isChecked) {
            val reminderRequest = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.HOURS)
                .build()
            workManager.enqueueUniquePeriodicWork("reminders", ExistingPeriodicWorkPolicy.REPLACE, reminderRequest)
        } else {
            workManager.cancelUniqueWork("reminders")
        }

        Toast.makeText(this, "Cài đặt đã lưu", Toast.LENGTH_SHORT).show()
        finish()
    }
}
