package com.example.keepyfitness.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class WeatherHelper(private val context: Context, private val apiKey: String) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val client = OkHttpClient()

    @SuppressLint("MissingPermission")
    fun getWeatherSuggestion(callback: (String) -> Unit) {
        // Kiểm tra quyền
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            callback("❌ Chưa có quyền vị trí.")
            return
        }

        // Lấy lastLocation trước
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                fetchWeather(location.latitude, location.longitude, callback)
            } else {
                // Nếu null, request location mới
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    null
                ).addOnSuccessListener { newLocation ->
                    if (newLocation != null) {
                        fetchWeather(newLocation.latitude, newLocation.longitude, callback)
                    } else {
                        callback("❌ Không lấy được vị trí GPS.")
                    }
                }.addOnFailureListener {
                    callback("❌ Lỗi khi lấy vị trí: ${it.message}")
                }
            }
        }.addOnFailureListener {
            callback("❌ Lỗi khi lấy vị trí: ${it.message}")
        }
    }

    private fun fetchWeather(lat: Double, lon: Double, callback: (String) -> Unit) {
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

                        // Kiểm tra lỗi API
                        if (json.has("cod") && json.getInt("cod") != 200) {
                            val msg = json.optString("message", "Không lấy được thời tiết")
                            callback("❌ Lỗi API: $msg")
                            return
                        }

                        // Lấy weather và nhiệt độ
                        val weather = json.getJSONArray("weather")
                            .getJSONObject(0)
                            .getString("main")
                        val temp = json.getJSONObject("main").getDouble("temp") // °C

                        // Sinh suggestion dựa trên weather + temp
                        val suggestion = when {
                            weather == "Rain" || weather == "Thunderstorm" || weather == "Drizzle" ->
                                "🌧️ Trời mưa – tập trong nhà: Plank, Squat."
                            temp < 15 -> "🥶 Trời lạnh, ưu tiên tập trong nhà: Plank, Squat."
                            temp in 15.0..25.0 && weather == "Clear" ->
                                "☀️ Thời tiết mát, ra ngoài chạy bộ hoặc Jumping Jack."
                            temp in 15.0..25.0 && weather == "Clouds" ->
                                "⛅ Trời râm mát, chạy bộ nhẹ ngoài trời."
                            temp > 30 -> "🥵 Trời nóng, tập trong nhà và nhớ uống đủ nước đầy đủ."
                            else -> "⚡ Thời tiết không ổn định, ưu tiên tập trong nhà."
                        }

                        callback(suggestion)

                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback("❌ Lỗi khi phân tích dữ liệu thời tiết.")
                    }
                } else {
                    callback("❌ Không lấy được dữ liệu thời tiết.")
                }
            }
        })
    }
}
