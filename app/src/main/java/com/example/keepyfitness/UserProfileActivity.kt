package com.example.keepyfitness

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.keepyfitness.security.CryptoUtil
import com.example.keepyfitness.security.XSSProtection

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

        setupInputFilters()
        loadUserProfile()

        btnSaveProfile.setOnClickListener {
            saveUserProfile()
        }
    }

    private fun setupInputFilters() {
        etAge.filters = arrayOf(InputFilter.LengthFilter(3))
        etWeight.filters = arrayOf(InputFilter.LengthFilter(6))
        etHeight.filters = arrayOf(InputFilter.LengthFilter(6))

        etAge.inputType = InputType.TYPE_CLASS_NUMBER
        etWeight.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        etHeight.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    }

    private fun loadUserProfile() {
        val user = auth.currentUser ?: return

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    try {
                        if (document.contains("age_enc")) {
                            val key = CryptoUtil.deriveKey(user.uid)

                            val ageEnc = document.getString("age_enc") ?: ""
                            val weightEnc = document.getString("weight_enc") ?: ""
                            val heightEnc = document.getString("height_enc") ?: ""
                            val genderEnc = document.getString("gender_enc") ?: ""

                            val age = CryptoUtil.decrypt(ageEnc, key).toIntOrNull() ?: 25
                            val weight = CryptoUtil.decrypt(weightEnc, key).toDoubleOrNull() ?: 70.0
                            val height = CryptoUtil.decrypt(heightEnc, key).toDoubleOrNull() ?: 170.0
                            val gender = CryptoUtil.decrypt(genderEnc, key)

                            etAge.setText(age.toString())
                            etWeight.setText(weight.toString())
                            etHeight.setText(height.toString())

                            if (gender == "Male") {
                                rbMale.isChecked = true
                            } else {
                                rbFemale.isChecked = true
                            }
                        } else {
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
                    } catch (e: Exception) {
                        Toast.makeText(this, "Lỗi giải mã: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Lỗi tải hồ sơ: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveUserProfile() {
        val user = auth.currentUser ?: return

        val ageText = XSSProtection.sanitizeNumeric(etAge.text.toString())
        val weightText = XSSProtection.sanitizeNumeric(etWeight.text.toString())
        val heightText = XSSProtection.sanitizeNumeric(etHeight.text.toString())

        if (ageText.isEmpty() || weightText.isEmpty() || heightText.isEmpty()) {
            Toast.makeText(this, "Vui lòng điền đầy đủ thông tin", Toast.LENGTH_SHORT).show()
            return
        }

        val age = ageText.toIntOrNull() ?: run {
            Toast.makeText(this, "Tuổi không hợp lệ", Toast.LENGTH_SHORT).show()
            return
        }
        val weight = weightText.toDoubleOrNull() ?: run {
            Toast.makeText(this, "Cân nặng không hợp lệ", Toast.LENGTH_SHORT).show()
            return
        }
        val height = heightText.toDoubleOrNull() ?: run {
            Toast.makeText(this, "Chiều cao không hợp lệ", Toast.LENGTH_SHORT).show()
            return
        }

        if (age !in 1..120) {
            Toast.makeText(this, "Tuổi phải từ 1-120", Toast.LENGTH_SHORT).show()
            return
        }
        if (weight !in 1.0..300.0) {
            Toast.makeText(this, "Cân nặng phải từ 1-300kg", Toast.LENGTH_SHORT).show()
            return
        }
        if (height !in 50.0..250.0) {
            Toast.makeText(this, "Chiều cao phải từ 50-250cm", Toast.LENGTH_SHORT).show()
            return
        }

        val gender = if (rbMale.isChecked) "Male" else "Female"

        val key = CryptoUtil.deriveKey(user.uid)
        val encAge = CryptoUtil.encrypt(age.toString(), key)
        val encWeight = CryptoUtil.encrypt(weight.toString(), key)
        val encHeight = CryptoUtil.encrypt(height.toString(), key)
        val encGender = CryptoUtil.encrypt(gender, key)

        val profile = hashMapOf(
            "age_enc" to encAge,
            "weight_enc" to encWeight,
            "height_enc" to encHeight,
            "gender_enc" to encGender,
            "enc_algo" to "AES-GCM-128"
        )

        db.collection("users").document(user.uid).set(profile)
            .addOnSuccessListener {
                Toast.makeText(this, "Hồ sơ đã được lưu an toàn", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Lỗi lưu hồ sơ: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}