package com.example.keepyfitness
import kotlin.math.abs

import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.media.ImageReader
import android.content.Context
import android.content.Intent
import android.media.ImageReader.OnImageAvailableListener
import android.view.Surface
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.graphics.Bitmap
import android.media.Image
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.toColorInt
import com.bumptech.glide.Glide
import com.example.keepyfitness.Model.ExerciseDataModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.Locale

class MainActivity : AppCompatActivity(), OnImageAvailableListener {

    val options = PoseDetectorOptions.Builder()
        .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
        .build()
    val poseDetector = PoseDetection.getClient(options)
    private lateinit var poseOverlay: PoseOverlay
    private lateinit var countTV: TextView
    private lateinit var exerciseDataModel: ExerciseDataModel

    // Workout tracking variables
    private var workoutStartTime: Long = 0
    private var targetCount: Int = 0

    // Form Correction variables - GIẢM SỬ DỤNG để tăng performance
    private lateinit var formCorrector: FormCorrector
    private var feedbackContainer: LinearLayout? = null
    private var feedbackCard: CardView? = null
    private var feedbackIcon: ImageView? = null
    private var feedbackTitle: TextView? = null
    private var feedbackMessage: TextView? = null
    private var formQualityProgress: ProgressBar? = null
    private var formQualityText: TextView? = null
    private var lastFeedbackTime = 0L
    private val feedbackCooldown = 3000L // Tăng từ 3s lên 5s

    // Voice Coach
    private lateinit var voiceCoach: VoiceCoach
    private var lastMotivationTime = 0L
    private val motivationInterval = 15000L // Tăng từ 30s lên 45s để giảm frequency
    private var workoutAnnounced = false

    // Timer variables
    private lateinit var timerText: TextView
    private lateinit var stopWorkoutCard: CardView
    private var workoutTimer: Handler? = null
    private var timerRunnable: Runnable? = null
    private var elapsedSeconds: Long = 0
    private var timerStarted = false // THÊM FLAG để kiểm soát timer

    // Camera variables
    var previewHeight = 0
    var previewWidth = 0
    var sensorOrientation = 0

    // Camera frame processing - TĂNG HIỆU SUẤT MẠNH HƠN
    private var isProcessingFrame = false
    private val yuvBytes = arrayOfNulls<ByteArray>(3)
    private var rgbBytes: IntArray? = null
    private var yRowStride = 0
    private var postInferenceCallback: Runnable? = null
    private var imageConverter: Runnable? = null
    private var rgbFrameBitmap: Bitmap? = null

    // AGGRESSIVE THROTTLING để giảm lag mạnh
    private var lastProcessTime = 0L
    private val processInterval = 120L // ~6–7 FPS
    private val frameSkipInterval = 2  // xử lý 1/2 frames
    // Tăng từ 200ms lên 300ms (~3.3 FPS)
    private var frameSkipCounter = 0
    //private val frameSkipInterval = 5 // Tăng từ 3 lên 5 - chỉ xử lý 1/5 frames

    // Exercise detection variables
    var pushUpCount = 0
    var isLowered = false
    var squatCount = 0
    var isSquatting = false
    var jumpingJackCount = 0
    var isHandsUpAndLegsApart = false
    var isInStartPosition = false
    var plankDogCount = 0
    var isInPlank = false
    var treePoseHoldTime = 0L
    var isInTreePose = false
    // ==== Debounce / cooldown helpers ====
    private val COUNT_COOLDOWN_MS = 800L // khoảng thời gian tối thiểu giữa 2 lần count cùng loại
    private val REQUIRED_CONSECUTIVE = 2
    // Push-up
    private var pushUpState = 0 // 0=IDLE, 1=DOWN_CONFIRMED, 2=COOLDOWN
    private var pushUpConsec = 0
    private var lastPushUpCountTime = 0L
    private var pushDownConsec = 0

    // Squat
    private var squatState = 0
    private var squatConsec = 0
    private var lastSquatCountTime = 0L
    private var squatDownConsec = 0

    // Jumping jack
    private var jjState = 0
    private var jjUpConsec = 0
    private var jjDownConsec = 0
    private var lastJJCountTime = 0L

