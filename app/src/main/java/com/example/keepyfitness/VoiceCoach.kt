package com.example.keepyfitness

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

class VoiceCoach(private val context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "VoiceCoach"
    }

    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private var speechQueue = mutableListOf<String>()

    // THROTTLING cho voice feedback để tránh spam
    private var lastSpeechTime = 0L
    private val speechCooldown = 3000L // Tăng từ 2 giây lên 3 giây để giảm lag
    private var lastFormFeedbackTime = 0L
    private val formFeedbackCooldown = 5000L // Tăng từ 4 giây lên 5 giây

    init {
        initTextToSpeech()
    }

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.let { tts ->
                // FORCE ENGLISH LANGUAGE - không sử dụng system default
                val englishResult = tts.setLanguage(Locale.ENGLISH)

                if (englishResult == TextToSpeech.LANG_MISSING_DATA ||
                    englishResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "English language not supported, trying US English")
                    val usEnglishResult = tts.setLanguage(Locale.US)

                    if (usEnglishResult == TextToSpeech.LANG_MISSING_DATA ||
                        usEnglishResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e(TAG, "English languages not available")
                        return
                    }
                }

                // OPTIMIZE SPEECH SETTINGS
                tts.setSpeechRate(1.2f) // Tăng tốc độ nói để giảm thời gian
                tts.setPitch(1.0f)

                isInitialized = true
                Log.d(TAG, "TextToSpeech initialized with English language")

                // Process queued messages
                processSpeechQueue()
            }
        } else {
            Log.e(TAG, "TextToSpeech initialization failed")
        }
    }

    private fun processSpeechQueue() {
        if (isInitialized && speechQueue.isNotEmpty()) {
            // CHỈ XỬ LÝ message đầu tiên để tránh spam
            val firstMessage = speechQueue.firstOrNull()
            if (firstMessage != null) {
                speakNow(firstMessage)
            }
            speechQueue.clear()
        }
    }

    private fun speakNow(message: String) {
        // KIỂM TRA COOLDOWN trước khi nói
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSpeechTime < speechCooldown) {
            return // Skip nếu nói quá gần
        }
        lastSpeechTime = currentTime

        // FORCE ENGLISH trước mỗi lần nói
        textToSpeech?.setLanguage(Locale.ENGLISH)
        textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun speak(message: String) {
        if (isInitialized) {
            speakNow(message)
        } else {
            // CHỈ QUEUE message cuối cùng để tránh spam
            speechQueue.clear()
            speechQueue.add(message)
        }
    }

    // Exercise-specific coaching messages
    fun announceExerciseStart(exerciseName: String) {
        speak("Starting $exerciseName. Get ready!")
    }

    // Thêm phương thức thông báo bắt đầu workout với target
    fun announceWorkoutStart(exerciseName: String, targetCount: Int) {
        speak("Start $exerciseName. Target $targetCount. Go!")
    }

    // PHƯƠNG THỨC CHÍNH: Thông báo lỗi động tác - TIẾNG ANH
    fun announceFormError(errorMessage: String) {
        speak("Form correction: $errorMessage")
    }

    // THROTTLED form feedback - TIẾNG ANH
    fun giveFormFeedback(feedback: String, isPositive: Boolean) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFormFeedbackTime < formFeedbackCooldown) {
            return
        }
        lastFormFeedbackTime = currentTime

        // SHORTER MESSAGES để giảm thời gian nói
        if (isPositive) {
            speak("Good form")
        } else {
            speak("Fix form")
        }
    }

    // Thông báo lỗi cụ thể cho từng bài tập - TOÀN BỘ TIẾNG ANH
    fun announcePushUpErrors(errorType: String) {
        when (errorType) {
            "ELBOW_FLARE" -> speak("Elbows too wide. Keep elbows close to your body")
            "BACK_SAG" -> speak("Keep your back straight. Engage your core")
            "INCOMPLETE_RANGE" -> speak("Go lower. Lower your chest closer to the ground")
            "HEAD_POSITION" -> speak("Look down at the ground. Keep neutral head position")
            else -> speak("Watch your push up form")
        }
    }

    fun announceSquatErrors(errorType: String) {
        when (errorType) {
            "KNEE_CAVE" -> speak("Knees caving in. Push knees out")
            "FORWARD_LEAN" -> speak("Keep chest up. Don't lean forward")
            "HEEL_LIFT" -> speak("Keep feet flat on the ground")
            "SHALLOW_SQUAT" -> speak("Go deeper. Squat down lower")
            "KNEE_FORWARD" -> speak("Knees too far forward. Push hips back")
            else -> speak("Watch your squat form")
        }
    }

    fun announceJumpingJackErrors(errorType: String) {
        when (errorType) {
            "ARMS_LOW" -> speak("Raise arms higher. Hands above your head")
            "FEET_NARROW" -> speak("Jump wider. Spread legs further apart")
            "TIMING_OFF" -> speak("Sync your arms and legs. Move together")
            "INCOMPLETE_RETURN" -> speak("Return to starting position completely")
            else -> speak("Watch your jumping jack form")
        }
    }

    fun announcePlankErrors(errorType: String) {
        when (errorType) {
            "HIP_SAG" -> speak("Hips too low. Lift your hips up")
            "HIP_HIGH" -> speak("Hips too high. Lower your hips")
            "ARM_POSITION" -> speak("Place hands directly under shoulders")
            "HEAD_POSITION" -> speak("Look down at the floor. Keep neutral neck")
            else -> speak("Watch your plank form")
        }
    }

    // Thông báo động viên - TIẾNG ANH
    fun giveMotivation() {
        val shortMotivations = listOf(
            "Great job!",
            "Keep going!",
            "You got this!",
            "Nice work!",
            "Perfect!"
        )
        speak(shortMotivations.random())
    }

    // Thông báo cảnh báo an toàn - TIẾNG ANH
    fun announceSafetyWarning(warning: String) {
        speak("Safety warning: $warning")
    }

    // Thông báo chất lượng động tác - TIẾNG ANH
    fun announceFormQuality(quality: Int) {
        when {
            quality >= 90 -> speak("Excellent form quality!")
            quality >= 80 -> speak("Good form quality!")
            quality >= 70 -> speak("Form quality is okay")
            quality >= 60 -> speak("Need to improve form")
            else -> speak("Focus on your posture")
        }
    }

    // SIMPLIFIED counting - chỉ nói số
    fun announceCount(count: Int, exerciseName: String) {
        speak("$count")
    }

    // OPTIMIZED progress announcements
    fun announceProgress(current: Int, target: Int) {
        val percentage = (current.toFloat() / target * 100).toInt()
        when {
            current == target -> speak("Complete!")
            current == target / 2 -> speak("Halfway!")
            percentage % 25 == 0 && percentage > 0 -> speak("$percentage percent!")
        }
    }

    fun announceRep(currentRep: Int, totalReps: Int) {
        speak("$currentRep") // Chỉ nói số
        if (currentRep == totalReps) {
            speak("Exercise complete! Great job!")
        } else if (currentRep == totalReps / 2) {
            speak("Halfway there! Keep going!")
        }
    }

    fun announceCountdown(seconds: Int) {
        when (seconds) {
            in 1..5 -> speak("$seconds")
            10 -> speak("10 seconds remaining")
            else -> if (seconds % 10 == 0) speak("$seconds seconds")
        }
    }

    fun announceMotivation() {
        val motivationalMessages = listOf(
            "You're doing great!",
            "Keep it up!",
            "Stay strong!",
            "Perfect form!",
            "You've got this!",
            "Amazing work!",
            "Keep pushing!"
        )
        speak(motivationalMessages.random())
    }

    fun announceFormCorrection(correction: String) {
        speak("Watch your form: $correction")
    }

    fun announceWorkoutComplete() {
        speak("Workout complete! Excellent work today!")
    }

    fun announceRest(seconds: Int) {
        speak("Rest for $seconds seconds")
    }

    fun stop() {
        textToSpeech?.stop()
    }

    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isInitialized = false
        speechQueue.clear()
    }

    fun isSpeaking(): Boolean {
        return textToSpeech?.isSpeaking ?: false
    }

    fun setLanguage(locale: Locale) {
        textToSpeech?.setLanguage(locale)
    }

    fun setSpeechRate(rate: Float) {
        textToSpeech?.setSpeechRate(rate)
    }

    fun setPitch(pitch: Float) {
        textToSpeech?.setPitch(pitch)
    }
}
