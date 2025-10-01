package com.example.keepyfitness

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.Camera
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.keepyfitness.Model.HeartRateData
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.analytics.FirebaseAnalytics // Thêm Analytics
import org.tensorflow.lite.Interpreter
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt
import android.os.Handler
import android.os.Looper
import java.util.*

@Suppress("DEPRECATION")
class HeartRateActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var heartRateText: TextView
    private lateinit var surfaceView: SurfaceView
    private lateinit var surfaceHolder: SurfaceHolder
    private var camera: Camera? = null
    private var tflite: Interpreter? = null
    private var isDestroyed = false

    // Heart rate detection variables
    private val redValuesList = mutableListOf<Double>()
    private var frameCount = 0
    private val maxFrames = 300 // 10 seconds at 30fps
    private val handler = Handler(Looper.getMainLooper())
    private var startTime = 0L

    // Firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var analytics: FirebaseAnalytics // Thêm Analytics

    companion object {
        private const val TAG = "HeartRateActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_heart_rate_simple)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        analytics = FirebaseAnalytics.getInstance(this) // Khởi tạo Analytics

        try {
            heartRateText = findViewById(R.id.tvHeartRate)
            surfaceView = findViewById(R.id.surfaceView)

            surfaceHolder = surfaceView.holder
            surfaceHolder.addCallback(this)

            // Load TensorFlow Lite model
            loadTensorFlowModel()

            // Migrate local data to Firestore
            migrateLocalDataToFirestore()

            if (allPermissionsGranted()) {
                updateUI("📱 Đặt ngón tay lên camera sau")
            } else {
                requestPermissions()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            showError("Lỗi khởi tạo: ${e.message}")
            finish()
        }
    }

    private fun migrateLocalDataToFirestore() {
        val prefs = getSharedPreferences("health_data", MODE_PRIVATE)
        val bpm = prefs.getInt("last_heart_rate_bpm", 0)
        val status = prefs.getString("last_heart_rate_status", null)
        val suggestion = prefs.getString("last_heart_rate_suggestion", null)
        val timestamp = prefs.getLong("last_heart_rate_time", 0L)

        if (bpm > 0 && status != null && suggestion != null && timestamp > 0) {
            val user = auth.currentUser
            if (user != null) {
                val heartRateData = HeartRateData(
                    id = timestamp.toString(), // Dùng timestamp làm ID
                    bpm = bpm,
                    status = status,
                    suggestion = suggestion,
                    timestamp = timestamp,
                    duration = 0L // Không có duration trong local, để mặc định
                )

                db.collection("users").document(user.uid).collection("healthMetrics")
                    .document(heartRateData.id)
                    .set(heartRateData)
                    .addOnSuccessListener {
                        Log.d(TAG, "Migrated local heart rate data to Firestore")
                        // Không xóa local để tương thích
                        // prefs.edit().clear().apply()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error migrating heart rate data", e)
                        Toast.makeText(this, "Lỗi migrate dữ liệu: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
        }
    }

    private fun loadTensorFlowModel() {
        try {
            val inputStream = assets.open("model.tflite")
            val modelBytes = inputStream.readBytes()
            inputStream.close()

            val byteBuffer = ByteBuffer.allocateDirect(modelBytes.size)
                .order(ByteOrder.nativeOrder())
            byteBuffer.put(modelBytes)
            byteBuffer.rewind()

            tflite = Interpreter(byteBuffer)
            Log.d(TAG, "TensorFlow Lite model loaded successfully")
            updateUI("✅ Model AI đã sẵn sàng")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading TensorFlow model", e)
            updateUI("⚠️ Không load được model AI, dùng thuật toán cơ bản")
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (allPermissionsGranted()) {
            startCamera()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopCamera()
    }

    private fun startCamera() {
        try {
            camera = Camera.open()
            camera?.let { cam ->
                val parameters = cam.parameters

                // Enable flash for heart rate detection
                parameters.flashMode = Camera.Parameters.FLASH_MODE_TORCH

                // Set preview size
                val supportedSizes = parameters.supportedPreviewSizes
                val optimalSize = supportedSizes.minByOrNull {
                    Math.abs(it.width * it.height - 640 * 480)
                }

                optimalSize?.let {
                    parameters.setPreviewSize(it.width, it.height)
                }

                cam.parameters = parameters
                cam.setPreviewDisplay(surfaceHolder)

                // Start heart rate detection
                startTime = System.currentTimeMillis()
                redValuesList.clear()
                frameCount = 0

                cam.setPreviewCallback { data, camera ->
                    if (!isDestroyed && frameCount < maxFrames) {
                        processHeartRateFrame(data, camera)
                    }
                }

                cam.startPreview()
                updateUI("💗 Đang đo nhịp tim... Giữ ngón tay yên")

                // Track sự kiện bắt đầu đo
                analytics.logEvent("start_heart_rate_measurement", Bundle())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting camera", e)
            showError("Không thể khởi động camera: ${e.message}")
        }
    }

    private fun processHeartRateFrame(data: ByteArray, camera: Camera) {
        if (isDestroyed) return

        try {
            val size = camera.parameters.previewSize
            frameCount++

            // Convert YUV to RGB and calculate average red value
            val redValue = calculateAverageRedValue(data, size.width, size.height)
            redValuesList.add(redValue)

            // Update progress
            val progress = (frameCount * 100) / maxFrames
            updateUI("💗 Đang đo: ${progress}% (${frameCount}/${maxFrames} frames)")

            // Calculate heart rate every 30 frames (1 second)
            if (frameCount % 30 == 0) {
                val currentBpm = calculateHeartRateFromRedValues()
                if (currentBpm > 0) {
                    updateUI("💓 Nhịp tim tạm thời: ~${currentBpm} BPM (${progress}%)")
                }
            }

            // Finish measurement after collecting enough frames
            if (frameCount >= maxFrames) {
                finishHeartRateDetection()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame", e)
        }
    }

    private fun calculateAverageRedValue(data: ByteArray, width: Int, height: Int): Double {
        var redSum = 0.0
        var pixelCount = 0

        val centerX = width / 2
        val centerY = height / 2
        val sampleSize = Math.min(width, height) / 4

        for (y in (centerY - sampleSize / 2) until (centerY + sampleSize / 2)) {
            for (x in (centerX - sampleSize / 2) until (centerX + sampleSize / 2)) {
                if (x >= 0 && x < width && y >= 0 && y < height) {
                    val index = y * width + x
                    if (index < data.size) {
                        val yValue = data[index].toInt() and 0xFF
                        redSum += yValue
                        pixelCount++
                    }
                }
            }
        }

        return if (pixelCount > 0) redSum / pixelCount else 0.0
    }

    private fun calculateHeartRateFromRedValues(): Int {
        if (redValuesList.size < 60) return 0

        try {
            val recentValues = redValuesList.takeLast(90)
            val filteredValues = applySimpleBandpassFilter(recentValues)
            val peaks = findPeaks(filteredValues)

            if (peaks.size >= 2) {
                val intervals = mutableListOf<Double>()
                for (i in 1 until peaks.size) {
                    intervals.add((peaks[i] - peaks[i - 1]).toDouble())
                }

                if (intervals.isNotEmpty()) {
                    val avgInterval = intervals.average()
                    val fps = 30.0
                    val bpm = (60.0 * fps / avgInterval).roundToInt()
                    return if (bpm in 40..200) bpm else 0
                }
            }

            return useTensorFlowForHeartRate(recentValues)

        } catch (e: Exception) {
            Log.e(TAG, "Error calculating heart rate", e)
            return 0
        }
    }

    private fun applySimpleBandpassFilter(values: List<Double>): List<Double> {
        val windowSize = 5
        val smoothed = mutableListOf<Double>()

        for (i in values.indices) {
            val start = maxOf(0, i - windowSize / 2)
            val end = minOf(values.size - 1, i + windowSize / 2)
            val sum = (start..end).sumOf { values[it] }
            smoothed.add(sum / (end - start + 1))
        }

        return smoothed
    }

    private fun findPeaks(values: List<Double>): List<Int> {
        val peaks = mutableListOf<Int>()
        val threshold = values.average() + values.map { Math.abs(it - values.average()) }.average() * 0.5

        for (i in 1 until values.size - 1) {
            if (values[i] > values[i - 1] && values[i] > values[i + 1] && values[i] > threshold) {
                peaks.add(i)
            }
        }

        return peaks
    }

    private fun useTensorFlowForHeartRate(values: List<Double>): Int {
        return try {
            if (tflite == null || values.size < 36) return 0

            val inputArray = Array(1) { Array(36) { Array(36) { FloatArray(3) } } }
            for (i in 0 until 36) {
                for (j in 0 until 36) {
                    val valueIndex = (i * 36 + j) % values.size
                    val normalizedValue = (values[valueIndex] / 255.0).toFloat()
                    inputArray[0][i][j][0] = normalizedValue
                    inputArray[0][i][j][1] = normalizedValue
                    inputArray[0][i][j][2] = normalizedValue
                }
            }

            val outputArray = Array(1) { FloatArray(1) }
            tflite!!.run(inputArray, outputArray)

            val result = outputArray[0][0]
            if (result.isFinite() && result > 0) result.roundToInt() else 0

        } catch (e: Exception) {
            Log.e(TAG, "Error using TensorFlow model", e)
            0
        }
    }

    private fun finishHeartRateDetection() {
        stopCamera()

        val finalBpm = calculateHeartRateFromRedValues()
        val elapsedTime = (System.currentTimeMillis() - startTime) / 1000

        val status = when {
            finalBpm < 60 -> "Nhịp tim chậm"
            finalBpm in 60..100 -> "Nhịp tim bình thường"
            finalBpm in 101..120 -> "Nhịp tim nhanh"
            finalBpm in 121..130 -> "Nhịp tim rất nhanh"
            else -> "Kết quả bất thường (>130 BPM)"
        }

        val suggestion = when {
            finalBpm <= 0 -> "Chưa có dữ liệu nhịp tim. Hãy đo lại."
            finalBpm < 60 -> "Bạn có thể tập Downward Dog hoặc Đứng một chân hoặc Dang tay chân."
            finalBpm in 60..100 -> "Bạn có thể tập Squat hoặc Chống đẩy."
            finalBpm in 101..120 -> "Bạn nên thư giãn."
            finalBpm in 121..130 -> "Bạn nên nghỉ ngơi, hạn chế vận động mạnh."
            else -> "⚠️ Kết quả bất thường. Vui lòng đo lại cho chính xác."
        }

        // Lưu vào SharedPreferences
        try {
            val prefs = getSharedPreferences("health_data", MODE_PRIVATE)
            prefs.edit()
                .putInt("last_heart_rate_bpm", finalBpm)
                .putString("last_heart_rate_status", status)
                .putString("last_heart_rate_suggestion", suggestion)
                .putLong("last_heart_rate_time", System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving heart rate result to local", e)
        }

        // Lưu vào Firestore
        saveHeartRateToFirestore(finalBpm, status, suggestion, elapsedTime)

        // Track sự kiện đo hoàn tất
        val bundle = Bundle().apply {
            putInt("bpm", finalBpm)
            putString("status", status)
            putLong("duration", elapsedTime)
        }
        analytics.logEvent("complete_heart_rate_measurement", bundle)

        val result = if (finalBpm > 0) {
            "✅ Kết quả đo nhịp tim\n\n" +
                    "❤️ ${finalBpm} BPM\n" +
                    "📊 ${status}\n" +
                    "💡 Gợi ý: ${suggestion}\n" +
                    "⏱️ Thời gian: ${elapsedTime}s\n" +
                    "📈 Frames: ${frameCount}"
        } else {
            "❌ Không đo được nhịp tim\n\n" +
                    "💡 Thử lại:\n" +
                    "• Đặt ngón tay che kín camera\n" +
                    "• Giữ yên không rung\n" +
                    "• Đảm bảo đèn flash sáng"
        }

        updateUI(result)

        Toast.makeText(this,
            if (finalBpm > 0) "Đo thành công: ${finalBpm} BPM"
            else "Đo không thành công", Toast.LENGTH_LONG).show()

        // Mở HeartRateHistoryActivity
        val intent = Intent(this, HeartRateHistoryActivity::class.java)
        startActivity(intent)

        // Auto close after 5 seconds
        handler.postDelayed({
            if (!isDestroyed) finish()
        }, 5000)
    }

    private fun saveHeartRateToFirestore(bpm: Int, status: String, suggestion: String, duration: Long) {
        val user = auth.currentUser
        if (user != null) {
            val heartRateData = HeartRateData(
                bpm = bpm,
                status = status,
                suggestion = suggestion,
                timestamp = System.currentTimeMillis(),
                duration = duration
            )

            db.collection("users").document(user.uid).collection("healthMetrics")
                .document(heartRateData.id)
                .set(heartRateData)
                .addOnSuccessListener {
                    Log.d(TAG, "Heart rate data saved to Firestore")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error saving heart rate to Firestore", e)
                    Toast.makeText(this, "Lỗi lưu heart rate: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Log.w(TAG, "User not logged in, skipping Firestore save")
        }
    }

    private fun stopCamera() {
        try {
            camera?.apply {
                setPreviewCallback(null)
                stopPreview()
                release()
            }
            camera = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera", e)
        }
    }

    private fun updateUI(text: String) {
        if (!isDestroyed) {
            heartRateText.text = text
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                if (surfaceHolder.surface.isValid) {
                    startCamera()
                }
            } else {
                showError("Cần quyền camera để đo nhịp tim")
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isDestroyed = true
        stopCamera()
        try {
            tflite?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleanup", e)
        }
    }
}