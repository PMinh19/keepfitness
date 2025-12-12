package com.example.keepyfitness

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class UserProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var etAge: EditText
    private lateinit var etWeight: EditText
    private lateinit var etHeight: EditText
    private lateinit var rgGender: RadioGroup
    private lateinit var rbMale: RadioButton
    private lateinit var rbFemale: RadioButton
    private lateinit var btnSaveProfile: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        etAge = findViewById(R.id.etAge)
        etWeight = findViewById(R.id.etWeight)
        etHeight = findViewById(R.id.etHeight)
        rgGender = findViewById(R.id.rgGender)
        rbMale = findViewById(R.id.rbMale)
        rbFemale = findViewById(R.id.rbFemale)
        btnSaveProfile = findViewById(R.id.btnSaveProfile)

        loadUserProfile()

        btnSaveProfile.setOnClickListener {
            saveUserProfile()
        }
    }

    private fun loadUserProfile() {
        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val age = document.getLong("age")?.toInt() ?: 25
                    val weight = document.getDouble("weight") ?: 70.0
                    val height = document.getDouble("height") ?: 170.0
                    val gender = document.getString("gender") ?: "Male"

                    etAge.setText(age.toString())
                    etWeight.setText(weight.toString())
                    etHeight.setText(height.toString())

                    if (gender == "Male") {
                        rbMale.isChecked = true
                    } else {
                        rbFemale.isChecked = true
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Lỗi tải hồ sơ: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveUserProfile() {
        val user = auth.currentUser ?: return

        val ageText = etAge.text.toString()
        val weightText = etWeight.text.toString()
        val heightText = etHeight.text.toString()

        if (ageText.isEmpty() || weightText.isEmpty() || heightText.isEmpty()) {
            Toast.makeText(this, "Vui lòng điền đầy đủ thông tin", Toast.LENGTH_SHORT).show()
            return
        }

        val age = ageText.toIntOrNull() ?: 25
        val weight = weightText.toDoubleOrNull() ?: 70.0
        val height = heightText.toDoubleOrNull() ?: 170.0

        if (age <= 0 || age > 120) {
            Toast.makeText(this, "Tuổi không hợp lệ", Toast.LENGTH_SHORT).show()
            return
        }

        if (weight <= 0 || weight > 300) {
            Toast.makeText(this, "Cân nặng không hợp lệ", Toast.LENGTH_SHORT).show()
            return
        }

        if (height <= 0 || height > 250) {
            Toast.makeText(this, "Chiều cao không hợp lệ", Toast.LENGTH_SHORT).show()
            return
        }

        val gender = if (rbMale.isChecked) "Male" else "Female"

        val profile = hashMapOf(
            "age" to age,
            "weight" to weight,
            "height" to height,
            "gender" to gender
        )

        db.collection("users").document(user.uid).set(profile)
            .addOnSuccessListener {
                Toast.makeText(this, "Hồ sơ đã được lưu", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Lỗi lưu hồ sơ: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
