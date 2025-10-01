package com.example.keepyfitness

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.keepyfitness.Model.Schedule
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import java.util.Calendar

class AddScheduleActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_schedule)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val btnPickTime = findViewById<Button>(R.id.btnPickTime)
        val btnSave = findViewById<Button>(R.id.btnSaveSchedule)
        val btnDelete = findViewById<Button>(R.id.btnDeleteSchedule)
        val edtTime = findViewById<EditText>(R.id.edtTime)
        val checkAll = findViewById<CheckBox>(R.id.checkAllDays)
        val checkMon = findViewById<CheckBox>(R.id.checkMon)
        val checkTue = findViewById<CheckBox>(R.id.checkTue)
        val checkWed = findViewById<CheckBox>(R.id.checkWed)
        val checkThu = findViewById<CheckBox>(R.id.checkThu)
        val checkFri = findViewById<CheckBox>(R.id.checkFri)
        val checkSat = findViewById<CheckBox>(R.id.checkSat)
        val checkSun = findViewById<CheckBox>(R.id.checkSun)
        val spinnerExercise = findViewById<Spinner>(R.id.spinnerExercise)
        val edtQuantity = findViewById<EditText>(R.id.edtQuantity)

        // Thiết lập danh sách bài tập cho Spinner
        val exerciseList = listOf("Tập Chống Đẩy", "Squat", "Dang Tay Chân Cardio", "Downward Dog Yoga", "Tree Pose")
        val exerciseAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, exerciseList)
        exerciseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerExercise.adapter = exerciseAdapter

        // Nếu có dữ liệu chỉnh sửa, hiển thị lên form
        val editScheduleId = intent.getStringExtra("edit_schedule_id")
        val editDataJson = intent.getStringExtra("edit_schedule_data")
        if (editScheduleId != null && editDataJson != null) {
            val gson = Gson()
            val editSchedule = gson.fromJson(editDataJson, Schedule::class.java)
            edtTime.setText(editSchedule.time)
            edtQuantity.setText(editSchedule.quantity.toString())
            spinnerExercise.setSelection(exerciseList.indexOf(editSchedule.exercise))
            checkMon.isChecked = editSchedule.days.contains("Thứ Hai")
            checkTue.isChecked = editSchedule.days.contains("Thứ Ba")
            checkWed.isChecked = editSchedule.days.contains("Thứ Tư")
            checkThu.isChecked = editSchedule.days.contains("Thứ Năm")
            checkFri.isChecked = editSchedule.days.contains("Thứ Sáu")
            checkSat.isChecked = editSchedule.days.contains("Thứ Bảy")
            checkSun.isChecked = editSchedule.days.contains("Chủ Nhật")
            checkAll.isChecked = editSchedule.days.size == 7
            btnDelete.visibility = Button.VISIBLE
        }

        btnPickTime.setOnClickListener {
            val calendar = Calendar.getInstance()
            val timePicker = TimePickerDialog(this, { _: TimePicker, hour: Int, minute: Int ->
                edtTime.setText(String.format("%02d:%02d", hour, minute))
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true)
            timePicker.show()
        }

        checkAll.setOnCheckedChangeListener { _, isChecked ->
            checkMon.isChecked = isChecked
            checkTue.isChecked = isChecked
            checkWed.isChecked = isChecked
            checkThu.isChecked = isChecked
            checkFri.isChecked = isChecked
            checkSat.isChecked = isChecked
            checkSun.isChecked = isChecked
        }

        btnSave.setOnClickListener {
            val selectedExercise = spinnerExercise.selectedItem.toString()
            val time = edtTime.text.toString()
            val quantityText = edtQuantity.text.toString()
            val quantity = quantityText.toIntOrNull() ?: 0
            val days = mutableListOf<String>()
            if (checkMon.isChecked) days.add("Thứ Hai")
            if (checkTue.isChecked) days.add("Thứ Ba")
            if (checkWed.isChecked) days.add("Thứ Tư")
            if (checkThu.isChecked) days.add("Thứ Năm")
            if (checkFri.isChecked) days.add("Thứ Sáu")
            if (checkSat.isChecked) days.add("Thứ Bảy")
            if (checkSun.isChecked) days.add("Chủ Nhật")
            if (time.isEmpty() || days.isEmpty() || quantity <= 0) {
                Toast.makeText(this, "Hãy chọn thời gian sau ít nhất 1 ngày kể từ bây giờ", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val schedule = Schedule(selectedExercise, time, days, quantity)
            val user = auth.currentUser
            if (user != null) {
                val scheduleId = if (editScheduleId != null) editScheduleId else "${selectedExercise}_${System.currentTimeMillis()}"
                db.collection("users").document(user.uid).collection("schedules")
                    .document(scheduleId)
                    .set(schedule, SetOptions.merge())
                    .addOnSuccessListener {
                        Toast.makeText(this, "Lịch tập đã được lưu!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Lỗi lưu lịch tập: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            } else {
                Toast.makeText(this, "Vui lòng đăng nhập.", Toast.LENGTH_SHORT).show()
            }
        }

        btnDelete.setOnClickListener {
            if (editScheduleId != null) {
                val user = auth.currentUser
                if (user != null) {
                    db.collection("users").document(user.uid).collection("schedules")
                        .document(editScheduleId)
                        .delete()
                        .addOnSuccessListener {
                            Toast.makeText(this, "Lịch tập đã được xóa!", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Lỗi xóa lịch tập: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
            }
        }
    }
}