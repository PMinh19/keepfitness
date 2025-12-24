package com.example.keepyfitness

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.keepyfitness.Model.UserGoals
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class GoalSettingActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var etCalorieGoal: EditText
    private lateinit var etStepsGoal: EditText
    private lateinit var btnSaveGoals: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_goal_setting)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        etCalorieGoal = findViewById(R.id.etCalorieGoal)
        etStepsGoal = findViewById(R.id.etStepsGoal)
        btnSaveGoals = findViewById(R.id.btnSaveGoals)

        loadUserGoals()

        btnSaveGoals.setOnClickListener {
            saveUserGoals()
        }
    }

    private fun loadUserGoals() {
        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid).collection("goals").document("daily").get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val goals = document.toObject(UserGoals::class.java)
                    goals?.let {
                        etCalorieGoal.setText(it.dailyCalorieGoal.toString())
                        etStepsGoal.setText(it.dailyStepsGoal.toString())
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Lỗi tải mục tiêu: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveUserGoals() {
        val user = auth.currentUser ?: return
        val calorieText = etCalorieGoal.text.toString()
        val stepsText = etStepsGoal.text.toString()

        if (calorieText.isEmpty() || stepsText.isEmpty()) {
            Toast.makeText(this, "Vui lòng điền đầy đủ", Toast.LENGTH_SHORT).show()
            return
        }

        val calorieGoal = calorieText.toDoubleOrNull() ?: 500.0
        val stepsGoal = stepsText.toIntOrNull() ?: 10000

        if (calorieGoal <= 0 || stepsGoal <= 0) {
            Toast.makeText(this, "Giá trị không hợp lệ", Toast.LENGTH_SHORT).show()
            return
        }

        val goals = UserGoals(dailyCalorieGoal = calorieGoal, dailyStepsGoal = stepsGoal)
        db.collection("users").document(user.uid).collection("goals").document("daily").set(goals)
            .addOnSuccessListener {
                Toast.makeText(this, "Mục tiêu đã lưu", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Lỗi lưu mục tiêu: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
