package com.example.keepyfitness.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class WeatherHelper(private val context: Context, private val apiKey: String) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val client = OkHttpClient()

    fun getWeatherSuggestion(callback: (String) -> Unit) {
        // ✅ Kiểm tra quyền trước khi gọi GPS
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            callback("❌ Chưa có quyền vị trí.")
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val lat = location.latitude
                val lon = location.longitude
                val url =
                    "https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lon&appid=$apiKey&units=metric&lang=vi"

                val request = Request.Builder().url(url).build()
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        callback("❌ Không lấy được dữ liệu thời tiết.")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val data = response.body?.string()
                        if (data != null) {
                            try {
                                val json = JSONObject(data)

                                // check lỗi API
                                if (json.has("cod") && json.getInt("cod") != 200) {
                                    val msg = json.optString("message", "Không lấy được thời tiết")
                                    callback("❌ Lỗi API: $msg")
                                    return
                                }

                                // parse bình thường
                                val weather = json.getJSONArray("weather")
                                    .getJSONObject(0)
                                    .getString("main")

                                val suggestion = when (weather) {
                                    "Rain", "Thunderstorm", "Drizzle" ->
                                        "🌧️ Trời mưa – hãy tập trong nhà: Plank, Squat."

                                    "Clear" ->
                                        "☀️ Trời đẹp – ra ngoài chạy bộ hoặc Jumping Jack."

                                    "Clouds" ->
                                        "⛅ Trời râm mát – bạn có thể chạy bộ nhẹ ngoài trời."

                                    else ->
                                        "⚡ Thời tiết không ổn định – ưu tiên tập trong nhà."
                                }
                                callback(suggestion)

                            } catch (e: Exception) {
                                e.printStackTrace()
                                callback("❌ Lỗi khi phân tích dữ liệu thời tiết.")
                            }
                        } else {
                            callback("Không lấy được dữ liệu thời tiết.")
                        }
                    }

                })
            }
        }
    }
}
