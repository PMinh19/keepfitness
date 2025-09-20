package com.example.keepyfitness.Model

import java.io.Serializable
import java.util.Date

data class WorkoutHistory(
    val id: String = System.currentTimeMillis().toString(),
    val exerciseId: Int,
    val exerciseName: String,
    val count: Int,
    val targetCount: Int,
    val date: Long = System.currentTimeMillis(),
    val duration: Long, // in seconds
    val caloriesBurned: Double = 0.0,
    val isCompleted: Boolean = false
) : Serializable

data class PersonalRecord(
    val exerciseId: Int,
    val exerciseName: String,
    val maxCount: Int,
    val bestDate: Long,
    val totalWorkouts: Int,
    val averageCount: Double
) : Serializable
