package com.example.keepyfitness

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.keepyfitness.Model.ExerciseDataModel
import com.example.keepyfitness.Model.WorkoutHistory
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class WorkoutResultsActivity : AppCompatActivity() {

    private lateinit var exerciseDataModel: ExerciseDataModel
    private var completedCount: Int = 0
    private var targetCount: Int = 0
    private var workoutDuration: Long = 0 // in seconds
    private var caloriesBurned: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout_results)

        // Get data from intent
        exerciseDataModel = intent.getSerializableExtra("exercise_data") as ExerciseDataModel
        completedCount = intent.getIntExtra("completed_count", 0)
        targetCount = intent.getIntExtra("target_count", 0)
        workoutDuration = intent.getLongExtra("workout_duration", 0)

        // Calculate calories burned (rough estimate)
        caloriesBurned = calculateCalories(exerciseDataModel.id, completedCount, workoutDuration)

        setupUI()
        saveWorkoutHistory()
    }

    private fun setupUI() {
        val exerciseImage = findViewById<ImageView>(R.id.exerciseImage)
        val exerciseName = findViewById<TextView>(R.id.exerciseName)
        val completedCountText = findViewById<TextView>(R.id.completedCount)
        val targetCountText = findViewById<TextView>(R.id.targetCount)
        val workoutDurationText = findViewById<TextView>(R.id.workoutDuration)
        val caloriesBurnedText = findViewById<TextView>(R.id.caloriesBurned)
        val completionPercentage = findViewById<TextView>(R.id.completionPercentage)
        val achievementMessage = findViewById<TextView>(R.id.achievementMessage)
        val btnWorkoutAgain = findViewById<MaterialButton>(R.id.btnWorkoutAgain)
        val btnFinish = findViewById<MaterialButton>(R.id.btnFinish)

        // Set exercise image and name
        Glide.with(this).asGif().load(exerciseDataModel.image).into(exerciseImage)
        exerciseName.text = exerciseDataModel.title

        // Set results
        completedCountText.text = "$completedCount reps"
        targetCountText.text = "$targetCount reps"

        // Format duration
        val minutes = workoutDuration / 60
        val seconds = workoutDuration % 60
        workoutDurationText.text = if (minutes > 0) {
            "${minutes} min ${seconds} sec"
        } else {
            "${seconds} sec"
        }

        caloriesBurnedText.text = "${caloriesBurned.toInt()} cal"

        // Calculate completion percentage
        val percentage = if (targetCount > 0) {
            (completedCount.toFloat() / targetCount * 100).toInt()
        } else {
            0
        }
        completionPercentage.text = "$percentage%"

        // Set achievement message based on performance
        achievementMessage.text = when {
            percentage >= 100 -> "Excellent! You've achieved your goal! 🎉"
            percentage >= 75 -> "Great job! You're almost there! 💪"
            percentage >= 50 -> "Good work! Keep pushing yourself! 👍"
            percentage >= 25 -> "Nice start! You can do better next time! 🔥"
            else -> "Every start counts! Keep going! 💪"
        }

        // Set button listeners
        btnWorkoutAgain.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("data", exerciseDataModel)
            intent.putExtra("target_count", targetCount)
            startActivity(intent)
            finish()
        }

        btnFinish.setOnClickListener {
            val intent = Intent(this, HomeScreen::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun calculateCalories(exerciseId: Int, reps: Int, duration: Long): Double {
        // Rough calorie calculations based on exercise type
        val baseCaloriesPerRep = when (exerciseId) {
            1 -> 0.35 // Push ups
            2 -> 0.4  // Squats
            3 -> 0.5  // Jumping jacks
            4 -> 0.3  // Plank to downward dog
            else -> 0.3
        }

        // Factor in duration (higher intensity = more calories)
        val intensityFactor = if (duration > 0) {
            minOf(2.0, reps.toDouble() / (duration / 60.0)) // reps per minute
        } else {
            1.0
        }

        return reps * baseCaloriesPerRep * intensityFactor
    }

    private fun saveWorkoutHistory() {
        val workoutHistory = WorkoutHistory(
            exerciseId = exerciseDataModel.id,
            exerciseName = exerciseDataModel.title,
            count = completedCount,
            targetCount = targetCount,
            date = System.currentTimeMillis(),
            duration = workoutDuration,
            caloriesBurned = caloriesBurned,
            isCompleted = completedCount >= targetCount
        )

        val prefs = getSharedPreferences("workout_history", MODE_PRIVATE)
        val gson = Gson()
        val type = object : TypeToken<MutableList<WorkoutHistory>>() {}.type
        val historyJson = prefs.getString("history_list", null)
        val historyList: MutableList<WorkoutHistory> = if (historyJson != null) {
            gson.fromJson(historyJson, type)
        } else {
            mutableListOf()
        }

        historyList.add(workoutHistory)
        prefs.edit().putString("history_list", gson.toJson(historyList)).apply()
    }
}
