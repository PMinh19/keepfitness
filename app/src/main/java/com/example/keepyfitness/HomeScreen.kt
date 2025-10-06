package com.example.keepyfitness

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.keepyfitness.Model.HeartRateData
import com.example.keepyfitness.utils.WeatherHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HomeScreen : AppCompatActivity() {

    private val LOCATION_PERMISSION_REQUEST = 2001
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var weatherHelper: WeatherHelper

    // ActivityResultLauncher để làm mới nhịp tim
    private val refreshHeartRateLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            loadHeartRateSuggestion()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home_screen)

        // Khởi tạo Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()


        weatherHelper = WeatherHelper(this)

//        // Nút quét calo
//        val btnScanCalo = findViewById<LinearLayout>(R.id.btnScanCalo)
//        btnScanCalo.setOnClickListener {
//            startActivity(Intent(this, FruitCaloActivity::class.java))
//        }

        // Nút đo nhịp tim
        val btnHeartRate = findViewById<LinearLayout>(R.id.btnHeartRate)
        btnHeartRate.setOnClickListener {
            refreshHeartRateLauncher.launch(Intent(this, HeartRateActivity::class.java))
        }

        // Nút xem lịch sử nhịp tim
        val btnHeartRateHistory = findViewById<LinearLayout>(R.id.btnHeartRateHistory)
        btnHeartRateHistory.setOnClickListener {
            if (auth.currentUser != null) {
                refreshHeartRateLauncher.launch(Intent(this, HeartRateHistoryActivity::class.java))
            } else {
                showCustomToast("Vui lòng đăng nhập để xem lịch sử nhịp tim.")
                startActivity(Intent(this, LoginActivity::class.java))
            }
        }

        // Nút bắt đầu bài tập
        val btnStartWorkout = findViewById<LinearLayout>(R.id.btnStartWorkout)
        btnStartWorkout.setOnClickListener {
            startActivity(Intent(this, ExerciseListActivity::class.java))
        }

        // Nút lịch tập
        val btnScheduleWorkout = findViewById<LinearLayout>(R.id.btnScheduleWorkout)
        btnScheduleWorkout.setOnClickListener {
            startActivity(Intent(this, ScheduleListActivity::class.java))
        }

        // Nút xem lịch sử tập
        val btnViewHistory = findViewById<LinearLayout>(R.id.btnViewHistory)
        btnViewHistory.setOnClickListener {
            startActivity(Intent(this, WorkoutHistoryActivity::class.java))
        }

        // Áp padding cho hệ thống bar
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Kiểm tra quyền vị trí
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST)
        } else {
            showWeatherSuggestion()
        }

        // Yêu cầu quyền thông báo cho Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        // Load heart rate suggestion on first open
        loadHeartRateSuggestion()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showWeatherSuggestion()
            } else {
                val tvSuggestion = findViewById<TextView>(R.id.tvWeatherSuggestion)
                tvSuggestion.text = "❌ Cần quyền vị trí để lấy gợi ý thời tiết"

                AlertDialog.Builder(this)
                    .setTitle("Quyền vị trí bị từ chối")
                    .setMessage("Không thể lấy gợi ý tập luyện theo thời tiết nếu không cấp quyền vị trí.")
                    .setPositiveButton("OK", null)
                    .setCancelable(true)
                    .show()
            }
        }
    }

    private fun showWeatherSuggestion() {
        val tvSuggestion = findViewById<TextView>(R.id.tvWeatherSuggestion)
        tvSuggestion.text = "⏳ Đang lấy vị trí và thời tiết...\n(Có thể mất 10-20 giây)"

        try {
            weatherHelper.getWeatherSuggestion { suggestion ->
                runOnUiThread {
                    tvSuggestion.text = suggestion
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                tvSuggestion.text = "❌ Lỗi: ${e.message}\n\nVui lòng thử lại sau."
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
                // Lưu nhịp tim lên Firestore nếu user đã đăng nhập
                val user = auth.currentUser
                if (user != null) {
                    saveHeartRateToFirestore(bpm, status, suggestion, time)
                }
            } else {
                tvHr.text = "🫀 Chưa có nhịp tim gần đây. Hãy đo để nhận gợi ý."
            }
        } catch (e: Exception) {
            tvHr.text = "🫀 Không thể tải gợi ý nhịp tim"
            showCustomToast("Lỗi tải nhịp tim: ${e.message}")
        }
    }

    private fun saveHeartRateToFirestore(bpm: Int, status: String, suggestion: String, timestamp: Long) {
        val user = auth.currentUser
        if (user != null) {
            val heartRateData = HeartRateData(
                id = timestamp.toString(),
                bpm = bpm,
                status = status,
                suggestion = suggestion,
                timestamp = timestamp,
                duration = 0L
            )
            db.collection("users").document(user.uid).collection("healthMetrics")
                .document(heartRateData.id)
                .set(heartRateData)
                .addOnSuccessListener {
                    // Không hiển thị toast để tránh làm phiền người dùng
                }
                .addOnFailureListener { e ->
                    showCustomToast("Lỗi lưu nhịp tim: ${e.message}")
                }
        }
    }

    private fun showCustomToast(message: String) {
        val inflater = layoutInflater
        val layout = inflater.inflate(R.layout.custom_toast, null)
        val text = layout.findViewById<TextView>(R.id.toast_text)
        text.text = message
        val toast = Toast(applicationContext)
        toast.duration = Toast.LENGTH_LONG
        toast.view = layout
        toast.show()
    }

    override fun onResume() {
        super.onResume()
        loadHeartRateSuggestion()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cleanup WeatherHelper
        weatherHelper.cleanup()
    }
}