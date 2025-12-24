package com.example.keepyfitness.Model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "schedules")
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val exercise: String,
    val time: String,
    val days: List<String>,  // Back to List<String> for Firestore compatibility
    val quantity: Int
) {
    // Thêm constructor không tham số
    constructor() : this(0, "", "", emptyList(), 0)
}

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromString(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun fromList(list: List<String>): String {
        return gson.toJson(list)
    }
}