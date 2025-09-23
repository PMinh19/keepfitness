package com.example.keepyfitness
import android.widget.TextView

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.keepyfitness.utils.WeatherHelper

class HomeScreen : AppCompatActivity() {

    private val LOCATION_PERMISSION_REQUEST = 2001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home_screen)

        val btnHeartRate = findViewById<LinearLayout>(R.id.btnHeartRate)
        btnHeartRate.setOnClickListener {
            startActivity(Intent(this, HeartRateActivity::class.java))
        }

        // Áp padding cho hệ thống bar
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Kiểm tra quyền vị trí
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
        } else {
            showWeatherSuggestion()
        }

        // Các nút LinearLayout
        val btnStartWorkout = findViewById<LinearLayout>(R.id.btnStartWorkout)
        btnStartWorkout.setOnClickListener {
            startActivity(Intent(this, ExerciseListActivity::class.java))
        }

        val btnScheduleWorkout = findViewById<LinearLayout>(R.id.btnScheduleWorkout)
        btnScheduleWorkout.setOnClickListener {
            startActivity(Intent(this, ScheduleListActivity::class.java))
        }

        val btnViewHistory = findViewById<LinearLayout>(R.id.btnViewHistory)
        btnViewHistory.setOnClickListener {
            startActivity(Intent(this, WorkoutHistoryActivity::class.java))
        }

        // Yêu cầu quyền thông báo cho Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }

        // Load heart rate suggestion on first open
        loadHeartRateSuggestion()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showWeatherSuggestion()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Quyền vị trí bị từ chối")
                    .setMessage("Không thể lấy gợi ý tập luyện theo thời tiết nếu không cấp quyền vị trí.")
                    .setPositiveButton("OK", null)
                    .setCancelable(true) // cho phép đóng dialog
                    .show()
            }
        }
    }

    private fun showWeatherSuggestion() {
        val tvSuggestion = findViewById<TextView>(R.id.tvWeatherSuggestion)
        tvSuggestion.text = "⏳ Đang lấy gợi ý thời tiết..."

        try {
            val weatherHelper = WeatherHelper(this, "73371ff12e460447cff4621d4a956c22")
            weatherHelper.getWeatherSuggestion { suggestion ->
                runOnUiThread {
                    tvSuggestion.text = suggestion
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                tvSuggestion.text = "❌ Không thể lấy gợi ý thời tiết"
            }
        }
    }

    private fun loadHeartRateSuggestion() {
        val tvHr = findViewById<TextView>(R.id.tvHeartRateSuggestion)
        try {
            val prefs = getSharedPreferences("health_data", MODE_PRIVATE)
            val bpm = prefs.getInt("last_heart_rate_bpm", -1)
            val status = prefs.getString("last_heart_rate_status", null)
            val suggestion = prefs.getString("last_heart_rate_suggestion", null)
            val time = prefs.getLong("last_heart_rate_time", 0L)

            if (bpm > 0 && status != null && suggestion != null && time > 0L) {
                tvHr.text = "🫀 Nhịp tim gần nhất: ${bpm} BPM\n📊 ${status}\n💡 ${suggestion}"
            } else {
                tvHr.text = "🫀 Chưa có nhịp tim gần đây. Hãy đo để nhận gợi ý."
            }
        } catch (e: Exception) {
            tvHr.text = "🫀 Không thể tải gợi ý nhịp tim"
        }
    }

    override fun onResume() {
        super.onResume()
        loadHeartRateSuggestion()
    }

}
