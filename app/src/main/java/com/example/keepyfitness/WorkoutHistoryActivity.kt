package com.example.keepyfitness

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.keepyfitness.Model.PersonalRecord
import com.example.keepyfitness.Model.WorkoutHistory
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class WorkoutHistoryActivity : AppCompatActivity() {

    private lateinit var tabLayout: TabLayout
    private lateinit var historyListView: ListView
    private lateinit var chartsScrollView: View
    private lateinit var recordsListView: ListView
    private lateinit var weeklyChart: BarChart
    private lateinit var pieChart: PieChart
    private lateinit var btnClearHistory: MaterialButton

    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout_history)

        initViews()
        setupTabs()
        setupClearHistoryButton()
        loadHistoryData()
    }

    private fun initViews() {
        tabLayout = findViewById(R.id.tabLayout)
        historyListView = findViewById(R.id.historyListView)
        chartsScrollView = findViewById(R.id.chartsScrollView)
        recordsListView = findViewById(R.id.recordsListView)
        weeklyChart = findViewById(R.id.weeklyChart)
        pieChart = findViewById(R.id.pieChart)
        btnClearHistory = findViewById(R.id.btnClearHistory)
    }

    private fun setupTabs() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> showHistoryView()
                    1 -> showChartsView()
                    2 -> showRecordsView()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setupClearHistoryButton() {
        btnClearHistory.setOnClickListener {
            showClearHistoryConfirmationDialog()
        }
    }

    private fun showClearHistoryConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear Workout History")
            .setMessage("Are you sure you want to delete all workout history? This action cannot be undone.")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Delete All") { _, _ ->
                clearAllHistory()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearAllHistory() {
        // Xóa dữ liệu từ SharedPreferences
        val prefs = getSharedPreferences("workout_history", MODE_PRIVATE)
        val editor = prefs.edit()
        editor.remove("history_list")
        editor.apply()

        // Refresh lại giao diện
        loadHistoryData()
        setupCharts()
        loadPersonalRecords()

        // Hiển thị thông báo thành công
        AlertDialog.Builder(this)
            .setTitle("History Cleared")
            .setMessage("All workout history has been successfully deleted.")
            .setIcon(android.R.drawable.ic_dialog_info)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showHistoryView() {
        historyListView.visibility = View.VISIBLE
        chartsScrollView.visibility = View.GONE
        recordsListView.visibility = View.GONE
    }

    private fun showChartsView() {
        historyListView.visibility = View.GONE
        chartsScrollView.visibility = View.VISIBLE
        recordsListView.visibility = View.GONE
        setupCharts()
    }

    private fun showRecordsView() {
        historyListView.visibility = View.GONE
        chartsScrollView.visibility = View.GONE
        recordsListView.visibility = View.VISIBLE
        loadPersonalRecords()
    }

    private fun loadHistoryData() {
        val prefs = getSharedPreferences("workout_history", MODE_PRIVATE)
        val historyJson = prefs.getString("history_list", null)
        val type = object : TypeToken<List<WorkoutHistory>>() {}.type
        val historyList: List<WorkoutHistory> = if (historyJson != null) {
            gson.fromJson(historyJson, type)
        } else {
            emptyList()
        }

        val adapter = WorkoutHistoryAdapter(this, historyList)
        historyListView.adapter = adapter
    }

    private fun setupCharts() {
        setupWeeklyChart()
        setupPieChart()
    }

    private fun setupWeeklyChart() {
        val prefs = getSharedPreferences("workout_history", MODE_PRIVATE)
        val historyJson = prefs.getString("history_list", null)
        val type = object : TypeToken<List<WorkoutHistory>>() {}.type
        val historyList: List<WorkoutHistory> = if (historyJson != null) {
            gson.fromJson(historyJson, type)
        } else {
            emptyList()
        }

        // Group workouts by day of week for last 7 days
        val calendar = Calendar.getInstance()
        val weekData = mutableMapOf<String, Int>()
        val dayLabels = mutableListOf<String>()

        for (i in 6 downTo 0) {
            calendar.add(Calendar.DAY_OF_YEAR, -i)
            val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
            val dayLabel = dayFormat.format(calendar.time)
            dayLabels.add(dayLabel)
            weekData[dayLabel] = 0
            if (i > 0) calendar.add(Calendar.DAY_OF_YEAR, i)
        }

        // Count workouts per day
        historyList.forEach { workout ->
            val workoutDate = Calendar.getInstance()
            workoutDate.timeInMillis = workout.date
            val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
            val dayLabel = dayFormat.format(workoutDate.time)
            weekData[dayLabel] = weekData[dayLabel]!! + 1
        }

        val entries = mutableListOf<BarEntry>()
        dayLabels.forEachIndexed { index, day ->
            entries.add(BarEntry(index.toFloat(), weekData[day]?.toFloat() ?: 0f))
        }

        val dataSet = BarDataSet(entries, "Workouts")
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()

        val barData = BarData(dataSet)
        weeklyChart.data = barData
        weeklyChart.xAxis.valueFormatter = IndexAxisValueFormatter(dayLabels)
        weeklyChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        weeklyChart.xAxis.granularity = 1f
        weeklyChart.description.isEnabled = false
        weeklyChart.legend.isEnabled = false
        weeklyChart.invalidate()
    }

    private fun setupPieChart() {
        val prefs = getSharedPreferences("workout_history", MODE_PRIVATE)
        val historyJson = prefs.getString("history_list", null)
        val type = object : TypeToken<List<WorkoutHistory>>() {}.type
        val historyList: List<WorkoutHistory> = if (historyJson != null) {
            gson.fromJson(historyJson, type)
        } else {
            emptyList()
        }

        // Count exercises by type
        val exerciseCount = mutableMapOf<String, Int>()
        historyList.forEach { workout ->
            exerciseCount[workout.exerciseName] = exerciseCount[workout.exerciseName]?.plus(1) ?: 1
        }

        val entries = mutableListOf<PieEntry>()
        exerciseCount.forEach { (exercise, count) ->
            entries.add(PieEntry(count.toFloat(), exercise))
        }

        val dataSet = PieDataSet(entries, "Exercise Distribution")
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()

        val pieData = PieData(dataSet)
        pieChart.data = pieData
        pieChart.description.isEnabled = false
        pieChart.centerText = "Exercise\nDistribution"
        pieChart.invalidate()
    }

    private fun loadPersonalRecords() {
        val prefs = getSharedPreferences("workout_history", MODE_PRIVATE)
        val historyJson = prefs.getString("history_list", null)
        val type = object : TypeToken<List<WorkoutHistory>>() {}.type
        val historyList: List<WorkoutHistory> = if (historyJson != null) {
            gson.fromJson(historyJson, type)
        } else {
            emptyList()
        }

        // Calculate personal records
        val recordsMap = mutableMapOf<Int, PersonalRecord>()

        historyList.groupBy { it.exerciseId }.forEach { (exerciseId, workouts) ->
            val maxCount = workouts.maxByOrNull { it.count }
            val totalWorkouts = workouts.size
            val averageCount = workouts.map { it.count }.average()

            if (maxCount != null) {
                recordsMap[exerciseId] = PersonalRecord(
                    exerciseId = exerciseId,
                    exerciseName = maxCount.exerciseName,
                    maxCount = maxCount.count,
                    bestDate = maxCount.date,
                    totalWorkouts = totalWorkouts,
                    averageCount = averageCount
                )
            }
        }

        val recordsList = recordsMap.values.toList()
        val adapter = PersonalRecordAdapter(this, recordsList)
        recordsListView.adapter = adapter
    }

    // Adapter for workout history
    class WorkoutHistoryAdapter(private val context: Context, private val historyList: List<WorkoutHistory>) : BaseAdapter() {

        override fun getCount(): Int = historyList.size
        override fun getItem(position: Int): Any = historyList[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.workout_history_item, parent, false)
            val workout = historyList[position]

            val exerciseIcon = view.findViewById<ImageView>(R.id.exerciseIcon)
            val exerciseName = view.findViewById<TextView>(R.id.exerciseName)
            val workoutDetails = view.findViewById<TextView>(R.id.workoutDetails)
            val workoutDate = view.findViewById<TextView>(R.id.workoutDate)
            val completionStatus = view.findViewById<TextView>(R.id.completionStatus)
            val caloriesBurned = view.findViewById<TextView>(R.id.caloriesBurned)

            exerciseName.text = workout.exerciseName
            workoutDetails.text = "${workout.count}/${workout.targetCount} reps • ${workout.duration / 60} min"

            val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            workoutDate.text = dateFormat.format(Date(workout.date))

            val completionPercentage = (workout.count.toFloat() / workout.targetCount * 100).toInt()
            completionStatus.text = "$completionPercentage%"

            // Set color based on completion
            when {
                completionPercentage >= 100 -> completionStatus.setTextColor(Color.parseColor("#4CAF50"))
                completionPercentage >= 75 -> completionStatus.setTextColor(Color.parseColor("#FF9800"))
                else -> completionStatus.setTextColor(Color.parseColor("#F44336"))
            }

            caloriesBurned.text = "${workout.caloriesBurned.toInt()} cal"

            // Set exercise icon based on type
            when (workout.exerciseId) {
                1 -> exerciseIcon.setImageResource(R.drawable.pushup)
                2 -> exerciseIcon.setImageResource(R.drawable.squat)
                3 -> exerciseIcon.setImageResource(R.drawable.jumping)
                4 -> exerciseIcon.setImageResource(R.drawable.plank)
                else -> exerciseIcon.setImageResource(R.drawable.ic_launcher_foreground)
            }

            return view
        }
    }

    // Adapter for personal records
    class PersonalRecordAdapter(private val context: Context, private val recordsList: List<PersonalRecord>) : BaseAdapter() {

        override fun getCount(): Int = recordsList.size
        override fun getItem(position: Int): Any = recordsList[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.personal_record_item, parent, false)
            val record = recordsList[position]

            val medalIcon = view.findViewById<ImageView>(R.id.medalIcon)
            val exerciseName = view.findViewById<TextView>(R.id.exerciseName)
            val recordDetails = view.findViewById<TextView>(R.id.recordDetails)
            val recordStats = view.findViewById<TextView>(R.id.recordStats)
            val recordDate = view.findViewById<TextView>(R.id.recordDate)
            val recordBadge = view.findViewById<TextView>(R.id.recordBadge)

            exerciseName.text = record.exerciseName
            recordDetails.text = "Personal Best: ${record.maxCount} reps"
            recordStats.text = "Total: ${record.totalWorkouts} workouts • Avg: ${record.averageCount.toInt()} reps"

            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            recordDate.text = "Best achieved: ${dateFormat.format(Date(record.bestDate))}"

            recordBadge.text = record.maxCount.toString()

            // Set medal icon based on exercise type
            when (record.exerciseId) {
                1 -> medalIcon.setImageResource(R.drawable.pushup)
                2 -> medalIcon.setImageResource(R.drawable.squat)
                3 -> medalIcon.setImageResource(R.drawable.jumping)
                4 -> medalIcon.setImageResource(R.drawable.plank)
                else -> medalIcon.setImageResource(R.drawable.ic_launcher_foreground)
            }

            return view
        }
    }
}
