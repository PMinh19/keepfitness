package com.example.keepyfitness

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.example.keepyfitness.Model.FoodItem
import com.example.keepyfitness.Model.FoodDao
import com.example.keepyfitness.Model.Schedule
import com.example.keepyfitness.Model.Converters
import com.example.keepyfitness.Model.UserGoals
import com.example.keepyfitness.Model.WorkoutHistory
import com.example.keepyfitness.Model.PersonalRecord

@Database(entities = [FoodItem::class, UserGoals::class, Schedule::class, WorkoutHistory::class, PersonalRecord::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    // Thêm DAOs cho các entities khác nếu cần

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "keepfitness_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}