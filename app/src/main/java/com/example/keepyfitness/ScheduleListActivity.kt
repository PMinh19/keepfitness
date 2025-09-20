package com.example.keepyfitness

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.keepyfitness.Model.Schedule
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ScheduleListActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule_list)

        // Nút Add Schedule (ImageView trong header)
        val btnAddSchedule = findViewById<ImageView>(R.id.btnAddSchedule)
        btnAddSchedule.setOnClickListener {
            val intent = Intent(this, AddScheduleActivity::class.java)
            startActivity(intent)
        }

        // Nút Create First Schedule (trong empty state)
        val btnCreateFirstSchedule = findViewById<MaterialButton>(R.id.btnCreateFirstSchedule)
        btnCreateFirstSchedule.setOnClickListener {
            val intent = Intent(this, AddScheduleActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        loadScheduleData()
    }

    private fun loadScheduleData() {
        val listView = findViewById<ListView>(R.id.scheduleListView)
        val emptyStateLayout = findViewById<View>(R.id.emptyStateLayout)
        val totalSchedulesCount = findViewById<TextView>(R.id.totalSchedulesCount)
        val todaySchedulesCount = findViewById<TextView>(R.id.todaySchedulesCount)

        val prefs = getSharedPreferences("schedules", MODE_PRIVATE)
        val gson = Gson()
        val type = object : TypeToken<List<Schedule>>() {}.type
        val listJson = prefs.getString("schedule_list", null)
        val scheduleList: MutableList<Schedule> = if (listJson != null) {
            gson.fromJson(listJson, type)
        } else {
            mutableListOf()
        }

        // Cập nhật stats
        totalSchedulesCount.text = scheduleList.size.toString()

        // Đếm schedules hôm nay (giả sử tất cả đều active)
        todaySchedulesCount.text = scheduleList.size.toString()

        if (scheduleList.isEmpty()) {
            // Hiển thị empty state
            listView.visibility = View.GONE
            emptyStateLayout.visibility = View.VISIBLE
        } else {
            // Hiển thị danh sách
            listView.visibility = View.VISIBLE
            emptyStateLayout.visibility = View.GONE

            val displayList = scheduleList.map {
                "${it.quantity} ${it.exercise} at ${it.time} on ${it.days.joinToString(", ")}"
            }
            val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayList)
            listView.adapter = adapter

            // Bắt sự kiện click để chỉnh sửa
            listView.setOnItemClickListener { _, _, position, _ ->
                val intent = Intent(this, AddScheduleActivity::class.java)
                intent.putExtra("edit_schedule_index", position)
                intent.putExtra("edit_schedule_data", gson.toJson(scheduleList[position]))
                startActivity(intent)
            }

            // Bắt sự kiện long click để xóa
            listView.setOnItemLongClickListener { _, _, position, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Delete Schedule")
                    .setMessage("Are you sure you want to delete this schedule?")
                    .setPositiveButton("Delete") { _, _ ->
                        scheduleList.removeAt(position)
                        prefs.edit().putString("schedule_list", gson.toJson(scheduleList)).apply()
                        // Reload data
                        loadScheduleData()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
        }
    }
}
