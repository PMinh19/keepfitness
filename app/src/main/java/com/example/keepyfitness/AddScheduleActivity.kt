package com.example.keepyfitness

import android.app.TimePickerDialog
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.keepyfitness.Model.Schedule
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar

class AddScheduleActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_schedule)

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
        val exerciseList = listOf("Push Ups", "Squats", "Jumping Jacks", "Plank To Downward Dog")
        val exerciseAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, exerciseList)
        exerciseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerExercise.adapter = exerciseAdapter

        // Nếu có dữ liệu chỉnh sửa, hiển thị lên form
        val editIndex = intent.getIntExtra("edit_schedule_index", -1)
        val editDataJson = intent.getStringExtra("edit_schedule_data")
        if (editIndex != -1 && editDataJson != null) {
            val gson = Gson()
            val editSchedule = gson.fromJson(editDataJson, Schedule::class.java)
            edtTime.setText(editSchedule.time)
            edtQuantity.setText(editSchedule.quantity.toString())
            spinnerExercise.setSelection(exerciseList.indexOf(editSchedule.exercise))
            checkMon.isChecked = editSchedule.days.contains("Monday")
            checkTue.isChecked = editSchedule.days.contains("Tuesday")
            checkWed.isChecked = editSchedule.days.contains("Wednesday")
            checkThu.isChecked = editSchedule.days.contains("Thursday")
            checkFri.isChecked = editSchedule.days.contains("Friday")
            checkSat.isChecked = editSchedule.days.contains("Saturday")
            checkSun.isChecked = editSchedule.days.contains("Sunday")
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
            if (checkMon.isChecked) days.add("Monday")
            if (checkTue.isChecked) days.add("Tuesday")
            if (checkWed.isChecked) days.add("Wednesday")
            if (checkThu.isChecked) days.add("Thursday")
            if (checkFri.isChecked) days.add("Friday")
            if (checkSat.isChecked) days.add("Saturday")
            if (checkSun.isChecked) days.add("Sunday")
            if (time.isEmpty() || days.isEmpty() || quantity <= 0) {
                Toast.makeText(this, "Please select time, at least one day, and enter a valid quantity", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val schedule = Schedule(selectedExercise, time, days, quantity)
            val prefs = getSharedPreferences("schedules", MODE_PRIVATE)
            val gson = Gson()
            val type = object : TypeToken<MutableList<Schedule>>() {}.type
            val listJson = prefs.getString("schedule_list", null)
            val scheduleList: MutableList<Schedule> = if (listJson != null) gson.fromJson(listJson, type) else mutableListOf()
            if (editIndex != -1 && editIndex < scheduleList.size) {
                scheduleList[editIndex] = schedule // cập nhật lịch tập
            } else {
                scheduleList.add(schedule) // thêm mới
            }
            prefs.edit().putString("schedule_list", gson.toJson(scheduleList)).apply()
            Toast.makeText(this, "Schedule saved!", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnDelete.setOnClickListener {
            if (editIndex != -1) {
                val prefs = getSharedPreferences("schedules", MODE_PRIVATE)
                val gson = Gson()
                val type = object : TypeToken<MutableList<Schedule>>() {}.type
                val listJson = prefs.getString("schedule_list", null)
                val scheduleList: MutableList<Schedule> = if (listJson != null) gson.fromJson(listJson, type) else mutableListOf()
                if (editIndex < scheduleList.size) {
                    scheduleList.removeAt(editIndex)
                    prefs.edit().putString("schedule_list", gson.toJson(scheduleList)).apply()
                    Toast.makeText(this, "Schedule deleted!", Toast.LENGTH_SHORT).show()
                }
            }
            finish()
        }
    }
}
