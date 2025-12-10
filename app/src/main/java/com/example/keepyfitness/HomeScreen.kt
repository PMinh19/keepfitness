package com.example.keepyfitness

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.keepyfitness.Model.HeartRateData
import com.example.keepyfitness.utils.WeatherHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class HomeScreen : AppCompatActivity() {

    private val LOCATION_PERMISSION_REQUEST = 2001
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var weatherHelper: WeatherHelper
    private val dailyGoal = 500.0 // Daily calorie burn goal (can be made configurable)

    // ActivityResultLauncher để làm mới nhịp tim
    private val refreshHeartRateLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            loadHeartRateSuggestion()
        }
    }

    // ActivityResultLauncher để làm mới calories sau khi quét thức ăn
    private val scanFoodLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // Reload calories dù result code là gì (vì có thể đã quét xong)
        loadRemainingCalories()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home_screen)

        // Khởi tạo Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()


        weatherHelper = WeatherHelper(this)

        // Hiển thị calo còn lại
        loadRemainingCalories()

        // Nút quét calo
        val btnScanCalo = findViewById<LinearLayout>(R.id.btnScanCalo)
        btnScanCalo.setOnClickListener {
            scanFoodLauncher.launch(Intent(this, FruitCalo::class.java))
        }

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

        // Nút hồ sơ người dùng
        val btnUserProfile = findViewById<LinearLayout>(R.id.btnUserProfile)
        btnUserProfile.setOnClickListener {
            startActivity(Intent(this, UserProfileActivity::class.java))
        }

        // Nút cài đặt thông báo
        val btnNotificationSettings = findViewById<LinearLayout>(R.id.btnNotificationSettings)
        btnNotificationSettings.setOnClickListener {
            startActivity(Intent(this, NotificationSettingsActivity::class.java))
        }

        // Logout button
        val logoutButton = findViewById<CardView>(R.id.logoutButton)
        logoutButton.setOnClickListener {
            showLogoutConfirmation()
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

    private fun loadRemainingCalories() {
        val tvCalories = findViewById<TextView>(R.id.tvTotalCalories)
        val user = auth.currentUser ?: run {
            tvCalories.text = "Còn cần đốt: 0 calo"
            return
        }
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = calendar.timeInMillis

        // Load goal from Firestore or calculate BMR, then subtract burned calories
        loadGoalAndCalculateRemaining(user.uid, startOfDay, endOfDay, tvCalories)
    }

    private fun loadGoalAndCalculateRemaining(uid: String, startOfDay: Long, endOfDay: Long, tvCalories: TextView) {
        Log.d("HomeScreen", "Loading consumed and burned calories")

        // Load consumed and burned calories directly
        loadCaloriesConsumedAndBurned(startOfDay, endOfDay, tvCalories)
    }

    // Keep old method for backward compatibility but not used in main flow
    private fun loadRemainingCaloriesOld() {
        val tvCalories = findViewById<TextView>(R.id.tvTotalCalories)
        val user = auth.currentUser ?: run {
            tvCalories.text = "0 calo"
            return
        }
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = calendar.timeInMillis

        // First, get the daily goal
        db.collection("users").document(user.uid).collection("goals").document("daily").get()
            .addOnSuccessListener { goalDoc ->
                val goal = if (goalDoc.exists()) {
                    goalDoc.getDouble("dailyCalorieGoal") ?: 500.0
                } else {
                    // If no custom goal, calculate BMR
                    calculateBMR(user.uid) { bmr ->
                        runOnUiThread {
                            loadCaloriesConsumedAndBurned(startOfDay, endOfDay, tvCalories)
                        }
                    }
                    return@addOnSuccessListener
                }
                loadCaloriesConsumedAndBurned(startOfDay, endOfDay, tvCalories)
            }
            .addOnFailureListener {
                // Fallback to BMR or default
                calculateBMR(user.uid) { bmr ->
                    runOnUiThread {
                        loadCaloriesConsumedAndBurned(startOfDay, endOfDay, tvCalories)
                    }
                }
            }
    }

    private fun loadCaloriesConsumedAndBurned(startOfDay: Long, endOfDay: Long, tvCalories: TextView) {
        val user = auth.currentUser ?: return

        // Load consumed calories from foodIntake
        db.collection("users").document(user.uid).collection("foodIntake")
            .whereGreaterThanOrEqualTo("date", startOfDay)
            .whereLessThan("date", endOfDay)
            .get()
            .addOnSuccessListener { consumedSnapshot ->
                var totalConsumed = 0.0
                for (document in consumedSnapshot.documents) {
                    val calories = document.getDouble("caloriesConsumed") ?: 0.0
                    totalConsumed += calories
                }

                // Load BMR
                calculateBMR(user.uid) { bmr ->
                    runOnUiThread {
                        // Load burned calories from workouts
                        db.collection("users").document(user.uid).collection("workouts")
                            .whereGreaterThanOrEqualTo("date", startOfDay)
                            .whereLessThan("date", endOfDay)
                            .get()
                            .addOnSuccessListener { burnedSnapshot ->
                                var totalBurned = 0.0
                                for (document in burnedSnapshot.documents) {
                                    val calories = document.getDouble("caloriesBurned") ?: 0.0
                                    totalBurned += calories
                                }

                                Log.d("HomeScreen", "Consumed: ${totalConsumed.toInt()}, BMR: ${bmr.toInt()}, Burned: ${totalBurned.toInt()}")

                                if (totalConsumed < bmr) {
                                    // Cảnh báo ăn ít quá
                                    val deficit = (bmr - totalConsumed).toInt()
                                    tvCalories.text = "⚠️ Ăn ít quá! Thiếu ${deficit} calo so với BMR cơ bản"
                                } else {
                                    // Tính calo cần đốt = consumed - BMR - burned
                                    val caloriesToBurn = totalConsumed - bmr - totalBurned
                                    if (caloriesToBurn > 0) {
                                        tvCalories.text = "📥${totalConsumed.toInt()} | 🔥${totalBurned.toInt()} | Còn đốt: ${caloriesToBurn.toInt()} calo"
                                    } else {
                                        val surplus = (-caloriesToBurn).toInt()
                                        tvCalories.text = "📥${totalConsumed.toInt()} | 🔥${totalBurned.toInt()} | Đủ rồi! Dư ${surplus} calo"
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("HomeScreen", "Error loading burned calories: ${e.message}")
                                tvCalories.text = "📥${totalConsumed.toInt()} | Lỗi tải calo đốt"
                            }
                    }
                }
            }
            .addOnFailureListener { e ->
                tvCalories.text = "Lỗi tải calo tiêu thụ"
                Log.e("HomeScreen", "Error loading consumed calories: ${e.message}")
            }
    }

    private fun calculateBMR(uid: String, callback: (Double) -> Unit) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val age = doc.getLong("age")?.toInt() ?: 25
                    val weight = doc.getDouble("weight") ?: 70.0
                    val height = doc.getDouble("height") ?: 170.0
                    val gender = doc.getString("gender") ?: "Male"
                    val bmr = if (gender == "Male") {
                        88.362 + (13.397 * weight) + (4.799 * height) - (5.677 * age)
                    } else {
                        447.593 + (9.247 * weight) + (3.098 * height) - (4.330 * age)
                    }
                    // Use BMR for basal metabolic rate
                    callback(bmr)
                } else {
                    callback(500.0) // Default
                }
            }
            .addOnFailureListener {
                callback(500.0)
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

    private fun showWeatherSuggestion() {
        val tvWeather = findViewById<TextView>(R.id.tvWeatherSuggestion)
        tvWeather.text = "🌤️ Đang lấy gợi ý thời tiết..."

        weatherHelper.getWeatherSuggestion { suggestion ->
            runOnUiThread {
                tvWeather.text = suggestion
            }
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
        loadRemainingCalories() // Reload remaining calories when returning to screen
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cleanup WeatherHelper
        weatherHelper.cleanup()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showWeatherSuggestion()
            } else {
                val tvWeather = findViewById<TextView>(R.id.tvWeatherSuggestion)
                tvWeather.text = "🌤️ Cần quyền vị trí để hiển thị gợi ý thời tiết"
            }
        }
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Đăng xuất")
            .setMessage("Bạn có chắc chắn muốn đăng xuất không?")
            .setPositiveButton("Có") { dialog, which ->
                auth.signOut()
                showCustomToast("Đã đăng xuất.")
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton("Không", null)
            .setCancelable(true)
            .show()
    }
}