package com.example.keepyfitness.Model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FoodDao {
    @Query("SELECT * FROM food_items WHERE name = :name")
    suspend fun getFoodByName(name: String): FoodItem?

    @Query("SELECT * FROM food_items")
    suspend fun getAllFoods(): List<FoodItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoods(foods: List<FoodItem>)
}