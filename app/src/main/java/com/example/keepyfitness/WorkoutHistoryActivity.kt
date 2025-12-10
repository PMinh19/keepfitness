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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout_history)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        initViews()
        setupTabs()
        setupClearHistoryButton()
        migrateLocalDataToFirestore() // Thêm: Migrate dữ liệu cũ
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
        val user = auth.currentUser
        if (user != null) {
            // Xóa workouts từ Firestore
            db.collection("users").document(user.uid).collection("workouts")
                .get()
                .addOnSuccessListener { documents ->
                    val batch = db.batch()
                    documents.forEach { batch.delete(it.reference) }
                    batch.commit().addOnSuccessListener {
                        // Xóa personal records
                        db.collection("users").document(user.uid).collection("personalRecords")
                            .get()
                            .addOnSuccessListener { recordDocs ->
                                val recordBatch = db.batch() // Sửa: Dùng recordBatch đúng
                                recordDocs.forEach { recordBatch.delete(it.reference) } // Sửa: Dùng recordBatch
                                recordBatch.commit().addOnSuccessListener {
                                    // Xóa local để đồng bộ
                                    val prefs = getSharedPreferences("workout_history", MODE_PRIVATE)
                                    prefs.edit().remove("history_list").apply()
                                    // Refresh giao diện
                                    loadHistoryData()
                                    setupCharts()
                                    loadPersonalRecords()
                                    AlertDialog.Builder(this)
                                        .setTitle("History Cleared")
                                        .setMessage("All workout history has been successfully deleted.")
                                        .setIcon(android.R.drawable.ic_dialog_info)
                                        .setPositiveButton("OK", null)
                                        .show()
                                }.addOnFailureListener { e ->
                                    AlertDialog.Builder(this)
                                        .setTitle("Error")
                                        .setMessage("Failed to clear personal records: ${e.message}")
                                        .setPositiveButton("OK", null)
                                        .show()
                                }
                            }.addOnFailureListener { e ->
                                AlertDialog.Builder(this)
                                    .setTitle("Error")
                                    .setMessage("Failed to clear history: ${e.message}")
                                    .setPositiveButton("OK", null)
                                    .show()
                            }
                    }.addOnFailureListener { e ->
                        AlertDialog.Builder(this)
                            .setTitle("Error")
                            .setMessage("Failed to clear history: ${e.message}")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
        } else {
            AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage("Please log in to clear history.")
                .setPositiveButton("OK", null)
                .show()
        }
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
        val user = auth.currentUser
        if (user != null) {
            db.collection("users").document(user.uid).collection("workouts")
                .orderBy("date", Query.Direction.DESCENDING) // Sắp xếp theo ngày mới nhất
                .limit(50) // Giới hạn 50 workout để tối ưu
                .get()
                .addOnSuccessListener { documents ->
                    val historyList = documents.mapNotNull { it.toObject(WorkoutHistory::class.java) }
                    val adapter = WorkoutHistoryAdapter(this, historyList)
                    historyListView.adapter = adapter
                }
                .addOnFailureListener { e ->
                    android.widget.Toast.makeText(this, "Lỗi tải lịch sử: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    historyListView.adapter = WorkoutHistoryAdapter(this, emptyList())
                }
        } else {
            android.widget.Toast.makeText(this, "Vui lòng đăng nhập.", android.widget.Toast.LENGTH_SHORT).show()
            historyListView.adapter = WorkoutHistoryAdapter(this, emptyList())
        }
    }

    private fun setupCharts() {
        setupWeeklyChart()
        setupPieChart()
    }

    private fun setupWeeklyChart() {
        val user = auth.currentUser
        if (user == null) {
            weeklyChart.data = null
            weeklyChart.invalidate()
            return
        }

        db.collection("users").document(user.uid).collection("workouts")
            .get()
            .addOnSuccessListener { documents ->
                val historyList = documents.mapNotNull { it.toObject(WorkoutHistory::class.java) }
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
            }.addOnFailureListener { e ->
                android.widget.Toast.makeText(this, "Lỗi tải dữ liệu biểu đồ: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                weeklyChart.data = null
                weeklyChart.invalidate()
            }
    }

    private fun setupPieChart() {
        val user = auth.currentUser
        if (user == null) {
            pieChart.data = null
            pieChart.invalidate()
            return
        }

        db.collection("users").document(user.uid).collection("workouts")
            .get()
            .addOnSuccessListener { documents ->
                val historyList = documents.mapNotNull { it.toObject(WorkoutHistory::class.java) }
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
            }.addOnFailureListener { e ->
                android.widget.Toast.makeText(this, "Lỗi tải dữ liệu biểu đồ: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                pieChart.data = null
                pieChart.invalidate()
            }
    }

    private fun loadPersonalRecords() {
        val user = auth.currentUser
        if (user != null) {
            db.collection("users").document(user.uid).collection("personalRecords")
                .get()
                .addOnSuccessListener { documents ->
                    val recordsList = documents.mapNotNull { it.toObject(PersonalRecord::class.java) }
                    val adapter = PersonalRecordAdapter(this, recordsList)
                    recordsListView.adapter = adapter
                }
                .addOnFailureListener { e ->
                    android.widget.Toast.makeText(this, "Lỗi tải PR: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    recordsListView.adapter = PersonalRecordAdapter(this, emptyList())
                }
        } else {
            android.widget.Toast.makeText(this, "Vui lòng đăng nhập.", android.widget.Toast.LENGTH_SHORT).show()
            recordsListView.adapter = PersonalRecordAdapter(this, emptyList())
        }
    }

    private fun migrateLocalDataToFirestore() {
        val prefs = getSharedPreferences("workout_history", MODE_PRIVATE)
        val historyJson = prefs.getString("history_list", null)
        if (historyJson != null) {
            val gson = Gson()
            val type = object : TypeToken<List<WorkoutHistory>>() {}.type
            val historyList: List<WorkoutHistory> = gson.fromJson(historyJson, type)
            val user = auth.currentUser
            if (user != null) {
                val batch = db.batch()
                historyList.forEach { workout ->
                    batch.set(
                        db.collection("users").document(user.uid).collection("workouts").document(workout.id),
                        workout
                    )
                }
                batch.commit().addOnSuccessListener {
                    // Migrate personal records
                    historyList.groupBy { it.exerciseId }.forEach { (exerciseId, workouts) ->
                        val maxCount = workouts.maxByOrNull { it.count }
                        if (maxCount != null) {
                            val totalWorkouts = workouts.size
                            val averageCount = workouts.map { it.count }.average()
                            val newRecord = PersonalRecord(
                                exerciseId = exerciseId,
                                exerciseName = maxCount.exerciseName,
                                maxCount = maxCount.count,
                                bestDate = maxCount.date,
                                totalWorkouts = totalWorkouts,
                                averageCount = averageCount
                            )
                            db.collection("users").document(user.uid).collection("personalRecords")
                                .document(exerciseId.toString())
                                .set(newRecord)
                        }
                    }
                    // Không xóa local data để đảm bảo tương thích
                    // prefs.edit().remove("history_list").apply()
                }.addOnFailureListener { e ->
                    android.widget.Toast.makeText(this, "Lỗi migrate dữ liệu: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
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

            val completionPercentageDouble = if (workout.targetCount > 0) {
                val p = (workout.count.toDouble() / workout.targetCount.toDouble()) * 100.0
                p.coerceIn(0.0, 100.0)
            } else {
                0.0
            }
            completionStatus.text = String.format(Locale.getDefault(), "%.0f%%", completionPercentageDouble)

            val completionPercentageInt = completionPercentageDouble.toInt()
            when {
                completionPercentageInt >= 100 -> completionStatus.setTextColor(Color.parseColor("#4CAF50"))
                completionPercentageInt >= 75 -> completionStatus.setTextColor(Color.parseColor("#FF9800"))
                else -> completionStatus.setTextColor(Color.parseColor("#F44336"))
            }

            caloriesBurned.text = "Bạn đã đốt cháy ${workout.caloriesBurned.toInt()} calo"

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