package com.example.keepyfitness

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.keepyfitness.utils.WeatherHelper
import android.widget.Toast

class HomeScreen : AppCompatActivity() {

    private val LOCATION_PERMISSION_REQUEST = 2001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_screen)

        // Kiểm tra quyền vị trí
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
        } else {
            // Có quyền rồi thì gọi luôn
            showWeatherSuggestion()
        }

        // các nút khác ...
    }

    // ✅ Xử lý khi user bấm Allow / Deny
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Người dùng vừa bấm Allow → gọi lại suggest
                showWeatherSuggestion()
            } else {
                // Người dùng từ chối
                AlertDialog.Builder(this)
                    .setTitle("Quyền vị trí bị từ chối")
                    .setMessage("Không thể lấy gợi ý tập luyện theo thời tiết nếu không cấp quyền vị trí.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun showWeatherSuggestion() {
        try {
            val weatherHelper = WeatherHelper(this, "73371ff12e460447cff4621d4a956c22")
            weatherHelper.getWeatherSuggestion { suggestion ->
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle("Gợi ý tập luyện hôm nay 🌦️")
                        .setMessage(suggestion)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Không thể lấy gợi ý thời tiết", Toast.LENGTH_SHORT).show()
        }
    }

}
