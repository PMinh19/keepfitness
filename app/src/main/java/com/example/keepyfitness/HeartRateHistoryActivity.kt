package com.example.keepyfitness

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.keepyfitness.Model.HeartRateData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class HeartRateHistoryActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var historyListView: ListView
    private lateinit var emptyStateText: TextView
    private val heartRateList = mutableListOf<HeartRateData>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_heart_rate_history)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        historyListView = findViewById(R.id.historyListView)
        emptyStateText = findViewById(R.id.emptyStateText)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        loadHeartRateHistory()
    }

    private fun loadHeartRateHistory() {
        val user = auth.currentUser
        if (user != null) {
            db.collection("users").document(user.uid).collection("healthMetrics")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener { documents ->
                    heartRateList.clear()
                    for (document in documents) {
                        val heartRate = document.toObject(HeartRateData::class.java)
                        heartRateList.add(heartRate)
                    }
                    updateUI()
                }
                .addOnFailureListener { e ->
                    showCustomToast("Lỗi tải lịch sử nhịp tim: ${e.message}")
                    loadFromSharedPreferences()
                }
        } else {
            showCustomToast("Vui lòng đăng nhập để xem lịch sử nhịp tim.")
            loadFromSharedPreferences()
        }
    }

    private fun loadFromSharedPreferences() {
        try {
            val prefs = getSharedPreferences("health_data", MODE_PRIVATE)
            val bpm = prefs.getInt("last_heart_rate_bpm", -1)
            val status = prefs.getString("last_heart_rate_status", null)
            val suggestion = prefs.getString("last_heart_rate_suggestion", null)
            val time = prefs.getLong("last_heart_rate_time", 0L)

            heartRateList.clear()
            if (bpm > 0 && status != null && suggestion != null && time > 0L) {
                val heartRate = HeartRateData(
                    id = time.toString(),
                    bpm = bpm,
                    status = status,
                    suggestion = suggestion,
                    timestamp = time,
                    duration = 0L
                )
                heartRateList.add(heartRate)
            }
            updateUI()
        } catch (e: Exception) {
            showCustomToast("Lỗi tải lịch sử từ bộ nhớ: ${e.message}")
            updateUI()
        }
    }

    private fun updateUI() {
        if (heartRateList.isEmpty()) {
            historyListView.visibility = View.GONE
            emptyStateText.visibility = View.VISIBLE
        } else {
            historyListView.visibility = View.VISIBLE
            emptyStateText.visibility = View.GONE
            val adapter = HeartRateHistoryAdapter(this, heartRateList)
            historyListView.adapter = adapter
        }
    }

    private fun showCustomToast(message: String) {
        val inflater = layoutInflater
        val layout = inflater.inflate(R.layout.custom_toast, null)
        val text = layout.findViewById<TextView>(R.id.toast_text)
        text.text = message
        val toast = Toast(applicationContext)
        toast.duration = Toast.LENGTH_LONG
        toast.view = layout
        toast.show()
    }

    inner class HeartRateHistoryAdapter(val context: Context, val heartRateList: List<HeartRateData>) : BaseAdapter() {
        override fun getCount(): Int = heartRateList.size
        override fun getItem(position: Int): Any = heartRateList[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = LayoutInflater.from(context).inflate(R.layout.heart_rate_history_item, parent, false)
            val tvBpm = view.findViewById<TextView>(R.id.tvBpm)
            val tvStatus = view.findViewById<TextView>(R.id.tvStatus)
            val tvSuggestion = view.findViewById<TextView>(R.id.tvSuggestion)
            val tvTimestamp = view.findViewById<TextView>(R.id.tvTimestamp)

            val heartRate = heartRateList[position]
            tvBpm.text = "${heartRate.bpm} BPM"
            tvStatus.text = heartRate.status
            tvSuggestion.text = heartRate.suggestion
            tvTimestamp.text = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(heartRate.timestamp))

            return view
        }
    }
}