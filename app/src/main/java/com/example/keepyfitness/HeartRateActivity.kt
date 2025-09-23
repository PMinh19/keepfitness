package com.example.keepyfitness

import android.Manifest
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

    companion object {
        private const val TAG = "HeartRateActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_heart_rate_simple)

        try {
            heartRateText = findViewById(R.id.tvHeartRate)
            surfaceView = findViewById(R.id.surfaceView)

            surfaceHolder = surfaceView.holder
            surfaceHolder.addCallback(this)

            // Load TensorFlow Lite model
            loadTensorFlowModel()

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
        // YUV420 format: Y plane followed by U and V planes
        var redSum = 0.0
        var pixelCount = 0

        // Sample center region (heart rate is best detected from fingertip)
        val centerX = width / 2
        val centerY = height / 2
        val sampleSize = Math.min(width, height) / 4 // 1/4 of the smaller dimension

        for (y in (centerY - sampleSize / 2) until (centerY + sampleSize / 2)) {
            for (x in (centerX - sampleSize / 2) until (centerX + sampleSize / 2)) {
                if (x >= 0 && x < width && y >= 0 && y < height) {
                    val index = y * width + x
                    if (index < data.size) {
                        // Y value represents luminance
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
        if (redValuesList.size < 60) return 0 // Need at least 2 seconds of data

        try {
            // Use recent values for calculation
            val recentValues = redValuesList.takeLast(90) // Last 3 seconds

            // Apply simple bandpass filter (0.5-4 Hz for heart rate)
            val filteredValues = applySimpleBandpassFilter(recentValues)

            // Find peaks
            val peaks = findPeaks(filteredValues)

            if (peaks.size >= 2) {
                // Calculate average interval between peaks
                val intervals = mutableListOf<Double>()
                for (i in 1 until peaks.size) {
                    intervals.add((peaks[i] - peaks[i - 1]).toDouble())
                }

                if (intervals.isNotEmpty()) {
                    val avgInterval = intervals.average()
                    val fps = 30.0 // Assuming 30 FPS
                    val bpm = (60.0 * fps / avgInterval).roundToInt()

                    // Validate heart rate range
                    return if (bpm in 40..200) bpm else 0
                }
            }

            // Fallback: Use TensorFlow model if available
            return useTensorFlowForHeartRate(recentValues)

        } catch (e: Exception) {
            Log.e(TAG, "Error calculating heart rate", e)
            return 0
        }
    }

    private fun applySimpleBandpassFilter(values: List<Double>): List<Double> {
        // Simple moving average to smooth the signal
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

            // Prepare input for TensorFlow model (36x36x3)
            val inputArray = Array(1) { Array(36) { Array(36) { FloatArray(3) } } }

            // Convert signal values to image-like format
            for (i in 0 until 36) {
                for (j in 0 until 36) {
                    val valueIndex = (i * 36 + j) % values.size
                    val normalizedValue = (values[valueIndex] / 255.0).toFloat()
                    inputArray[0][i][j][0] = normalizedValue // Red channel
                    inputArray[0][i][j][1] = normalizedValue // Green channel
                    inputArray[0][i][j][2] = normalizedValue // Blue channel
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
            finalBpm < 60 -> "Nhịp tim chậm (Bradycardia)"
            finalBpm in 60..100 -> "Nhịp tim bình thường"
            finalBpm > 100 -> "Nhịp tim nhanh (Tachycardia)"
            else -> "Không đo được"
        }

        // Build training suggestion based on heart rate
        val suggestion = when {
            finalBpm <= 0 -> "Chưa có dữ liệu nhịp tim. Hãy đo lại."
            finalBpm < 60 -> "Nhịp tim thấp khi nghỉ ngơi. Nếu không có triệu chứng bất thường, bình thường với người vận động nhiều."
            finalBpm in 60..100 -> "Nhịp tim bình thường khi nghỉ ngơi. Có thể tập luyện nhẹ đến trung bình."
            finalBpm in 101..120 -> "Nhịp tim cao khi nghỉ ngơi. Hãy nghỉ ngơi, thư giãn, và đo lại nếu cần."
            else -> "Nhịp tim rất cao! Ngưng hoạt động, theo dõi sát và tham khảo ý kiến bác sĩ nếu vẫn cao."
        }


        // Persist result for HomeScreen suggestion
        try {
            val prefs = getSharedPreferences("health_data", MODE_PRIVATE)
            prefs.edit()
                .putInt("last_heart_rate_bpm", finalBpm)
                .putString("last_heart_rate_status", status)
                .putString("last_heart_rate_suggestion", suggestion)
                .putLong("last_heart_rate_time", System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving heart rate result", e)
        }

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

        // Auto close after 5 seconds
        handler.postDelayed({
            if (!isDestroyed) finish()
        }, 5000)
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
