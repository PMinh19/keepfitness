package com.example.keepyfitness

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.example.keepyfitness.Model.ExerciseDataModel
import com.example.keepyfitness.Model.Schedule
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class ExerciseListActivity : AppCompatActivity() {

    private lateinit var exerciseListView: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_exercise_list)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        exerciseListView = findViewById(R.id.exerciseList)

        val list = listOf(
            ExerciseDataModel("Push Ups", R.drawable.pushup, 1, Color.parseColor("#0041a8")),
            ExerciseDataModel("Squats", R.drawable.squat, 2, Color.parseColor("#f20226")),
            ExerciseDataModel("Jumping Jacks", R.drawable.jumping, 3, Color.parseColor("#f7680f")),
            ExerciseDataModel("Plank To Downward Dog", R.drawable.plank, 4, Color.parseColor("#008a40")),
        )

        val adapter = ExerciseAdapter(this, list)
        exerciseListView.adapter = adapter
    }

    class ExerciseAdapter(val context: Context, val exerciseList: List<ExerciseDataModel>) : BaseAdapter() {
        override fun getCount(): Int {
            return exerciseList.size
        }

        override fun getItem(position: Int): Any {
            return exerciseList[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = LayoutInflater.from(context).inflate(R.layout.exercise_item, parent, false)
            val titleTV = view.findViewById<TextView>(R.id.textView2)
            val exerciseImg = view.findViewById<ImageView>(R.id.imageView)
            val card = view.findViewById<CardView>(R.id.cardView)

            card.setOnClickListener {
                // Lấy target từ schedule của ngày hôm nay
                val targetReps = getTodayTargetForExercise(exerciseList[position].title)

                val intent = Intent(context, MainActivity::class.java)
                intent.putExtra("data", exerciseList[position])
                intent.putExtra("target_count", targetReps)
                context.startActivity(intent)
            }

            card.setCardBackgroundColor(exerciseList[position].color)
            Glide.with(context).asGif().load(exerciseList[position].image).into(exerciseImg)
            titleTV.text = exerciseList[position].title
            return view
        }

        private fun getTodayTargetForExercise(exerciseName: String): Int {
            val prefs = context.getSharedPreferences("schedules", Context.MODE_PRIVATE)
            val gson = Gson()
            val type = object : TypeToken<List<Schedule>>() {}.type
            val listJson = prefs.getString("schedule_list", null)
            val scheduleList: List<Schedule> = if (listJson != null) {
                gson.fromJson(listJson, type)
            } else {
                emptyList()
            }

            // Lấy tên ngày hôm nay
            val calendar = Calendar.getInstance()
            val dayFormat = SimpleDateFormat("EEEE", Locale.ENGLISH) // Monday, Tuesday, etc.
            val today = dayFormat.format(calendar.time)

            // Tìm schedule cho bài tập này trong ngày hôm nay
            val todaySchedule = scheduleList.find { schedule ->
                schedule.exercise == exerciseName && schedule.days.contains(today)
            }

            return todaySchedule?.quantity ?: 0 // Trả về 0 nếu không có target
        }
    }
}