    // Plank -> Downward Dog
    private var plankDogState = 0
    private var plankDogConsec = 0
    private var lastPlankDogCountTime = 0L


    // Tree pose (you already added)
    private var lastTreePoseStart: Long = 0L

    var treePoseCount = 0

    private var treePoseHoldStart: Long = 0
    private val TREE_HOLD_THRESHOLD_MS = 3000L
    private var lastTreePoseCountTime: Long = 0L
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Safe casting for serializable data
        exerciseDataModel = try {
            intent.getSerializableExtra("data") as? ExerciseDataModel
                ?: throw IllegalArgumentException("Exercise data is required")
        } catch (e: Exception) {
            Log.e("MainActivity", "Lỗi khi lấy dữ liệu bài tập: ${e.message}")
            finish()
            return
        }

        targetCount = intent.getIntExtra("target_count", 50)
        workoutStartTime = System.currentTimeMillis()

        // Initialize components
        formCorrector = FormCorrector()
        voiceCoach = VoiceCoach(this)



        poseOverlay = findViewById(R.id.po)
        countTV = findViewById(R.id.textView)
        timerText = findViewById(R.id.timerText)
        stopWorkoutCard = findViewById(R.id.stopWorkoutCard)
        val countCard = findViewById<android.widget.FrameLayout>(R.id.countCard) // Sửa từ CardView thành FrameLayout

        setupFormFeedbackOverlay()
        setupTimer()
        setupStopWorkoutButton()

        // Set background cho FrameLayout thay vì CardView
        countCard.background = resources.getDrawable(R.drawable.circle_background, null)

        val topCard = findViewById<CardView>(R.id.card2)
        topCard.setBackgroundColor(exerciseDataModel.color)

        val topImg = findViewById<ImageView>(R.id.imageView2)
        Glide.with(applicationContext).asGif().load(exerciseDataModel.image).into(topImg)

