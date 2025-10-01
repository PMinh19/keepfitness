package com.example.keepyfitness.Model

import com.google.firebase.firestore.PropertyName
import java.io.Serializable

data class WorkoutHistory(
    @PropertyName("id") val id: String = System.currentTimeMillis().toString(),
    @PropertyName("exerciseId") val exerciseId: Int = 0,
    @PropertyName("exerciseName") val exerciseName: String = "",
    @PropertyName("count") val count: Int = 0,
    @PropertyName("targetCount") val targetCount: Int = 0,
    @PropertyName("date") val date: Long = System.currentTimeMillis(),
    @PropertyName("duration") val duration: Long = 0L,
    @PropertyName("caloriesBurned") val caloriesBurned: Double = 0.0,
    @PropertyName("isCompleted") val isCompleted: Boolean = false
) : Serializable

data class PersonalRecord(
    @PropertyName("exerciseId") val exerciseId: Int = 0,
    @PropertyName("exerciseName") val exerciseName: String = "",
    @PropertyName("maxCount") val maxCount: Int = 0,
    @PropertyName("bestDate") val bestDate: Long = 0L,
    @PropertyName("totalWorkouts") val totalWorkouts: Int = 0,
    @PropertyName("averageCount") val averageCount: Double = 0.0
) : Serializable