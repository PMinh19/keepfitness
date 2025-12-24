package com.example.keepyfitness.Model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_goals")
data class UserGoals(
    @PrimaryKey val id: Int = 1,  // Chỉ 1 record cho user
    val dailyCalorieGoal: Double = 500.0,
    val dailyStepsGoal: Int = 10000
)