        // Request camera permission
        if (checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
            val permission = arrayOf(android.Manifest.permission.CAMERA)
            requestPermissions(permission, 1122)
        } else {
            setFragment()
        }
    }

    private fun setupTimer() {
        workoutTimer = Handler(Looper.getMainLooper())
        timerRunnable = object : Runnable {
            override fun run() {
                elapsedSeconds++
                updateTimerDisplay()
                workoutTimer?.postDelayed(this, 1000)
            }
        }
        // CHỈ START TIMER 1 LẦN - không gọi startTimer() ở đây
    }

    private fun startTimer() {
        if (!timerStarted) { // CHỈ START KHI CHƯA START
            workoutTimer?.post(timerRunnable!!)
            timerStarted = true
        }
    }

    private fun stopTimer() {
        workoutTimer?.removeCallbacks(timerRunnable!!)
        timerStarted = false
    }

    private fun updateTimerDisplay() {
        val minutes = elapsedSeconds / 60
        val seconds = elapsedSeconds % 60
        timerText.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun setupStopWorkoutButton() {
        stopWorkoutCard.setOnClickListener {
            Log.d("MainActivity", "Nút dừng tập đã được nhấn.")
            stopWorkout()
        }

        // Đảm bảo button có thể click được
        stopWorkoutCard.isClickable = true
        stopWorkoutCard.isFocusable = true
    }

    private fun stopWorkout() {
        Log.d("MainActivity", "Đang ngừng tập luyện...")
        stopTimer()

        // Get current count based on exercise type
        val currentCount = when(exerciseDataModel.id) {
            1 -> pushUpCount
            2 -> squatCount
            3 -> jumpingJackCount
            4 -> plankDogCount
            else -> 0
        }

        Log.d("MainActivity", "Current count: $currentCount, Target: $targetCount, Duration: $elapsedSeconds")

        try {
            // Navigate to results screen
            val intent = Intent(this, WorkoutResultsActivity::class.java)
            intent.putExtra("exercise_data", exerciseDataModel)
            intent.putExtra("completed_count", currentCount)
            intent.putExtra("target_count", targetCount)
            intent.putExtra("workout_duration", elapsedSeconds)

            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error navigating to results: ${e.message}")
            // Fallback: just go back to home screen
            val intent = Intent(this, HomeScreen::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun setupFormFeedbackOverlay() {
        val inflater = layoutInflater
        val feedbackOverlay = inflater.inflate(R.layout.form_feedback_overlay, findViewById(R.id.main), false)

        feedbackContainer = feedbackOverlay.findViewById(R.id.feedbackContainer)
        feedbackCard = feedbackOverlay.findViewById(R.id.feedbackCard)
        feedbackIcon = feedbackOverlay.findViewById(R.id.feedbackIcon)
        feedbackTitle = feedbackOverlay.findViewById(R.id.feedbackTitle)
        feedbackMessage = feedbackOverlay.findViewById(R.id.feedbackMessage)
        formQualityProgress = feedbackOverlay.findViewById(R.id.formQualityProgress)
        formQualityText = feedbackOverlay.findViewById(R.id.formQualityText)

        val mainLayout = findViewById<ConstraintLayout>(R.id.main)
        val layoutParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.topToBottom = R.id.card2
        layoutParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        layoutParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        layoutParams.setMargins(0, 16, 0, 0)

        feedbackOverlay.layoutParams = layoutParams
        mainLayout.addView(feedbackOverlay)

        feedbackOverlay.findViewById<ImageView>(R.id.dismissFeedback).setOnClickListener {
            hideFeedback()
        }
    }

    private fun showFeedback(feedback: String, isPositive: Boolean, formQuality: Int) {
        if (System.currentTimeMillis() - lastFeedbackTime < feedbackCooldown) return
        lastFeedbackTime = System.currentTimeMillis()

        feedbackContainer?.visibility = android.view.View.VISIBLE
        feedbackCard?.setCardBackgroundColor(
            if (isPositive) "#4CAF50".toColorInt()
            else "#F44336".toColorInt()
        )
        feedbackIcon?.setImageResource(
            if (isPositive) android.R.drawable.ic_dialog_info
            else android.R.drawable.ic_dialog_alert
        )
        feedbackTitle?.text = if (isPositive) "Good Job!" else "Correction Needed!"
        feedbackMessage?.text = feedback

        formQualityProgress?.progress = formQuality
        formQualityText?.text = getString(R.string.form_quality_format, formQuality)

        val progressColor = when {
            formQuality >= 80 -> "#4CAF50".toColorInt()
            formQuality >= 60 -> "#FF9800".toColorInt()
            else -> "#F44336".toColorInt()
        }
        formQualityProgress?.progressTintList = android.content.res.ColorStateList.valueOf(progressColor)

        Handler(Looper.getMainLooper()).postDelayed({
            hideFeedback()
        }, 4000)
    }

    private fun hideFeedback() {
        feedbackContainer?.visibility = android.view.View.GONE
    }

    protected fun setFragment() {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var cameraId: String? = null
        try {
            cameraId = manager.cameraIdList[0]
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        val camera2Fragment = CameraConnectionFragment.newInstance(
            object : CameraConnectionFragment.ConnectionCallback {
                override fun onPreviewSizeChosen(size: Size?, cameraRotation: Int) {
                    previewHeight = size!!.height
                    previewWidth = size.width
                    poseOverlay.imageHeight = previewHeight
                    sensorOrientation = cameraRotation - getScreenOrientation()
                    poseOverlay.sensorOrientation = sensorOrientation
                }

                override fun onTextureViewChosen(width: Int, height: Int) {
                    poseOverlay.videoWidth = width
                    poseOverlay.videoHeight = height
                }
            },
            this,
            R.layout.camera_fragment,
            Size(320, 240)
        )
        camera2Fragment.setCamera(cameraId)
        supportFragmentManager.beginTransaction().replace(R.id.container, camera2Fragment).commit()
    }

    @Suppress("DEPRECATION")
    protected fun getScreenOrientation(): Int {
        return when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_270 -> 270
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_90 -> 90
            else -> 0
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setFragment()
        } else {
            finish()
        }
    }

    override fun onImageAvailable(reader: ImageReader) {
        if (previewWidth == 0 || previewHeight == 0) return

        val image = reader.acquireLatestImage() ?: return
        try {
            // Skip frames
            frameSkipCounter++
            if (frameSkipCounter % frameSkipInterval != 0) {
                image.close()
                return
            }

            // Throttle
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastProcessTime < processInterval) {
                image.close()
                return
            }
            lastProcessTime = currentTime

            synchronized(this) {
                if (isProcessingFrame) {
                    image.close()
                    return
                }
                isProcessingFrame = true
            }

            val planes = image.planes
            fillBytes(planes, yuvBytes) // dùng hàm gốc của bạn
            yRowStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride

            // Đảm bảo buffer đúng kích thước
            val needed = previewWidth * previewHeight
            if (rgbBytes == null || rgbBytes!!.size != needed) {
                rgbBytes = IntArray(needed)
            }

            // Reuse hoặc tạo bitmap mới nếu cần
            if (rgbFrameBitmap == null ||
                rgbFrameBitmap?.width != previewWidth ||
                rgbFrameBitmap?.height != previewHeight
            ) {
                try {
                    rgbFrameBitmap?.recycle()
                } catch (_: Exception) { }
                rgbFrameBitmap = Bitmap.createBitmap(
                    previewWidth, previewHeight,
                    Bitmap.Config.ARGB_8888
                )
            }

            // Kiểm tra yuvBytes trước khi convert
            if (yuvBytes[0] == null || yuvBytes[1] == null || yuvBytes[2] == null) {
                Log.w("MainActivity", "yuvBytes chưa sẵn sàng, skip frame")
                image.close()
                isProcessingFrame = false
                return
            }

            imageConverter = Runnable {
                ImageUtils.convertYUV420ToARGB8888(
                    yuvBytes[0]!!, yuvBytes[1]!!, yuvBytes[2]!!,
                    previewWidth, previewHeight,
                    yRowStride, uvRowStride, uvPixelStride,
                    rgbBytes!!
                )
            }

            postInferenceCallback = Runnable {
                try { image.close() } catch (_: Exception) { }
                isProcessingFrame = false
            }

            processImage()
        } catch (e: Exception) {
            Log.e("MainActivity", "Exception onImageAvailable: ${e.message}", e)
            try { image.close() } catch (_: Exception) { }
            isProcessingFrame = false
        }
    }

    private fun processImage() {
        try {
            imageConverter?.run()

            rgbFrameBitmap?.setPixels(
                rgbBytes!!, 0, previewWidth,
                0, 0, previewWidth, previewHeight
            )

            val inputImage = InputImage.fromBitmap(rgbFrameBitmap!!, sensorOrientation)

            poseDetector.process(inputImage)
                .addOnSuccessListener { results ->
                    // Giữ nguyên logic gốc của bạn
                    poseOverlay.setPose(results)

                    if (!timerStarted) startTimer()
                    if (!workoutAnnounced) {
                        voiceCoach.announceWorkoutStart(exerciseDataModel.title, targetCount)
                        workoutAnnounced = true
                    }

                    if (frameSkipCounter % 6 == 0) analyzeFormAndGiveFeedback(results)
                    detectAndCountExercise(results)
                }
                .addOnFailureListener { e ->
                    Log.e("PoseDetection", "Failed to process image", e)
                }
                .addOnCompleteListener {
                    postInferenceCallback?.run()
                }
        } catch (oom: OutOfMemoryError) {
            Log.e("MainActivity", "OOM in processImage: ${oom.message}")
            postInferenceCallback?.run()
        } catch (e: Exception) {
            Log.e("MainActivity", "Exception in processImage: ${e.message}", e)
            postInferenceCallback?.run()
        }
    }


    // TÁCH RIÊNG form analysis để optimize
    private fun analyzeFormAndGiveFeedback(results: Pose) {
        val feedbacks = formCorrector.analyzeForm(exerciseDataModel.id, results)
        val formQuality = formCorrector.calculateFormQuality(exerciseDataModel.id, results)

        runOnUiThread {
            formQualityProgress?.progress = formQuality
            formQualityText?.text = getString(R.string.form_quality_format, formQuality)
        }

        // Voice feedback for form corrections - THROTTLED
        val criticalFeedback = feedbacks.firstOrNull {
            it.severity == com.example.keepyfitness.Model.FeedbackSeverity.CRITICAL
        }
        val warningFeedback = feedbacks.firstOrNull {
            it.severity == com.example.keepyfitness.Model.FeedbackSeverity.WARNING
        }
        val positiveFeedback = feedbacks.firstOrNull {
            it.severity == com.example.keepyfitness.Model.FeedbackSeverity.INFO &&
                    (it.message.contains("Perfect") || it.message.contains("Great") || it.message.contains("Tuyệt vời"))
        }

        when {
            criticalFeedback != null -> {
                runOnUiThread {
                    showFeedback(criticalFeedback.message, false, formQuality)
                    voiceCoach.giveFormFeedback(criticalFeedback.message, false)
                }
            }
            warningFeedback != null -> {
                runOnUiThread {
                    showFeedback(warningFeedback.message, false, formQuality)
                    voiceCoach.giveFormFeedback(warningFeedback.message, false)
                }
            }
            positiveFeedback != null -> {
                runOnUiThread {
                    showFeedback(positiveFeedback.message, true, formQuality)
                    voiceCoach.giveFormFeedback(positiveFeedback.message, true)
                }
            }
        }
    }

    // TÁCH RIÊNG exercise detection để tối ưu
    private fun detectAndCountExercise(results: Pose) {
        var currentCount = 0
        when(exerciseDataModel.id) {
            1 -> {
                val oldCount = pushUpCount
                detectPushUp(results)
                currentCount = pushUpCount
                if (pushUpCount > oldCount) {
                    voiceCoach.announceCount(pushUpCount, "tập chống đẩy")
                    voiceCoach.announceProgress(pushUpCount, targetCount)
                }
                runOnUiThread { countTV.text = pushUpCount.toString() }
            }
            2 -> {
                val oldCount = squatCount
                detectSquat(results)
                currentCount = squatCount
                if (squatCount > oldCount) {
                    voiceCoach.announceCount(squatCount, "squat")
                    voiceCoach.announceProgress(squatCount, targetCount)
                }
                runOnUiThread { countTV.text = squatCount.toString() }
            }
            3 -> {
                val oldCount = jumpingJackCount
                detectJumpingJack(results)
                currentCount = jumpingJackCount
                if (jumpingJackCount > oldCount) {
                    voiceCoach.announceCount(jumpingJackCount, "Dang tay chân Cardio")
                    voiceCoach.announceProgress(jumpingJackCount, targetCount)
                }
                runOnUiThread { countTV.text = jumpingJackCount.toString() }
            }
            4 -> {
                val oldCount = plankDogCount
                detectPlankToDownwardDog(results)
                currentCount = plankDogCount
                if (plankDogCount > oldCount) {
                    voiceCoach.announceCount(plankDogCount, "Downward Dog")
                    voiceCoach.announceProgress(plankDogCount, targetCount)
                }
                runOnUiThread { countTV.text = plankDogCount.toString() }
            }
            5 -> {
                val oldCount = treePoseCount
                detectTreePose(results)
                if (treePoseCount > oldCount) {
                    voiceCoach.announceCount(treePoseCount, "đứng một chân")
                    voiceCoach.announceProgress(treePoseCount, targetCount)
                }
                runOnUiThread { countTV.text = treePoseCount.toString() }
            }


        }

        // Motivational messages - GIẢM TẦN SUẤT
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastMotivationTime > motivationInterval && currentCount > 0) {
            voiceCoach.giveMotivation()
            lastMotivationTime = currentTime
        }
    }

    protected fun fillBytes(planes: Array<Image.Plane>, yuvBytes: Array<ByteArray?>) {
        for (i in planes.indices) {
            val buffer = planes[i].buffer
            if (yuvBytes[i] == null) {
                yuvBytes[i] = ByteArray(buffer.capacity())
            }
            buffer.get(yuvBytes[i]!!)
        }
    }

    private fun resetExerciseStates() {
        pushUpState = 0; pushUpConsec = 0; lastPushUpCountTime = 0L
        squatState = 0; squatConsec = 0; lastSquatCountTime = 0L
        jjState = 0; jjUpConsec = 0; jjDownConsec = 0; lastJJCountTime = 0L
        plankDogState = 0; plankDogConsec = 0; lastPlankDogCountTime = 0L
        treePoseCount = 0; isInTreePose = false; treePoseHoldStart = 0L; lastTreePoseCountTime = 0L
    }


    fun detectPushUp(pose: Pose) {
        val ls = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val le = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val lw = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val lh = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val lk = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        if (listOf(ls, le, lw, lh, lk).any { it == null }) return

        val elbowAngle = calculateAngle(ls!!, le!!, lw!!)
        val torsoAngle = calculateAngle(ls, lh!!, lk!!)
        val inPlank = torsoAngle > 140

        // confirm "down" on consecutive frames
        if (elbowAngle < 110 && inPlank) pushUpConsec++ else pushUpConsec = 0

        // state transitions
        val now = System.currentTimeMillis()
        when (pushUpState) {
            0 -> if (pushUpConsec >= REQUIRED_CONSECUTIVE) {
                pushUpState = 1  // DOWN_CONFIRMED
                Log.d("PushUpState", "DOWN confirmed")
            }
            1 -> { // waiting for up
                if (elbowAngle > 150) {
                    if (now - lastPushUpCountTime > COUNT_COOLDOWN_MS) {
                        pushUpCount++
                        lastPushUpCountTime = now
                        pushUpState = 2 // cooldown
                        pushUpConsec = 0
                        Log.d("PushUpState", "COUNTED pushUp=$pushUpCount")
                    }
                }
            }
            2 -> { // cooldown -> back to idle after cooldown period
                if (now - lastPushUpCountTime > COUNT_COOLDOWN_MS) {
                    pushUpState = 0
                }
            }
        }
    }

    fun detectSquat(pose: Pose) {
        val lh = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val lk = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val la = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        if (listOf(lh, lk, la).any { it == null }) return

        val kneeAngle = calculateAngle(lh!!, lk!!, la!!)
        val hipBelowKnee = lh.position.y > lk.position.y

        if (kneeAngle < 100 && hipBelowKnee) squatConsec++ else squatConsec = 0

        val now = System.currentTimeMillis()
        when (squatState) {
            0 -> if (squatConsec >= REQUIRED_CONSECUTIVE) squatState = 1
            1 -> if (kneeAngle > 150) {
                if (now - lastSquatCountTime > COUNT_COOLDOWN_MS) {
                    squatCount++
                    lastSquatCountTime = now
                    squatState = 2
                    squatConsec = 0
                    Log.d("Squat", "COUNTED squat=$squatCount")
                }
            }
            2 -> if (now - lastSquatCountTime > COUNT_COOLDOWN_MS) squatState = 0
        }
    }


    fun detectJumpingJack(pose: Pose) {
        val lw = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rw = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val la = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val ra = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
        val lh = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rh = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val ls = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rs = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        if (listOf(lw, rw, la, ra, lh, rh, ls, rs).any { it == null }) return

        val avgShoulderY = (ls!!.position.y + rs!!.position.y) / 2
        val avgWristY = (lw!!.position.y + rw!!.position.y) / 2
        val handsAbove = avgWristY < avgShoulderY - 15

        val hipWidth = distance(lh!!, rh!!)
        val ankleDist = distance(la!!, ra!!)
        val legsApart = ankleDist > hipWidth * 1.3

        val handsDown = avgWristY > avgShoulderY + 15
        val legsTogether = ankleDist <= hipWidth * 1.1

        if (handsAbove && legsApart) jjUpConsec++ else jjUpConsec = 0
        if (handsDown && legsTogether) jjDownConsec++ else jjDownConsec = 0

        val now = System.currentTimeMillis()
        when (jjState) {
            0 -> if (jjUpConsec >= REQUIRED_CONSECUTIVE) jjState = 1
            1 -> if (jjDownConsec >= REQUIRED_CONSECUTIVE) {
                if (now - lastJJCountTime > COUNT_COOLDOWN_MS) {
                    jumpingJackCount++
                    lastJJCountTime = now
                    jjState = 2
                    jjUpConsec = 0; jjDownConsec = 0
                    Log.d("JumpJack", "COUNTED JJ=$jumpingJackCount")
                }
            }
            2 -> if (now - lastJJCountTime > COUNT_COOLDOWN_MS) jjState = 0
        }
    }


    fun detectPlankToDownwardDog(pose: Pose) {
        val ls = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rs = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val lh = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rh = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val la = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val ra = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
        val lw = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rw = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        if (listOf(ls, rs, lh, rh, la, ra, lw, rw).any { it == null }) return

        val shoulderY = (ls!!.position.y + rs!!.position.y) / 2
        val hipY = (lh!!.position.y + rh!!.position.y) / 2
        val ankleY = (la!!.position.y + ra!!.position.y) / 2
        val wristY = (lw!!.position.y + rw!!.position.y) / 2

        val bodyAlign = abs(shoulderY - hipY) < 80 && abs(hipY - ankleY) < 80
        val handsOnGround = abs(wristY - shoulderY) < 150
        val inPlank = bodyAlign && handsOnGround

        val hipsElevated = hipY < shoulderY - 20 && hipY < ankleY - 10
        val inDownwardDog = hipsElevated && handsOnGround

        if (inDownwardDog) plankDogConsec++ else plankDogConsec = 0

        val now = System.currentTimeMillis()
        when (plankDogState) {
            0 -> if (inPlank) plankDogState = 10
            10 -> if (plankDogConsec >= REQUIRED_CONSECUTIVE && inDownwardDog) {
                if (now - lastPlankDogCountTime > COUNT_COOLDOWN_MS) {
                    plankDogCount++
                    lastPlankDogCountTime = now
                    plankDogState = 20
                    plankDogConsec = 0
                    Log.d("PlankDog", "COUNTED plank->dog=$plankDogCount")
                }
            }
            20 -> if (now - lastPlankDogCountTime > COUNT_COOLDOWN_MS) plankDogState = 0
        }
    }

    fun detectTreePose(pose: Pose) {
        val la = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val ra = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
        if (la == null || ra == null) return

        val diffY = abs(la.position.y - ra.position.y)
        val threshold = previewHeight * 0.25f
        val currentlyInPose = diffY > threshold

        if (currentlyInPose) {
            if (!isInTreePose) {
                isInTreePose = true
                treePoseHoldStart = System.currentTimeMillis()
                Log.d("TreePose", "Start holding")
            } else {
                val holdTime = System.currentTimeMillis() - treePoseHoldStart
                if (holdTime >= TREE_HOLD_THRESHOLD_MS) {
                    val now = System.currentTimeMillis()
                    if (now - lastTreePoseCountTime > TREE_HOLD_THRESHOLD_MS) {
                        treePoseCount++
                        lastTreePoseCountTime = now
                        Log.d("TreePose", "COUNTED treePose=$treePoseCount")
                    }
                }
            }
        } else {
            if (isInTreePose) {
                Log.d("TreePose", "Exit pose, held ${(System.currentTimeMillis()-treePoseHoldStart)/1000}s")
            }
            isInTreePose = false
        }
    }



    fun calculateAngle(first: PoseLandmark, mid: PoseLandmark, last: PoseLandmark): Double {
        val a = distance(mid, last)
        val b = distance(first, mid)
        val c = distance(first, last)
        return kotlin.math.acos((b * b + a * a - c * c) / (2 * b * a)) * (180 / kotlin.math.PI)
    }

    fun distance(p1: PoseLandmark, p2: PoseLandmark): Double {
        val dx = p1.position.x - p2.position.x
        val dy = p1.position.y - p2.position.y
        return kotlin.math.sqrt((dx * dx + dy * dy).toDouble())
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
        voiceCoach.shutdown()
    }

    override fun onPause() {
        super.onPause()
        stopTimer()
    }

    override fun onResume() {
        super.onResume()
        if (timerRunnable != null) {
            startTimer()
        }
    }
}
